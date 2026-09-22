/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.rest.model.Constants.API_PATH;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.document.test.ContentTestHelper;
import org.nuxeo.rest.model.ModelAPI;
import org.nuxeo.rest.model.ModelManager;
import org.nuxeo.rest.model.validation.ModelValidator;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Model REST API tests.
 */
@Features(CoreFeature.class)
@SpringBootTest(classes = { ModelAPI.class, ModelManager.class, ModelValidator.class })
class ModelAPITest extends AbstractAPITest {

    private static final String PATH_VALIDATE_ONLY_TRUE = API_PATH + PARAM_VALIDATE_ONLY_TRUE;

    private static final String PATH_VALIDATE_ONLY_TRUE_FOR_PROJECT =
            API_PATH + PROJECT_PF_00000 + PARAM_VALIDATE_ONLY_TRUE;


    private static final String NEW_TYPE_PARENT = "NewTypeParent";
    private static final String NEW_TYPE_CHILD = "NewTypeChild";
    private static final String PUT_FOR_PROJECT_PATH = API_PATH + "/project/myproject";
    private static final String PUT_FOR_PROJECT_BASE_BODY = """
                {
                   "primaryTypes": {
                     "NewTypeParent": {
                       "extends": "%s",
                       "schemas": [
                         "system"
                       ]
                     },
                     "NewTypeChild": {
                       "extends": "%s",
                       "schemas": [
                         "system"
                       ]
                     }
                   }
                 }
           """;
    private static final String EMPTY_PARENT_PUT_FOR_PROJECT_BODY = PUT_FOR_PROJECT_BASE_BODY.formatted(" ", " ");
    private static final String NON_EXISTING_PARENT_PUT_FOR_PROJECT_BODY = PUT_FOR_PROJECT_BASE_BODY.formatted("NotExisting", "NotExisting");
    private static final String EXTENDING_ITSELF_PARENT_PUT_FOR_PROJECT_BODY = PUT_FOR_PROJECT_BASE_BODY.formatted(NEW_TYPE_PARENT, NEW_TYPE_CHILD);
    private static final String LOOP_PARENT_PUT_FOR_PROJECT_BODY = PUT_FOR_PROJECT_BASE_BODY.formatted(NEW_TYPE_CHILD, NEW_TYPE_PARENT);

    private static final String BASE_PARENT_PATCH = """
                                [
                                  {
                                    "op": "add",
                                    "path": "/primaryTypes/NewTypeParent",
                                    "value": {
                                      "extends": "%s",
                                      "schemas": ["system", "sysfile"]
                                    }
                                  },
                                  {
                                    "op": "add",
                                    "path": "/primaryTypes/NewTypeChild",
                                    "value": {
                                      "extends": "%s",
                                      "schemas": ["system", "sysfile"]
                                    }
                                  }
                                ]
                                """;

    private static final String EMPTY_PARENT_PATCH_BODY = BASE_PARENT_PATCH.formatted(" ", " ");
    private static final String NON_EXISTING_PARENT_PATCH_BODY = BASE_PARENT_PATCH.formatted("NotExisting", "NotExisting");
    private static final String EXTENDING_ITSELF_PARENT_PATCH_BODY = BASE_PARENT_PATCH.formatted(NEW_TYPE_PARENT, NEW_TYPE_CHILD);
    private static final String LOOP_PARENT_PATCH_BODY = BASE_PARENT_PATCH.formatted(NEW_TYPE_CHILD, NEW_TYPE_PARENT);

    private TypeService typeService;

    @BeforeEach
    void setUp() {
        typeService = Framework.getService(TypeService.class);
        typeService.setSchemaManager(REPO_ID, typeService.getDefaultSchemaManager());
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    void testGetUnauthenticated() throws Exception {
        mvc.perform(get(API_PATH).header(HXCS_REPOSITORY, REPO_ID))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @Test
    void testGetBadRepo() throws Exception {
        checkResponse(NOT_FOUND, GET, BAD_REPO_ID, API_PATH);
    }

    @Test
    @WithUser(ALT_USER)
    void testGet() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    @WithUser(ALT_USER)
    void testGetV1() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTENT_TYPE, APPLICATION_JSON);
        headers.add(HXCS_REPOSITORY, REPO_ID);
        var result = checkResponse(OK, GET, headers, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    @WithUser(ALT_USER)
    void testGetV0() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, "application/vnd.hxcs.v0+json");
        headers.add(HXCS_REPOSITORY, REPO_ID);
        checkResponse(NOT_ACCEPTABLE, GET, headers, API_PATH);
    }

