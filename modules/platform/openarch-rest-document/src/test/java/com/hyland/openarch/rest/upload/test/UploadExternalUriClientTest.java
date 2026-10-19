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
package com.hyland.openarch.rest.upload.test;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.rest.upload.UploadExternalUriClient.UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY;
import static com.hyland.openarch.rest.upload.UploadExternalUriClient.UPLOAD_URI_SCHEME_ALLOW_INSECURE_PROPERTY;
import static com.hyland.openarch.rest.upload.test.UploadAPITest.EXTERNAL_URI;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.common.httpclient.JitteredExponentialBackoffRetryHandler;
import jakarta.ws.rs.core.Response.Status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import com.hyland.openarch.rest.upload.UriWithHeaders;
import com.hyland.openarch.rest.upload.UploadExternalUriClient;
import com.hyland.openarch.rest.upload.UploadServiceImpl;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import com.hyland.openarch.runtime.test.runner.WithFrameworkProperty;

@ExtendWith(FeaturesExtension.class)
@Features({RuntimeFeature.class, MockitoFeature.class})
public class UploadExternalUriClientTest {

    @Mock
    private HttpResponse headers;

    @Test
    void shouldConfigurePoolTimeoutsAndRetryHandlerFromProperties() {
        HttpClientBuilder builder = Mockito.mock(HttpClientBuilder.class, Mockito.RETURNS_SELF);
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        when(builder.build()).thenReturn(httpClient);
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class);
                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            clients.when(HttpClients::custom).thenReturn(builder);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("httpclient", Map.of(
                    "hxpr.httpclient.retry.max-attempts", "5",
                    "hxpr.httpclient.retry.base-delay-ms", "250",
                    "hxpr.httpclient.retry.jitter-factor", "0.25",
                    "hxpr.httpclient.timeout.connect", "1000",
                    "hxpr.httpclient.timeout.connection-request", "2000",
                    "hxpr.httpclient.timeout.socket", "3000",
                    "hxpr.httpclient.pool.max-total", "80",
                    "hxpr.httpclient.pool.max-per-route", "40")));
            context.register(UploadServiceImpl.class);
            context.refresh();

            Mockito.verify(builder).setMaxConnTotal(80);
            Mockito.verify(builder).setMaxConnPerRoute(40);

            ArgumentCaptor<RequestConfig> requestConfig = ArgumentCaptor.forClass(RequestConfig.class);
            Mockito.verify(builder).setDefaultRequestConfig(requestConfig.capture());
            assertEquals(1000, requestConfig.getValue().getConnectTimeout());
            assertEquals(2000, requestConfig.getValue().getConnectionRequestTimeout());
            assertEquals(3000, requestConfig.getValue().getSocketTimeout());

            ArgumentCaptor<HttpRequestRetryHandler> retryHandler = ArgumentCaptor.forClass(HttpRequestRetryHandler.class);
            Mockito.verify(builder).setRetryHandler(retryHandler.capture());
            assertInstanceOf(JitteredExponentialBackoffRetryHandler.class, retryHandler.getValue());
            assertEquals(5, ReflectionTestUtils.getField(retryHandler.getValue(), "maxTry"));
            assertEquals(250L, ReflectionTestUtils.getField(retryHandler.getValue(), "baseDelayMs"));
            assertEquals(0.25, ReflectionTestUtils.getField(retryHandler.getValue(), "jitterFactor"));
        }
    }

    @Test
    void shouldConfigureTimeoutsAndRetryHandlerAndCloseHttpClient() throws IOException {
        HttpClientBuilder builder = Mockito.mock(HttpClientBuilder.class, Mockito.RETURNS_SELF);
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        when(builder.build()).thenReturn(httpClient);
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::custom).thenReturn(builder);
            UploadServiceImpl service = new UploadServiceImpl();

            Mockito.verify(builder).setMaxConnTotal(50);
            Mockito.verify(builder).setMaxConnPerRoute(50);

            ArgumentCaptor<RequestConfig> requestConfig = ArgumentCaptor.forClass(RequestConfig.class);
            Mockito.verify(builder).setDefaultRequestConfig(requestConfig.capture());
            assertEquals(5000, requestConfig.getValue().getConnectTimeout());
            assertEquals(5000, requestConfig.getValue().getConnectionRequestTimeout());
            assertEquals(5000, requestConfig.getValue().getSocketTimeout());

            ArgumentCaptor<HttpRequestRetryHandler> retryHandler = ArgumentCaptor.forClass(HttpRequestRetryHandler.class);
            Mockito.verify(builder).setRetryHandler(retryHandler.capture());
            assertInstanceOf(JitteredExponentialBackoffRetryHandler.class, retryHandler.getValue());
            Mockito.verify(builder).disableCookieManagement();
            Mockito.verify(builder).disableRedirectHandling();

            service.close();
            Mockito.verify(httpClient).close();
        }
    }

    @Test
    void shouldRejectNonPositivePoolLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadServiceImpl(3, 100, 0.5, 5000, 5000, 5000, 0, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new UploadServiceImpl(3, 100, 0.5, 5000, 5000, 5000, 50, -1));
    }

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
        HttpResponse httpHeaders = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        UploadExternalUriClient client = getUploadExternalUriManager();
        var e = assertThrows(RepositoryException.class, () ->  client.getMimeTypeAndEncoding(httpHeaders));
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
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            UploadExternalUriClient client = new UploadExternalUriClient(httpClient);
            try (CloseableHttpResponse response = client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(EXTERNAL_URI)))) {
                assertEquals(Status.OK.getStatusCode(), response.getStatusLine().getStatusCode());
                assertNotNull(response.getEntity().getContent());
            }
        }
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
        when(headers.getFirstHeader("Content-Type")).thenReturn(new BasicHeader("Content-Type", "text/plain"));
        assertEquals("text/plain", getMimeTypeAndEncoding(headers).mimeType());
    }

    @Test
    public void testGetEncodingFromResponseHeader() {
        when(headers.getFirstHeader("Content-Type")).thenReturn(new BasicHeader("Content-Type", "text/plain; charset=iso-8859-1"));
        assertEquals("ISO-8859-1", getMimeTypeAndEncoding(headers).encoding());
    }

    @Test
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenNoAllowedHostList() throws IOException, URISyntaxException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);
        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenEmptyAllowedHostList() throws IOException, URISyntaxException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);
        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenNullResponse() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(null);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowRepositoryExceptionWhenExecuteThrowsInterruptedIOException() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        when(httpClient.execute(any(HttpGet.class))).thenThrow(new InterruptedIOException());

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowRepositoryExceptionWhenSendThrowsIOException() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException());

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), exception.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrowExceptionWhenNullResponseBody() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(null);

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(getInputStream(body));

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(uriWithHeaders));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
        Mockito.verify(response).close();
    }

    @Test
    void shouldCloseResponseWhenStatusIsNotOK() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(new InputStreamEntity(getInputStream("not found")));
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 404, "Not Found"));
        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        RepositoryException exception = assertThrows(RepositoryException.class,
                () -> client.downloadResourceFromURI(new UriWithHeaders(URI.create(EXTERNAL_URI), Map.of())));
        assertEquals(BAD_REQUEST.getStatusCode(), exception.getStatusCode());
        assertEquals("Stream download response code is: 404", exception.getMessage());
        Mockito.verify(response).close();
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com,www.hyland.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization")
    void shouldThrow400WhenForbiddenHeaderPassed() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(new InputStreamEntity(getInputStream("ok")));
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            # Cookie: secret
            # other comment

            https://www.example.com/images/earth/earth-night.jpeg
            """;

        var e = assertThrows(RepositoryException.class, () -> client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body))));
        assertEquals(BAD_REQUEST.getStatusCode(), e.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "")
    void shouldThrow400WhenNoHeadersAllowedButPassed() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(new InputStreamEntity(getInputStream("ok")));
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        var e = assertThrows(RepositoryException.class, () -> client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body))));
        assertEquals(400, e.getStatusCode());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    void shouldParseDefaultHeaderSuccessfully() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(new InputStreamEntity(getInputStream("ok")));
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            https://www.example.com/images/earth/earth-night.jpeg
            """;
        client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body)));
        ArgumentCaptor<HttpGet> requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        Mockito.verify(httpClient).execute(requestCaptor.capture());
        HttpGet capturedRequest = requestCaptor.getValue();
        assertEquals("GET", capturedRequest.getMethod());
        assertEquals("https://www.example.com/images/earth/earth-night.jpeg", capturedRequest.getURI().toString());
        assertEquals(1, capturedRequest.getAllHeaders().length);
        assertEquals("Bearer token", capturedRequest.getFirstHeader("Authorization").getValue());
    }

    @Test
    @WithFrameworkProperty(name = UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, value = "www.example.com")
    @WithFrameworkProperty(name = UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY, value = "Authorization,Cookie,Header")
    void shouldPassMoreThanOneHeaderIfAllowedAndIgnoreBlankLinesAndComments() throws Exception {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(new InputStreamEntity(getInputStream("ok")));
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));

        UploadExternalUriClient client = new UploadExternalUriClient(httpClient);

        String body = """
            # Authorization: Bearer token
            # Cookie: cookie

            #Header: header
            # comment line should be ignored

            https://www.example.com/images/earth/earth-night.jpeg
            """;
        client.downloadResourceFromURI(UriWithHeaders.of(getInputStream(body)));
        ArgumentCaptor<HttpGet> requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        Mockito.verify(httpClient).execute(requestCaptor.capture());
        HttpGet capturedRequest = requestCaptor.getValue();
        assertEquals(3, capturedRequest.getAllHeaders().length);
        assertEquals("cookie", capturedRequest.getFirstHeader("Cookie").getValue());
        assertEquals("header", capturedRequest.getFirstHeader("Header").getValue());
        assertEquals("Bearer token", capturedRequest.getFirstHeader("Authorization").getValue());
        assertNull(capturedRequest.getFirstHeader("comment"));
    }


    private UploadExternalUriClient getUploadExternalUriManager() {
        return new UploadExternalUriClient(Mockito.mock(CloseableHttpClient.class));
    }

    private boolean isValidUri(InputStream inputStream) throws IOException, URISyntaxException {
        UploadExternalUriClient uriManager = getUploadExternalUriManager();
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);
        return uriManager.isValidURI(uriWithHeaders.uri());
    }

    private UriWithHeaders getUriWithHeaders(InputStream inputStream) throws IOException, URISyntaxException {
        return UriWithHeaders.of(inputStream);
    }

    private UploadExternalUriClient.MimeTypeAndEncoding getMimeTypeAndEncoding(HttpResponse headers) {
        return getUploadExternalUriManager().getMimeTypeAndEncoding(headers);
    }

    private InputStream getInputStream(String body) {
        return new ByteArrayInputStream(body.getBytes(UTF_8));
    }
}
