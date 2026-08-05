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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PREFIX;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.nuxeo.ecm.core.storage.State;

/**
 * Function to flatten and convert {@link State} into NXQL {@link Map}&lt;{@link String}, {@link Object}&gt;.
 *
 * @since 8.3
 */
public class DBSStateFlattener {

    protected final Map<String, String> keyMappings;

    public DBSStateFlattener() {
        this.keyMappings = Collections.emptyMap();
    }

    public DBSStateFlattener(Map<String, String> keyMappings) {
        this.keyMappings = keyMappings != null ? keyMappings : Collections.emptyMap();
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
            } else if (key.startsWith(SYS_PREFIX) || key.startsWith(SYSVER_PREFIX)) {
                name = DBSSession.convToNXQL(key);
                if (name == null) {
                    // present in state but not returned to caller
                    continue;
                }
            } else {
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
