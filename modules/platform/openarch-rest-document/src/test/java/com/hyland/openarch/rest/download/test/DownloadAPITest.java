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
package com.hyland.openarch.rest.download.test;

import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NOT_IMPLEMENTED;
import static jakarta.ws.rs.core.Response.Status.NOT_MODIFIED;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static com.hyland.openarch.rest.download.DownloadAPI.PATH;
import static org.springframework.http.HttpHeaders.ACCEPT_RANGES;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.rest.document.test.DocumentAPITestConfiguration;
import com.hyland.openarch.rest.download.DownloadAPI;
import com.hyland.openarch.rest.download.DownloadManager;
import com.hyland.openarch.rest.download.DownloadStudioFormDataAPI;
import com.hyland.openarch.rest.utils.Headers;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Download Rest API tests.
 */
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-repo-core-types-contrib.xml")
@Features(CoreFeature.class)
@SpringBootTest(classes = { DownloadAPI.class, DownloadStudioFormDataAPI.class, DownloadManager.class })
@Import(DocumentAPITestConfiguration.class)
@ExtendWith(MockitoExtension.class)
public class DownloadAPITest extends AbstractAPITest {

    protected static final Map<String, String> GENERATE_URL_QUERY_PARAMS = Map.of("expiration", "2000");

    @RuntimeInject
    protected CoreSession coreSession;

    protected String docId;

    protected StringBlob getBlob(String name, String content) {
        StringBlob b = new StringBlob(content);
        b.setFilename(name);
        return b;
    }

    @BeforeEach
    protected void createDocument() {
        DocumentModel doc = coreSession.createDocumentModel("/", "doc", "TestDocument");
        // blobs
        doc.setPropertyValue(SYSFILE_BLOB, getBlob("hello", "hello world!"));
        doc.setPropertyValue("tp_fileList", List.of( //
                getBlob("foo", "hello multiple world1!"), //
                getBlob("bar", "hello multiple world2!"),
                // non-regression test for https://github.com/HylandSoftware/hxpr/pull/201
                Blobs.createBlob("hello multiple world3!".getBytes()) //
        ));
        doc.setPropertyValue("tp_fileComplexList", List.of( //
                Map.of("filenameItem", "bar", "blobItem", getBlob("complex", "hello complex world!"))));

        doc.create();
        coreSession.save();
        docId = doc.getId();
    }

    @AfterEach
    protected void cleanupDocument() {
        if (docId != null) {
            coreSession.removeDocument(new IdRef(docId));
        }
        docId = null;
    }

    protected ResultActions checkResponseStatus(HttpMethod method, Response.Status expectedStatus, HttpHeaders headers,
            String docId, String xpath) throws Exception {
        String path = PATH + "/{docId}/{propertyXPathAndFilename}";
        var requestBuilder = getMockHttpServletRequestBuilder(method, headers, docId, path, xpath);
        var r = mvc.perform(requestBuilder);
        return r.andExpect(status().is(expectedStatus.getStatusCode()));
    }

    private static MockHttpServletRequestBuilder getMockHttpServletRequestBuilder(HttpMethod method,
            HttpHeaders headers, String docId, String path, String xpath) {
        return MockMvcRequestBuilders.request(method, path, docId, xpath + "/test.txt").headers(headers).with(csrf());
    }

    protected ResultActions checkResponseStatus(HttpMethod method, Response.Status expectedStatus, String docId,
            String xpath, Map<String, String> queryParam) throws Exception {
        String path = PATH + "/url/{docId}/{propertyXPathAndFilename}";
        var requestBuilder = getMockHttpServletRequestBuilder(method, getRepositoryHttpHeaders(REPO_ID), docId, path,
                xpath);

        for (Map.Entry<String, String> entry : queryParam.entrySet()) {
            requestBuilder.queryParam(entry.getKey(), entry.getValue());
        }

        ResultActions r = mvc.perform(requestBuilder);
        r.andExpect(status().is(expectedStatus.getStatusCode()));
        if (expectedStatus == OK) {
            r.andExpect(jsonPath("$").exists());
        }
        return r;
    }

    protected ResultActions checkResponseStatus(HttpMethod method, Response.Status expectedStatus, String docId,
            String xpath) throws Exception {
        return checkResponseStatus(method, expectedStatus, getRepositoryHttpHeaders(REPO_ID), docId, xpath);
    }

