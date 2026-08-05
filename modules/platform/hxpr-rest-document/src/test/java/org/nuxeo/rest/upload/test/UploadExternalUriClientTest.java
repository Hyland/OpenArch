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
package org.nuxeo.rest.upload.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.nuxeo.rest.upload.UploadExternalUriClient.UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY;
import static org.nuxeo.rest.upload.UploadExternalUriClient.UPLOAD_URI_SCHEME_ALLOW_INSECURE_PROPERTY;
import static org.nuxeo.rest.upload.test.UploadAPITest.EXTERNAL_URI;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import jakarta.ws.rs.core.Response.Status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.rest.upload.UriWithHeaders;
import org.nuxeo.rest.upload.UploadExternalUriClient;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

@ExtendWith(FeaturesExtension.class)
@Features({RuntimeFeature.class, MockitoFeature.class})
public class UploadExternalUriClientTest {

    @Mock
    private HttpHeaders headers;

    @Test
    public void testUriIsReturnedAsExpected() throws IOException, URISyntaxException {
        String body = "https://www.example.com/images/earth/earth-night.jpeg";
        URI returnedUri = getUriWithHeaders(getInputStream(body)).uri();
        assertEquals(body, returnedUri.toString());
    }

    @Test
    public void testOneCommentedLineIsAddedAsHeaders() throws IOException, URISyntaxException {
        String body =
                     """
                     # Authorization: Bearer 0b79bab50daca910b000d4f1a2b675d604257e42
                     https://www.example.com/images/earth/earth-night.jpeg
                     """;
        UriWithHeaders uriWithHeaders = getUriWithHeaders(getInputStream(body));
        assertEquals("Bearer 0b79bab50daca910b000d4f1a2b675d604257e42", uriWithHeaders.headers().get("Authorization"));
        assertEquals("https://www.example.com/images/earth/earth-night.jpeg", uriWithHeaders.uri().toString());
    }

    @Test
    public void testUriIsMalformed() {
        String body = "https:// malformed uri /images/earth/earth-night.jpeg";
        InputStream inputStream = getInputStream(body);
        assertThrows(URISyntaxException.class, () -> getUriWithHeaders(inputStream));
    }

    @Test
    public void testMoreThanOneUriShouldThrowException() {
        String body =
                """
                # Authorization: Bearer 0b79bab50daca910b000d4f1a2b675d604257e42
                # Cookie: cookie1
                https://www.example.com/images/earth/earth-night.jpeg
                https://github.githubassets.com/assets/apple-touch-icon-144x144-b882e354c005.png
                """;
        InputStream inputStream = getInputStream(body);
        assertThrows(URISyntaxException.class, () -> getUriWithHeaders(inputStream));
    }

    @Test
    void shouldThrowExceptionWhenNoContentTypeHeader() {
        HttpHeaders httpHeaders = HttpHeaders.of(Map.of(), (s1, s2) -> true);
        UploadExternalUriClient client = getUploadExternalUriManager();
        var e = assertThrows(NuxeoException.class, () ->  client.getMimeTypeAndEncoding(httpHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), e.getStatusCode());
    }

    @Test
    public void testNoUriShouldThrowException() {
        String body =
                """
                # Authorization: Bearer 0b79bab50daca910b000d4f1a2b675d604257e42
                # Cookie: cookie1
                """;
        InputStream inputStream = getInputStream(body);
        assertThrows(URISyntaxException.class, () -> getUriWithHeaders(inputStream));
    }

    @Test
    public void testDownloadFromExternalUri() throws IOException, URISyntaxException {
        HttpResponse<InputStream> inputStreamHttpResponse = downloadFromUri(getInputStream(EXTERNAL_URI));
        assertEquals(Status.OK.getStatusCode(), inputStreamHttpResponse.statusCode());
        assertEquals(EXTERNAL_URI, inputStreamHttpResponse.uri().toString());
        assertEquals("https", inputStreamHttpResponse.uri().getScheme());
        assertNotNull(inputStreamHttpResponse.body());
    }

