/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriWithHeadersTest {

    @Test
    void shouldCreateHeadersAndUriFromTypicalBody() throws URISyntaxException, IOException {
        InputStream inputStream = prepareInputStream("#Authorization: Bearer~/token_some-1234\n#Cookie123_-Cookie: cookie_/~-123\nhttp://some-uri1.com");
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);

        assertEquals(2, uriWithHeaders.headers().size());
        assertEquals("Bearer~/token_some-1234", uriWithHeaders.headers().get("Authorization"));
        assertEquals("cookie_/~-123", uriWithHeaders.headers().get("Cookie123_-Cookie"));
        assertEquals("http://some-uri1.com", uriWithHeaders.uri().toString());
    }

    @Test
    void shouldIgnoreCommentLines() throws URISyntaxException, IOException {
        InputStream inputStream = prepareInputStream("#Authorization: Bearer~/token_some-1234\n # some comment 1234\nhttp://some-uri1.com");
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);

        assertEquals(1, uriWithHeaders.headers().size());
        assertEquals("Bearer~/token_some-1234", uriWithHeaders.headers().get("Authorization"));
        assertEquals("http://some-uri1.com", uriWithHeaders.uri().toString());
    }

    @Test
    void shouldIgnoreMalformedHeaders() throws URISyntaxException, IOException {
        InputStream inputStream = prepareInputStream("#Authorization Bearer token\n#Cookie cookie1\nhttp://some-uri1.com");
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);

        assertEquals(0, uriWithHeaders.headers().size());
        assertEquals("http://some-uri1.com", uriWithHeaders.uri().toString());
    }

    @Test
    void shouldIgnoreBlankLines() throws URISyntaxException, IOException {
        InputStream inputStream = prepareInputStream("    \n #Authorization: Bearer~/token_some-1234\n\nhttp://some-uri1.com");
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);

        assertEquals(1, uriWithHeaders.headers().size());
        assertEquals("Bearer~/token_some-1234", uriWithHeaders.headers().get("Authorization"));
        assertEquals("http://some-uri1.com", uriWithHeaders.uri().toString());
    }

    @Test
    void shouldHandleOnlyUriWithoutHeaders() throws URISyntaxException, IOException {
        InputStream inputStream = prepareInputStream("http://some-uri1.com");
        UriWithHeaders uriWithHeaders = UriWithHeaders.of(inputStream);

        assertEquals(0, uriWithHeaders.headers().size());
        assertEquals("http://some-uri1.com", uriWithHeaders.uri().toString());
    }

    @ParameterizedTest
    @MethodSource("invalidBodyCases")
    void shouldThrowURISyntaxExceptionForInvalidBody(String body, String expectedInput) {
        InputStream inputStream = prepareInputStream(body);
        URISyntaxException e = assertThrows(URISyntaxException.class, () -> UriWithHeaders.of(inputStream));
        assertEquals(expectedInput, e.getInput());
    }

    @Test
    void shouldThrowExceptionWhenBodyExceedsMaxSize() {
        String tooLargeBody = "#Authorization: Bearer token\n" + "x".repeat((128 * 1024));
        InputStream inputStream = prepareInputStream(tooLargeBody);
        RepositoryException e = assertThrows(RepositoryException.class, () -> UriWithHeaders.of(inputStream));
        assertEquals(400, e.getStatusCode());
    }

    private InputStream prepareInputStream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    private static Stream<Arguments> invalidBodyCases() {
        return Stream.of(
                Arguments.of(
                        "#Authorization Bearer token\n#Cookie cookie1\nhttp://some-uri1.com\nhttps://some-uri2.com",
                        "Request body contains more than one URI which is not supported"),
                Arguments.of("http://some-uri1.com\nhttps://some-uri2.com",
                        "Request body contains more than one URI which is not supported"),
                Arguments.of("", "Request body does not contain any URI"),
                Arguments.of("#Authorization Bearer token\n#Cookie cookie1\nhttp://some-uri1.com\nbad line",
                        "Request body contains malformed lines which is not supported"),
                Arguments.of("#Authorization Bearer token\nbad line\n#Cookie cookie1\nhttp://some-uri1.com",
                        "Request body contains malformed lines which is not supported"));
    }
}
