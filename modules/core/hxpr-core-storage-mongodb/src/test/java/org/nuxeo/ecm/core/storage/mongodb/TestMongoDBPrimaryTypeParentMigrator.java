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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_ROOT;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class TestMongoDBPrimaryTypeParentMigrator {

    @Test
    public void nullOrEmptyPrimaryTypesDoesNotMigrate() {
        assertFalse(MongoDBPrimaryTypeParentMigrator.migrate(null));
        assertFalse(MongoDBPrimaryTypeParentMigrator.migrate(new HashMap<>()));
    }

    @Test
    public void invalidParentIsReplacedWithSysContent() {
        Map<String, Object> fileType = new HashMap<>();
        fileType.put(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD, "unknownParent");
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put("File", fileType);

        boolean migrated = MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertTrue(migrated);
        assertEquals(SYS_CONTENT, fileType.get(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD));
    }

    @Test
    public void selfParentIsReplacedWithSysContent() {
        Map<String, Object> folderType = new HashMap<>();
        folderType.put(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD, "Folder");
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put("Folder", folderType);

        boolean migrated = MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertTrue(migrated);
        assertEquals(SYS_CONTENT, folderType.get(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD));
    }

    @Test
    public void sysRelationGetsContentParent() {
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put(SYS_CONTENT, new HashMap<>());
        primaryTypes.put("SysRelation", new HashMap<>());

        boolean migrated = MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertTrue(migrated);
        assertEquals(SYS_CONTENT,
                ((Map<?, ?>) primaryTypes.get("SysRelation")).get(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD));
    }

    @Test
    public void sysRootWithInvalidParentIsReplacedWithSysFolder() {
        Map<String, Object> sysRootType = new HashMap<>();
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put(SYS_ROOT, sysRootType);
        primaryTypes.put(SYS_CONTENT, new HashMap<>());

        boolean migrated = MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertTrue(migrated);
        assertEquals(SYS_FOLDER, sysRootType.get(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD));
    }

    @Test
    public void validParentIsKeptUnchanged() {
        Map<String, Object> contentType = new HashMap<>();
        Map<String, Object> folderType = new HashMap<>();
        folderType.put(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD, "Document");
        Map<String, Object> documentType = new HashMap<>();
        documentType.put(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD, SYS_CONTENT);
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put(SYS_CONTENT, contentType);
        primaryTypes.put("Folder", folderType);
        primaryTypes.put("Document", documentType);

        boolean migrated = MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertFalse(migrated);
        assertEquals("Document", folderType.get(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD));
    }

    @Test
    public void nonMapEntriesAreIgnoredAndDoNotTriggerMigration() {
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put("Invalid", "not-a-map");
        primaryTypes.put("AlsoInvalid", null);

        boolean migrated = MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertFalse(migrated);
    }

    @Test
    public void migrationDoesNotInjectTypeNameFieldIntoTypeMap() {
        Map<String, Object> type = new HashMap<>();
        type.put(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD, 123L);
        Map<String, Object> primaryTypes = new HashMap<>();
        primaryTypes.put("MyType", type);

        MongoDBPrimaryTypeParentMigrator.migrate(primaryTypes);

        assertFalse(type.containsKey("MyType"));
        assertEquals(SYS_CONTENT, type.get(MongoDBPrimaryTypeParentMigrator.EXTENDS_FIELD));
    }
}
