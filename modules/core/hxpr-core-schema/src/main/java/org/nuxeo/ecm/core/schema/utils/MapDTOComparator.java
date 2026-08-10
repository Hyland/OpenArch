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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.nuxeo.ecm.core.schema.validation.SchemaDTONode;

/**
 * This class is written for processing difference between key-value pairs between two nested maps.
 * The result is the map of original key-value pairs, maintaining the structure of the nested map.
 */
public class MapDTOComparator {

    private final Map<String, Object> differences;

    private final List<SchemaDTONode> removedNodes = new ArrayList<>();

    private final List<Pair<SchemaDTONode, SchemaDTONode>> modifiedNodes = new ArrayList<>();

    public MapDTOComparator(Map<String, Object> originalMap, Map<String, Object> updatedMap) {
        this.differences = calculateDifferences(originalMap, updatedMap);
    }

    public Map<String, Object> getDifferences() {
        return differences;
    }

    public List<SchemaDTONode> getRemovedNodes() {
        return removedNodes;
    }

    public List<Pair<SchemaDTONode, SchemaDTONode>> getModifiedNodes() {
        return modifiedNodes;
    }

    private SchemaDTONode nextNode(Map<String, Object> map, SchemaDTONode node, String key) {
        if (node == null) {
            node = new SchemaDTONode(map, key);
        } else {
            node = node.get(key);
        }
        return node;
    }

    /**
     * Compares the two maps provided and returns a Map of the entries in the original map that have been altered or removed in the updated map.
     * @return A map of entries from {@code originalMap} that have been modified or removed in {@code updateMap}
     */
    private Map<String, Object> calculateDifferences(Map<String, Object> origMap, Map<String, Object> updateMap) {
        return calculateDifferences(origMap, null, updateMap, null);
    }

    private Map<String, Object> calculateDifferences(Map<String, Object> origMap, SchemaDTONode originalNode,
            Map<String, Object> updateMap, SchemaDTONode updatedNode) {
        Map<String, Object> mapDifferences = new HashMap<>();
        // Iterate through the entries in the first map
        origMap.entrySet().forEach(entry -> {
            String key = entry.getKey();
            SchemaDTONode nextOrigNode = nextNode(origMap, originalNode, key);
            SchemaDTONode nextUpdatedNode = nextNode(updateMap, updatedNode, key);
            Object value1 = nextOrigNode.getNode();
            Object value2 = nextUpdatedNode.getNode();
            if (nextOrigNode.getNode() != null && nextUpdatedNode.getNode() == null) {
                // Key is not present in the second map
                mapDifferences.put(key, nextOrigNode.getNode());
                removedNodes.add(nextOrigNode);
            } else if (nextOrigNode.isMap() && nextUpdatedNode.isMap()) {
                // Both values are maps, so we need to compare them recursively
                Map<String, Object> nestedDifferences = calculateDifferences(nextOrigNode.asMap(), nextOrigNode,
                        nextUpdatedNode.asMap(), nextUpdatedNode);
                if (!nestedDifferences.isEmpty()) {
                    mapDifferences.put(key, nestedDifferences);
                }
            } else if (value1 != null && !value1.equals(value2) &&
                    !((value1 instanceof Number number1 && value2 instanceof Number number2)
                            && number1.intValue() == number2.intValue())) {
                // Values are different
                mapDifferences.put(key, value1);
                modifiedNodes.add(Pair.of(nextOrigNode, nextUpdatedNode));
            }
        }
        );
        return mapDifferences;
    }

}
