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
package com.hyland.openarch.rest.document.test;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CHANGE_TOKEN;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static com.hyland.openarch.rest.document.AbstractDocumentAPI.PATH;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flipkart.zjsonpatch.Jackson3JsonPatch;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.Response.StatusType;
import org.apache.http.HttpStatus;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockTestConfiguration;
import com.hyland.openarch.rest.auth.RolesAllowedInterceptor;
import com.hyland.openarch.rest.document.ObjectMapperConfiguration;
import com.hyland.openarch.rest.document.test.multipart.MockMultiPartFilePart;
import com.hyland.openarch.rest.exception.RepositoryExceptionController;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.springboot.junit.SpringBootObjectMappers;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit-test version of the server document api rest testing.
 */
@Features({CoreFeature.class, UserManagerMockFeature.class})
@Import({ RepositoryExceptionController.class, ObjectMapperConfiguration.class, RolesAllowedInterceptor.class, UserManagerMockTestConfiguration.class })
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-repo-core-types-contrib.xml")
public abstract class AbstractAPITest extends RuntimeSpringBootTest {

    protected static final String ROOT_ID = "00000000-0000-0000-0000-000000000000";

    protected static final String REPO_ID = "test";

    protected static final String BAD_REPO_ID = "noSuchRepo";

    protected static final String PF_00000 = "PF00000";

    protected static final String PROJECT_PF_00000 = "/project/" + PF_00000;

    protected static final String ADMIN = TEST_ADMIN_USER;

    protected static final String ALT_USER = "jchan";
    protected static final String ALT_USER_ID = "ced9ee59-c4d5-4b58-866a-b84cd6cc6fae";

    protected static final String ALT_ANOTHER_USER = "tiry";

    protected static final String NOT_JSON = "this-is-not-json";

    private static final String PARAM_VALIDATE_ONLY_PREFIX = "?validateOnly=";

    protected static final String PARAM_VALIDATE_ONLY_FALSE = PARAM_VALIDATE_ONLY_PREFIX + "false";

    protected static final String PARAM_VALIDATE_ONLY_TRUE = PARAM_VALIDATE_ONLY_PREFIX + "true";

    protected static final RepositoryPrincipal ALT_PRINCIPAL = DummyLoginFeature.getTestPrincipal(ALT_USER_ID, false);

    protected static final ValueMatcher<Object> NON_NULL_MATCHER = (o1, o2) -> o1 != null && o2 != null;

    protected static final String EVENT_DATA_PROPERTY = "data";

    protected static final ObjectMapper MAPPER = SpringBootObjectMappers.productive(ObjectMapperConfiguration.class);

    protected static final JSONComparator DOC_COMPARATOR = new CustomComparator(NON_EXTENSIBLE,
            new Customization(SYS_CREATED, NON_NULL_MATCHER), //
            new Customization(SYS_MODIFIED, NON_NULL_MATCHER), //
            new Customization(SYS_CHANGE_TOKEN, (o1, o2) -> true), //
            new Customization(SYS_ANCESTOR_IDS, NON_NULL_MATCHER));

    protected static final StatusType UNPROCESSABLE_ENTITY = new StatusType(){
        @Override
        public int getStatusCode() {
            return HttpStatus.SC_UNPROCESSABLE_ENTITY;
        }

        @Override
        public Family getFamily() {
            return Family.CLIENT_ERROR;
        }

        @Override
        public String getReasonPhrase() {
            return "Unprocessable Entity";
        }
    };

    protected ResultActions checkResponse(StatusType expectedStatus, HttpMethod method, String repoId, String path)
            throws Exception {
        return checkResponse(expectedStatus, method, getRepositoryHttpHeaders(repoId), path);
    }

    protected ResultActions checkResponse(StatusType expectedStatus, HttpMethod method, HttpHeaders headers,
            String path, Object... params) throws Exception {
        return mvc.perform(getBaseRequestBuilder(method, headers, path, params))
                  .andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponse(StatusType expectedStatus, HttpMethod method, String path) throws Exception {
        return checkResponse(expectedStatus, method, new HttpHeaders(), path);
    }

    protected MockHttpServletRequestBuilder getBaseRequestBuilder(HttpMethod method, HttpHeaders headers, String path, Object... pathVariables) {
        var requestBuilder = getBaseRequestBuilder(method, path, pathVariables).headers(headers).with(csrf());
        if (headers.getAccept().isEmpty()) {
            requestBuilder.accept(Versions.APP_HXCS_JSON_V1);
        }
        if (headers.getOrEmpty(HXCS_REPOSITORY).isEmpty()) {
            requestBuilder.header(HXCS_REPOSITORY, REPO_ID);
        }
        return requestBuilder;
    }

    private MockHttpServletRequestBuilder getBaseRequestBuilder(HttpMethod method, String path, Object... params) {
        return params != null && params.length > 0 ? MockMvcRequestBuilders.request(method, path, params)
                : MockMvcRequestBuilders.request(method, path);
    }

    protected HttpHeaders getRepositoryHttpHeaders(String repoId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HXCS_REPOSITORY, repoId);
        return headers;
    }

