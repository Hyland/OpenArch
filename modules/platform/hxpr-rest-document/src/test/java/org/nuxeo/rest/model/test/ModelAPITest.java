/*
 * (C) Copyright 2021-2025 Hyland (http://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.rest.model.test;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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

import jakarta.ws.rs.core.MediaType;

import java.util.stream.Stream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.model.ModelAPI;
import org.nuxeo.rest.model.ModelManager;
import org.nuxeo.rest.model.dto.Constants;
import org.nuxeo.rest.model.validation.ModelValidator;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Model REST API tests.
 */
@Features(CoreFeature.class)
@SpringBootTest(classes = { ModelAPI.class, ModelManager.class, ModelValidator.class })
class ModelAPITest extends AbstractAPITest {

    private static final String PATH_VALIDATE_ONLY_TRUE = Constants.API_PATH + PARAM_VALIDATE_ONLY_TRUE;

    private static final String PATH_VALIDATE_ONLY_TRUE_FOR_PROJECT =
            Constants.API_PATH + PROJECT_PF_00000 + PARAM_VALIDATE_ONLY_TRUE;

    private TypeService typeService;

    @BeforeEach
    void setUp() {
        typeService = Framework.getService(TypeService.class);
        typeService.setSchemaManager(REPO_ID, typeService.getDefaultSchemaManager());
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    void testGetUnauthenticated() throws Exception {
        mvc.perform(get(Constants.API_PATH).header(HXCS_REPOSITORY, REPO_ID))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @Test
    void testGetBadRepo() throws Exception {
        checkResponse(NOT_FOUND, GET, BAD_REPO_ID, Constants.API_PATH);
    }

    @Test
    @WithUser(ALT_USER)
    void testGet() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    @WithUser(ALT_USER)
    void testGetV1() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTENT_TYPE, APPLICATION_JSON);
        headers.add(HXCS_REPOSITORY, REPO_ID);
        var result = checkResponse(OK, GET, headers, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    @WithUser(ALT_USER)
    void testGetV0() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, "application/vnd.hxcs.v0+json");
        headers.add(HXCS_REPOSITORY, REPO_ID);
        checkResponse(NOT_ACCEPTABLE, GET, headers, Constants.API_PATH);
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
                        "system",
                        "myschema"
                      ]
                    }
                  }
                }
                """;

        JSONObject jsonObject = new JSONObject(putForProject);
        String newBody = jsonObject.toString();
        checkResponseWithPayload(OK, PUT, REPO_ID, newBody, Constants.API_PATH + PROJECT_PF_00000);

        // get for just project PF00000
        ResultActions result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH + PROJECT_PF_00000);
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
                        "system",
                        "myschema"
                      ]
                    }
                  }
                }
                """;
        checkResponseBodyEquals(expected, result);

        // cleanup: remove everything about project PF00000
        result = checkResponseWithPayload(OK, PUT, REPO_ID, "{}", Constants.API_PATH + PROJECT_PF_00000);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    void testPutUnauthenticated(String queryParam) throws Exception {
        mvc.perform(put(Constants.API_PATH + queryParam).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPutUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PUT, REPO_ID, "{}", Constants.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, BAD_REPO_ID, "{}", Constants.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, NOT_JSON, Constants.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPutUnchanged(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        String body = result.andReturn().getResponse().getContentAsString();
        // put it back unchanged
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, Constants.API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPut(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
        result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, Constants.API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated.patch.json", result);
        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, Constants.API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPutValidateTrue() throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPutWithMixin(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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

        result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, Constants.API_PATH + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_with_mixins.patch.json", result);

        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, Constants.API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    void testPatchUnauthenticated(String queryParam) throws Exception {
        String url = Constants.API_PATH + queryParam;
        mvc.perform(patch(url).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPatchUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, "{}",
                Constants.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, BAD_REPO_ID, "{}",
                Constants.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, NOT_JSON,
                Constants.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void givenModelValidation_whenReplacementValueIsAnInvalidObjectTypeForTypesAndModelInUse_thenAnInvalidObjectViolationWithAlreadyExistsViolationWhenValidateOnlyTrueShouldBeInResponse(
            String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;
        String badPatch = """
                [
                      {
                          "op": "replace",
                          "path": "/types/sysfiles#blobitem",
                          "value": "sysfiles#blob-foobar"
                      }
                 ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
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
        String path = Constants.API_PATH + queryParam;
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
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
        String path = Constants.API_PATH + queryParam;
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
                        "op": "replace",
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_primarytype_dto_validate_only.json" :
                "rest/validation_primarytype_dto_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void givenModelValidation_whenReplacementValueIsAnInvalidObjectTypeForMixinTypes_thenAnAlreadyExistViolationShouldBeInResponse(
            String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;
        String badPatch = """
              [
                     {
                        "op": "replace",
                        "path": "/mixinTypes/SysVersionable",
                        "value": ["Versionable-foobar"]
                    }
               ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_mixintype_dto_validate_only.json" :
                "rest/validation_mixintype_dto_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testValidationErrorsForRemove(String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        String expectedFilePath = queryParam.equals(PARAM_VALIDATE_ONLY_TRUE) ?
                "rest/validation_schema_dto_for_remove_validate_only.json" :
                "rest/validation_schema_dto_for_remove_validate_only_false.json";
        checkResponseBody(expectedFilePath, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testValidationErrorsForAdd(String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;
        String badPatch = """
                [
                       {
                            "op": "add",
                            "path": "/mixinTypes/SysFolderish/schemas",
                            "value":"schemas-foobar"
                        }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, Constants.API_PATH);
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, Constants.API_PATH);
        assertEqualsInJsonPath(error, result, "errors[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testLengthConstraintValidation(String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;

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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        assertEqualsInJsonPath(lengthConstraintError, result, "errors[0]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testNumericConstraintValidation(String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;

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

        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
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

        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                badPatch, path);
        assertEqualsInJsonPath(numericConstraintError, result, "errors[0]");

    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testObjectResolverConstraintValidation(String queryParam) throws Exception {
        String path = Constants.API_PATH + queryParam;

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

        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, badPatch, path);
        assertEqualsInJsonPath(objectResolverConstraintError, result, "errors[0]");
    }

    @ParameterizedTest
    @MethodSource("provideStringsForConstraintSuccessValidation")
    void testConstraintValidationSuccess(String goodPatch) throws Exception {
        String path = Constants.API_PATH + PARAM_VALIDATE_ONLY_TRUE;

        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, goodPatch, path);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchUnchanged(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        String body = """
                [
                { "op": "replace", "path": "/schemas/mydublincore/fields/mydc_coverage", "value": "string" }
                ]
                """;
        // put it back unchanged
        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPatchUnchangedValidateTrue() throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        String body = """
                [
                { "op": "replace", "path": "/schemas/mydublincore/fields/mydc_coverage", "value": {"type":"string"} }
                ]
                """;
        // put it back unchanged
        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                PATH_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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

        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);
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

        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);
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

        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);
        // replace projectId from the original JSON
        body = """
                [
                { "op": "replace", "path": "/primaryTypes/SysFile/projectId", "value": "P321" },
                { "op": "replace", "path": "/mixinTypes/SysFolderish/projectId", "value": "P321" },
                { "op": "replace", "path": "/schemas/mydublincore/projectId", "value": "P321" }
                ]
                """;

        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);
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

        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);

        // remove projectId from the original JSON
        body = """
                [
                { "op": "remove", "path": "/primaryTypes/SysFile/projectId" },
                { "op": "remove", "path": "/mixinTypes/SysFolderish/projectId" },
                { "op": "remove", "path": "/schemas/mydublincore/projectId" }
                ]
                """;

        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                Constants.API_PATH + queryParam);
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

        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, body,
                PATH_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPatchRemove(String queryParam) throws Exception {
        String primaryTypesWithoutDummyFolder = """
                {
                    "SysContent": {},
                    "SysEmbeddings": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysembeddings",
                            "system"
                        ]
                    },
                    "SysFile": {
                        "extends": "SysContent",
                        "mixins": [
                            "SysFilish"
                        ],
                        "schemas": [
                            "mydublincore",
                            "sysfiles",
                            "system"
                        ]
                    },
                    "SysFolder": {
                        "extends": "SysContent",
                        "mixins": [
                            "SysFolderish"
                        ],
                        "schemas": [
                            "mydublincore",
                            "system"
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
                            "sysmdanno",
                            "system"
                        ],
                       "mixins": [
                         "SysVersionedWithParent"
                       ]
                    },
                    "SysMDBookmark": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysmdbm",
                            "system"
                        ],
                       "mixins": [
                         "SysVersionedWithParent"
                       ]
                    },
                    "SysMDComment": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysmdcomment",
                            "system"
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
                        "schemas": [
                            "sysrelation",
                            "system"
                        ]
                    },
                    "SysRendition": {
                        "extends": "SysContent",
                        "schemas": [
                            "sysrendition",
                            "system"
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
                            "sysembeddings",
                            "system"
                        ]
                    },
                    "SysVocabulary": {
                        "extends": "SysContent",
                        "mixins": [
                            "SysFolderish"
                        ],
                        "schemas": [
                            "system",
                            "sysvocab"
                        ],
                        "subtypes": [
                            "SysVocabularyEntry"
                        ]
                    },
                    "SysVocabularyEntry": {
                        "extends": "SysContent",
                        "schemas": [
                            "system",
                            "sysvocab"
                        ]
                    },
                    "TestDocument": {
                        "extends": "SysContent",
                        "schemas": [
                            "mydublincore",
                            "sysfile",
                            "system",
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
        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, removalBody,
                Constants.API_PATH + queryParam);
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, removalBody, PATH_VALIDATE_ONLY_TRUE);
        assertNoErrors(result);
        // check the model has not been changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addBody,
                Constants.API_PATH + queryParam);
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
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, deleteBody,
                Constants.API_PATH + queryParam);
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
        checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addBodyToArray, PATH_VALIDATE_ONLY_TRUE);
        String addBody = """
                [
                    { "op": "add", "path": "/primaryTypes/SysOrderedFolder/schemas","value":["sysfile"] }
                ]
                """;
        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addBody,
                PATH_VALIDATE_ONLY_TRUE).andExpect(jsonPath("$").doesNotExist());
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);

        // We'll do the addition this time, so we can test the delete will validate
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addBody,
                Constants.API_PATH);
        assertEqualsInJsonPath(orderedFolderPartAfterAddition, result, orderedFolderPath);

        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addBodyToArray,
                Constants.API_PATH);
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
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                deleteBody, PATH_VALIDATE_ONLY_TRUE);
        // check there are no errors in response
        assertNoErrors(result);
        // check the model has not been changed (we are checking that the original update is still applied)
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                Constants.API_PATH + queryParam);
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
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addNewMemberIntoNewType, Constants.API_PATH + queryParam);
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
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                replaceOldMemberType, Constants.API_PATH + queryParam);
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
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, testMembers,
                Constants.API_PATH + queryParam);
        assertEqualsInJsonPath(newTypeJson, result, newTypePath);

        // move newType from types to mixinTypes
        String newerTypePath = "types.newNewType";
        String moveMembers = """
                [
                { "op": "add", "path": "/types/newNewType", "value": { "fields": {}} },
                { "op": "move", "from": "/types/newType/fields/oldMember", "path": "/types/newNewType/fields/oldMember"}
                ]
                """;
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, moveMembers,
                Constants.API_PATH + queryParam);
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
        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                PATH_VALIDATE_ONLY_TRUE);
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                Constants.API_PATH);
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
        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addNewMemberIntoNewType, PATH_VALIDATE_ONLY_TRUE);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBodyEquals(newTypeJson1ResponseBody, result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                addNewMemberIntoNewType, Constants.API_PATH);
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
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                replaceOldMemberType, PATH_VALIDATE_ONLY_TRUE);
        // check there are no errors in response
        assertNoErrors(result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBodyEquals(newTypeJson2ResponseBody, result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                replaceOldMemberType, Constants.API_PATH);
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

        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, testMembers,
                PATH_VALIDATE_ONLY_TRUE);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBodyEquals(newTypeJson3ResponseBody, result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, testMembers,
                Constants.API_PATH);
        assertEqualsInJsonPath(newTypeJson3, result, newTypePath);
        String testMembersUpdateResponseBody = result.andReturn().getResponse().getContentAsString();

        // move newType from types to mixinTypes
        String moveMembers = """
                [
                { "op": "add", "path": "/types/newNewType", "value": { "fields": {}} },
                { "op": "move", "from": "/types/newType/fields/oldMember", "path": "/types/newNewType/fields/oldMember"}
                ]
                """;
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                moveMembers, PATH_VALIDATE_ONLY_TRUE);
        // Check for no errors
        assertNoErrors(result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
        var result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                Constants.API_PATH + queryParam);
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
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                copyFieldFromOldBlobBody, Constants.API_PATH + queryParam);
        assertEqualsInJsonPath(newBlobJson, result, newBlobPath);

        // delete newBlob type
        String deleteNewBlobTypeBody = """
                [
                { "op": "remove", "path": "/types/newBlob" }
                ]
                """;
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, deleteNewBlobTypeBody,
                Constants.API_PATH + queryParam).andExpect(jsonPath("types", not(hasKey("newBlob"))));
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
        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                PATH_VALIDATE_ONLY_TRUE);
        // check the model has not been changed
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);

        // Apply for next validation check
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, addNewTypeBody,
                Constants.API_PATH);
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
        checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                copyFieldFromOldBlobBody, PATH_VALIDATE_ONLY_TRUE);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBodyEquals(newBlobJson1ResponseBody, result);

        // Apply for next validation
        result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                copyFieldFromOldBlobBody, Constants.API_PATH);
        assertEqualsInJsonPath(newBlobJson2, result, newBlobPath);
        String newBlobJson2ResponseBody = result.andReturn().getResponse().getContentAsString();

        // delete newBlob type
        String deleteNewBlobTypeBody = """
                [
                { "op": "remove", "path": "/types/newBlob" }
                ]
                """;
        result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                deleteNewBlobTypeBody, PATH_VALIDATE_ONLY_TRUE);
        // Check that there are no errors
        assertNoErrors(result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBodyEquals(newBlobJson2ResponseBody, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    public void testPutUnauthenticatedForProject(String queryParam) throws Exception {
        String url = Constants.API_PATH + PROJECT_PF_00000 + queryParam;
        mvc.perform(put(url).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    public void testPutUnauthorizedForProject(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PUT, REPO_ID, "{}", Constants.API_PATH + PROJECT_PF_00000 + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    public void testPutBadRepoForProject(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, BAD_REPO_ID, "{}", Constants.API_PATH + PROJECT_PF_00000 + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    public void testPutInvalidJsonForProject(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, NOT_JSON,
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForProjectConflictWithDefaultModel(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);

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
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBody("rest/validation_error_for_default_content_model_item.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForProjectUnknownMixinTypeValidationUnderPrimaryTypes(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is already
        // present as a default content model item.
        String putBodyForProject = """
                        {
                          "primaryTypes": {
                            "MixinTest": {
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
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBody("rest/validation_error_for_unknown_mixin_under_primarytype.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForANewProject(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);

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
                        "system",
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
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
        checkResponseBodyPatched("rest/model.json", "rest/model_updated_ForProject.patch.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForANewProjectWithSchemaValidationError(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);

        String invalidObjectViolation = """
                {
                      "invalidValue": {
                        "list": {}
                      },
                      "message": "The value \\"'{'list='{''}''}'\\" is an invalid type in the field \\"schemas/test/fields/test_desc5\\".",
                      "messageKey": "label.schema.application.violation.InvalidObjectViolation.fields",
                      "parameters": [
                        {
                          "list": {}
                        },
                        "schemas/test/fields/test_desc5"
                      ],
                      "path": "schemas/test/fields/test_desc5"
                    }
                """;

        // Content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is not supported
        // by the repository
        // in the content model
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
                        "system",
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, REPO_ID, newBody,
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
        assertEqualsInJsonPath(invalidObjectViolation, result, "errors[2]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForAnExistingProject(String queryParam) throws Exception {
        // get original JSON
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
                        "system",
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
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
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
                        "system",
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
                Constants.API_PATH + "/project/PBAR9999" + queryParam);
        checkResponseBody("rest/validation_error_model_item_exists_for_another_project.json", result);

        // cleanup: put back the original one
        result = checkResponseWithPayload(OK, PUT, REPO_ID, body, Constants.API_PATH + queryParam);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    public void testPutForProjectValidateTrue() throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
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
                        "system",
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
        var result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    public void testPutForProjectEmptyPayload(String queryParam) throws Exception {
        // get original JSON
        checkResponse(OK, GET, REPO_ID, Constants.API_PATH);

        // Empty content model payload
        String putBodyForProject = "{}";
        JSONObject jsonObject = new JSONObject(putBodyForProject);
        // put new model
        String newBody = jsonObject.toString();
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody,
                Constants.API_PATH + PROJECT_PF_00000 + queryParam);
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
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, deleteRootPrimaryTypeBody, PATH_VALIDATE_ONLY_TRUE);
        checkResponseBody("rest/validation_error_for_remove_root_primary_type.json", result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testPatchRemoveInPrimaryTypesDocumentValidateTrue() throws Exception {

        // delete Document Primary type
        String deleteDocumentPrimaryTypeBody = """
                [
                { "op": "remove", "path": "/primaryTypes/SysContent" }
                ]
                """;
        var result = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, deleteDocumentPrimaryTypeBody, PATH_VALIDATE_ONLY_TRUE);
        checkResponseBody("rest/validation_error_for_remove_document_primary_type.json", result);
        // Check that the previous update has not changed
        result = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testTrueFlagFullTextIndexedOnPrimaryTypeDefinitionAndField() throws Exception {
        // get original JSON
        var originalModel = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        String putBodyForProject = """
                {
                  "primaryTypes": {
                    "FileFullText": {
                      "extends": "SysContent",
                      "schemas": [
                        "system",
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
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, Constants.API_PATH + "/project/HxP001");
        var expectedPrimaryTypeJson = """
                {
                  "extends": "SysContent",
                  "mixins": [
                    "SysFullTextIndexable"
                  ],
                  "projectId": "HxP001",
                  "schemas": [
                    "system",
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
                originalModel.andReturn().getResponse().getContentAsString(), Constants.API_PATH);
        checkResponseBody("rest/model.json", result);
    }

    @Test
    void testFalseFlagFullTextIndexedOnPrimaryTypeDefinitionAndField() throws Exception {
        // get original JSON
        var originalModel = checkResponse(OK, GET, REPO_ID, Constants.API_PATH);
        String putBodyForProject = """
                {
                  "primaryTypes": {
                    "FileWithoutFullText": {
                      "extends": "SysContent",
                      "schemas": [
                        "system",
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
        var result = checkResponseWithPayload(OK, PUT, REPO_ID, newBody, Constants.API_PATH + "/project/HxP001");
        var expectedPrimaryTypeJson = """
                {
                  "extends": "SysContent",
                  "projectId": "HxP001",
                  "schemas": [
                    "system",
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
                originalModel.andReturn().getResponse().getContentAsString(), Constants.API_PATH);
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
