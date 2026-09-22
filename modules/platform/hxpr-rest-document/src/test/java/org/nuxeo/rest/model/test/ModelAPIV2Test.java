/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nuxeo.rest.model.test;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;

import static org.hamcrest.Matchers.hasSize;
import static org.nuxeo.ecm.core.schema.TypeServiceImpl.CONFIG_TYPES;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_CONFIG_VERSION;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.model.Constants;
import org.nuxeo.rest.model.ModelAPI;
import org.nuxeo.rest.model.ModelManager;
import org.nuxeo.rest.model.validation.ModelValidator;
import org.nuxeo.rest.utils.Versions;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Model REST API v2 tests, focused on the optimistic-locking behaviour.
 * <p>
 * The {@code @BeforeEach} resets the model via the version-unaware {@link TypeService#setSchemaManager}, which persists
 * a configuration document with no version field. That is exactly the "pre-optimistic-locking" state, so every test
 * starts from version {@code 0}.
 */
@Features(CoreFeature.class)
@SpringBootTest(classes = { ModelAPI.class, ModelManager.class, ModelValidator.class })
class ModelAPIV2Test extends AbstractAPITest {

    @RuntimeInject
    protected CoreFeature coreFeature;

    private TypeService typeService;

    @BeforeEach
    void setUp() {
        // Optimistic locking is enforced by the persistence engine, which only the MongoDB backend provides
        // (MongoDBRepository implements ConfigurationServiceIO). The in-memory backend registers no engine, so config
        // stays in an in-JVM map and version tracking is a no-op; skip there rather than assert behaviour it cannot
        // provide (and which is moot for a single-JVM, non-persistent backend).
        assumeTrue(coreFeature.getStorageConfiguration().isDBSMongoDB(),
                "Optimistic locking is only enforced on the MongoDB backend");
        typeService = Framework.getService(TypeService.class);
        typeService.setSchemaManager(REPO_ID, typeService.getDefaultSchemaManager());
        // the write above atomically bumps the version; strip it so each test starts from a deterministic, version-less
        // baseline (as a freshly-bootstrapped or pre-optimistic-locking config would be), i.e. version 0
        coreFeature.getStorageConfiguration().runWithMongoDatabase(db ->
                db.getCollection(coreFeature.getStorageConfiguration().getRepositoryName() + ".config")
                  .updateOne(Filters.eq(MONGODB_ID, CONFIG_TYPES), Updates.unset(MONGODB_CONFIG_VERSION)));
    }

    /** Headers selecting the v2 media type (the default helper Accept is the v1 media type). */
    private HttpHeaders v2Headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HXCS_REPOSITORY, REPO_ID);
        headers.set(ACCEPT, Versions.APP_HXCS_JSON_V2);
        return headers;
    }

    private HttpHeaders v2PutHeaders() {
        HttpHeaders headers = v2Headers();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        return headers;
    }

    private JSONObject getModel() throws Exception {
        ResultActions result = checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2);
        return new JSONObject(result.andReturn().getResponse().getContentAsString());
    }

    /**
     * Returns a copy of {@code model} whose content actually differs, by adding a field to a schema. Writing an
     * unchanged model is a no-op that does not advance the version, so a test that expects an increment must change
     * something.
     */
    private static JSONObject withExtraField(JSONObject model, String fieldName) throws JSONException {
        JSONObject changed = new JSONObject(model.toString());
        changed.getJSONObject("schemas")
               .getJSONObject("mydublincore")
               .getJSONObject("fields")
               .put(fieldName, new JSONObject().put("type", "string"));
        return changed;
    }

    /** A project-scoped model update carrying the read version, for the v2 project endpoints. */
    private static final String PROJECT_UPDATE_V2 = """
            {
              "version": 0,
              "types": {
                "mytype": {
                  "list": { "type": "string" }
                }
              },
              "schemas": {
                "myschema": {
                  "prefix": "my",
                  "fields": {
                    "my_field": { "type": "string" }
                  }
                }
              },
              "mixinTypes": {
                "MyMixin": {
                }
              },
              "primaryTypes": {
                "MyFile": {
                  "extends": "SysContent",
                  "mixins": [
                    "MyMixin"
                  ],
                  "schemas": [
                    "system",
                    "myschema"
                  ]
                }
              }
            }
            """;

    @Test
    void testGetReturnsVersion() throws Exception {
        // a freshly (re)initialized model with no versioned write yet reports version 0
        checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void testCleanUpdateIncrementsVersion() throws Exception {
        JSONObject model = getModel();
        long version = model.getLong("version");

        // a real change, sending the version we read: this must succeed and bump the version
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), withExtraField(model, "mydc_v2a").toString(),
                Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(version + 1));

        // a subsequent read reflects the new version
        checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void testUnchangedUpdateIsNoOpAndKeepsVersion() throws Exception {
        JSONObject model = getModel();
        long version = model.getLong("version");

        // putting back the very same content changes nothing, so no write happens and the version stays put: this makes
        // concurrent deployments of an identical content model free (no conflicts, no version inflation)
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), model.toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version));

        // and it is still unchanged after a fresh read, however many times it is applied
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), model.toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version));
        checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void testUnchangedUpdateWithNumericConstraintIsNoOp() throws Exception {
        JSONObject model = getModel();
        long version = model.getLong("version");
        // a numeric constraint is where a strict map comparison could fail to see two identical models as identical:
        // the same bound can be an Integer coming from JSON and a Long coming from the constraint layer
        JSONObject withConstraint = new JSONObject(model.toString());
        withConstraint.getJSONObject("schemas")
                      .getJSONObject("mydublincore")
                      .getJSONObject("fields")
                      .put("mydc_len", new JSONObject().put("type", "string")
                                                       .put("constraints", new JSONObject().put("maxLength", 10)));
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), withConstraint.toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version + 1));

        // re-applying the very same payload must be a no-op, whatever numeric type the bound round-tripped as
        checkResponseWithPayload(OK, PUT, v2PutHeaders(),
                withConstraint.put("version", version + 1).toString(), Constants.API_PATH_V2)
                        .andExpect(jsonPath("$.version").value(version + 1));

        // and so must putting back exactly what GET returns
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), getModel().toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void testUnchangedUpdateWithStaleVersionStillConflicts() throws Exception {
        JSONObject model = getModel();
        long staleVersion = model.getLong("version");

        // move the persisted version forward with a real change
        JSONObject changed = withExtraField(model, "mydc_v2b");
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), changed.toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(staleVersion + 1));

        // re-sending that same content with the now-stale version is still rejected: a no-op write does not excuse a
        // stale read, since the caller's version would otherwise be wrong
        checkResponseWithPayload(CONFLICT, PUT, v2PutHeaders(), changed.toString(), Constants.API_PATH_V2);
    }

    @Test
    void testStaleVersionConflicts() throws Exception {
        JSONObject model = getModel();
        long staleVersion = model.getLong("version");

        // first write succeeds and moves the persisted version forward
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), withExtraField(model, "mydc_v2c").toString(),
                Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(staleVersion + 1));

        // a second write still carrying the now-stale version is rejected with 409 Conflict
        checkResponseWithPayload(CONFLICT, PUT, v2PutHeaders(), withExtraField(model, "mydc_v2d").toString(),
                Constants.API_PATH_V2);

        // after a fresh read the retry succeeds
        JSONObject fresh = getModel();
        long freshVersion = fresh.getLong("version");
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), withExtraField(fresh, "mydc_v2d").toString(),
                Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(freshVersion + 1));
    }

    @Test
    void testMissingVersionIsBadRequest() throws Exception {
        // start from a valid model, then drop the version: a v2 write must carry it, so this is a 400 Bad Request
        JSONObject model = getModel();
        model.remove("version");
        checkResponseWithPayload(BAD_REQUEST, PUT, v2PutHeaders(), model.toString(), Constants.API_PATH_V2);
        // a validate-only request describes the write it is checking, so it carries a version too
        checkResponseWithPayload(BAD_REQUEST, PUT, v2PutHeaders(), model.toString(),
                Constants.API_PATH_V2 + PARAM_VALIDATE_ONLY_TRUE);
    }

    @Test
    void testWriteCreatesMissingConfig() throws Exception {
        // simulate a repository whose content-model config document does not exist yet (nothing bootstrapped it): a v2
        // write with version 0 must create it and start the counter at 1, not fail with a spurious 409
        coreFeature.getStorageConfiguration().runWithMongoDatabase(db ->
                db.getCollection(coreFeature.getStorageConfiguration().getRepositoryName() + ".config")
                  .deleteOne(Filters.eq(MONGODB_ID, CONFIG_TYPES)));

        JSONObject model = getModel();
        model.put("version", 0);
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), model.toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void testPutV2ValidateOnlyDoesNotPersist() throws Exception {
        JSONObject model = getModel();
        long version = model.getLong("version");
        // a validate-only write returns 204 and persists nothing
        checkResponseWithPayload(NO_CONTENT, PUT, v2PutHeaders(), model.toString(),
                Constants.API_PATH_V2 + PARAM_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // nothing was persisted: the version is unchanged
        checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void testPutForProjectV2() throws Exception {
        // sets project-scoped items with the read version; returns the full model with the incremented version
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), PROJECT_UPDATE_V2, Constants.API_PATH_V2 + PROJECT_PF_00000)
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.mixinTypes.MyMixin.projectId").value(PF_00000));
    }

    /**
     * Reproduces a cluster invalidation lag: the schema is added straight to storage, so the persisted model (which the
     * version refers to) has it while this node's local model does not.
     */
    private void addSchemaToStorageOnly(String schemaName, long newVersion) {
        Document schema = new Document("prefix", schemaName).append("fields",
                new Document(schemaName + "_field", new Document("type", "string")));
        coreFeature.getStorageConfiguration()
                   .runWithMongoDatabase(db -> db.getCollection(
                           coreFeature.getStorageConfiguration().getRepositoryName() + ".config")
                                                 .updateOne(Filters.eq(MONGODB_ID, CONFIG_TYPES),
                                                         Updates.combine(
                                                                 Updates.set("schemas." + schemaName, schema),
                                                                 Updates.set(MONGODB_CONFIG_VERSION, newVersion))));
    }

    /**
     * A v2 write reports its diff against the persisted model its version refers to, not against the node-local model,
     * which lags behind while a cluster invalidation is in flight.
     * <p>
     * Only a diff against the persisted state can tell that this request <em>modifies</em> the schema rather than adding
     * it, which validate-only reports as a violation. Against the stale local model the change looks like an addition
     * and is reported as clean.
     */
    @Test
    void testPutV2ValidateOnlyDiffsAgainstPersistedModel() throws Exception {
        addSchemaToStorageOnly("laggy", getModel().getLong("version") + 1);

        // the v2 read reflects storage, so the client legitimately holds the schema and the version that goes with it
        JSONObject model = getModel();
        model.getJSONObject("schemas")
             .getJSONObject("laggy")
             .getJSONObject("fields")
             .put("laggy_field", new JSONObject().put("type", "long"));

        checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, v2PutHeaders(), model.toString(),
                Constants.API_PATH_V2 + PARAM_VALIDATE_ONLY_TRUE);
    }

    /**
     * The project-ownership checks of a v2 project write run against the persisted model too, so an item that already
     * exists in the default model cannot be claimed for a project just because this node hasn't seen it yet.
     */
    @Test
    void testPutForProjectV2ChecksOwnershipAgainstPersistedModel() throws Exception {
        long version = getModel().getLong("version") + 1;
        addSchemaToStorageOnly("laggy", version);

        String claimsLaggySchema = """
                {
                  "version": %d,
                  "schemas": {
                    "laggy": {
                      "prefix": "laggy",
                      "fields": {
                        "laggy_field": { "type": "string" }
                      }
                    }
                  }
                }
                """.formatted(version);
        // the payload is refused, which is an UNPROCESSABLE_ENTITY and not the CONFLICT of a stale version: nothing
        // is wrong with the version sent, so re-reading and retrying would not help
        checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, v2PutHeaders(), claimsLaggySchema,
                Constants.API_PATH_V2 + PROJECT_PF_00000);
    }

    /**
     * The project-ownership checks of the v2 project endpoint run against the persisted model the write is conditioned
     * on, so claiming an item that already belongs to the default model for a project is rejected.
     */
    @Test
    void testPutForProjectV2ConflictWithDefaultModel() throws Exception {
        String claimsDefaultItems = """
                {
                  "version": 0,
                  "mixinTypes": {
                    "SysFolderish": {
                      "noPerDocumentQuery": true
                    }
                  },
                  "primaryTypes": {
                    "SysContent": {
                    }
                  }
                }
                """;
        checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, v2PutHeaders(), claimsDefaultItems,
                Constants.API_PATH_V2 + PROJECT_PF_00000);
    }

    @Test
    void testGetForProjectV2() throws Exception {
        // seed project-scoped items
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), PROJECT_UPDATE_V2, Constants.API_PATH_V2 + PROJECT_PF_00000)
                .andExpect(jsonPath("$.version").value(1));
        // the project GET returns only that project's items, tagged with the projectId, plus the version
        checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2 + PROJECT_PF_00000)
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.mixinTypes.MyMixin.projectId").value(PF_00000))
                .andExpect(jsonPath("$.schemas.myschema.projectId").value(PF_00000))
                .andExpect(jsonPath("$.mixinTypes.*", hasSize(1)));
    }

    @Test
    void testV1PutAdvancesVersionInternally() throws Exception {
        // a v1 write is version-unaware to clients, but must still advance the counter (not reset it) so a concurrent
        // v2 writer stays protected; the increment is observable only through the v2 read
        JSONObject model = getModel();
        long version = model.getLong("version");
        JSONObject changed = withExtraField(model, "mydc_v1a");
        changed.remove("version"); // a v1 payload carries no version
        checkResponseWithPayload(OK, PUT, REPO_ID, changed.toString(), Constants.API_PATH);
        checkResponse(OK, GET, v2Headers(), Constants.API_PATH_V2).andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void testMigrationFromVersionlessState() throws Exception {
        // the initial state has no persisted version (written by the version-unaware setSchemaManager), simulating
        // configuration written before optimistic locking existed; a v2 write with version 0 must succeed and start
        // the counter at 1
        JSONObject model = withExtraField(getModel(), "mydc_mig");
        model.put("version", 0);
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), model.toString(), Constants.API_PATH_V2)
                .andExpect(jsonPath("$.version").value(1));
    }
}
