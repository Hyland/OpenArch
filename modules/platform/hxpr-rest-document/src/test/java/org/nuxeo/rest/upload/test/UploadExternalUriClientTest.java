/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 *     Abdul Mohammed <abdul.mohammed@hyland.com>
 */
package org.nuxeo.rest.upload.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.nuxeo.rest.upload.UploadExternalUriClient.UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY;
import static org.nuxeo.rest.upload.UploadExternalUriClient.UPLOAD_URI_SCHEME_ALLOW_INSECURE_PROPERTY;
import static org.nuxeo.rest.upload.test.UploadAPITest.EXTERNAL_URI;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Optional;

import jakarta.ws.rs.core.Response.Status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
        URI returnedUri = getURI(getInputStream(body));
        assertEquals(body, returnedUri.toString());
    }

    @Test
    public void testCommentedLinesAreIgnoredInBody() throws IOException, URISyntaxException {
        String body =
                     """
                     # Authorization: Bearer 0b79bab50daca910b000d4f1a2b675d604257e42
                     https://www.example.com/images/earth/earth-night.jpeg
                     """;
        URI returnedUri = getURI(getInputStream(body));
        assertEquals("https://www.example.com/images/earth/earth-night.jpeg", returnedUri.toString());
    }

    @Test
    public void testUriIsMalformed() throws IOException, URISyntaxException {
        String body = "https:// malformed uri /images/earth/earth-night.jpeg";
        assertThrows(URISyntaxException.class, () -> getURI(getInputStream(body)));
    }

    @Test
    public void testMultipleUrisInBodyReturnsURISyntaxException() throws IOException, URISyntaxException {
        String body =
                        """
                        https://www.example.com/images/earth/earth-night.jpeg
                        https://www.example.com/images/earth/earth-day.jpeg
                        """;
        assertThrows(URISyntaxException.class, () -> getURI(getInputStream(body)));
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

    private UploadExternalUriClient getUploadExternalUriManager(InputStream inputStream) {
        return new UploadExternalUriClient(inputStream);
    }

    private HttpResponse<InputStream> downloadFromUri(InputStream inputStream) throws IOException, URISyntaxException {
        UploadExternalUriClient uriManager = getUploadExternalUriManager(inputStream);
        URI uri = uriManager.getURI();
        return uriManager.downloadResourceFromURI(uri);
    }

    private boolean isValidUri(InputStream inputStream) throws IOException, URISyntaxException {
        UploadExternalUriClient uriManager = getUploadExternalUriManager(inputStream);
        URI uri = uriManager.getURI();
        return uriManager.isValidURI(uri);
    }

    private URI getURI(InputStream inputStream) throws IOException, URISyntaxException {
        return getUploadExternalUriManager(inputStream).getURI();
    }

    private UploadExternalUriClient.MimeTypeAndEncoding getMimeTypeAndEncoding(HttpHeaders headers) {
        return getUploadExternalUriManager(null).getMimeTypeAndEncoding(headers);
    }

    private InputStream getInputStream(String body) {
        return new ByteArrayInputStream(body.getBytes(UTF_8));
    }
}