    @Test
    public void testInvalidUriSchemeHTTP() throws IOException, URISyntaxException {
        String body = "http://www.example.com/images/earth/earth-night.jpeg";
        assertFalse(isValidUri(getInputStream(body)));
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_SCHEME_ALLOW_INSECURE_PROPERTY, value = "true")
    public void testInvalidUriSchemeHTTPWithAllowInsecureProperty() throws IOException, URISyntaxException {
        String body = "http://www.example.com/images/earth/earth-night.jpeg";
        assertTrue(isValidUri(getInputStream(body)));
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "hello.com")
    public void testUriHostNotInAllowedHostListProperty() throws IOException, URISyntaxException {
        String body = "https://www.example.com/images/earth/earth-night.jpeg";
        assertFalse(isValidUri(getInputStream(body)));
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    public void testUriHostInAllowedHostListProperty() throws IOException, URISyntaxException {
        String body = "https://www.example.com/images/earth/earth-night.jpeg";
        assertTrue(isValidUri(getInputStream(body)));
    }

    @Test
    public void testGetMimeTypeFromResponseHeader() {
        when(headers.firstValue("Content-Type")).thenReturn(Optional.of("text/plain"));
        assertEquals("text/plain", getMimeTypeAndEncoding(headers).mimeType());
    }

    @Test
    public void testGetEncodingFromResponseHeader() {
        when(headers.firstValue("Content-Type")).thenReturn(Optional.of("text/plain; charset=iso-8859-1"));
        assertEquals("ISO-8859-1", getMimeTypeAndEncoding(headers).encoding());
    }

    @Test
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenNoAllowedHostList() throws IOException, URISyntaxException {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);
        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        NuxeoException exception = assertThrows(NuxeoException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenEmptyAllowedHostList() throws IOException, URISyntaxException {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);
        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        NuxeoException exception = assertThrows(NuxeoException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenNullResponse() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenReturn(null);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        NuxeoException exception = assertThrows(NuxeoException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowNuxeoExceptionWhenSendThrowsInterruptedException() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenThrow(new InterruptedException());

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        NuxeoException exception = assertThrows(NuxeoException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowNuxeoExceptionWhenSendThrowsIOException() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenThrow(new IOException());

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        NuxeoException exception = assertThrows(NuxeoException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenNullResponseBody() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);

        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenReturn(response);
        when(response.body()).thenReturn(null);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        NuxeoException exception = assertThrows(NuxeoException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com,www.hyland.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrow400WhenForbiddenHeaderPassed() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);

        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenReturn(response);
        when(response.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(UTF_8)));
        when(response.statusCode()).thenReturn(200);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            # Cookie: secret
            # other comment

            https://www.example.com/images/earth/earth-night.jpeg
            """;

        var e = assertThrows(NuxeoException.class, () -> client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body))));
        assertEquals(BAD_REQUEST.getStatusCode(), e.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "")
    void shouldThrow400WhenNoHeadersAllowedButPassed() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);

        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenReturn(response);
        when(response.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(UTF_8)));
        when(response.statusCode()).thenReturn(200);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        var e = assertThrows(NuxeoException.class, () -> client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body))));
        assertEquals(400, e.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    void shouldParseDefaultHeaderSuccessfully() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);

        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenReturn(response);
        when(response.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(UTF_8)));
        when(response.statusCode()).thenReturn(200);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body)));
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(
                requestCaptor.capture(),
                Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        );
        HttpRequest capturedRequest = requestCaptor.getValue();
        var headersMap = capturedRequest.headers().map();
        assertEquals(1, headersMap.size());
        assertEquals("Bearer token", headersMap.get("Authorization").getFirst());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization,Cookie,Header")
    void shouldPassMoreThanOneHeaderIfAllowedAndIgnoreBlankLinesAndComments() throws Exception {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);

        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        )).thenReturn(response);
        when(response.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(UTF_8)));
        when(response.statusCode()).thenReturn(200);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            # Cookie: cookie

            #Header: header
            # comment line should be ignored

            https://www.example.com/images/earth/earth-night.jpeg
            """;
        client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body)));
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(
                requestCaptor.capture(),
                Mockito.<HttpResponse.BodyHandler<InputStream>>any()
        );
        HttpRequest capturedRequest = requestCaptor.getValue();
        var headersMap = capturedRequest.headers().map();
        assertEquals(3, headersMap.size());
        assertEquals("cookie", headersMap.get("Cookie").getFirst());
        assertEquals("header", headersMap.get("Header").getFirst());
        assertEquals("Bearer token", headersMap.get("Authorization").getFirst());
        assertNull(headersMap.get("comment"));
    }


    private UploadExternalUriClient getUploadExternalUriManager() {
        return new UploadExternalUriClient(HttpClient.newHttpClient());
    }

    private HttpResponse<InputStream> downloadFromUri(InputStream inputStream) throws IOException, URISyntaxException {
        UploadExternalUriClient uriManager = getUploadExternalUriManager();
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);
        return uriManager.downloadResourceFromURI(uriWithHeaders);
    }

    private boolean isValidUri(InputStream inputStream) throws IOException, URISyntaxException {
        UploadExternalUriClient uriManager = getUploadExternalUriManager();
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);
        return uriManager.isValidURI(uriWithHeaders.uri());
    }

    private UriWithHeaders getUriWithHeaders(InputStream inputStream) throws IOException, URISyntaxException {
        return UriWithHeaders.of(inputStream);
    }

    private UploadExternalUriClient.MimeTypeAndEncoding getMimeTypeAndEncoding(HttpHeaders headers) {
        return getUploadExternalUriManager().getMimeTypeAndEncoding(headers);
    }

    private InputStream getInputStream(String body) {
        return new ByteArrayInputStream(body.getBytes(UTF_8));
    }
}
