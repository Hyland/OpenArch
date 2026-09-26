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
package com.hyland.openarch.rest.query.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SearchAfterToken} encoding and decoding.
 */
public class SearchAfterTokenTest {

    @Test
    public void testEncodeDecodeSimpleToken() {
        // Given
        List<Object> sortValues = List.of("TestTitle", "doc-123");
        List<String> sortFields = List.of("sys_title", "sys_id");
        SearchAfterToken original = new SearchAfterToken(sortValues, sortFields);

        // When
        String encoded = original.encode();
        SearchAfterToken decoded = SearchAfterToken.decode(encoded);

        // Then
        assertNotNull(encoded);
        assertNotNull(decoded);
        assertEquals(original.sortValues(), decoded.sortValues());
        assertEquals(original.sortFields(), decoded.sortFields());
    }

    @Test
    public void testEncodeDecodeWithNumericValues() {
        // Given
        List<Object> sortValues = List.of(12345L, "doc-123");
        List<String> sortFields = List.of("sys_created", "sys_id");
        SearchAfterToken original = new SearchAfterToken(sortValues, sortFields);

        // When
        String encoded = original.encode();
        SearchAfterToken decoded = SearchAfterToken.decode(encoded);

        // Then
        assertNotNull(decoded);
        assertEquals(2, decoded.sortValues().size());
        // JSON decoding converts numbers to Integer/Long/Double
        assertInstanceOf(Number.class, decoded.sortValues().get(0));
        assertEquals(12345, ((Number) decoded.sortValues().get(0)).intValue());
        assertEquals("doc-123", decoded.sortValues().get(1));
    }

    @Test
    public void testEncodeDecodeWithNullValues() {
        // Given
        List<Object> sortValues = Arrays.asList(null, "doc-123");
        List<String> sortFields = List.of("sys_title", "sys_id");
        SearchAfterToken original = new SearchAfterToken(sortValues, sortFields);

        // When
        String encoded = original.encode();
        SearchAfterToken decoded = SearchAfterToken.decode(encoded);

        // Then
        assertNotNull(decoded);
        assertEquals(2, decoded.sortValues().size());
        assertNull(decoded.sortValues().get(0));
        assertEquals("doc-123", decoded.sortValues().get(1));
    }

    @Test
    public void testDecodeInvalidBase64() {
        // Given
        String invalidBase64 = "This is not base64!!!";

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(invalidBase64));
        assertTrue(exception.getMessage().contains("Invalid search_after token"));
    }

    @Test
    public void testDecodeInvalidJson() {
        // Given - valid base64 but invalid JSON
        String invalidJson = Base64.getEncoder().encodeToString("not a json".getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(invalidJson));
        assertTrue(exception.getMessage().contains("Invalid search_after token"));
    }

    @Test
    public void testDecodeMissingSortValues() {
        // Given - JSON without sortValues field
        String jsonWithoutSortValues = """
            {"sortFields":["sys_title","sys_id"]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithoutSortValues.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("sortValues"));
    }

    @Test
    public void testDecodeMissingSortFields() {
        // Given - JSON without sortFields field
        String jsonWithoutSortFields = """
            {"sortValues":["TestTitle","doc-123"]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithoutSortFields.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("sortFields"));
    }

    @Test
    public void testDecodeEmptySortValues() {
        // Given
        String jsonWithEmptySortValues = """
            {"sortValues":[],"sortFields":["sys_id"]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithEmptySortValues.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("sortValues") && exception.getMessage().contains("sortFields"));
    }

    @Test
    public void testDecodeEmptySortFields() {
        // Given
        String jsonWithEmptySortFields = """
            {"sortValues":["doc-123"],"sortFields":[]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithEmptySortFields.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("sortValues") && exception.getMessage().contains("sortFields"));
    }

    @Test
    public void testDecodeMismatchedLengths() {
        // Given - different lengths for sortValues and sortFields
        String jsonWithMismatch = """
            {"sortValues":["TestTitle","doc-123"],"sortFields":["sys_id"]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithMismatch.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("sortValues") && exception.getMessage().contains("sortFields"));
    }

    @Test
    public void testEncodeProducesValidBase64() {
        // Given
        SearchAfterToken token = new SearchAfterToken(List.of("test", "123"), List.of("field1", "field2"));

        // When
        String encoded = token.encode();

        // Then
        assertNotNull(encoded);
        // URL-safe base64 (without padding) uses A-Z, a-z, 0-9, -, _
        assertTrue(encoded.matches("^[A-Za-z0-9_-]*$"), "Encoded token should be valid URL-safe base64");
    }

    @Test
    public void testDecodeRejectsNonScalarSortValues() {
        // Given - JSON with an object (Map) as a sort value
        String jsonWithObjectValue = """
            {"sortValues":[{"$gt":"2024-01-01"}],"sortFields":["dc:created"]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithObjectValue.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("scalar types"));
    }

    @Test
    public void testDecodeRejectsArrayAsSortValue() {
        // Given - JSON with an array as a sort value
        String jsonWithArrayValue = """
            {"sortValues":[["nested","array"]],"sortFields":["dc:title"]}
            """;
        String encoded = Base64.getEncoder().encodeToString(jsonWithArrayValue.getBytes());

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> SearchAfterToken.decode(encoded));
        assertTrue(exception.getMessage().contains("scalar types"));
    }

    @Test
    public void testConstructorRejectsNonScalarSortValues() {
        // Given - a Map object directly passed as sort value
        List<Object> sortValues = List.of(java.util.Map.of("key", "value"));
        List<String> sortFields = List.of("dc:title");

        // When/Then
        RepositoryException exception = assertThrows(RepositoryException.class, () -> new SearchAfterToken(sortValues, sortFields));
        assertTrue(exception.getMessage().contains("scalar types"));
    }

    @Test
    public void testRoundTripWithSpecialCharacters() {
        // Given - test with special characters that need proper encoding
        List<Object> sortValues = List.of("Title with spaces", "ID-with-hyphens_123", "special:chars/test");
        List<String> sortFields = List.of("sys_title", "sys_id", "custom_field");
        SearchAfterToken original = new SearchAfterToken(sortValues, sortFields);

        // When
        String encoded = original.encode();
        SearchAfterToken decoded = SearchAfterToken.decode(encoded);

        // Then
        assertEquals(original.sortValues(), decoded.sortValues());
        assertEquals(original.sortFields(), decoded.sortFields());
    }
}