    private ResultActions checkResponseAndContent(HttpMethod method, Response.Status expectedStatus, String docId,
            String xpath, String expectedDigest, String content) throws Exception {
        HttpHeaders httpHeaders = getRepositoryHttpHeaders(REPO_ID);
        String path = PATH + "/{docId}/{propertyXPathAndFilename}";
        var requestBuilder = getMockHttpServletRequestBuilder(method, httpHeaders, docId, path, xpath);

        MvcResult mvcResult = mvc.perform(requestBuilder)
                                 .andExpect(status().is(expectedStatus.getStatusCode()))
                                 .andExpect(request().asyncStarted())
                                 .andReturn();

        ResultActions r = mvc.perform(asyncDispatch(mvcResult))
                             .andExpect(status().is(expectedStatus.getStatusCode()))
                             .andExpect(content().string(content));

        if (OK.equals(expectedStatus) && expectedDigest != null) {
            r.andExpectAll(content().contentType(MediaType.TEXT_PLAIN),
                    header().string(ETAG, String.format("\"%s\"", expectedDigest)),
                    header().string(CONTENT_DISPOSITION, "attachment; filename=test.txt"),
                    header().string(ACCEPT_RANGES, "bytes"));
        }
        return r;
    }

    @Test
    public void testHead() throws Exception {
        checkResponseStatus(HEAD, NOT_FOUND, "foo", SYSFILE_BLOB);

        checkResponseStatus(HEAD, NOT_FOUND, docId, "foo");
        ResultActions r = checkResponseStatus(HEAD, NO_CONTENT, docId, SYSFILE_BLOB);
        r.andExpectAll(content().string(""), header().doesNotExist(HttpHeaders.CONTENT_ENCODING));

        checkResponseStatus(HEAD, NOT_FOUND, docId, "tp_fileList/42");
        r = checkResponseStatus(HEAD, NO_CONTENT, docId, "tp_fileList/1");
        r.andExpectAll(content().string(""), header().doesNotExist(HttpHeaders.CONTENT_ENCODING));

        // non-regression test for https://github.com/HylandSoftware/hxpr/pull/201
        r = checkResponseStatus(HEAD, NO_CONTENT, docId, "tp_fileList/2");
        r.andExpectAll(content().string(""), header().doesNotExist(HttpHeaders.CONTENT_ENCODING));

        checkResponseStatus(HEAD, NOT_FOUND, docId, "tp_fileComplexList/1/blobItem");
        r = checkResponseStatus(HEAD, NO_CONTENT, docId, "tp_fileComplexList/0/blobItem");
        r.andExpectAll(content().string(""), header().doesNotExist(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    public void testHeadV1() throws Exception {
        checkResponseStatus(HEAD, NOT_FOUND, getRepositoryHttpHeaders(REPO_ID), docId, "foo");
    }

    @Test
    public void testGetWithNoModify() throws Exception {
        HttpHeaders httpHeaders = getRepositoryHttpHeaders(REPO_ID);
        httpHeaders.add(Headers.IF_MODIFIED_SINCE, String.valueOf(System.currentTimeMillis()));
        checkResponseStatus(GET, NOT_MODIFIED, httpHeaders, docId, SYSFILE_BLOB);
    }

    @Test
    public void testHeadV0() throws Exception {
        HttpHeaders httpHeaders = getRepositoryHttpHeaders(REPO_ID);
        httpHeaders.add("Accept", "application/vnd.hxcs.v0+octet-stream");
        checkResponseStatus(HEAD, NOT_ACCEPTABLE, httpHeaders, docId, "foo");
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testHeadUnauthorized() throws Exception {
        checkResponseStatus(HEAD, UNAUTHORIZED, "foo", SYSFILE_BLOB);
        checkResponseStatus(HEAD, UNAUTHORIZED, docId, "foo");
        checkResponseStatus(HEAD, UNAUTHORIZED, docId, SYSFILE_BLOB);
    }

    @Test
    @WithUser(ALT_USER)
    public void testHeadPermission() throws Exception {
        checkResponseStatus(HEAD, NOT_FOUND, docId, SYSFILE_BLOB);
    }

    @Test
    public void testGet() throws Exception {
        checkResponseStatus(GET, NOT_FOUND, "foo", SYSFILE_BLOB);

        checkResponseStatus(GET, NOT_FOUND, docId, "foo");
        checkResponseAndContent(GET, OK, docId, SYSFILE_BLOB,
                "7509e5bda0c762d2bac7f90d758b5b2263fa01ccbc542ab5e3df163be08e6ca9", "hello world!");

        checkResponseStatus(GET, NOT_FOUND, docId, "tp_fileList/42");
        checkResponseAndContent(GET, OK, docId, "tp_fileList/1",
                "3a3015cef5604dbbe1a5cc17bd1805b76129ca09814beec574163b9da8342e80", "hello multiple world2!");
        // non-regression test for https://github.com/HylandSoftware/hxpr/pull/201
        ResultActions r = checkResponseStatus(GET, OK, docId, "tp_fileList/2");
        r.andExpect(content().contentType(Versions.APP_HXCS_OCTET_STREAM_V1));
        r.andExpectAll(
                header().string("ETag",
                        String.format("\"%s\"", "94a502ff3cd058267a72e738c56b04b6546154362a81a2a98334b2a835bf4cce")),
                header().string("Content-Disposition", "attachment; filename=test.txt"),
                header().string("Accept-Ranges", "bytes"));
        checkResponseAndContent(GET, OK, docId, "tp_fileList/2", null, "hello multiple world3!");

        checkResponseStatus(GET, NOT_FOUND, docId, "tp_fileComplexList/1/blobItem");
        checkResponseAndContent(GET, OK, docId, "tp_fileComplexList/0/blobItem",
                "4b7a6b13da04c3e5a77f466d341dc3a54ba60add1c4a02cf4c159e51c1ac4445", "hello complex world!");
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testGetUnauthorized() throws Exception {
        checkResponseStatus(GET, UNAUTHORIZED, "foo", SYSFILE_BLOB);
        checkResponseStatus(GET, UNAUTHORIZED, docId, "foo");
        checkResponseStatus(GET, UNAUTHORIZED, docId, SYSFILE_BLOB);
    }

    @Test
    @WithUser(ALT_USER)
    public void testGetPermission() throws Exception {
        checkResponseStatus(GET, NOT_FOUND, docId, SYSFILE_BLOB);
    }

    @Test
    @Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    public void testGetDownloadUrl() throws Exception {
        ResultActions r = checkResponseStatus(GET, OK, docId, SYSFILE_BLOB, GENERATE_URL_QUERY_PARAMS);
        assertTrue(containsPreSignedURLWithExpiration(r.andReturn().getResponse().getContentAsString(), 3600));
    }

    @Test
    @Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    public void testGetDownloadUrlInline() throws Exception {
        ResultActions r = checkResponseStatus(GET, OK, docId, SYSFILE_BLOB, Map.of("inline", "true"));
        assertTrue(r.andReturn().getResponse().getContentAsString().contains("inline=true"));
    }

    @Test
    @Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    public void testGetDownloadUrlDefaultsToNotInline() throws Exception {
        ResultActions r = checkResponseStatus(GET, OK, docId, SYSFILE_BLOB, GENERATE_URL_QUERY_PARAMS);
        assertTrue(r.andReturn().getResponse().getContentAsString().contains("inline=false"));
    }

    @Test
    @Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-presignedurl-provider-contrib.xml")
    public void testGetDownloadUrlComplexBlob() throws Exception {
        ResultActions r = checkResponseStatus(GET, OK, docId, "tp_fileComplexList/0/blobItem", GENERATE_URL_QUERY_PARAMS);
        assertTrue(containsPreSignedURLWithExpiration(r.andReturn().getResponse().getContentAsString(), 3600));
    }

    @Test
    public void testErrorWhileGeneratingUrl() throws Exception {
        checkResponseStatus(GET, NOT_IMPLEMENTED, docId, SYSFILE_BLOB, GENERATE_URL_QUERY_PARAMS);
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testGetUnauthorizedForDownloadUrl() throws Exception {
        checkResponseStatus(GET, UNAUTHORIZED, "foo", SYSFILE_BLOB, GENERATE_URL_QUERY_PARAMS);
        checkResponseStatus(GET, UNAUTHORIZED, docId, "foo", GENERATE_URL_QUERY_PARAMS);
        checkResponseStatus(GET, UNAUTHORIZED, docId, SYSFILE_BLOB, GENERATE_URL_QUERY_PARAMS);
    }

    @Test
    @WithUser(ALT_USER)
    public void testGetPermissionForDownloadUrl() throws Exception {
        checkResponseStatus(GET, NOT_FOUND, docId, SYSFILE_BLOB, GENERATE_URL_QUERY_PARAMS);
    }

    public static boolean containsPreSignedURLWithExpiration(String urlString, long expiration) {

        // Regular expression to match expiration timestamp in the URL
        Pattern pattern = Pattern.compile("Expires=(\\d+)");
        Matcher matcher = pattern.matcher(urlString);
        // Check if the URL contains a pre-signed URL with expiration
        boolean containsPreSignedURL = urlString.contains("https://s3.amazonaws.com") && urlString.contains("Expires=");
        if (containsPreSignedURL && matcher.find()) {
            // If the URL contains "Expires=", extract and parse the expiration timestamp
            String expirationString = matcher.group(1);
            long expirationTimestamp = Long.parseLong(expirationString);
            assertEquals(expiration, expirationTimestamp);
            return true;
        }
        return false;
    }

}
