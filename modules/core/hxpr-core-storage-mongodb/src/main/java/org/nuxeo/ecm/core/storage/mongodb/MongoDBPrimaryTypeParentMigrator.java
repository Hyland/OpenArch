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
package org.nuxeo.ecm.core.storage.mongodb;

import java.util.Map;
import java.util.Set;

import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_RELATION;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_ROOT;

public class MongoDBPrimaryTypeParentMigrator {

    public static final String EXTENDS_FIELD = "extends";

    public static boolean migrate(Map<String, Object> primaryTypes) {
        boolean migrated = false;
        if (primaryTypes == null || primaryTypes.isEmpty()) {
            return migrated;
        }
        for (Map.Entry<String, Object> entry : primaryTypes.entrySet()) {
            String primaryTypeName = entry.getKey();
            Object primaryType = entry.getValue();
            if (primaryTypeName.equals(SYS_CONTENT) || primaryTypeName.equals(SYS_RELATION)) {
                continue; // root types does not have parent type
            }
            if (primaryType == null || !(primaryType instanceof Map<?,?>)) {
                continue;
            }
            Map<String, Object> primaryTypeMap = (Map<String, Object>) primaryType;
            if (invalidParent(primaryTypeMap, primaryTypes.keySet(), primaryTypeName)) {
                if (primaryTypeName.equals(SYS_ROOT)) { // sys root is a root folder of content, so it should extend sys folder
                    primaryTypeMap.put(EXTENDS_FIELD, SYS_FOLDER);
                } else {
                    primaryTypeMap.put(EXTENDS_FIELD, SYS_CONTENT);
                }
                migrated = true;

            }

        }
        return migrated;
    }

    private static boolean invalidParent(Map<String, Object> primaryTypeMap, Set<String> primaryTypesNames, String currentType) {
        if (primaryTypeMap == null || primaryTypeMap.isEmpty()) {
            return true;
        }
        Object parent = primaryTypeMap.get(EXTENDS_FIELD);
        if (parent == null || !(parent instanceof String)) {
            return true;
        }
        String parentString = (String) parent;
        if (primaryTypesNames.contains(parentString)) {
            if (currentType.equals(parentString)) {
                // a type cannot be the parent of itself
                return true;
            }
            return false;
        }
        return true;
    }
}
