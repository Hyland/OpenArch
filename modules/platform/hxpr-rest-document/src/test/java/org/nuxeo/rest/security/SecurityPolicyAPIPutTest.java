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
package org.nuxeo.rest.security;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.springframework.http.HttpMethod.PUT;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * This class provides basic tests against the PUT operation in {@link SecurityPolicyAPI}.
 */
class SecurityPolicyAPIPutTest extends AbstractSecurityPolicyAPITest {

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    void testPutUnauthenticated(String queryParam) throws Exception {
        checkUnauthorizedPutWithCsrf(SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPutUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PUT, REPO_ID, "{}", SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, BAD_REPO_ID, "{}", SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, NOT_JSON, SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutContainsDeserializeError(String queryParam) throws Exception {
        // bad engine name
        String body = """
                        {
                          "security clearance": {
                            "engine": "bad engine name",
                            "mixin": "Clearance",
                            "userAttribute": "clearance"
                          }
                        }
                        """;
        String message = checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, body,
                SecurityPolicyAPI.API_PATH + queryParam).andReturn().getResponse().getContentAsString();
        assertTrue(Pattern.matches(
                ".*" + ("Could not resolve type id 'bad engine name' .*" + "known type ids = \\[.*]") + ".*",
                message.replace("\n", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE })
    void testPutUnchanged(String queryParam) throws Exception {
        resetAfterChange((String original) -> { // just in case
            var response = checkResponseWithPayload(OK, PUT, REPO_ID, original,
                    SecurityPolicyAPI.API_PATH + queryParam);
            checkResponseBodyEquals(ORIGINAL_POLICIES, response);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPut(String queryParam) throws Exception {
        resetAfterChange((String original) -> {
            if (queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)) {
                var response = checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, NEW_POLICY,
                        SecurityPolicyAPI.API_PATH + queryParam);
                assertEquals("", response.andReturn().getResponse().getContentAsString());
                assertNoChange(ORIGINAL_POLICIES);
            } else {
                var response = checkResponseWithPayload(OK, PUT, REPO_ID, NEW_POLICY,
                        SecurityPolicyAPI.API_PATH + queryParam);
                checkResponseBodyEquals(NEW_POLICY, response);
            }
        });
    }

    @Test
    public void testPutReturns422IfParametersAreIncorrect() throws Exception {
        String body = """
                        {
                          "testincorrectparams": {
                            "engine": "externaluserrelations",
                            "mixin": "Clearance",
                            "userAttribute": "clearance"
                          }
                        }
                        """;
        String expected = """
                {
                  "errors" : [ {
                    "message" : "There are no permissions set on the \\"testincorrectparams\\" security policy",
                    "messageKey" : "There are no permissions set on the \\"{0}\\" security policy",
                    "parameters" : [ "testincorrectparams" ]
                  }, {
                    "message" : "The relations must be set as a list on security policy \\"testincorrectparams\\"",
                    "messageKey" : "The {0} must be set as a list on security policy \\"{1}\\"",
                    "parameters" : [ "relations", "testincorrectparams" ]
                  } ]
                }""";
        String message = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, REPO_ID, body,
                SecurityPolicyAPI.API_PATH).andReturn().getResponse().getContentAsString();
        JSONAssert.assertEquals(expected, message, NON_EXTENSIBLE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutReturnsMultipleProjectPolicies(String queryParam) throws Exception {
        // tests that policies with different projectIds or are system-wide are returned unlike PUT project
        // See testPutProjectReturnsSingleProjectPolicies for comparison
        resetAfterChange((String original) -> {
            if (queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)) {
                var result = checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, NEW_PROJECT_POLICIES,
                        SecurityPolicyAPI.API_PATH + queryParam);
                assertEquals("", result.andReturn().getResponse().getContentAsString());
                assertNoChange(ORIGINAL_POLICIES);
            } else {
                var result = checkResponseWithPayload(OK, PUT, REPO_ID, NEW_PROJECT_POLICIES,
                        SecurityPolicyAPI.API_PATH + queryParam);
                checkResponseBodyEquals(NEW_PROJECT_POLICIES, result);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutCombinedValidationFailures(String queryParam) throws Exception {
        resetAfterChange((String original) -> {
            // service layer: blank policy name           - error
            // service layer: missing mixin               - error
            // service layer: missing userAttribute       - error
            //    rest layer: using immutable policy name - warning ignored
            // service layer: invalid contentTypes        - warning
            // service layer: invalid permissions         - warning
            String newBody = """
                    {
                      "acl": {
                        "engine": "acl"
                      },
                      "": {
                        "engine": "securitylevel",
                        "contentTypes": ["TestDocument"],
                        "mixin": "Clearance",
                        "userAttribute": "clearance"
                      },
                      "missing policy": {
                        "engine": "securitylevel",
                        "contentTypes": ["invalid", "TestDocument"],
                        "permissions": ["invalid", "Read"]
                      }
                    }
                    """;
            String message = checkResponseWithPayload(UNPROCESSABLE_ENTITY, PUT, REPO_ID, newBody,
                    SecurityPolicyAPI.API_PATH + queryParam).andReturn().getResponse().getContentAsString();
            String expected = """
                    {
                      "errors": [
                        {
                          "message": "A security policy name must not be blank",
                          "messageKey": "A security policy name must not be blank"
                        },
                        {
                          "message": "There are no permissions set on the \\"\\" security policy",
                          "messageKey": "There are no permissions set on the \\"{0}\\" security policy",
                          "parameters": [
                            ""
                          ]
                        },
                        {
                          "message": "The mixin must be set on security policy \\"missing policy\\"",
                          "messageKey": "The {0} must be set on security policy \\"{1}\\"",
                          "parameters": [
                            "mixin",
                            "missing policy"
                          ]
                        },
                        {
                          "message": "The userAttribute must be set on security policy \\"missing policy\\"",
                          "messageKey": "The {0} must be set on security policy \\"{1}\\"",
                          "parameters": [
                            "userAttribute",
                            "missing policy"
                          ]
                        }
                      ]
                    }
                    """;
            if (queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)) {
                String warnings = """
                        {
                          "warnings": [
                            {
                              "message": "Security policy \\"acl\\" may not be modified - ignored",
                              "messageKey": "Security policy \\"{0}\\" may not be modified - ignored",
                              "parameters": [
                                "acl"
                              ]
                            },
                            {
                              "message": "An element of permissions \\"invalid\\" on security policy \\"missing policy\\" is unknown",
                              "messageKey": "An element of {0} \\"{1}\\" on security policy \\"{2}\\" is unknown",
                              "parameters": [
                                "permissions",
                                "invalid",
                                "missing policy"
                              ]
                            }
                          ]
                        }
                        """;
                expected = expected.substring(0, expected.length() - 2) + "," + warnings.substring(1);
            }
            JSONAssert.assertEquals(expected, message, NON_EXTENSIBLE);
        });
    }
}
