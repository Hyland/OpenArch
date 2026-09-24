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
package org.nuxeo.rest.query.io;

import java.util.Base64;
import java.util.List;

import com.hyland.openarch.ecm.core.api.RepositoryException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Represents a search_after cursor token for efficient pagination in OpenSearch.
 * <p>
 * The token contains the sort values of the last document in a result set, allowing
 * OpenSearch to efficiently retrieve the next page without the performance penalties
 * of deep pagination using offset.
 * <p>
 * The token is encoded as Base64 JSON for opacity and URL-safety.
 */
public record SearchAfterToken(
        @JsonProperty("sortValues") List<Object> sortValues,
        @JsonProperty("sortFields") List<String> sortFields) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonCreator
    public SearchAfterToken {
        if (sortValues == null || sortFields == null) {
            throw new RepositoryException("sortValues and sortFields must not be null");
        }
        if (sortValues.size() != sortFields.size()) {
            throw new RepositoryException(
                    String.format("sortValues size (%d) must match sortFields size (%d)",
                            sortValues.size(), sortFields.size()));
        }
        // protect against JSON-Injection by ensuring sort values are scalar types
        for (Object value : sortValues) {
            if (value != null && !(value instanceof String) && !(value instanceof Number)
                    && !(value instanceof Boolean)) {
                throw new RepositoryException(
                        "sortValues must contain only scalar types (String, Number, Boolean, null), got: "
                                + value.getClass().getSimpleName());
            }
        }
    }

    /**
     * Encodes this token as a Base64-encoded JSON string.
     *
     * @return the encoded token string
     */
    public String encode() {
        try {
            byte[] json = MAPPER.writeValueAsBytes(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JacksonException e) {
            throw new RepositoryException("Failed to encode search_after token", e);
        }
    }

    /**
     * Decodes a Base64-encoded JSON token string.
     *
     * @param encodedToken the encoded token string
     * @return the decoded SearchAfterToken
     * @throws RepositoryException if the token is invalid or cannot be decoded
     */
    public static SearchAfterToken decode(String encodedToken) {
        if (encodedToken == null || encodedToken.isEmpty()) {
            throw new RepositoryException("search_after token cannot be null or empty");
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedToken);
            return MAPPER.readValue(json, SearchAfterToken.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new RepositoryException("Invalid search_after token: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a token from sort values and field names.
     *
     * @param sortValues the sort values from the last document
     * @param sortFields the sort field names
     * @return a new SearchAfterToken
     */
    public static SearchAfterToken of(List<Object> sortValues, List<String> sortFields) {
        return new SearchAfterToken(sortValues, sortFields);
    }
}