    protected ResultActions checkResponseWithReferencePayload(StatusType expectedStatus, String path, HttpMethod method,
            String referencePostBody, Object... args) throws Exception {
        String json = ContentTestHelper.getReferenceContent(referencePostBody);
        if (args != null && args.length != 0) {
            json = String.format(json, args);
        }
        return checkResponseWithPayload(expectedStatus, method, REPO_ID, json, path);
    }

    protected ResultActions checkResponseWithPayload(StatusType expectedStatus, HttpMethod method, String contentType,
            String repoId, String jsonPayload, String path, Object... params) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HXCS_REPOSITORY, repoId);
        headers.add(CONTENT_TYPE, contentType);

        return checkResponseWithPayload(expectedStatus, method, headers, jsonPayload, path, params);
    }

    protected ResultActions checkResponseWithPayloadWithUser(StatusType expectedStatus, HttpMethod method, String contentType,
                                                     String repoId, String jsonPayload, String path, String user,Object... params) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HXCS_REPOSITORY, repoId);
        headers.add(CONTENT_TYPE, contentType);

        return checkResponseWithPayloadWithUser(expectedStatus, method, headers, jsonPayload, path, user, params);
    }

    protected ResultActions checkResponseWithPayloadWithUser(StatusType expectedStatus, HttpMethod method, HttpHeaders headers,
                                                     String jsonPayload, String path, String user, Object... params) throws Exception {

        var requestSpec = getBaseRequestBuilder(method, headers, path, params).with(user(user));

        if (jsonPayload != null) {
            requestSpec.content(jsonPayload);
        }
        requestSpec.with(csrf());

        ResultActions result = mvc.perform(requestSpec);

        return result.andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponseWithPayload(StatusType expectedStatus, HttpMethod method, HttpHeaders headers,
            String jsonPayload, String path, Object... params) throws Exception {

        var requestSpec = getBaseRequestBuilder(method, headers, path, params);

        if (jsonPayload != null) {
            requestSpec.content(jsonPayload);
        }
        requestSpec.with(csrf());

        ResultActions result = mvc.perform(requestSpec);

        return result.andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponseWithPayload(StatusType expectedStatus, HttpMethod method, HttpHeaders headers,
            String path, byte[] data, Object... params) throws Exception {
        return mvc.perform(getBaseRequestBuilder(method, headers, path, params).content(data))
                  .andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponseWithPayloadForMultipartFormData(StatusType expectedStatus, HttpMethod method,
            String repoId, String jsonPayload, String path, MockMultipartFile[] multiPartSpecifications)
            throws Exception {

        var requestSpec = multipart(method, path);

        if (jsonPayload != null) {
            var docPart = new MockMultipartFile("main", "main", MediaType.APPLICATION_JSON, jsonPayload.getBytes());
            requestSpec.part(new MockMultiPartFilePart(docPart));
        }
        for (var multiPartSpecification : multiPartSpecifications) {
            requestSpec.part(new MockMultiPartFilePart(multiPartSpecification));
        }
        requestSpec.header(HXCS_REPOSITORY, repoId).with(csrf());
        return mvc.perform(requestSpec).andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponseWithPayloadWithContentTypeMultipartFormData(StatusType expectedStatus, HttpMethod method,
            String repoId, String jsonPayload, String path, MockMultipartFile[] multiPartSpecifications)
            throws Exception {

        var requestSpec = multipart(method, path);

        if (jsonPayload != null) {
            var docPart = new MockMultipartFile("main", "main", MediaType.MULTIPART_FORM_DATA, jsonPayload.getBytes());
            requestSpec.part(new MockMultiPartFilePart(docPart));
        }
        for (var multiPartSpecification : multiPartSpecifications) {
            requestSpec.part(new MockMultiPartFilePart(multiPartSpecification));
        }
        requestSpec.header(HXCS_REPOSITORY, repoId).with(csrf());
        return mvc.perform(requestSpec).andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponseWithPayload(StatusType expectedStatus, HttpMethod method, String repoId,
            String jsonPayload, String path) throws Exception {
        return checkResponseWithPayload(expectedStatus, method, MediaType.APPLICATION_JSON, repoId, jsonPayload, path);
    }

    protected void checkResponseBody(String expectedFilePath, ResultActions result) throws Exception {
        checkResponseBodyEquals(ContentTestHelper.getReferenceContent(expectedFilePath), result);
    }

    protected void checkResponseBodyPatched(String expectedBaseFilePath, String expectedPatchPath, ResultActions result) throws Exception {
        String baseJSONString = ContentTestHelper.getReferenceContent(expectedBaseFilePath);
        JsonNode baseJSON = MAPPER.readTree(baseJSONString);
        String patchString = ContentTestHelper.getReferenceContent(expectedPatchPath);
        JsonNode patch = MAPPER.readTree(patchString);
        JsonNode patched = Jackson3JsonPatch.apply(patch, baseJSON);
        checkResponseBodyEquals(patched.toString(), result);
    }

    protected void checkResponseBodyEquals(String expected, ResultActions resultActions) throws Exception {
        String actual = resultActions.andReturn().getResponse().getContentAsString();
        JSONAssert.assertEquals(expected, actual, NON_EXTENSIBLE);
    }

    protected void testFeatureFlag(Status expectedStatus, String docId, String apiName) throws Exception {
        checkResponseWithPayload(expectedStatus, POST, REPO_ID, null, PATH + "/" + docId + "/" + apiName);
    }
}
