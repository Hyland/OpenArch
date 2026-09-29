/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.document.test;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static com.hyland.openarch.ecm.core.SysEmbeddingsPolicies.EMBEDDINGS_NAME_PREFIX;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;
import static com.hyland.openarch.rest.document.AbstractDocumentAPI.PATH;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.getJsonPath;
import static com.hyland.openarch.rest.exception.RepositoryErrorResponse.ENTITY_TYPE_PROP_NAME;
import static com.hyland.openarch.rest.exception.RepositoryErrorResponse.STATUS_PROP_NAME;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.hyland.openarch.rest.exception.RepositoryExceptionController;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.SysEmbeddingsViolation;
import com.hyland.openarch.rest.document.DocumentAPI;
import com.hyland.openarch.rest.document.DocumentMultipartController;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.LogFeature;
import com.hyland.openarch.runtime.test.runner.LoggerLevel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.ws.rs.core.Response.StatusType;

/**
 * REST API tests for SysEmbeddings document type operations.
 *
 * This test suite validates the REST API layer for SysEmbeddings document type
 * creation,
 * validation, and mixin management under a generic SysContent-derived parent.
 *
 * The tests are organized by acceptance rules to ensure comprehensive coverage
 * of:
 * - SysHasEmbeddings mixin lifecycle management
 * - Parent mixin requirements enforcement
 * - Name validation, auto-prefixing, and enforcement policies
 * - SysEmbeddings creation and validation via REST API
 *
 * Each acceptance rule groups related positive and negative test cases for
 * easier
 * maintenance and understanding of the business logic at the REST API layer.
 */
@SpringBootTest(classes = { DocumentAPI.class, DocumentMultipartController.class, DocumentAPITestConfiguration.class })
@TestPropertySource(properties = { "nuxeo.upload.handler.s3.enabled=false" })
@Features({ LogFeature.class })
public class DocumentAPISysEmbeddingsDoctypeTest extends AbstractAPITest {

    public static final String SYSEMB_EMBEDDINGS_FILENAME = SYSEMB_EMBEDDINGS + "." + KEY_BLOB_FILENAME;
    public static final String SYSEMB_EMBEDDINGS_DIGEST = SYSEMB_EMBEDDINGS + "." + KEY_BLOB_DIGEST;
    public static final String SYSEMB_EMBEDDINGS_LENGTH = SYSEMB_EMBEDDINGS + "." + KEY_BLOB_LENGTH;
    public static final String SYSEMB_EMBEDDINGS_MIME_TYPE = SYSEMB_EMBEDDINGS + "." + KEY_BLOB_MIME_TYPE;

    // =======================================================================
    // ACCEPTANCE RULE 1: SysHasEmbeddings Mixin Management
    // =======================================================================

