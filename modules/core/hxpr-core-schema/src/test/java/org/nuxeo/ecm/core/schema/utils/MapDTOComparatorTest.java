/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.schema.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.dto.DTOPath;

public class MapDTOComparatorTest {

    @Test
    public void testRemoveNestedValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapRemoveTypesBlobFieldsEncoding();

        List<DTOPath> expectedMissingNodes = List.of(
                pathIn(originalMap, "types", "blob", "fields", "encoding"));

        List<Pair<DTOPath, DTOPath>> expectedModifiedNodes = List.of();

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testRemoveTopLevelValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapRemoveTypesBlob();

        List<DTOPath> expectedMissingNodes = List.of(
                pathIn(originalMap, "types", "blob"));

        List<Pair<DTOPath, DTOPath>> expectedModifiedNodes = List.of();

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testModifiedNestedValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapModifyTypesBlobFieldsEncoding();

        List<DTOPath> expectedMissingNodes = List.of();

        List<Pair<DTOPath, DTOPath>> expectedModifiedNodes = List.of(
                Pair.of(
                        pathIn(originalMap, "types", "blob", "fields", "encoding"),
                        pathIn(updatedMap, "types", "blob", "fields", "encoding")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);
        assertComparator(expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testAddValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapAddConstraint();

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(List.of(), List.of(), comparator);
    }

    @Test
    public void testRemoveAndModifyValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapRemoveDublinAndModifyNestedValue();

        List<DTOPath> expectedMissingNodes = List.of(
                pathIn(originalMap, "types", "dublincore#clob"));
        var expectedModifiedNodes = List.of(
                Pair.of(
                        pathIn(originalMap, "types", "blob", "fields", "length"),
                        pathIn(updatedMap, "types", "blob", "fields", "length")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testModifyNestedMapToObject() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapModifyNestedMapToObject();

        List<DTOPath> expectedMissingNodes = List.of();
        List<Pair<DTOPath, DTOPath>> expectedModifiedNodes = List.of(
                Pair.of(
                        pathIn(originalMap, "types", "blob", "fields"),
                        pathIn(updatedMap, "types", "blob", "fields")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);
        assertComparator(expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testModifyObjectToMap() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapModifyObjectToMap();

        List<DTOPath> expectedMissingNodes = List.of();
        List<Pair<DTOPath, DTOPath>> expectedModifiedNodes = List.of(
                Pair.of(
                        pathIn(originalMap, "types", "dublincore#clob", "extends"),
                        pathIn(updatedMap, "types", "dublincore#clob", "extends")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);
        assertComparator(expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testMapWithNumbers() {
        Map<String, Object> integerMap = Map.of("types",
                Map.of("dublincore#clob", Map.of("constraints", Map.of("maxLength", 2000))));
        Map<String, Object> longMap = Map.of("types",
                Map.of("dublincore#clob", Map.of("constraints", Map.of("maxLength", 2000L))));
        // an Integer and a Long of the same value are not a change
        MapDTOComparator mapComparator = new MapDTOComparator(integerMap, longMap);
        assertComparator(List.of(), List.of(), mapComparator);

        // a number and a string are
        Map<String, Object> stringMap = Map.of("types",
        Map.of("dublincore#clob", Map.of("constraints", Map.of("maxLength", "2000"))));
        mapComparator = new MapDTOComparator(longMap, stringMap);
        assertComparator(List.of(), List.of(Pair.of(
                pathIn(longMap, "types", "dublincore#clob", "constraints", "maxLength"),
                pathIn(stringMap, "types", "dublincore#clob", "constraints", "maxLength"))), mapComparator);
    }

    /** Builds the {@link DTOPath} of {@code keys} within {@code map}, values included. */
    private static DTOPath pathIn(Map<String, Object> map, String... keys) {
        Object value = map.get(keys[0]);
        DTOPath path = DTOPath.root(keys[0], value);
        for (int i = 1; i < keys.length; i++) {
            value = value instanceof Map<?, ?> nested ? nested.get(keys[i]) : null;
            path = path.child(keys[i], value);
        }
        return path;
    }

    private void assertComparator(List<DTOPath> expectedMissingNodes,
            List<Pair<DTOPath, DTOPath>> expectedModifiedNodes, MapDTOComparator comparator) {
        assertEquals(expectedMissingNodes, comparator.getRemovedNodes());
        assertEquals(expectedModifiedNodes, comparator.getModifiedNodes());
    }

    private static Map<String, Object> testMapOrig() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string")),
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000))));
    }

    private static Map<String, Object> testMapRemoveTypesBlob() {
        return Map.of(
                "types", Map.of(
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000))));
    }

    private static Map<String, Object> testMapRemoveTypesBlobFieldsEncoding() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string")),
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000))));
    }

    private static Map<String, Object> testMapModifyTypesBlobFieldsEncoding() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "long",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string")),
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000))));
    }

    private static Map<String, Object> testMapAddConstraint() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string")),
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000,
                                        "minLength", 100))));
    }

    private static Map<String, Object> testMapRemoveDublinAndModifyNestedValue() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "long",
                                        "mimeType", "string"))));
    }

    private static Map<String, Object> testMapModifyNestedMapToObject() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", 1000),
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000))));
    }

    private static Map<String, Object> testMapModifyObjectToMap() {
        return Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string")),
                        "dublincore#clob", Map.of(
                                "extends", Map.of(
                                        "This", Map.of(
                                                "Should", Map.of(
                                                        "Not", "be here"))),
                                "constraints", Map.of(
                                        "maxLength", 2000))));
    }
}