    @Test
    public void testGetProject() throws Exception {
        // put in project PF00000
        String putForProject = """
                {
                  "types": {
                    "mytype": {
                      "list": { "type" : "string" }
                    }
                  },
                  "schemas": {
                    "myschema": {
                      "prefix": "my",
                      "fields": {
                        "my_field": {
                          "type": "string"
                        }
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
                        "myschema"
                      ]
                    }
                  }
                }
                """;

        JSONObject jsonObject = new JSONObject(putForProject);
        String newBody = jsonObject.toString();
        checkResponseWithPayload(OK, PUT, REPO_ID, newBody, API_PATH + PROJECT_PF_00000);

        // get for just project PF00000
        ResultActions result = checkResponse(OK, GET, REPO_ID, API_PATH + PROJECT_PF_00000);
        String expected = """
                {
                  "types": {
                    "mytype": {
                      "projectId": "PF00000",
                      "list": { "type" : "string" }
                    }
                  },
                  "schemas": {
                    "myschema": {
                      "projectId": "PF00000",
                      "prefix": "my",
                      "fields": {
                        "my_field": {
                          "type": "string"
                        }
                      }
                    }
                  },
                  "mixinTypes": {
                    "MyMixin": {
                      "projectId": "PF00000"
                    }
                  },
                  "primaryTypes": {
                    "MyFile": {
                      "projectId": "PF00000",
                      "extends": "SysContent",
                      "mixins": [
                        "MyMixin"
                      ],
                      "schemas": [
                        "myschema"
                      ]
                    }
                  }
                }
                """;
        checkResponseBodyEquals(expected, result);

        // cleanup: remove everything about project PF00000
        result = checkResponseWithPayload(OK, PUT, REPO_ID, "{}", API_PATH + PROJECT_PF_00000);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    void testPutUnauthenticated(String queryParam) throws Exception {
        mvc.perform(put(API_PATH + queryParam).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPutUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PUT, REPO_ID, "{}", API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, BAD_REPO_ID, "{}", API_PATH + queryParam);
    }

    /**
     * A missing body is a bad request, not a server error. The bodies used to be declared optional,
     * which let a {@code null} model reach the validator and NPE out as a 500.
     */
    @Test
    void testMissingBody() throws Exception {
        mvc.perform(put(API_PATH).header(HXCS_REPOSITORY, REPO_ID)
                .contentType(APPLICATION_JSON).with(csrf())).andExpect(status().is(BAD_REQUEST.getStatusCode()));
        mvc.perform(put(API_PATH + PROJECT_PF_00000).header(HXCS_REPOSITORY, REPO_ID)
                .contentType(APPLICATION_JSON).with(csrf())).andExpect(status().is(BAD_REQUEST.getStatusCode()));
        mvc.perform(patch(API_PATH).header(HXCS_REPOSITORY, REPO_ID)
                .contentType(APPLICATION_JSON_PATCH_JSON).with(csrf()))
           .andExpect(status().is(BAD_REQUEST.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, NOT_JSON, API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPutUnchanged(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = result.andReturn().getResponse().getContentAsString();
        // put it back unchanged
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPut(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = result.andReturn().getResponse().getContentAsString();
        JSONObject jsonObject = new JSONObject(body);
        // change some stuff
        JSONObject field = new JSONObject();
        field.put("type", "string");
        JSONObject fooBar2 = new JSONObject();
        JSONObject fooBar2Constraint = new JSONObject();
        fooBar2Constraint.put("pattern", "[^@]+@[^\\.]+\\..+");
        fooBar2Constraint.put("nullable", true);
        fooBar2.put("extends", "string");
        fooBar2.put("constraints", fooBar2Constraint);

        JSONObject fooBar3 = new JSONObject();
        JSONObject fooBar3Constraint = new JSONObject();
        JSONObject fooBar3Resolver = new JSONObject();
        fooBar3Resolver.put("name", "documentResolver");
        fooBar3Resolver.put("store", "id");
        fooBar3Resolver.put("validation", false);
        fooBar3Constraint.put("resolver", fooBar3Resolver);
        fooBar3Constraint.put("nullable", true);
        fooBar3.put("extends", "string");
        fooBar3.put("constraints", fooBar3Constraint);

        JSONObject fieldsData = jsonObject.getJSONObject("schemas")
                                          .getJSONObject("mydublincore")
                                          .getJSONObject("fields");

        fieldsData.put("mydc_foobar", field);
        fieldsData.put("mydc_foobr", fooBar2);
        fieldsData.put("mydc_foobr3", fooBar3);
        // put new model
        String newBody = jsonObject.toString();
        result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated.patch.json", result);
        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPutValidateTrue() throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = result.andReturn().getResponse().getContentAsString();
        JSONObject jsonObject = new JSONObject(body);

        // change some stuff
        JSONObject field = new JSONObject();
        field.put("type", "string");
        jsonObject.getJSONObject("schemas")
                  .getJSONObject("mydublincore")
                  .getJSONObject("fields")
                  .put("mydc_foobar4", field);
        // put new model
        String newBody = jsonObject.toString();
        result = checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, newBody, PATH_VALIDATE_ONLY_TRUE);
        result.andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    /**
     * The root model PUT runs request-level validation before any schema generation, and a violation there is
     * reported as CONFLICT rather than as the UNPROCESSABLE_ENTITY used by the schema layer. Pin that HTTP
     * boundary so the declared 409 stays backed by an executable assertion.
     */
    @Test
    void testPutValidationConflictWithInvalidBooleanDefault() throws Exception {
        String body = """
                {
                  "schemas": {
                    "hxcs7598": {
                      "prefix": "hxcs7598",
                      "fields": {
                        "badBoolean": {
                          "type": "boolean",
                          "default": "not-a-boolean"
                        }
                      }
                    }
                  }
                }
                """;

        var result = checkResponseWithPayload(CONFLICT, PUT, REPO_ID, body, PATH_VALIDATE_ONLY_TRUE);
        checkResponseBody("rest/validation_error_for_root_model_invalid_boolean_default.json", result);

        // validateOnly=true must not persist, and a rejected model must not persist either
        checkResponseBody("rest/model.json", checkResponse(OK, GET, REPO_ID, API_PATH));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPutWithMixin(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = result.andReturn().getResponse().getContentAsString();
        JSONObject jsonObject = new JSONObject(body);
        // change some stuff
        JSONObject mixinData = jsonObject.getJSONObject("mixinTypes");

        JSONArray schemas = new JSONArray();
        schemas.put(SYSFILE);
        schemas.put("sysfiles");

        JSONObject mixinType = new JSONObject();
        mixinType.put("noPerDocumentQuery", true);
        mixinType.put("schemas", schemas);
        mixinData.put("Test", mixinType);

        // put new model
        String newBody = jsonObject.toString();

        result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_with_mixins.patch.json", result);

        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    void testPatchUnauthenticated(String queryParam) throws Exception {
        String url = API_PATH + queryParam;
        mvc.perform(patch(url).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPatchUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, "{}",
                API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PATCH, APPLICATION_JSON_PATCH_JSON, BAD_REPO_ID, "{}",
                API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, NOT_JSON,
                API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void givenModelValidation_whenReplacementValueIsAnInvalidObjectTypeForTypesAndModelInUse_thenAnInvalidObjectViolationWithAlreadyExistsViolationWhenValidateOnlyTrueShouldBeInResponse(
            String queryParam) throws Exception {
        String path = API_PATH + queryParam;
        String badPatch = """
                [
                      {
                          "op": "replace",
                          "path": "/types/sysfiles#blobitem",
                          "value": "sysfiles#blob-foobar"
                      }
                 ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_type_dto_validate_only.json" :
                "rest/validation_type_dto_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void givenModelValidation_whenReplacementValueIsAnInvalidObjectTypeForSchemasAndModelInUse_thenAnInvalidObjectViolationWithAlreadyExistsViolationWhenValidateOnlyTrueShouldBeInResponse(
            String queryParam) throws Exception {
        String path = API_PATH + queryParam;
        String badPatch = """
              [
                    {
                        "op": "replace",
                        "path": "/schemas/mydublincore/fields/mydc_coverage",
                        "value": ["mydc_coverage-foobar"]
                    },
                    {
                        "op": "replace",
                        "path": "/schemas/sysrelation",
                        "value": ["relation-foobar"]
                    },
                    {
                        "op": "replace",
                        "path": "/schemas/sysfiles/fields",
                        "value": ["files-foobar"]
                    }
               ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_schema_dto_validate_only.json" :
                "rest/validation_schema_dto_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void givenModelValidation_whenReplacementValueIsAnInvalidObjectTypeForPrimaryTypesAndModelInUse_thenAnInvalidObjectViolationWithAlreadyExistsViolationWhenValidateOnlyTrueShouldBeInResponse(
            String queryParam) throws Exception {
        String path = API_PATH + queryParam;
        String badPatch = """
              [
                    {
                        "op": "replace",
                        "path": "/primaryTypes/SysFile",
                        "value": "File-foobar"
                    },
                    {
                        "op": "replace",
                        "path": "/primaryTypes/SysFolder/extends",
                        "value": {"extends-foo": "bar" }
                    },
                    {
                        "op": "replace",
                        "path": "/primaryTypes/SysRelation/schemas",
                        "value": {"schemas-foo": "bar" }
                    },
                    {
                        "op": "add",
                        "path": "/primaryTypes/SysRendition/mixins",
                        "value": "mixins-foobar"
                    },
                    {
                        "op": "replace",
                        "path": "/primaryTypes/SysOrderedFolder/subtypes",
                        "value": {
                            "subtypes-foo" : "bar"
                        }
                    }
               ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)
                ? "rest/validation_primarytype_dto_validate_only.json"
                : "rest/validation_primarytype_dto_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void givenModelValidation_whenReplacementValueIsAnInvalidObjectTypeForMixinTypes_thenAnAlreadyExistViolationShouldBeInResponse(
            String queryParam) throws Exception {
        String path = API_PATH + queryParam;
        String badPatch = """
              [
                     {
                        "op": "replace",
                        "path": "/mixinTypes/SysVersionable",
                        "value": ["Versionable-foobar"]
                    }
               ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_mixintype_dto_validate_only.json" :
                "rest/validation_mixintype_dto_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testValidationErrorsForRemove(String queryParam) throws Exception {
        String path = API_PATH + queryParam;
        String badPatch = """
             [
                    {
                         "op": "remove",
                         "path": "/types/sysfiles#blobs/list"
                    },
                    {
                         "op": "remove",
                         "path": "/types/blob"
                    }
             ]
                 """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_schema_dto_for_remove_validate_only.json" :
                "rest/validation_schema_dto_for_remove_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testValidationErrorsForAdd(String queryParam) throws Exception {
        String path = API_PATH + queryParam;
        String badPatch = """
                [
                       {
                            "op": "add",
                            "path": "/mixinTypes/SysFolderish/schemas",
                            "value":"schemas-foobar"
                        }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_schema_dto_for_add_validate_only.json" :
                "rest/validation_schema_dto_for_add_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @Test
    void testValidateTypesNotAMap() throws Exception {
        String error = """
                {
                    "invalidValue": "badValue",
                    "message": "The value \\"badValue\\" is invalid.",
                    "messageKey": "label.schema.application.violation.InvalidObjectViolation",
                    "parameters": [
                        "badValue",
                        "types"
                    ],
                    "path": "types"
                }
                """;
        String badPatch = """
                [
                    {
                        "op": "replace",
                        "path": "/types",
                        "value": "badValue"
                    }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, API_PATH);
        assertEqualsInJsonPath(error, result, "errors[0]");
    }

    @Test
    void testValidatePrimaryTypesNotAMap() throws Exception {
        String error = """
                {
                    "invalidValue": "badValue",
                    "message": "The value \\"badValue\\" is invalid.",
                    "messageKey": "label.schema.application.violation.InvalidObjectViolation",
                    "parameters": [
                        "badValue",
                        "primaryTypes"
                    ],
                    "path": "primaryTypes"
                }
                """;
        String badPatch = """
                [
                    {
                        "op": "replace",
                        "path": "/primaryTypes",
                        "value": "badValue"
                    }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, API_PATH);
        assertEqualsInJsonPath(error, result, "errors[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testLengthConstraintValidation(String queryParam) throws Exception {
        String path = API_PATH + queryParam;

        // Test min > max throws error
        String lengthConstraintError = """
                {
                    "invalidValue": 2001,
                    "message": "The value \\"2001\\" must be less than the value \\"2000\\".",
                    "messageKey": "label.schema.application.violation.RangeViolation",
                    "parameters": [
                        "schemas/mydublincore/fields/dc_foo/constraints/minLength",
                        2001,
                        "schemas/mydublincore/fields/dc_foo/constraints/maxLength",
                        2000
                    ],
                    "path": "schemas/mydublincore/fields/dc_foo/constraints/minLength"
                }
                """;
        String badPatch = """
                [
                    {
                        "op": "add",
                        "path": "/schemas/mydublincore/fields/dc_foo",
                        "value": {
                            "extends": "long",
                            "constraints":
                            {
                                "minLength": 2001,
                                "maxLength": 2000
                            }
                        }
                    }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        assertEqualsInJsonPath(lengthConstraintError, result, "errors[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testNumericConstraintValidation(String queryParam) throws Exception {
        String path = API_PATH + queryParam;

        String numericConstraintError = """
                {
                    "invalidValue": 1,
                    "message": "The value \\"1\\" must be less than the value \\"0\\".",
                    "messageKey": "label.schema.application.violation.RangeViolation",
                    "parameters":[
                        "schemas/mydublincore/fields/dc_foo/constraints/minInclusive",
                        1,
                        "schemas/mydublincore/fields/dc_foo/constraints/maxExclusive",
                        0
                    ],
                    "path":"schemas/mydublincore/fields/dc_foo/constraints/minInclusive"
                }
                """;

        String badPatch = """
                [
                    {
                        "op": "add",
                        "path": "/schemas/mydublincore/fields/dc_foo",
                        "value": {
                            "extends": "long",
                            "constraints":
                            {
                                "minInclusive": 1,
                                "maxExclusive": 0
                            }
                        }
                    }
                ]
                """;

        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        assertEqualsInJsonPath(numericConstraintError, result, "errors[0]");

        numericConstraintError = """
                {
                    "invalidValue": 0,
                    "message": "The value \\"0\\" must be less than the value \\"0\\".",
                    "messageKey": "label.schema.application.violation.RangeViolation",
                    "parameters": [
                        "schemas/mydublincore/fields/dc_foo/constraints/minExclusive",
                        0,
                        "schemas/mydublincore/fields/dc_foo/constraints/maxInclusive",
                        0
                    ],
                    "path": "schemas/mydublincore/fields/dc_foo/constraints/minExclusive"
                }
                """;

        badPatch = """
                [
                    {
                        "op": "add",
                        "path": "/schemas/mydublincore/fields/dc_foo",
                        "value": {
                            "extends": "long",
                            "constraints":
                            {
                                "minExclusive": 0,
                                "maxInclusive": 0
                            }
                        }
                    }
                ]
                """;

        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                badPatch, path);
        assertEqualsInJsonPath(numericConstraintError, result, "errors[0]");

    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testObjectResolverConstraintValidation(String queryParam) throws Exception {
        String path = API_PATH + queryParam;

        String objectResolverConstraintError = """
                {
                    "invalidValue": {
                        "resolver": {
                            "name": "notAKnownResolver"
                        }
                    },
                    "message": "The resolver \\"notAKnownResolver\\" is not a known resolver.",
                    "messageKey": "label.schema.application.violation.UnknownObjectViolation.constraints.resolver",
                    "parameters": [
                        {
                            "resolver": {
                                "name": "notAKnownResolver"
                            }
                        },
                        "notAKnownResolver",
                        "schemas/mydublincore/fields/dc_foo/constraints"
                    ],
                    "path": "schemas/mydublincore/fields/dc_foo/constraints"
                }
                """;

        String badPatch = """
                [
                    {
                        "op": "add",
                        "path": "/schemas/mydublincore/fields/dc_foo",
                        "value": {
                            "extends": "string",
                            "constraints":
                            {
                                "resolver": {
                                    "name": "notAKnownResolver"
                                }
                            }
                        }
                    }
                ]
                """;

        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        assertEqualsInJsonPath(objectResolverConstraintError, result, "errors[0]");
    }

    @ParameterizedTest
    @MethodSource("provideStringsForConstraintSuccessValidation")
    void testConstraintValidationSuccess(String goodPatch) throws Exception {
        String path = API_PATH + PARAM_VALIDATE_ONLY_TRUE;

        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, goodPatch, path);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchUnchanged(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = """
                [
                { "op": "replace", "path": "/schemas/mydublincore/fields/mydc_coverage", "value": "string" }
                ]
                """;
        // put it back unchanged
        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPatchUnchangedValidateTrue() throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = """
                [
                { "op": "replace", "path": "/schemas/mydublincore/fields/mydc_coverage", "value": {"type":"string"} }
                ]
                """;
        // put it back unchanged
        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                PATH_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchAddOneLine(String queryParam) throws Exception {
        // get original JSON
        String body = """
                [
                { "op": "add", "path": "/schemas/mydublincore/fields/mydc_foobar", "value": {"type":"string"} },
                { "op": "add", "path": "/schemas/mydublincore/fields/mydc_foobr",
                "value": {"constraints": {"pattern": "[^@]+@[^\\\\.]+\\\\..+"}, "extends": "string"}},
                { "op": "add", "path": "/schemas/mydublincore/fields/mydc_foobr3",
                "value": {"constraints": {"resolver": {"name": "documentResolver", "store": "id", "validation": false}},"extends": "string"}}
                ]
                """;

        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated.patch.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchAddProjectId(String queryParam) throws Exception {
        // add projectId to the original JSON
        String body = """
                [
                { "op": "add", "path": "/primaryTypes/SysFile/projectId", "value": "P123" },
                { "op": "add", "path": "/mixinTypes/SysFolderish/projectId", "value": "P123" },
                { "op": "add", "path": "/schemas/mydublincore/projectId", "value": "P123" }
                ]
                """;

        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_withProjectId.patch.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchReplaceProjectId(String queryParam) throws Exception {
        // add projectId to the original JSON

        String body = """
                [
                { "op": "add", "path": "/primaryTypes/SysFile/projectId", "value": "P123" },
                { "op": "add", "path": "/mixinTypes/SysFolderish/projectId", "value": "P123" },
                { "op": "add", "path": "/schemas/mydublincore/projectId", "value": "P123" }
                ]
                """;

        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);
        // replace projectId from the original JSON
        body = """
                [
                { "op": "replace", "path": "/primaryTypes/SysFile/projectId", "value": "P321" },
                { "op": "replace", "path": "/mixinTypes/SysFolderish/projectId", "value": "P321" },
                { "op": "replace", "path": "/schemas/mydublincore/projectId", "value": "P321" }
                ]
                """;

        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_withModifiedProjectId.patch.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchRemoveProjectId(String queryParam) throws Exception {
        // add projectId to the original JSON
        String body = """
                [
                { "op": "add", "path": "/primaryTypes/SysFile/projectId", "value": "P123" },
                { "op": "add", "path": "/mixinTypes/SysFolderish/projectId", "value": "P123" },
                { "op": "add", "path": "/schemas/mydublincore/projectId", "value": "P123" }
                ]
                """;

        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);

        // remove projectId from the original JSON
        body = """
                [
                { "op": "remove", "path": "/primaryTypes/SysFile/projectId" },
                { "op": "remove", "path": "/mixinTypes/SysFolderish/projectId" },
                { "op": "remove", "path": "/schemas/mydublincore/projectId" }
                ]
                """;

        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPatchAddOneLineValidateTrue() throws Exception {
        // get original JSON
        String body = """
                [
                { "op": "add", "path": "/schemas/mydublincore/fields/mydc_foobar", "value": "string" }
                ]
                """;

        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                PATH_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchRemove(String queryParam) throws Exception {
        String primaryTypesWithoutDummyFolder = """
                {
                    "SysContent": {
                        "schemas": [
                            "system"
                        ]
                    },
                    "SysEmbeddings": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysembeddings"
                        ]
                    },
                    "SysFile": {
                        "extends": "SysContent",
                        "mixins": [
                            "SysFilish"
                        ],
                        "schemas": [
                            "mydublincore",
                            "sysfiles"
                        ]
                    },
                    "SysFolder": {
                        "extends": "SysContent",
                        "mixins": [
                            "SysFolderish"
                        ],
                        "schemas": [
                            "mydublincore"
                        ],
                        "subtypes": [
                            "SysFile",
                            "SysFolder",
                            "SysOrderedFolder"
                        ]
                    },
                    "SysMDAnnotation": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysmdanno"
                        ],
                       "mixins": [
                         "SysVersionedWithParent"
                       ]
                    },
                    "SysMDBookmark": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysmdbm"
                        ],
                       "mixins": [
                         "SysVersionedWithParent"
                       ]
                    },
                    "SysMDComment": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysmdcomment"
                        ],
                       "mixins": [
                         "SysVersionedWithParent"
                       ]
                    },
                    "SysOrderedFolder": {
                        "extends": "SysFolder",
                        "mixins": [
                            "SysOrderable"
                        ],
                        "subtypes": [
                            "SysFile",
                            "SysFolder",
                            "SysOrderedFolder"
                        ]
                    },
                    "SysRelation": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysrelation"
                        ]
                    },
                    "SysRendition": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysrendition"
                        ]
                    },
                    "SysRenditionsContainer": {
                        "extends": "SysFolder",
                        "mixins": [
                            "SysVersionedWithParent"
                        ],
                        "subtypes": [
                            "SysRendition"
                        ]
                    },
                    "SysRoot": {
                        "extends": "SysFolder"
                    },
                    "SysEmbeddings": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysembeddings"
                        ]
                    },
                    "SysVocabulary": {
                        "extends": "SysContent",
                        "mixins": [
                            "SysFolderish"
                        ],
                        "schemas": [
                            "sysvocab"
                        ],
                        "subtypes": [
                            "SysVocabularyEntry"
                        ]
                    },
                    "SysVocabularyEntry": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysvocab"
                        ]
                    },
                    "TestDocument": {
                        "extends": "SysContent",
                        "schemas": [
                            "mydublincore",
                            "sysfile",
                            "test-properties"
                        ]
                    }
                }
                """;

        // perform removal Patch
        String removalBody = """
                [
                { "op": "remove", "path": "/primaryTypes/DummyFolder" }
                ]
                """;
        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, removalBody,
                API_PATH + queryParam);
        assertEqualsInJsonPath(primaryTypesWithoutDummyFolder, result, "primaryTypes");
    }

    @Test
    void testPatchRemoveValidateTrue() throws Exception {
        // perform removal Patch
        String removalBody = """
                [
                { "op": "remove", "path": "/primaryTypes/SysOrderedFolder" }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, removalBody, PATH_VALIDATE_ONLY_TRUE);
        assertNoErrors(result);
        // check the model has not been changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchAddAndRemoveToArray(String queryParam) throws Exception {
        // perform add to array Patch
        String orderedFolderPath = "primaryTypes.SysOrderedFolder";
        String addBody = """
                [
                { "op": "add", "path": "/primaryTypes/SysOrderedFolder/mixins/-","value":"SysVersionable"}
                ]
                """;
        String orderedFolderPartAfterAddition = """
                {
                    "extends": "SysFolder",
                    "mixins": [
                        "SysOrderable",
                        "SysVersionable"
                    ],
                    "subtypes": [
                        "SysFile",
                        "SysFolder",
                        "SysOrderedFolder"
                    ]
                }
                """;
        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addBody,
                API_PATH + queryParam);
        assertEqualsInJsonPath(orderedFolderPartAfterAddition, result, orderedFolderPath);

        // perform delete elements within array
        String deleteBody = """
                [
                { "op": "remove", "path": "/primaryTypes/SysOrderedFolder/mixins/0"}
                ]
                """;
        String orderedFolderPartOriginal = """
                {
                    "extends": "SysFolder",
                    "mixins": [
                        "SysVersionable"
                    ],
                    "subtypes": [
                        "SysFile",
                        "SysFolder",
                        "SysOrderedFolder"
                    ]
                }
                """;
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, deleteBody,
                API_PATH + queryParam);
        assertEqualsInJsonPath(orderedFolderPartOriginal, result, orderedFolderPath);
    }

    @Test
    void testPatchAddAndRemoveToArrayValidateTrue() throws Exception {
        // perform add to array Patch
        String orderedFolderPath = "primaryTypes.SysOrderedFolder";
        String addBodyToArray = """
                [
                { "op": "add", "path": "/primaryTypes/SysOrderedFolder/mixins/-","value":"SysVersionable"}
                ]
                """;
        String orderedFolderPartAfterAddition = """
                {
                    "extends": "SysFolder",
                    "mixins": [
                        "SysOrderable"
                    ],
                    "schemas": [
                        "sysfile"
                    ],
                    "subtypes": [
                        "SysFile",
                        "SysFolder",
                        "SysOrderedFolder"
                    ]
                }
                """;
        checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addBodyToArray, PATH_VALIDATE_ONLY_TRUE);
        String addBody = """
                [
                    { "op": "add", "path": "/primaryTypes/SysOrderedFolder/schemas","value":["sysfile"] }
                ]
                """;
        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addBody,
                PATH_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);

        // We'll do the addition this time, so we can test the delete will validate
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addBody,
                API_PATH);
        assertEqualsInJsonPath(orderedFolderPartAfterAddition, result, orderedFolderPath);

        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addBodyToArray,
                API_PATH);
        orderedFolderPartAfterAddition = """
                {
                    "extends": "SysFolder",
                    "mixins": [
                        "SysOrderable",
                        "SysVersionable"
                    ],
                    "schemas": [
                        "sysfile"
                    ],
                    "subtypes": [
                        "SysFile",
                        "SysFolder",
                        "SysOrderedFolder"
                    ]
                }
                """;
        assertEqualsInJsonPath(orderedFolderPartAfterAddition, result, orderedFolderPath);

        // perform delete elements within array
        String deleteBody = """
                [
                { "op": "remove", "path": "/primaryTypes/SysOrderedFolder/mixins/0"},
                { "op": "remove", "path": "/primaryTypes/SysOrderedFolder/schemas"}
                ]
                """;
        // remove/ modify will produce 422 as of HXCS-758, but will produce no errors
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                deleteBody, PATH_VALIDATE_ONLY_TRUE);
        // check there are no errors in response
        assertNoErrors(result);
        // check the model has not been changed (we are checking that the original update is still applied)
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        assertEqualsInJsonPath(orderedFolderPartAfterAddition, result, orderedFolderPath);
    }

    private void assertNoErrors(ResultActions result) throws Exception {
        result.andExpect(jsonPath("errors").exists()).andExpect(jsonPath("errors.length()", is(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchAddMoveAndTestToSchema(String queryParam) throws Exception {
        // add new type to schema
        String newTypePath = "types.newType";
        String addNewTypeBody = """
                [
                { "op": "add", "path": "/types/newType", "value": { "fields": { "oldMember": {"type": "string"} }} }
                ]
                """;
        String newTypeJson = """
                {
                    "fields": {
                        "oldMember": {
                                 "type":"string"
                            }
                    }
                }
                """;
        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                API_PATH + queryParam);
        assertEqualsInJsonPath(newTypeJson, result, newTypePath);

        // add another member into new type
        String addNewMemberIntoNewType = """
                [
                { "op": "add", "path": "/types/newType/fields/newMember", "value": {"type": "string"}}
                ]
                """;
        newTypeJson = """
                {
                    "fields": {
                        "oldMember": {
                            "type":"string"
                        },
                        "newMember": {
                            "type":"string"
                        }
                    }
                }
                """;
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addNewMemberIntoNewType, API_PATH + queryParam);
        assertEqualsInJsonPath(newTypeJson, result, newTypePath);

        // replace old member into new type
        String replaceOldMemberType = """
                [
                { "op": "replace", "path": "/types/newType/fields/oldMember", "value": {"type":"blob"} }
                ]
                """;
        newTypeJson = """
                {
                    "fields": {
                        "oldMember": {
                            "type": "blob"
                        },
                        "newMember": {
                            "type": "string"
                        }
                    }
                }
                """;
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                replaceOldMemberType, API_PATH + queryParam);
        assertEqualsInJsonPath(newTypeJson, result, newTypePath);

        // test members
        String testMembers = """
                [
                { "op": "test", "path": "/types/newType/fields/oldMember",
                    "value": {
                        "type":"blob"
                    }
                },
                { "op": "test", "path": "/types/newType/fields/newMember",
                    "value": {
                        "type":"string"
                    }
                }
                ]
                """;
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, testMembers,
                API_PATH + queryParam);
        assertEqualsInJsonPath(newTypeJson, result, newTypePath);

        // move newType from types to mixinTypes
        String newerTypePath = "types.newNewType";
        String moveMembers = """
                [
                { "op": "add", "path": "/types/newNewType", "value": { "fields": {}} },
                { "op": "move", "from": "/types/newType/fields/oldMember", "path": "/types/newNewType/fields/oldMember"}
                ]
                """;
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, moveMembers,
                API_PATH + queryParam);
        newTypeJson = """
                {
                    "fields": {
                        "newMember": {
                            "type":"string"
                        }
                    }
                }
                """;
        String newerTypeJson = """
                {
                    "fields": {
                        "oldMember": {
                            "type":"blob"
                        }
                    }
                }
                """;
        assertEqualsInJsonPath(newerTypeJson, result, newerTypePath);
        assertEqualsInJsonPath(newTypeJson, result, newTypePath);
    }

    @Test
    void testPatchAddMoveAndTestToSchemaValidateTrue() throws Exception {
        // add new type to schema
        String newTypePath = "types.newType";
        String addNewTypeBody = """
                [
                { "op": "add", "path": "/types/newType", "value": { "fields": { "oldMember": {"type":"string"} }} }
                ]
                """;
        String newTypeJson1 = """
                {
                    "fields": {
                        "oldMember": {
                            "type": "string"
                        }
                    }
                }
                """;
        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                PATH_VALIDATE_ONLY_TRUE);
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                API_PATH);
        assertEqualsInJsonPath(newTypeJson1, result, newTypePath);
        String newTypeJson1ResponseBody = result.andReturn().getResponse().getContentAsString();

        // add another member into new type
        String addNewMemberIntoNewType = """
                [
                { "op": "add", "path": "/types/newType/fields/newMember", "value": {"type":"string"}}
                ]
                """;
        String newTypeJson2 = """
                {
                    "fields": {
                        "oldMember": {
                            "type":"string"
                        },
                        "newMember": {
                            "type":"string"
                        }
                    }
                }
                """;
        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addNewMemberIntoNewType, PATH_VALIDATE_ONLY_TRUE);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(newTypeJson1ResponseBody, result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addNewMemberIntoNewType, API_PATH);
        assertEqualsInJsonPath(newTypeJson2, result, newTypePath);
        String newTypeJson2ResponseBody = result.andReturn().getResponse().getContentAsString();

        // replace old member into new type
        String replaceOldMemberType = """
                [
                { "op": "replace", "path": "/types/newType/fields/oldMember", "value": {"type":"blob"}}
                ]
                """;
        String newTypeJson3 = """
                {
                    "fields": {
                        "oldMember": {
                            "type": "blob"
                        },
                        "newMember": {
                            "type": "string"
                        }
                    }
                }
                """;
        // remove/ modify will produce 422 as of HXCS-758, but will produce no errors
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                replaceOldMemberType, PATH_VALIDATE_ONLY_TRUE);
        // check there are no errors in response
        assertNoErrors(result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(newTypeJson2ResponseBody, result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                replaceOldMemberType, API_PATH);
        assertEqualsInJsonPath(newTypeJson3, result, newTypePath);
        String newTypeJson3ResponseBody = result.andReturn().getResponse().getContentAsString();

        // test members
        String testMembers = """
                [
                { "op": "test", "path": "/types/newType/fields/oldMember",
                    "value": {
                        "type":"blob"
                    }
                },
                { "op": "test", "path": "/types/newType/fields/newMember",
                    "value": {
                        "type":"string"
                    }
                }
                ]
                """;

        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, testMembers,
                PATH_VALIDATE_ONLY_TRUE);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(newTypeJson3ResponseBody, result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, testMembers,
                API_PATH);
        assertEqualsInJsonPath(newTypeJson3, result, newTypePath);
        String testMembersUpdateResponseBody = result.andReturn().getResponse().getContentAsString();

        // move newType from types to mixinTypes
        String moveMembers = """
                [
                { "op": "add", "path": "/types/newNewType", "value": { "fields": {}} },
                { "op": "move", "from": "/types/newType/fields/oldMember", "path": "/types/newNewType/fields/oldMember"}
                ]
                """;
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                moveMembers, PATH_VALIDATE_ONLY_TRUE);
        // Check for no errors
        assertNoErrors(result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(testMembersUpdateResponseBody, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchCopyAndRemoveInSchema(String queryParam) throws Exception {
        // copy new type to schema
        String newBlobPath = "types.newBlob";
        String addNewTypeBody = """
                [
                { "op": "add", "path": "/types/newBlob", "value": { "fields": { } }}
                ]
                """;
        String newBlobJson = """
                {
                    "fields": {
                    }
                }
                """;
        var result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                API_PATH + queryParam);
        assertEqualsInJsonPath(newBlobJson, result, newBlobPath);

        // copy field from file
        String copyFieldFromOldBlobBody = """
                [
                { "op": "copy", "from": "/types/blob/fields/digest", "path": "/types/newBlob/fields/digest" }
                ]
                """;
        newBlobJson = """
                {
                    "fields": {
                        "digest": {
                                "type":"string"
                        }
                    }
                }
                """;
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                copyFieldFromOldBlobBody, API_PATH + queryParam);
        assertEqualsInJsonPath(newBlobJson, result, newBlobPath);

        // delete newBlob type
        String deleteNewBlobTypeBody = """
                [
                { "op": "remove", "path": "/types/newBlob" }
                ]
                """;
        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, deleteNewBlobTypeBody,
                API_PATH + queryParam).andExpect(jsonPath("types", not(hasKey("newBlob"))));
    }

    @Test
    void testPatchCopyAndRemoveInSchemaValidateTrue() throws Exception {
        // copy new type to schema
        String newBlobPath = "types.newBlob";
        String addNewTypeBody = """
                [
                { "op": "add", "path": "/types/newBlob", "value": { "fields": { } }}
                ]
                """;
        String newBlobJson1 = """
                {
                    "fields": {
                    }
                }
                """;
        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                PATH_VALIDATE_ONLY_TRUE);
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                API_PATH);
        assertEqualsInJsonPath(newBlobJson1, result, newBlobPath);
        String newBlobJson1ResponseBody = result.andReturn().getResponse().getContentAsString();
        // copy field from file
        String copyFieldFromOldBlobBody = """
                [
                { "op": "copy", "from": "/types/blob/fields/digest", "path": "/types/newBlob/fields/digest" }
                ]
                """;
        String newBlobJson2 = """
                {
                    "fields": {
                        "digest": {
                            "type":"string"
                        }
                    }
                }
                """;
        checkResponseWithPayload(NO_CONTENT, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                copyFieldFromOldBlobBody, PATH_VALIDATE_ONLY_TRUE);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(newBlobJson1ResponseBody, result);

        // Apply for next validation
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                copyFieldFromOldBlobBody, API_PATH);
        assertEqualsInJsonPath(newBlobJson2, result, newBlobPath);
        String newBlobJson2ResponseBody = result.andReturn().getResponse().getContentAsString();

        // delete newBlob type
        String deleteNewBlobTypeBody = """
                [
                { "op": "remove", "path": "/types/newBlob" }
                ]
                """;
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID,
                deleteNewBlobTypeBody, PATH_VALIDATE_ONLY_TRUE);
        // Check that there are no errors
        assertNoErrors(result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(newBlobJson2ResponseBody, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    public void testPutUnauthenticatedForProject(String queryParam) throws Exception {
        String url = API_PATH + PROJECT_PF_00000 + queryParam;
        mvc.perform(put(url).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    public void testPutUnauthorizedForProject(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PUT, REPO_ID, "{}", API_PATH + PROJECT_PF_00000 + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    public void testPutBadRepoForProject(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, BAD_REPO_ID, "{}", API_PATH + PROJECT_PF_00000 + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    public void testPutInvalidJsonForProject(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, NOT_JSON,
                API_PATH + PROJECT_PF_00000 + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForProjectConflictWithDefaultModel(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is already
        // present as a default content model item.
        String putBodyForProject = """
                        {
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

        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model for consuming the project specific PUT endpoint
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(CONFLICT, PUT, REPO_ID, newBody,
                API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBody("rest/validation_error_for_default_content_model_item.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForProjectUnknownMixinTypeValidationUnderPrimaryTypes(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is already
        // present as a default content model item.
        String putBodyForProject = """
                        {
                          "primaryTypes": {
                            "MixinTest": {
                              "extends": "SysContent",
                              "mixins": ["fakeMixin"],
                              "schemas": ["system"]
                            }
                          }
                        }
                """;

        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model for consuming the project specific PUT endpoint
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, REPO_ID, newBody,
                API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBody("rest/validation_error_for_unknown_mixin_under_primarytype.json", result);
    }

    // INVALID PARENT TYPE ADDING TEST
    @ParameterizedTest
    @MethodSource("provideParentValidationTestCases")
    void testPrimaryTypesParentValidation(String path, HttpMethod method, String contentType, String queryParam, String body) throws Exception {
        checkResponseWithPayload(UNPROCESSABLE_ENTITY, method, contentType, REPO_ID, body,
                path + queryParam);

        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    private static String updatedSysRenditionParentBody(String currentModelBody, String parentValue) throws JSONException {
        JSONObject jsonObject = new JSONObject(currentModelBody);
        jsonObject.getJSONObject("primaryTypes").getJSONObject("SysRendition").put("extends", parentValue);
        return jsonObject.toString();
    }

    private static String putTypesBody(String currentModelBody, String parent1, String parent2) throws JSONException {
        JSONObject jsonObject = new JSONObject(currentModelBody);
        jsonObject.getJSONObject("primaryTypes").put(NEW_TYPE_PARENT, new JSONObject("""
                {"extends": "%s", "schemas": ["system", "sysfile"]}
            """.formatted(parent1)));
        jsonObject.getJSONObject("primaryTypes").put(NEW_TYPE_CHILD, new JSONObject("""
                {"extends": "%s", "schemas": ["system", "sysfile"]}
            """.formatted(parent2)));
        return jsonObject.toString();
    }

    private static Stream<Arguments> provideParentValidationTestCases() throws JSONException, IOException {
        String body = ContentTestHelper.getReferenceContent("rest/model.json");
        return Stream.of(
                // PUT test cases
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_FALSE, updatedSysRenditionParentBody(body, "")),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_FALSE, updatedSysRenditionParentBody(body, "NotExistingParent")),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_FALSE, updatedSysRenditionParentBody(body, "SysRendition")),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_FALSE, putTypesBody(body, NEW_TYPE_CHILD, NEW_TYPE_PARENT)),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_TRUE, updatedSysRenditionParentBody(body, "")),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_TRUE, updatedSysRenditionParentBody(body, "NotExistingParent")),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_TRUE, updatedSysRenditionParentBody(body, "SysRendition")),
                Arguments.of(API_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_TRUE, putTypesBody(body, NEW_TYPE_CHILD, NEW_TYPE_PARENT)),
                // PUT for Project test cases
                Arguments.of(PUT_FOR_PROJECT_PATH, PUT, APPLICATION_JSON, "", EMPTY_PARENT_PUT_FOR_PROJECT_BODY),
                Arguments.of(PUT_FOR_PROJECT_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_FALSE, NON_EXISTING_PARENT_PUT_FOR_PROJECT_BODY),
                Arguments.of(PUT_FOR_PROJECT_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_FALSE, EXTENDING_ITSELF_PARENT_PUT_FOR_PROJECT_BODY),
                Arguments.of(PUT_FOR_PROJECT_PATH, PUT, APPLICATION_JSON, PARAM_VALIDATE_ONLY_TRUE, LOOP_PARENT_PUT_FOR_PROJECT_BODY),
                // PATCH test cases
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, "", EMPTY_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, "", NON_EXISTING_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, "", EXTENDING_ITSELF_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, "", LOOP_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, PARAM_VALIDATE_ONLY_TRUE, EMPTY_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, PARAM_VALIDATE_ONLY_TRUE, NON_EXISTING_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, PARAM_VALIDATE_ONLY_TRUE, EXTENDING_ITSELF_PARENT_PATCH_BODY),
                Arguments.of(API_PATH, PATCH, APPLICATION_JSON_PATCH_JSON, PARAM_VALIDATE_ONLY_TRUE, LOOP_PARENT_PATCH_BODY)
        );
    }

    // PARENT TYPE REMOVAL TEST
    private static String putNewTypeWithRemovedParentTypeBody(String currentModelBody, String removedParent) throws JSONException {
        JSONObject jsonObject = new JSONObject(currentModelBody);
        jsonObject.getJSONObject("primaryTypes").put(NEW_TYPE_CHILD, new JSONObject("""
                {"extends": "%s", "schemas": ["system", "sysfile"]}
            """.formatted(removedParent)));
        return jsonObject.toString();
    }

    private static final String PUT_FOR_PROJECT_REMOVE_PARENT_BODY = """
                {
                   "primaryTypes": {
                     "NewTypeChild": {
                       "extends": "%s",
                       "schemas": [
                         "system"
                       ]
                     }
                   }
                }
           """;

    private static final String ADD_VALID_TYPES_PATCH = BASE_PARENT_PATCH.formatted("SysContent", "NewTypeParent");
    private static final String REMOVE_PARENT_PATCH = """
            [
              { "op": "remove", "path": "/primaryTypes/NewTypeParent" }
            ]
        """;

    static Stream<org.junit.jupiter.params.provider.Arguments> providePrimaryTypesRemoveParentValidationTestCases() throws IOException, JSONException {
        String originalBody = ContentTestHelper.getReferenceContent("rest/model.json");
        return Stream.of(
                Arguments.of(API_PATH, PUT, putTypesBody(originalBody, SYS_CONTENT, NEW_TYPE_PARENT), putNewTypeWithRemovedParentTypeBody(originalBody, NEW_TYPE_PARENT), PARAM_VALIDATE_ONLY_TRUE),
                Arguments.of(API_PATH, PUT, putTypesBody(originalBody, SYS_CONTENT, NEW_TYPE_PARENT), putNewTypeWithRemovedParentTypeBody(originalBody, NEW_TYPE_PARENT), ""),
                Arguments.of(PUT_FOR_PROJECT_PATH, PUT, PUT_FOR_PROJECT_BASE_BODY.formatted(SYS_CONTENT, NEW_TYPE_PARENT), PUT_FOR_PROJECT_REMOVE_PARENT_BODY.formatted(NEW_TYPE_PARENT), PARAM_VALIDATE_ONLY_TRUE),
                Arguments.of(PUT_FOR_PROJECT_PATH, PUT, PUT_FOR_PROJECT_BASE_BODY.formatted(SYS_CONTENT, NEW_TYPE_PARENT), PUT_FOR_PROJECT_REMOVE_PARENT_BODY.formatted(NEW_TYPE_PARENT), ""),
                Arguments.of(API_PATH, PATCH, ADD_VALID_TYPES_PATCH, REMOVE_PARENT_PATCH, PARAM_VALIDATE_ONLY_TRUE),
                Arguments.of(API_PATH, PATCH, ADD_VALID_TYPES_PATCH, REMOVE_PARENT_PATCH, "")
        );
    }

    @ParameterizedTest
    @MethodSource("providePrimaryTypesRemoveParentValidationTestCases")
    void testPrimaryTypesRemoveParentValidation(String path, HttpMethod method, String addBody, String rmBody, String queryParam) throws Exception {
        // get original model to clean up after the test
        var response = checkResponse(OK, GET, REPO_ID, API_PATH);
        String originalModel = response.andReturn().getResponse().getContentAsString();
        String contentType = method.equals(PATCH) ? APPLICATION_JSON_PATCH_JSON : APPLICATION_JSON;

        checkResponseWithPayload(OK, method, contentType, REPO_ID, addBody, path);
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, method, contentType, REPO_ID, rmBody, path + queryParam);
        result.andExpect(MockMvcResultMatchers.jsonPath("errors").exists());
        var getResult = checkResponse(OK, GET, REPO_ID, API_PATH);
        getResult.andExpect(MockMvcResultMatchers.jsonPath("primaryTypes.NewTypeParent").exists());
        getResult.andExpect(MockMvcResultMatchers.jsonPath("primaryTypes.NewTypeChild").exists());

        //clean up - put back the original model
        result = checkResponseWithPayload(OK, PUT, REPO_ID, originalModel, API_PATH);
        checkResponseBody("rest/model.json", result);
    }


    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForANewProject(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is not present
        // in the content model
        String putBodyForProject = """
                {
                  "types": {
                    "testT": {
                      "list": { "type" : "string" }
                    }
                  },
                  "mixinTypes": {
                    "testMT": {
                      "noPerDocumentQuery": true
                    }
                  },
                  "primaryTypes": {
                    "File1": {
                      "extends": "SysContent",
                      "mixins": [
                        "SysVersionable"
                      ],
                      "schemas": [
                        "sysfile",
                        "mydublincore"
                      ],
                      "subtypes": [
                        "SysFile",
                        "SysFolder"
                      ]
                    }
                  },
                  "schemas": {
                    "test": {
                      "fields": {
                        "test_desc": {
                          "type": "string"
                        },
                        "test_desc2": {
                          "fields": {
                            "first":{
                                "type": "string"
                            }
                          }
                        },
                        "test_desc3": {
                            "list": {
                                "type": "string"
                            }
                        },
                        "test_desc4": {
                            "extends": "string",
                            "constraints": {
                                "minLength": 10,
                                "maxLength": 40
                            }
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model for consuming the project specific PUT endpoint
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody,
                API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_ForProject.patch.json", result);
    }

    /**
     * A list whose item is a complex type declared inline is a valid declaration: the schema layer
     * has always been able to build it, and it round-trips through the persisted shape as
     * {@code {"list": {"fields": {…}}}}. It used to be rejected only because the REST layer's list
     * DTO could hold nothing but a type reference, so the nested {@code fields} were dropped before
     * the schema layer ever saw them.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForANewProjectWithListOfInlineComplexType(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);

        String putBodyForProject = """
                {
                  "mixinTypes": {
                    "testMT": {
                      "noPerDocumentQuery": true
                    }
                  },
                  "primaryTypes": {
                    "File1": {
                      "extends": "SysContent",
                      "mixins": [
                        "SysVersionable"
                      ],
                      "schemas": [
                        "sysfile"
                      ],
                      "subtypes": [
                        "SysFile",
                        "SysFolder"
                      ]
                    }
                  },
                  "schemas": {
                    "test": {
                      "fields": {
                        "test_desc": {
                          "type": "string"
                        },
                        "test_desc2": {
                          "fields": {
                            "first":{
                                "type": "string"
                            }
                          }
                        },
                        "test_desc3": {
                            "list": {
                                "type": "string"
                            }
                        },
                        "test_desc4": {
                            "extends": "string",
                            "constraints": {
                                "minLength": 10,
                                "maxLength": 40
                            }
                        },
                        "test_desc5": {
                             "list": {
                                 "fields": {
                                     "first": {
                                         "type": "string"
                                     }
                                 }
                             }
                         }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model for consuming the project specific PUT endpoint
        String newBody = jsonObject.toString();
        checkResponseWithPayload(OK, PUT, REPO_ID, newBody, API_PATH + PROJECT_PF_00000 + queryParam);
        // the inline complex item survives the round trip
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        result.andExpect(jsonPath("$.schemas.test.fields.test_desc5.list.fields.first.type", is("string")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForAnExistingProject(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        String body = result.andReturn().getResponse().getContentAsString();

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is present in
        // the content model for the same project.
        String putBodyForSameProject = """
                {
                  "mixinTypes": {
                    "testMTX": {
                      "noPerDocumentQuery": true
                    }
                  },
                  "primaryTypes": {
                    "FileX": {
                      "extends": "SysContent",
                      "mixins": [
                        "SysVersionable"
                      ],
                      "schemas": [
                        "sysfile",
                        "mydublincore"
                      ],
                      "subtypes": [
                        "SysFile"
                      ]
                    }
                  },
                  "schemas": {
                    "testX": {
                      "fields": {
                        "test_desc": {
                          "type": "string"
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;

        JSONObject jsonObject = new JSONObject(putBodyForSameProject);
        // put new model for consuming the project specific PUT endpoint
        String newBody = jsonObject.toString();
        result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody,
                API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_ForSameProject.patch.json", result);

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project exists for a
        // different project in the content model.
        putBodyForSameProject = """
                {
                  "mixinTypes": {
                    "testMTX": {
                      "noPerDocumentQuery": true
                    }
                  },
                  "primaryTypes": {
                    "FileX": {
                      "extends": "SysContent",
                      "mixins": [
                        "SysVersionable"
                      ],
                      "schemas": [
                        "sysfile"
                      ],
                      "subtypes": [
                        "SysFile"
                      ]
                    }
                  },
                  "schemas": {
                    "testX": {
                      "fields": {
                        "test_desc": {
                          "type": "string"
                        }
                      },
                      "prefix": "testX"
                    }
                  }
                }
                """;

        jsonObject = new JSONObject(putBodyForSameProject);
        // put new model for consuming the project specific PUT endpoint
        newBody = jsonObject.toString();
        result = checkResponseWithPayload(CONFLICT, PUT, REPO_ID, newBody,
                API_PATH + "/project/PBAR9999" + queryParam);
        checkResponseBody("rest/validation_error_model_item_exists_for_another_project.json", result);

        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    public void testPutForProjectValidateTrue() throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);
        String putBodyForProject = """
                {
                  "mixinTypes": {
                    "testMT": {
                      "noPerDocumentQuery": true
                    }
                  },
                  "primaryTypes": {
                    "File1": {
                      "extends": "SysContent",
                      "mixins": [
                        "SysVersionable"
                      ],
                      "schemas": [
                        "sysfile"
                      ],
                      "subtypes": [
                        "SysFile"
                      ]
                    }
                  },
                  "schemas": {
                    "test": {
                      "fields": {
                        "test_desc": {
                          "type": "string"
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model
        String newBody = jsonObject.toString();
        checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, newBody, PATH_VALIDATE_ONLY_TRUE_FOR_PROJECT).andExpect(
                jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForProjectEmptyPayload(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, API_PATH);

        // Empty content model payload
        String putBodyForProject = "{}";
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody,
                API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPatchRemoveInPrimaryTypesRootValidateTrue() throws Exception {

        // delete SysRoot Primary type
        String deleteRootPrimaryTypeBody = """
                [
                { "op": "remove", "path": "/primaryTypes/SysRoot" }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, deleteRootPrimaryTypeBody, PATH_VALIDATE_ONLY_TRUE);
        checkResponseBody("rest/validation_error_for_remove_root_primary_type.json", result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPatchRemoveInPrimaryTypesDocumentValidateTrue() throws Exception {

        // delete Document Primary type
        String deleteDocumentPrimaryTypeBody = """
                [
                { "op": "remove", "path": "/primaryTypes/SysFolder" }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, deleteDocumentPrimaryTypeBody, PATH_VALIDATE_ONLY_TRUE);
        checkResponseBody("rest/validation_error_for_remove_document_primary_type.json", result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testTrueFlagFullTextIndexedOnPrimaryTypeDefinitionAndField() throws Exception {
        // get original JSON
        var originalModel = checkResponse(OK, GET, REPO_ID, API_PATH);
        String putBodyForProject = """
                {
                  "primaryTypes": {
                    "FileFullText": {
                      "extends": "SysContent",
                      "schemas": [
                        "test"
                      ],
                      "mixins": [
                        "SysFullTextIndexable"
                       ]
                    }
                  },
                  "schemas": {
                    "test": {
                      "fields": {
                        "test_field": {
                          "type": "string",
                          "fullTextIndexed": true
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, API_PATH + "/project/HxP001");
        var expectedPrimaryTypeJson = """
                {
                  "extends": "SysContent",
                  "mixins": [
                    "SysFullTextIndexable"
                  ],
                  "projectId": "HxP001",
                  "schemas": [
                    "test"
                  ]
                }
                """;
        var expectedFieldJson = """
                {
                  "fields": {
                    "test_field": {
                      "extends": "string",
                      "fullTextIndexed": true
                    }
                  },
                  "prefix": "test",
                  "projectId": "HxP001"
                }
                """;
        assertEqualsInJsonPath(expectedPrimaryTypeJson, result, "primaryTypes.FileFullText");
        assertEqualsInJsonPath(expectedFieldJson, result, "schemas.test");

        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID,
                originalModel.andReturn().getResponse().getContentAsString(), API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testFalseFlagFullTextIndexedOnPrimaryTypeDefinitionAndField() throws Exception {
        // get original JSON
        var originalModel = checkResponse(OK, GET, REPO_ID, API_PATH);
        String putBodyForProject = """
                {
                  "primaryTypes": {
                    "FileWithoutFullText": {
                      "extends": "SysContent",
                      "schemas": [
                        "test"
                      ]
                    }
                  },
                  "schemas": {
                    "test": {
                      "fields": {
                        "test_field": {
                          "type": "string",
                          "fullTextIndexed": false
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, API_PATH + "/project/HxP001");
        var expectedPrimaryTypeJson = """
                {
                  "extends": "SysContent",
                  "projectId": "HxP001",
                  "schemas": [
                    "test"
                  ]
                }
                """;
        var expectedFieldJson = """
                {
                  "fields": {
                    "test_field": {
                      "type": "string"
                    }
                  },
                  "prefix": "test",
                  "projectId": "HxP001"
                }
                """;
        assertEqualsInJsonPath(expectedPrimaryTypeJson, result, "primaryTypes.FileWithoutFullText");
        assertEqualsInJsonPath(expectedFieldJson, result, "schemas.test");

        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID,
                originalModel.andReturn().getResponse().getContentAsString(), API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    private void assertEqualsInJsonPath(String expected, ResultActions actualResult, String pathInActualBody)
            throws Exception {
        actualResult.andExpect(jsonPath(pathInActualBody).value(equalTo(JsonPath.read(expected, "$"))));
    }

    private static Stream<String> provideStringsForConstraintSuccessValidation() {
        return Stream.of(
            // Test LengthConstraint with both minLength and maxLength
            """
            [
                {
                    "op": "add",
                    "path": "/types/mydublincore#clob",
                    "value": {
                        "constraints": {
                            "minLength": 8,
                            "maxLength": 2000,
                            "nullable": true
                        },
                        "extends": "string"
                    }
                }
            ]
            """,
            // Test LengthConstraint where minLength == maxLength
            """
            [
                {
                    "op": "add",
                    "path": "/types/mydublincore#clob",
                    "value": {
                        "constraints": {
                            "minLength": 2000,
                            "maxLength": 2000,
                            "nullable": true
                        },
                        "extends": "string"
                    }
                }
            ]
            """,
            // Test LengthConstraint with minLength only
            """
            [
                {
                    "op": "add",
                    "path": "/types/mydublincore#clob",
                    "value": {
                        "constraints": {
                            "minLength": 1
                        },
                        "extends": "string"
                    }
                }
            ]
            """,
            // Test LengthConstraint with maxLength only
            """
                [
                    {
                        "op": "add",
                        "path": "/types/mydublincore#clob",
                        "value": {
                            "constraints": {
                                "maxLength": 2000,
                                "nullable": true
                            },
                            "extends": "string"
                        }
                    }
                ]
            """,
            // Test NumericIntervalConstraint with both max and min
            """
            [
                {
                    "op": "add",
                    "path": "/schemas/mydublincore/fields/mydc_foo",
                    "value": {
                        "extends": "long",
                        "constraints":
                        {
                            "minExclusive": 0,
                            "maxInclusive": 2000
                        }
                    }
                }
            ]
            """,
            // Test NumericIntervalConstraint with min only
            """
            [
                {
                    "op": "add",
                    "path": "/schemas/mydublincore/fields/mydc_foo",
                    "value": {
                        "extends": "long",
                        "constraints":
                        {
                            "minInclusive": 0
                        }
                    }
                }
            ]
            """,
            // Test NumericIntervalConstraint with max only
            """
            [
                {
                    "op": "add",
                    "path": "/schemas/mydublincore/fields/mydc_foo",
                    "value": {
                        "extends": "long",
                        "constraints":
                        {
                            "maxInclusive": 2000
                        }
                    }
                }
            ]
            """,
            // Test NumericIntervalConstraint when inclusiveMin == inclusiveMax
            """
            [
                {
                    "op": "add",
                    "path": "/schemas/mydublincore/fields/mydc_foo",
                    "value": {
                        "extends": "long",
                        "constraints":
                        {
                            "minInclusive": 2000,
                            "maxInclusive": 2000
                        }
                    }
                }
            ]
            """
        );
    }
}
