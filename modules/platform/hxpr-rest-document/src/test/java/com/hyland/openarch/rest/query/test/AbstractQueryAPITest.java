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
package com.hyland.openarch.rest.query.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_DOCUMENT_ID;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_ID;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_SCORE;
import static com.hyland.openarch.rest.query.io.DocumentQueryDTO.QUERY_STRING_PROP;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response.Status;
import org.json.JSONException;
import com.hyland.openarch.rest.document.DocumentDTO;
import com.hyland.openarch.rest.document.ObjectMapperConfiguration;
import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.rest.document.test.ContentTestHelper;
import com.hyland.openarch.rest.exception.NuxeoExceptionController;
import com.hyland.openarch.rest.query.io.DocumentQueryResultDTO;
import com.hyland.openarch.rest.utils.Versions;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@Import({ NuxeoExceptionController.class, ObjectMapperConfiguration.class })
public abstract class AbstractQueryAPITest extends AbstractAPITest {

    static final String PROPERTY_MATCHER_TEMPLATE = "%s[*].%s";

    protected static final String NEXT_SEARCH_AFTER = "nextSearchAfter";

    protected static final JSONComparator RESULT_COMPATOR = new CustomComparator(NON_EXTENSIBLE,
            getDocumentCustomization("documents"), getEmbeddingCustomization("embeddings"),
            new Customization(NEXT_SEARCH_AFTER, NON_NULL_MATCHER));

    protected static final List<Customization> getNotNullCustomizations(String arrayPath, List<String> properties) {
        return properties.stream()
                         .map(p -> new Customization(PROPERTY_MATCHER_TEMPLATE.formatted(arrayPath, p),
                                 NON_NULL_MATCHER))
                         .toList();
    }

    protected static final List<Customization> getDoubleCustomizations(String arrayPath, List<String> doubleFields,
            double tolerance) {
        ValueMatcher<Object> toleranceMatcher = (a, b) -> Math.abs((Double) a - (Double) b) < tolerance;
        return doubleFields.stream()
                           .map(doubleField -> new Customization(
                                   PROPERTY_MATCHER_TEMPLATE.formatted(arrayPath, doubleField), toleranceMatcher))
                           .toList();
    }

    protected static final Customization getDocumentCustomization(String arrayPath) {
        List<Customization> customizations = getNotNullCustomizations(arrayPath,
                List.of(SYS_ID, SYS_CREATED, SYS_MODIFIED));
        return new Customization(arrayPath, new ArrayValueMatcher<>(
                new CustomComparator(NON_EXTENSIBLE, customizations.toArray(new Customization[0]))));
    }

    protected static final Customization getEmbeddingCustomization(String arrayPath) {
        List<Customization> customizations = new ArrayList<>(
                getNotNullCustomizations(arrayPath, List.of(EMBEDDING_PROPERTY_ID, EMBEDDING_PROPERTY_DOCUMENT_ID)));
        customizations.addAll(getDoubleCustomizations(arrayPath, List.of(EMBEDDING_PROPERTY_SCORE), 0.0001));
        return new Customization(arrayPath, new ArrayValueMatcher<>(
                new CustomComparator(NON_EXTENSIBLE, customizations.toArray(new Customization[0]))));
    }

    protected ResultActions checkResponse(Status expectedStatus, String path, HttpMethod method, HttpHeaders headers,
            Map<String, Object> queryParameters) throws Exception {
        var requestBuilder = request(method, path).headers(headers).with(csrf());
        if (POST.equals(method)) {
            // as json request body
            requestBuilder.contentType(MediaType.APPLICATION_JSON);
            Map<String, Object> postObject = new HashMap<>();
            postObject.put("repositoryId", REPO_ID);
            if (queryParameters != null) {
                queryParameters.forEach((k, v) -> {
                    if (QUERY_STRING_PROP.equals(k)) {
                        postObject.put("query", v);
                    } else {
                        postObject.put(k, v);
                    }
                });
                String json = mapper.writeValueAsString(postObject);
                requestBuilder.content(json);
            }
        } else {
            requestBuilder.header(HXCS_REPOSITORY, REPO_ID);
            if (GET.equals(method) && queryParameters != null) {
                // as request query parameters
                queryParameters.forEach((k, v) -> {
                    if (v instanceof List<?> list) {
                        list.forEach(param -> requestBuilder.param(k, param.toString()));
                    } else {
                        requestBuilder.queryParam(k, v.toString());
                    }
                });
            }
        }
        return mvc.perform(requestBuilder).andExpect(status().is(expectedStatus.getStatusCode()));
    }

    protected ResultActions checkResponse(Status expectedStatus, String path, HttpMethod method,
            Map<String, Object> queryParameters) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return checkResponse(expectedStatus, path, method, headers, queryParameters);
    }

    protected void checkQueryResponseBody(String expectedFilePath, ResultActions resultActions, Object... args)
            throws IOException, JSONException {
        ContentTestHelper.checkJson(expectedFilePath, resultActions, RESULT_COMPATOR, args);
    }

    protected void checkResult(DocumentQueryResultDTO actual, //
            String repositoryId, //
            String query, //
            Long limit, //
            long offset, //
            long count, //
            Long totalCount) {
        assertEquals(repositoryId, actual.repositoryId());
        assertEquals(query, actual.query());
        assertEquals(limit, actual.limit());
        assertEquals(offset, actual.offset());
        assertEquals(count, actual.count());
        assertEquals(totalCount, actual.totalCount());
    }

    protected void checkResultDocument(DocumentQueryResultDTO actual, int docIndex, String title) {
        assertNotNull(actual);
        List<DocumentDTO> docs = actual.documents();
        assertNotNull(docs);
        assertEquals(title, docs.get(docIndex).getTitle());
    }

    protected HttpHeaders getBaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HXCS_REPOSITORY, REPO_ID);
        return headers;
    }

    protected HttpHeaders getV1Headers() {
        HttpHeaders headers = getBaseHeaders();
        headers.add(HttpHeaders.ACCEPT, Versions.APP_HXCS_JSON_V1);
        return headers;
    }

    protected HttpHeaders getV0Headers() {
        HttpHeaders headers = getBaseHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/vnd.hxcs.v0+json");
        return headers;
    }
}
