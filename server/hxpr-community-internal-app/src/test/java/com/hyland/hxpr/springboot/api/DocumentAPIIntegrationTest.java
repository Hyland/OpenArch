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
package com.hyland.hxpr.springboot.api;

import static io.restassured.RestAssured.given;
import static io.restassured.http.Method.DELETE;
import static io.restassured.http.Method.GET;
import static io.restassured.http.Method.POST;
import static io.restassured.http.Method.PUT;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;

import com.hyland.hxpr.springboot.AbstractSpringTest;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.ws.rs.core.Response.Status;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class DocumentAPIIntegrationTest extends AbstractSpringTest {

    protected static final String CHANGE_TOKEN_PROPERTY = "sys_changeToken";

    protected static final String ID_PROPERTY = "sys_id";

    protected static final String NAME_PROPERTY = "sys_name";

    protected static final String ID_MARKER = "***ID_MARKER***";

    protected static final String NAME_MARKER = "***NAME_MARKER***";

    protected static final String ROOT_ID = "00000000-0000-0000-0000-000000000000";

    protected static final ValueMatcher<Object> DATE_VALUE_MATCHER = (o1, o2) -> o1 != null && o2 != null;

    protected static final JSONComparator DOC_COMPARATOR = new CustomComparator(NON_EXTENSIBLE,
            new Customization(SYS_CREATED, DATE_VALUE_MATCHER),
            new Customization(SYS_MODIFIED, DATE_VALUE_MATCHER),
            new Customization(CHANGE_TOKEN_PROPERTY, (o1, o2) -> true));

    protected String getReferenceContent(String path) throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            return IOUtils.toString(stream, UTF_8).trim();
        }
    }

    protected void checkResponseBody(String expectedFilePath, Response response) throws IOException, JSONException {
        String expected = getReferenceContent(expectedFilePath);
        String actual = response.getBody().asString();
        JSONAssert.assertEquals(expected, actual, NON_EXTENSIBLE);
    }

    protected Response checkResponse(Status expectedStatus, Method method, String path, Object... pathParams) {
        Response response = authenticated().header("Accept", ContentType.JSON.getAcceptHeader())
                                           .request(method, PATH + path, pathParams);
        assertEquals(expectedStatus.getStatusCode(), response.getStatusCode());
        return response;
    }

    protected Response checkErrorResponse(Status expectedStatus, String expectedMessage, Response response) {
        assertEquals(expectedStatus.getStatusCode(), response.getStatusCode());
        JsonPath jsonPath = response.jsonPath();
        assertEquals("exception", jsonPath.get("entity-type"));
        assertEquals(expectedStatus.getStatusCode(), jsonPath.getInt("status"));
        assertEquals(expectedMessage, jsonPath.get("message"));
        return response;
    }

    protected Response checkResponseWithPayload(Status expectedStatus, Method method, String jsonPayload, String path,
            Object... pathParams) {
        Response response = authenticated().header("Accept", ContentType.JSON.getAcceptHeader())
                                           .contentType(ContentType.JSON)
                                           .body(jsonPayload)
                                           .request(method, PATH + path, pathParams);
        assertEquals(expectedStatus.getStatusCode(), response.getStatusCode());
        return response;
    }

    @Test
    public void testUnauthorized() {
        Arrays.asList(GET, POST, PUT, DELETE).forEach(method -> {
            given().header("Accept", ContentType.JSON.getAcceptHeader())
                   .contentType(ContentType.JSON)
                   .body("{}")
                   .when()
                   .request(method, PATH + "/{docId}", ROOT_ID)
                   .then()
                   .statusCode(UNAUTHORIZED.getStatusCode());
        });
    }

    @Test
    public void testGetById() throws IOException, JSONException {
        checkResponse(NOT_FOUND, GET, "/{docId}", "foo");
        checkResponse(NOT_FOUND, GET, "/{docId}", "");
        Response r = checkResponse(OK, GET, "/{docId}", ROOT_ID);
        checkResponseBody("rest/root.json", r);
    }

    protected String getCreationJson() {
        return """
                {
                  "sys_title": "My Folder",
                  "sys_name": "My Folder",
                  "sys_primaryType": "SysFolder"
                }
                """;
    }

    protected String getInvalidCreationJson() {
        // missing type info
        return """
                {
                  "sys_title": "My Folder"
                }
                """;
    }

    protected String getInvalidCreationJson2() {
        // invalid type info
        return """
                {
                  "sys_title": "My Folder",
                  "sys_primaryType": "Unknown Type"
                }
                """;
    }

    protected String getInvalidCreationJson3() {
        // invalid property
        return """
                {
                  "dc_foo": "bar",
                  "sys_primaryType": "SysFolder"
                }
                """;
    }

    protected String getUpdateJson() {
        return """
                {
                  "sys_title": "My Folder updated"
                }
                """;
    }

    protected String getInvalidUpdateJson() {
        return """
                {
                  "dc_foo": "bar"
                }
                """;
    }

    protected void checkResponseBodyReplacing(String expectedFilePath, String id, String name, Response response)
            throws IOException, JSONException {
        String expected = getReferenceContent(expectedFilePath);
        String actual = response.getBody().asString();
        expected = expected.replace(ID_MARKER, id);
        expected = expected.replace(NAME_MARKER, name);
        JSONAssert.assertEquals(expected, actual, DOC_COMPARATOR);
    }

    @Test
    public void testCreateUpdateAndDeleteById() throws IOException, JSONException {
        String creationJson = getCreationJson();
        // invalid creation
        checkResponseWithPayload(NOT_FOUND, POST, creationJson, "/{parentId}", "foo");
        // valid creation
        Response r = checkResponseWithPayload(OK, POST, creationJson, "/{parentId}", ROOT_ID);
        JsonPath jsonPath = r.jsonPath();
        String id = jsonPath.get(ID_PROPERTY);
        assertNotNull(id);
        String name = jsonPath.get(NAME_PROPERTY);
        assertNotNull(name);
        checkResponseBodyReplacing("rest/folder.json", id, name, r);

        // check update
        String updateJson = getUpdateJson();
        // invalid update
        checkResponseWithPayload(NOT_FOUND, PUT, updateJson, "/{docId}", "foo");
        // valid update
        r = checkResponseWithPayload(OK, PUT, updateJson, "/{docId}", id);
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, r);
        // check get again after update
        r = checkResponse(OK, GET, "/{docId}", id);
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, r);

        // delete
        // invalid delete
        checkResponse(NOT_FOUND, DELETE, "/{docId}", "foo");
        // valid delete
        checkResponse(NO_CONTENT, DELETE, "/{docId}", id);
        // check get after delete
        checkResponse(NOT_FOUND, GET, "/{docId}", id);
    }

    @Test
    public void testInvalidCreate() {
        // missing type
        String json = getInvalidCreationJson();
        Response r = checkResponseWithPayload(BAD_REQUEST, POST, json, "/{parentId}", ROOT_ID);
        checkErrorResponse(BAD_REQUEST, "Missing document type for document creation: primaryType", r);
        // invalid type
        json = getInvalidCreationJson2();
        r = checkResponseWithPayload(BAD_REQUEST, POST, json, "/{parentId}", ROOT_ID);
        checkErrorResponse(BAD_REQUEST, "Unknown document type: Unknown Type", r);
        // invalid property
        json = getInvalidCreationJson3();
        r = checkResponseWithPayload(BAD_REQUEST, POST, json, "/{parentId}", ROOT_ID);
        checkErrorResponse(BAD_REQUEST, "dc_foo", r);
    }

    @Test
    public void testInvalidUpdate() {
        // invalid property
        String json = getInvalidUpdateJson();
        Response r = checkResponseWithPayload(BAD_REQUEST, PUT, json, "/{docId}", ROOT_ID);
        checkErrorResponse(BAD_REQUEST, "dc_foo", r);
    }

    private Response postWithContentType(String contentType) {
        return authenticated().header("Accept", ContentType.JSON.getAcceptHeader())
                              .header("Content-Type", contentType)
                              // bytes, not a String: Rest Assured refuses to encode a String body under a
                              // multipart content type, and the point here is to send a body it cannot split
                              .body("x".getBytes(UTF_8))
                              .post(PATH + "/{parentId}", ROOT_ID);
    }

    /**
     * A {@code multipart/form-data} request with no usable {@code boundary} parameter cannot be split into
     * parts, and the exception the dispatcher throws for that -- {@code MultipartException} -- carried no
     * status of its own, so before HXCS-8835 a 15-byte request answered 500 here and on four sibling
     * operations, each costing an ERROR-level log line and telling the caller nothing.
     * <p>
     * This lives at the integration layer because only a real servlet container parses a multipart body:
     * MockMvc hands the dispatcher parts that are already resolved, so the parse failure cannot be provoked
     * there at all.
     * <p>
     * The control row matters as much as the two failing ones. A body that does not match a <em>declared</em>
     * boundary was always classified correctly, which is what places the trigger in the header rather than in
     * the body -- and what keeps this test honest about which failure was actually fixed.
     */
    @Test
    public void testMultipartBodyWithNoUsableBoundaryIsABadRequest() {
        String parseFailure = "Failed to parse multipart servlet request";
        // no boundary parameter at all
        checkErrorResponse(BAD_REQUEST, parseFailure, postWithContentType("multipart/form-data"));
        // a boundary parameter with an empty value, which the parser cannot use either
        checkErrorResponse(BAD_REQUEST, parseFailure, postWithContentType("multipart/form-data; boundary="));
        // control: with a boundary the parser can use, the body is split and the missing part is reported
        checkErrorResponse(BAD_REQUEST, "Required part 'main' is not present.",
                postWithContentType("multipart/form-data; boundary=zzz"));
    }

}
