/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.schema.validation;

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.ANONYMOUS_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchemaDTONode {

    private static final String PATH_SEGMENT_MSG_FORMAT = "/%s";

    private SchemaDTONode parentNode;

    private Object node;

    private String key;

    private boolean isMap;

    private SchemaDTONode(SchemaDTONode parentNode, Object node, String key) {
        this.parentNode = parentNode;
        this.node = node;
        this.key = key;
        this.isMap = (getNode() instanceof Map);
    }

    /**
     * Creates a SchemaDTONode. Uses the provided {@code Map<String,Object>} and attempts to set the root node to the {@code rootKey}.
     * @param dto The dto object
     * @param key The key used to create the {@link SchemaDTONode}
     */
    public SchemaDTONode(Map<String, Object> dto, String key) {
        this(null, dto.get(key), key);
    }

    /**
     * Create a {@link SchemaDTONode}, to be used to create an {@code #anonymousType}
     */
    public SchemaDTONode createAnonymousNode() {
        return new SchemaDTONode(getParentNode(), getNode(), ANONYMOUS_TYPE);
    }

    public SchemaDTONode getParentNode() {
        return parentNode;
    }

    public Object getNode() {
        return node;
    }

    public String getKey() {
        return key;
    }

    public boolean isMap() {
        return isMap;
    }

    public boolean isList() {
        return (getNode() instanceof List);
    }

    /**
     * Will return the {@code node} {@code Object} as a {@code List<String=>} if it is a {@code List},
     * {@code null} if it is not a {@code List}
     * @return The {@code node} as a {@code List}
     */
    @SuppressWarnings("unchecked")
    public List<String> asList() {
        List<String> nullList = null;
        return isList() ? new ArrayList<>((List<String>) getNode()) : nullList;
    }

    /**
     * Gets a readable string path in the format {@code '/rootNode/parentNode/parentNode/node'}
     */
    public String getPath() {
        if (getKey().equals(ANONYMOUS_TYPE)) {
            return getKey();
        }
        return calculatePath(null);
    }


    private String calculatePath(StringBuilder sb) {
        if (sb == null) {
            sb = new StringBuilder();
        }
        if (getParentNode() != null) {
            sb.insert(0, PATH_SEGMENT_MSG_FORMAT.formatted(getKey()));
            return getParentNode().calculatePath(sb);
        } else {
            sb.insert(0, getKey());
            return sb.toString();
        }
    }

    /**
     * Will return the {@code node} {@code Object} as a {@code Map<String, Object>} if it is a {@code Map},
     * {@code null} if it is not a {@code Map}
     * @return The {@code node} as a {@code Map<String, Object>}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap() {
        Map<String, Object> nullMap = null;
        return isMap() ? (Map<String, Object>) getNode() : nullMap;
    }

    /**
     * Return a child node, a new {@link SchemaDTONode}, using the key provided, if it exists.
     * @param key
     * @return The {@link SchemaDTONode} at the {@code key}, or {@code null} if it doesn't exist
     */
    public SchemaDTONode get(String key) {
        if (getNode() == null) {
            return null;
        }
        Map<String, Object> nodeMap = asMap();
        if (nodeMap == null || !nodeMap.containsKey(key)) {
            return new SchemaDTONode(this, null, key);
        } else {
            return new SchemaDTONode(this, nodeMap.get(key), key);
        }
    }

    public boolean containsKey(Object key) {
        Map<String, Object> nodeMap = asMap();
        return nodeMap != null && nodeMap.containsKey(key);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((parentNode == null) ? 0 : parentNode.hashCode());
        result = prime * result + ((node == null) ? 0 : node.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SchemaDTONode other = (SchemaDTONode) obj;
        if (parentNode == null) {
            if (other.parentNode != null)
                return false;
        } else if (!parentNode.equals(other.parentNode))
            return false;
        if (node == null) {
            if (other.node != null)
                return false;
        } else if (!node.equals(other.node))
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        return true;
    }
}
