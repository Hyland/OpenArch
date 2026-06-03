/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     David Edwards
 */
package org.nuxeo.ecm.core.schema.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.validation.SchemaDTONode;

public class MapDTOComparatorTest {

    @Test
    public void testRemoveNestedValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapRemoveTypesBlobFieldsEncoding();

        Map<String, Object> expectedRemovedMap = Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "encoding", "string"))));

        Map<String, Object> expectedResult = expectedRemovedMap;

        List<SchemaDTONode> expectedMissingNodes = List.of(
                new SchemaDTONode(originalMap, "types").get("blob").get("fields").get("encoding"));

        List<Pair<SchemaDTONode, SchemaDTONode>> expectedModifiedNodes = List.of();

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(expectedResult, expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testRemoveTopLevelValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapRemoveTypesBlob();

        Map<String, Object> expectedResult = Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string"))));

        List<SchemaDTONode> expectedMissingNodes = List.of(
                new SchemaDTONode(originalMap, "types").get("blob"));

        List<Pair<SchemaDTONode, SchemaDTONode>> expectedModifiedNodes = List.of();

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(expectedResult, expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testModifiedNestedValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapModifyTypesBlobFieldsEncoding();

        Map<String, Object> expectedResult = Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "encoding", "string"))));

        List<SchemaDTONode> expectedMissingNodes = List.of();

        List<Pair<SchemaDTONode, SchemaDTONode>> expectedModifiedNodes = List.of(
                Pair.of(
                        new SchemaDTONode(originalMap, "types").get("blob").get("fields").get("encoding"),
                        new SchemaDTONode(updatedMap, "types").get("blob").get("fields").get("encoding")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);
        assertComparator(expectedResult, expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testAddValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapAddConstraint();

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(Map.of(), List.of(), List.of(), comparator);
    }

    @Test
    public void testRemoveAndModifyValue() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapRemoveDublinAndModifyNestedValue();

        Map<String, Object> expectedResult = Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "length", "string")),
                        "dublincore#clob", Map.of(
                                "extends", "string",
                                "constraints", Map.of(
                                        "maxLength", 2000))));

        List<SchemaDTONode> expectedMissingNodes = List.of(
                new SchemaDTONode(originalMap, "types").get("dublincore#clob"));
        var expectedModifiedNodes = List.of(
                Pair.of(
                        new SchemaDTONode(originalMap, "types").get("blob").get("fields").get("length"),
                        new SchemaDTONode(updatedMap, "types").get("blob").get("fields").get("length")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);

        assertComparator(expectedResult, expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testModifyNestedMapToObject() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapModifyNestedMapToObject();

        Map<String, Object> expectedResult = Map.of(
                "types", Map.of(
                        "blob", Map.of(
                                "fields", Map.of(
                                        "digest", "string",
                                        "encoding", "string",
                                        "filename", "string",
                                        "key", "string",
                                        "length", "string",
                                        "mimeType", "string"))));

        List<SchemaDTONode> expectedMissingNodes = List.of();
        List<Pair<SchemaDTONode, SchemaDTONode>> expectedModifiedNodes = List.of(
                Pair.of(
                        new SchemaDTONode(originalMap, "types").get("blob").get("fields"),
                        new SchemaDTONode(updatedMap, "types").get("blob").get("fields")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);
        assertComparator(expectedResult, expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testModifyObjectToMap() {
        Map<String, Object> originalMap = testMapOrig();
        Map<String, Object> updatedMap = testMapModifyObjectToMap();

        Map<String, Object> expectedResult = Map.of(
                "types", Map.of(
                        "dublincore#clob", Map.of(
                                "extends", "string")));

        List<SchemaDTONode> expectedMissingNodes = List.of();
        List<Pair<SchemaDTONode, SchemaDTONode>> expectedModifiedNodes = List.of(
                Pair.of(
                        new SchemaDTONode(originalMap, "types").get("dublincore#clob").get("extends"),
                        new SchemaDTONode(updatedMap, "types").get("dublincore#clob").get("extends")));

        MapDTOComparator comparator = new MapDTOComparator(originalMap, updatedMap);
        assertComparator(expectedResult, expectedMissingNodes, expectedModifiedNodes, comparator);
    }

    @Test
    public void testMapWithNumbers() {
        Map<String, Object> integerMap = Map.of("types",
                Map.of("dublincore#clob", Map.of("constraints", Map.of("maxLength", 2000))));
        Map<String, Object> longMap = Map.of("types",
                Map.of("dublincore#clob", Map.of("constraints", Map.of("maxLength", 2000L))));
        MapDTOComparator mapComparator = new MapDTOComparator(integerMap, longMap);
        assertComparator(Map.of(), List.of(), List.of(), mapComparator);

        Map<String, Object> stringMap = Map.of("types",
        Map.of("dublincore#clob", Map.of("constraints", Map.of("maxLength", "2000"))));
        mapComparator = new MapDTOComparator(longMap, stringMap);
        assertEquals(longMap, mapComparator.getDifferences());
    }

    private void assertComparator(Map<String, Object> expectedMap, List<SchemaDTONode> expectedMissingNodes,
            List<Pair<SchemaDTONode, SchemaDTONode>> expectedModifiedNodes, MapDTOComparator comparator) {
        assertEquals(expectedMap, comparator.getDifferences());
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
