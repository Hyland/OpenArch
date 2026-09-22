/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage.dbs;

import static java.util.Objects.requireNonNull;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PATH_INTERNAL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.storage.State;

/**
 * Function to flatten and convert {@link State} into NXQL {@code Map<String, Object>}.
 *
 * @since 8.3
 */
public class DBSStateFlattener {

    protected final Map<String, String> keyMappings;

    protected final SchemaManager schemaManager;

    public DBSStateFlattener(Map<String, String> keyMappings, SchemaManager schemaManager) {
        this.keyMappings = keyMappings != null ? keyMappings : Map.of();
        this.schemaManager = requireNonNull(schemaManager);
    }

    /**
     * Flattens with optional property key mappings.
     * @param state state
     * @return flattened result
     * @since 9.3
     */
    public Map<String, Object> flatten(State state) {
        Map<String, Object> map = new HashMap<>();
        flatten(map, state, null);
        return map;
    }

    protected void flatten(Map<String, Object> map, State state, String prefix) {
        for (Entry<String, Object> en : state.entrySet()) {
            String key = en.getKey();
            Object value = en.getValue();
            String name;
            String realName = keyMappings.get(key);
            if (realName != null) {
                name = realName;
            } else {
                if (KEY_PATH_INTERNAL.equals(key)) {
                    continue;
                }
                Field field = schemaManager.getField(key);
                if (field != null && field.getCharacteristics().notProjectable()) {
                    continue;
                }
                name = key;
            }
            name = prefix == null ? name : prefix + name;
            if (value instanceof State vState) {
                flatten(map, vState, name + '/');
            } else if (value instanceof List) {
                String nameSlash = name + '/';
                int i = 0;
                for (Object v : (List<?>) value) {
                    if (v instanceof State vState) {
                        flatten(map, vState, nameSlash + i + '/');
                    } else {
                        map.put(nameSlash + i, v);
                    }
                    i++;
                }
            } else if (value instanceof Object[] objects) {
                String nameSlash = name + '/';
                int i = 0;
                for (Object v : objects) {
                    map.put(nameSlash + i, v);
                    i++;
                }
            } else {
                map.put(name, value);
            }
        }
    }

}
