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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.nuxeo.ecm.core.schema.dto.DTOPath;

/**
 * Computes the difference between two nested maps, reporting which entries of the original were
 * removed or modified, and where.
 */
public class MapDTOComparator {

    private final List<DTOPath> removedNodes = new ArrayList<>();

    private final List<Pair<DTOPath, DTOPath>> modifiedNodes = new ArrayList<>();

    public MapDTOComparator(Map<String, Object> originalMap, Map<String, Object> updatedMap) {
        compare(originalMap, null, updatedMap, null);
    }

    public List<DTOPath> getRemovedNodes() {
        return removedNodes;
    }

    public List<Pair<DTOPath, DTOPath>> getModifiedNodes() {
        return modifiedNodes;
    }

    private static DTOPath nextNode(DTOPath parent, String key, Object value) {
        return parent == null ? DTOPath.root(key, value) : parent.child(key, value);
    }

    /**
     * Walks both maps together, collecting the entries of the original that the update removed or
     * changed. A removed subtree is reported once, at its root, rather than leaf by leaf; a changed
     * one is reported at the leaf that actually differs.
     */
    private void compare(Map<String, Object> origMap, DTOPath originalNode, Map<String, Object> updateMap,
            DTOPath updatedNode) {
        origMap.forEach((key, origValue) -> {
            Object updatedValue = updateMap == null ? null : updateMap.get(key);
            DTOPath nextOrigNode = nextNode(originalNode, key, origValue);
            DTOPath nextUpdatedNode = nextNode(updatedNode, key, updatedValue);
            if (origValue != null && updatedValue == null) {
                // Key is not present in the second map
                removedNodes.add(nextOrigNode);
            } else if (origValue instanceof Map && updatedValue instanceof Map) {
                // Both values are maps, so we need to compare them recursively
                compare(asMap(origValue), nextOrigNode, asMap(updatedValue), nextUpdatedNode);
            } else if (origValue != null && !equalValues(origValue, updatedValue)) {
                // Values are different
                modifiedNodes.add(Pair.of(nextOrigNode, nextUpdatedNode));
            }
        });
    }

    /**
     * Numbers are compared by value: the same constraint bound can arrive as an {@link Integer} on
     * one side and a {@link Long} on the other depending on how it was serialized.
     */
    private static boolean equalValues(Object left, Object right) {
        if (left.equals(right)) {
            return true;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return new BigDecimal(leftNumber.toString()).compareTo(new BigDecimal(rightNumber.toString())) == 0;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
