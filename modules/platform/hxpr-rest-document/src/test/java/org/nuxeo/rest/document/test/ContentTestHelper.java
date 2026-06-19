/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.document.test;

import static org.hamcrest.Matchers.matchesPattern;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

public class ContentTestHelper {

    private static boolean PRINT_ACTUAL_CONTENT = false;

    private ContentTestHelper() {
        // Utility class
    }

    public static <T> T getJsonPath(ResultActions result, String jsonPath) throws Exception {
        return getJsonPath(result.andReturn().getResponse().getContentAsString(), jsonPath);
    }

    public static <T> T getJsonPath(String responseBody, String jsonPath) {
        return JsonPath.parse(responseBody).read(jsonPath);
    }

    public static String getReferenceContent(String path) throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (stream != null) {
                return IOUtils.toString(stream, UTF_8).trim();
            }
            return "null";
        }
    }

    public static String getResponseBody(ResultActions response) throws UnsupportedEncodingException {
        String body = response.andReturn().getResponse().getContentAsString();
        if (PRINT_ACTUAL_CONTENT) {
            System.err.println(body);
        }
        return body;
    }

    public static void checkJson(String expectedFilePath, ResultActions response) throws IOException, JSONException {
        String expected = getReferenceContent(expectedFilePath);
        String actual = getResponseBody(response);
        JSONAssert.assertEquals(expected, actual, NON_EXTENSIBLE);
    }

    public static void checkJson(String expectedFilePath, ResultActions response, JSONComparator comparator,
            Object... args) throws IOException, JSONException {
        String expected = getReferenceContent(expectedFilePath);
        if (args != null && args.length != 0) {
            expected = String.format(expected, args);
        }
        String actual = getResponseBody(response);
        JSONAssert.assertEquals(expected, actual, comparator);
    }

    public static void checkErrorResponse(Status expectedStatus, String expectedMessage, ResultActions resultActions)
            throws Exception {
        checkErrorResponse(expectedStatus, resultActions).andExpect(jsonPath("message").value(expectedMessage));
    }

    public static void checkErrorResponse(StatusType expectedStatus, Pattern expectedPattern,
            ResultActions resultActions) throws Exception {
        checkErrorResponse(expectedStatus, resultActions).andExpect(
                jsonPath("message").value(matchesPattern(expectedPattern)));
    }

    private static ResultActions checkErrorResponse(StatusType expectedStatus, ResultActions response)
            throws Exception {
        return response.andExpect(status().is(expectedStatus.getStatusCode()))
                       .andExpectAll(jsonPath("entity-type").value("exception"),
                               jsonPath("status").value(expectedStatus.getStatusCode()));
    }

}
