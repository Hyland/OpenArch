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
package org.nuxeo.rest.upload;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.ws.rs.core.Response;

import org.apache.http.entity.ContentType;
import org.nuxeo.ecm.core.api.NuxeoException;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bean allowing the download of object from an external uri.
 */
public class UploadExternalUriClient {

    private static final Logger log = LoggerFactory.getLogger(UploadExternalUriClient.class);

    public static final String UPLOAD_URI_SCHEME_ALLOW_INSECURE_PROPERTY = "hxp.rest.upload.uri.scheme.allowInsecure";

    public static final String UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY = "hxp.rest.upload.uri.allowedHostList";

    public static final String UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY = "hxp.rest.upload.uri.allowedHeadersList";

    private static final String DEFAULT_ALLOWED_HEADERS = "Authorization";

    private static final String SCHEME_HTTPS = "https";

    private HttpClient httpClient;

    public UploadExternalUriClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpResponse<InputStream> downloadResourceFromURI(UriWithHeaders uriWithHeaders) {
        URI uri = uriWithHeaders.uri();
        if (!isValidURI(uri)) {
            throw new NuxeoException("Invalid URI %s".formatted(uri), BAD_REQUEST.getStatusCode());
        }
        var headers = uriWithHeaders.headers();
        validateHeaders(headers);
        try {
            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder(uri);
            headers.forEach(httpRequestBuilder::header);
            HttpRequest httpRequest = httpRequestBuilder.GET().build();
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response == null || response.body() == null) {
                throw new NuxeoException("Error during stream downloading of URI: %s".formatted(uri), BAD_REQUEST.getStatusCode());
            }
            if (response.statusCode() != Response.Status.OK.getStatusCode()) {
                throw new NuxeoException("Stream download response code is: %s".formatted(response.statusCode()), BAD_REQUEST.getStatusCode());
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NuxeoException(e);
        } catch (IOException e) {
            throw new NuxeoException("IOException occurred during stream download of URI: %s.  %s".formatted(uri, e.getMessage()));
        }
    }

    public boolean isValidURI(URI uri) {
        boolean isUriSchemeInsecureAllowed = Framework.isBooleanPropertyTrue(UPLOAD_URI_SCHEME_ALLOW_INSECURE_PROPERTY);
        List<String> uriAllowedHostList = Arrays.asList(Framework.getProperty(UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY, "*").split(","));
        return (isUriInAllowedHostList(uriAllowedHostList, uri)
                && (uri.getScheme().equalsIgnoreCase(SCHEME_HTTPS) || isUriSchemeInsecureAllowed));
    }

    private void validateHeaders(Map<String, String> inputHeaders) {
        List<String> headersAllowedList = getAllowedHeaders();
        for (String headerName : inputHeaders.keySet()) {
            if (!headersAllowedList.contains(headerName)) {
                throw new NuxeoException("Request payload contains forbidden header: %s".formatted(headerName), BAD_REQUEST.getStatusCode());
            }
        }
    }

    public MimeTypeAndEncoding getMimeTypeAndEncoding(HttpHeaders headers) {
        String mimeType = getContentType(headers).getMimeType();
        Charset charset = getContentType(headers).getCharset();
        String encoding = charset == null ? null : charset.name();
        return new MimeTypeAndEncoding(mimeType, encoding);
    }

    private boolean isUriInAllowedHostList(List<String> uriAllowedHostList, URI uri) {
        // check if property uriAllowedHostList is empty
        if (uriAllowedHostList.size() == 1 && "".equals(uriAllowedHostList.get(0))) {
            log.error("Property {} is not set", UPLOAD_URI_ALLOWED_HOST_LIST_PROPERTY);
            return false;
        }
        return uriAllowedHostList.stream().anyMatch(hostname -> hostname.equals("*") || hostname.equals(uri.getHost()));
    }

    private ContentType getContentType(HttpHeaders headers) {
        Optional<String> headerContentType = headers.firstValue(CONTENT_TYPE);
        if (headerContentType.isEmpty()) {
            throw new NuxeoException("Content-Type not found in response headers of stream download URI", BAD_REQUEST.getStatusCode());
        }
        return ContentType.parse(headerContentType.get());
    }

    private static List<String> getAllowedHeaders() {
        if (!Framework.isInitialized()) {
            return List.of();
        }
        return Arrays.stream(Framework.getProperty(UploadExternalUriClient.UPLOAD_URI_ALLOWED_HEADER_LIST_PROPERTY,
                                DEFAULT_ALLOWED_HEADERS)
                        .split(","))
                .map(String::trim)
                .filter(header -> !header.isEmpty())
                .toList();
    }

    public record MimeTypeAndEncoding( //
               String mimeType, //
               String encoding //
    ) {

    }

}
