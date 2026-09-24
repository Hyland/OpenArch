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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Record holding a data for downloading the blob for external uri upload.
 * @param uri - uri from where the blob is downloaded
 * @param headers - headers used for download request
 */
public record UriWithHeaders(URI uri, Map<String, String> headers) {

    private static final Logger log = LoggerFactory.getLogger(UriWithHeaders.class);

    private static final String URI_BEGINNING = "http";

    private static final String COMMENT_BEGINNING = "#";

    private static final Long MAX_BODY_SIZE_BYTES = 1024 * 64L;

    private static final Pattern HEADER_PATTERN = Pattern.compile("^#\\s*([\\w-]+)\\s*:\\s*(.*?)\\s*$");

    /**
     * Parsing the input stream (external uri upload payload) to create UriWithHeaders Record
     * Expected format of input stream.
     * Header lines should start with # and contain a colon separating the header name and value.
     * Comment lines are allowed and ignored during parsing. Comment lines start with # and must not have any colon.
     * Blank lines are allowed and ignored during parsing.
     * The payload must contain exactly one valid uri. Uri line must start with http or https.
     * Example of valid payload:
     * #Header1: value1
     * #Header2: value2
     * #Comment line (without colon) accepted but not parsed. Blank lines accepted and ignored too
     *
     * http://some-uri.com
     */
    public static UriWithHeaders of(InputStream inputStream) throws URISyntaxException, IOException {
        try (BoundedInputStream bounded = BoundedInputStream.builder()
                .setInputStream(inputStream)
                .setMaxCount(MAX_BODY_SIZE_BYTES + 1)
                .get()) {
            List<String> inputLines = IOUtils.readLines(bounded, UTF_8);
            if (bounded.getCount() > MAX_BODY_SIZE_BYTES) {
                throw new NuxeoException("Indirect streaming request body is too large. Maximum allowed size is "
                        + MAX_BODY_SIZE_BYTES + " bytes", BAD_REQUEST.getStatusCode());
            }
            Map<String, String> headers = new HashMap<>();
            URI uri = null;
            int uriCounter = 0;
            for (int lineNumber = 0; lineNumber < inputLines.size(); lineNumber++) {
                String trimmedInputLine = inputLines.get(lineNumber).trim();
                Matcher m = HEADER_PATTERN.matcher(trimmedInputLine);
                if (m.matches()) {
                    String name = m.group(1);
                    String value = m.group(2);
                    headers.put(name, value);
                } else if (trimmedInputLine.isBlank() || trimmedInputLine.startsWith(COMMENT_BEGINNING)) {
                    continue; // ignore non header comment - continue loop
                } else if (trimmedInputLine.startsWith(URI_BEGINNING)) {
                    if (uriCounter >= 1) {
                        throw new URISyntaxException("Request body contains more than one URI which is not supported",
                                "Request body should contain only one valid URI");
                    }
                    try {
                        uri = new URI(trimmedInputLine);
                        uriCounter++;
                    } catch (URISyntaxException e) {
                        log.debug("Invalid URI in indirect streaming upload body at line {}", lineNumber);
                        throw e;
                    }
                } else {
                    throw new URISyntaxException("Request body contains malformed lines which is not supported",
                            "Request body should contain only header, comment, blank lines and one valid URI line");
                }
            }
            if (uri == null) {
                throw new URISyntaxException("Request body does not contain any URI",
                        "Exactly one valid URI is required");
            }
            return new UriWithHeaders(uri, headers);
        }
    }
}