    /**
     * Removing SysHasEmbeddings mixin from parent without SysEmbeddings children
     * should succeed.
     */
    @Test
    @WithMockUser(ADMIN)
    public void testRemoveSysHasEmbeddingsOnParentWithoutChildShouldSucceed() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // when removing the SysHasEmbeddings mixin
        // then it succeeds
        removingSysHasEmbeddings(id, OK);
    }

    /**
     * Removing SysHasEmbeddings mixin from parent with SysEmbeddings children should
     * fail with conflict error.
     */
    @Test
    @WithMockUser(ADMIN)
    @LoggerLevel(klass = RepositoryExceptionController.class, level = "OFF")
    public void testRemoveSysHasEmbeddingsOnParentWithSysEmbeddingsChildShouldConflict() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // given also the doc has embeddings children
        createEmbeddingsWithBlob(id, "foo", OK);

        // when removing the SysHasEmbeddings mixin
        // then it fails
        removingSysHasEmbeddings(id, CONFLICT);
    }

    // =======================================================================
    // ACCEPTANCE RULE 2: Parent Mixin Requirement
    // =======================================================================

    /**
     * Creating SysEmbeddings under a parent without SysHasEmbeddings mixin should
     * fail with validation error.
     */
    @Test
    @WithMockUser(ADMIN)
    @LoggerLevel(klass = RepositoryExceptionController.class, level = "OFF")
    public void testCreateSysEmbeddingsWithoutSysHasEmbeddingsShouldFail() throws Exception {
        // given a doc without SysHasEmbeddings mixin
        String id = createDocWithoutSysHasEmbeddings();

        // when creating an embeddings child
        // then it fails
        var result = createEmbeddingsWithBlob(id, "foo", UNPROCESSABLE_ENTITY);
        // with parent missing mixin error
        result.andExpectAll( //
                jsonPath("errors.length()").value(1), //
                jsonPath("errors[0].path").value(SYS_PARENT_ID),
                jsonPath("errors[0].messageKey").value(SysEmbeddingsViolation.PARENT_MISSING_MIXIN_KEY));
    }

    // =======================================================================
    // ACCEPTANCE RULE 3: Name Validation & Enforcement
    // =======================================================================

    /**
     * Creating SysEmbeddings with non-prefixed name and enforcement enabled should
     * fail with name prefix error.
     */
    @Test
    @WithMockUser(ADMIN)
    @LoggerLevel(klass = RepositoryExceptionController.class, level = "OFF")
    public void testCreateSysEmbeddingsWithNonPrefixedNameAndEnforcementShouldFail() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // when creating an embedding child with non-prefixed name and enforcement
        // then it fails
        var result = createEmbeddingsWithBlob(id, "unprefixed_embeddings_name", true, UNPROCESSABLE_ENTITY);
        // with name prefix error
        result.andExpectAll( //
                jsonPath("errors.length()").value(1), //
                jsonPath("errors[0].path").value(SYS_NAME),
                jsonPath("errors[0].messageKey").value(SysEmbeddingsViolation.NAME_PREFIX_KEY));
    }

    /**
     * Creating SysEmbeddings with valid prefixed name and enforcement enabled
     * should succeed.
     */
    @Test
    @WithMockUser(ADMIN)
    public void testCreateSysEmbeddingsWithValidNameAndEnforcementShouldSucceed() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // when creating an embedding child with prefixed name and enforcement
        // then it succeeds
        String name = EMBEDDINGS_NAME_PREFIX + "foo";
        var result = createEmbeddingsWithBlob(id, name, true, OK);
        // with exact name
        result.andExpectAll( //
                jsonPath(SYS_PRIMARY_TYPE).value(SYS_EMBEDDINGS), //
                jsonPath(SYS_NAME).value(name), //
                jsonPath(SYSEMB_EMBEDDINGS_FILENAME).value(TEST_FILENAME));
    }

    /**
     * Creating SysEmbeddings with non-prefixed name and enforcement disabled should succeed with auto-prefixing.
     */
    @Test
    @WithMockUser(ADMIN)
    public void testCreateSysEmbeddingsWithNonPrefixedNameAndNoEnforcementShouldAutoPrefix() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // when creating an embedding child with non-prefixed name and no enforcement
        // then it succeeds
        String sysEmbeddingName = "unprefixed";
        var result = createEmbeddingsWithBlob(id, sysEmbeddingName, false, OK);
        // with auto-prefixed name
        result.andExpectAll( //
                jsonPath(SYS_PRIMARY_TYPE).value(SYS_EMBEDDINGS), //
                jsonPath(SYS_NAME).value(Matchers.startsWith(EMBEDDINGS_NAME_PREFIX.concat(sysEmbeddingName))),
                // ensure no double-prefix like _e__e_unprefixed
                jsonPath(SYS_NAME)
                        .value(Matchers.not(Matchers.startsWith(EMBEDDINGS_NAME_PREFIX + EMBEDDINGS_NAME_PREFIX))),
                jsonPath(SYSEMB_EMBEDDINGS_FILENAME).value(TEST_FILENAME));
    }

    /**
     * Creating duplicate SysEmbeddings names without enforcement should succeed
     * with auto-deduplication.
     */
    @Test
    @WithMockUser(ADMIN)
    public void testCreateDuplicateSysEmbeddingsNamesWithoutEnforcementShouldAutoRename() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // given also an existing embeddings child
        String name = EMBEDDINGS_NAME_PREFIX + "foo";
        createEmbeddingsWithBlob(id, name, OK);

        // when creating another embeddings child with same name without enforcement
        // then it succeeds
        var result = createEmbeddingsWithBlob(id, name, false, OK);
        // with renamed name
        result.andExpectAll( //
                jsonPath(SYS_PRIMARY_TYPE).value(SYS_EMBEDDINGS),
                jsonPath(SYS_NAME).value(Matchers.not(name)),
                jsonPath(SYS_NAME).value(Matchers.startsWith(name + ".")),
                jsonPath(SYSEMB_EMBEDDINGS_FILENAME).value(TEST_FILENAME));
    }

    /**
     * Creating duplicate SysEmbeddings names with enforcement should fail with
     * conflict error.
     */
    @Test
    @WithMockUser(ADMIN)
    @LoggerLevel(klass = RepositoryExceptionController.class, level = "OFF")
    public void testCreateDuplicateSysEmbeddingsNamesWithEnforcementShouldConflict() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // given also an existing embeddings child
        String name = EMBEDDINGS_NAME_PREFIX + "foo";
        createEmbeddingsWithBlob(id, name, OK);

        // when creating another embeddings child with same name with enforcement
        // then it fails
        var result = createEmbeddingsWithBlob(id, name, true, CONFLICT);
        // with conflict
        result.andExpectAll( //
                jsonPath(ENTITY_TYPE_PROP_NAME).value("exception"),
                jsonPath(STATUS_PROP_NAME).value(CONFLICT.getStatusCode()));
    }

    // =======================================================================
    // ACCEPTANCE RULE 4: SysEmbeddings Creation & Validation
    // =======================================================================

    /**
     * Creating SysEmbeddings via multipart upload should succeed with full
     * validation including file properties.
     */
    @Test
    @WithMockUser(ADMIN)
    public void testCreateSysEmbeddingsViaMultipartShouldSucceed() throws Exception {
        // given a doc with SysHasEmbeddings mixin
        String id = createDocWithSysHasEmbeddings();

        // when creating an embedding child
        String name = EMBEDDINGS_NAME_PREFIX + "foo";
        var result = createEmbeddingsWithBlob(id, name, OK);

        // then embeddings is there with its properties
        result.andExpectAll( //
                jsonPath(SYS_PRIMARY_TYPE).value(SYS_EMBEDDINGS),
                jsonPath(SYSEMB_EMBEDDINGS_FILENAME).value(TEST_FILENAME),
                jsonPath(SYSEMB_EMBEDDINGS_MIME_TYPE).value("text/plain"),
                jsonPath(SYSEMB_EMBEDDINGS_LENGTH).value(12),
                jsonPath(SYSEMB_EMBEDDINGS_DIGEST).exists());
    }

    // =======================================================================

    static final String TEST_FILENAME = "test.txt"; // from MultiPartTestHelper

    String getApiPath(String id) {
        return String.format("%s/%s", PATH, id);
    }

    String createDocWithoutSysHasEmbeddings() throws Exception {
        String json = """
                {
                  "sys_primaryType": "TestDocument",
                  "sys_title": "Test Parent"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, json, getApiPath(ROOT_ID));
        return getJsonPath(result, SYS_ID);
    }

    String createDocWithSysHasEmbeddings() throws Exception {
        String json = """
                {
                  "sys_primaryType": "TestDocument",
                  "sys_mixinTypes": ["SysHasEmbeddings"],
                  "sys_title": "Test Parent"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, json, getApiPath(ROOT_ID));
        return getJsonPath(result, SYS_ID);
    }

    ResultActions removingSysHasEmbeddings(String parentId, StatusType expectedStatus) throws Exception {
        String removeMixinJson = """
                {
                  "sys_mixinTypes": []
                }
                """;
        return checkResponseWithPayload(expectedStatus, PUT, REPO_ID, removeMixinJson, getApiPath(parentId));
    }

    ResultActions createEmbeddingsWithBlob(String parentId, String name, StatusType expectedStatus) throws Exception {
        return createEmbeddingsWithBlob(parentId, name, null, expectedStatus);
    }

    ResultActions createEmbeddingsWithBlob(String parentId, String name, Boolean enforce, StatusType expectedStatus)
            throws Exception {
        String json = String.format("""
                {
                  "sys_primaryType": "SysEmbeddings",
                  "sys_name": "%s",
                  "sys_title": "My Embeddings",
                  "sysemb_embeddings": { "uploadId": "mainUploadId" }
                }
                """, name);
        MockMultipartFile[] parts = new MockMultipartFile[] { MultiPartTestHelper.getMainUploadIdPart() };
        String urlParameters = enforce == null ? "" : "?enforceSysName=" + enforce;
        return checkResponseWithPayloadForMultipartFormData(expectedStatus, POST, REPO_ID, json,
                getApiPath(parentId) + urlParameters, parts);
    }

}
