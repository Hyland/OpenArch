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
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH_DEPTH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_FOLDERISH;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Instant;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalDocument.WriteContext;
import org.nuxeo.ecm.core.model.WriteResult;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.FieldCharacteristics;
import org.nuxeo.ecm.core.schema.types.FieldCharacteristics.ValueSource;
import org.nuxeo.ecm.core.schema.types.SchemaImpl;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-dbsdocument.xml")
public class TestDBSDocument {
    @Inject
    private TypeService typeService;

    private SchemaManager schemaManager;

    private DBSSession dbsSession;

    @Mock
    @RuntimeService
    private SecurityService securityService;

    @Mock
    private SecurityPolicyService securityPolicyService;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
        dbsSession = mock(DBSSession.class);
        when(dbsSession.getSchemaManager()).thenReturn(schemaManager);
        when(dbsSession.getRepositoryName()).thenReturn("test");
        when(securityService.getSecurityPolicyService("test")).thenReturn(securityPolicyService);
    }

    @Test
    public void testFlagIsFolderish() {
        //create and test a dbsDocument from type folder
        DocumentType type = schemaManager.getDocumentType(SYS_FOLDER);
        boolean isFolder = new DBSDocument(getFolderDBSDocumentState(type), type, dbsSession, false).isFolder();
        assertTrue(isFolder);

        //create and test a dbsDocument from type file
        type = schemaManager.getDocumentType(SYS_FILE);
        isFolder = new DBSDocument(getFolderDBSDocumentState(type), type, dbsSession, false).isFolder();
        assertFalse(isFolder);
    }

    @Test
    public void ordinaryFieldHasDefaultCharacteristicsAndUsesStoredValue() {
        SchemaImpl schema = new SchemaImpl("custom", "custom");
        Field field = schema.addField("flag", BooleanType.INSTANCE, null, null);
        DocumentType type = schemaManager.getDocumentType(SYS_FILE);
        DBSDocument doc = new DBSDocument(getFolderDBSDocumentState(type), type, dbsSession, false);
        State state = new State();
        state.put("custom_flag", Boolean.TRUE);

        assertFalse(field.isRepositoryManaged());
        assertSame(FieldCharacteristics.DEFAULT, field.getCharacteristics());
        assertTrue((Boolean) doc.getValueField(state, field, null, null));
    }

    @Test
    public void exceptionalSourceControlsManagedFallback() {
        SchemaImpl schema = new SchemaImpl("system", "sys");
        FieldCharacteristics computed = new FieldCharacteristics(true, ValueSource.COMPUTED, false, false, false, null,
                null, null, null, null);
        Field computedField = schema.addField("unknownComputed", StringType.INSTANCE, null, null, computed);
        FieldCharacteristics query = new FieldCharacteristics(true, ValueSource.QUERY_VIRTUAL, false, false, false,
                null, null, null, null, null);
        Field queryField = schema.addField("unknownQuery", StringType.INSTANCE, null, null, query);
        DocumentType type = schemaManager.getDocumentType(SYS_FILE);
        DBSDocument doc = new DBSDocument(getFolderDBSDocumentState(type), type, dbsSession, false);
        State state = new State();
        state.put(queryField.getName(), "stored");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> doc.getValueField(state, computedField, null, null));
        assertEquals("No computed system value handler for: sys_unknownComputed", e.getMessage());
        assertNull(doc.getValueField(state, queryField, null, null));
    }

    @Test
    public void pathAndDepthCoverRootPlacelessAndNestedDocuments() {
        DocumentType type = schemaManager.getDocumentType(SYS_FILE);
        DBSDocument root = newDocument(type, "root", null, "");
        DBSDocument placeless = newDocument(type, "placeless-id", null, "placeless");
        DBSDocument firstLevel = newDocument(type, "first-id", "root", "first");
        DBSDocument nested = newDocument(type, "nested-id", "first-id", "nested");
        when(dbsSession.getDocument("root", true)).thenReturn(root);
        when(dbsSession.getDocument("first-id", true)).thenReturn(firstLevel);

        assertPath(root, "/", 0);
        assertPath(placeless, "placeless", 1);
        assertPath(firstLevel, "/first", 2);
        assertPath(nested, "/first/nested", 3);
    }

    private DBSDocument newDocument(DocumentType type, String id, String parentId, String name) {
        DBSDocumentState state = new DBSDocumentState();
        state.put(SYS_ID, id);
        if (parentId != null) {
            state.put(SYS_PARENT_ID, parentId);
        }
        state.put(SYS_NAME, name);
        return new DBSDocument(state, type, dbsSession, false);
    }

    private static void assertPath(InternalDocument doc, String path, int depth) {
        assertEquals(path, doc.getPath());
        assertEquals(path, doc.getValue(SYS_PATH));
        assertEquals(depth, doc.getValue(SYS_PATH_DEPTH));
    }

    @Test
    public void ignoredSystemOwnedWriteHasNoWriteSideEffects() {
        DocumentType type = schemaManager.getDocumentType(SYS_FILE);
        DBSDocumentState state = getFolderDBSDocumentState(type);
        state.put(SYSVER_IS_CHECKED_IN, Boolean.TRUE);
        DBSDocument doc = new DBSDocument(state, type, dbsSession, false);
        clearInvocations(dbsSession);

        WriteResult result = doc.setValue(SYS_NAME, "ignored", new WriteContext(null, null, true));

        assertSame(WriteResult.EMPTY, result);
        verifyNoInteractions(dbsSession);
    }

    @Test
    public void ignoredSameValueVersionWriteHasNoWriteSideEffects() {
        DocumentType type = schemaManager.getDocumentType(SYS_FILE);
        DBSDocumentState state = getFolderDBSDocumentState(type);
        state.put(SYS_TITLE, "title");
        state.put(SYSVER_IS_VERSION, Boolean.TRUE);
        DBSDocument doc = new DBSDocument(state, type, dbsSession, true);
        clearInvocations(dbsSession);

        WriteResult result = doc.setValue(SYS_TITLE, "title", new WriteContext(null, null, true));

        assertSame(WriteResult.EMPTY, result);
        verifyNoInteractions(dbsSession);
    }

    private DBSDocumentState getFolderDBSDocumentState(DocumentType type) {
        DBSDocumentState docState = new DBSDocumentState();
        if (type != null && type.isFolder()) {
            docState.put(SYS_IS_FOLDERISH, Boolean.TRUE);
        }
        return docState;
    }

    @Test
    public void testFlagIsFolderishUpdatedAfterMixinChangeOnFile() {
        DocumentType type = schemaManager.getDocumentType(SYS_FILE);
        DBSDocument doc = new DBSDocument(getFolderDBSDocumentState(type), type, dbsSession, false);
        // initially file is not folderish
        assertFalse(doc.isFolder());
        // add a SysFolderish mixin
        doc.setPrimaryTypeAndMixinTypes(null, Set.of(SYS_FOLDERISH));
        assertTrue(doc.isFolder());
        // remove it, it's not folderish anymore
        doc.setPrimaryTypeAndMixinTypes(null, Set.of());
        assertFalse(doc.isFolder());
    }

    @Test
    public void testFlagIsFolderishUpdatedAfterMixinChangeOnFolder() {
        DocumentType type = schemaManager.getDocumentType(SYS_FOLDER);
        DBSDocument doc = new DBSDocument(getFolderDBSDocumentState(type), type, dbsSession, false);
        // a folder is already folderish from its primary type mixins
        assertTrue(doc.isFolder());
        // add a SysFolderish mixin
        doc.setPrimaryTypeAndMixinTypes(null, Set.of(SYS_FOLDERISH));
        assertTrue(doc.isFolder());
        // remove it, it's still folderish
        doc.setPrimaryTypeAndMixinTypes(null, Set.of());
        assertTrue(doc.isFolder());
    }

    @Test
    public void legalHoldDocumentIsUnderRetention() {
        DBSDocument doc = mock(DBSDocument.class);
        when(doc.hasLegalHold()).thenReturn(true);
        when(doc.isUnderRetentionOrLegalHold()).thenCallRealMethod();
        assertTrue(doc.isUnderRetentionOrLegalHold());
    }

    @Test
    public void markingADocumentAsRecordDoesNotBringItUnderRetention() {
        DBSDocument doc = mock(DBSDocument.class);
        when(doc.isRecord()).thenReturn(true);
        when(doc.isUnderRetentionOrLegalHold()).thenCallRealMethod();
        assertFalse(doc.isUnderRetentionOrLegalHold());
    }

    @Test
    public void retainedDocumentShouldBeRecordAndHaveValidRetainUntilDate() {
        DBSDocument doc = mock(DBSDocument.class);
        when(doc.isRecord()).thenReturn(true);
        Instant retainUntil = Instant.now().plusSeconds(1);
        when(doc.getRetainUntil()).thenReturn(retainUntil);

        when(doc.isUnderRetentionOrLegalHold()).thenCallRealMethod();

        assertTrue(doc.isUnderRetentionOrLegalHold());
    }

    @Test
    public void documentWouldNotBeRetainedOnceRetainUntilExpires() {
        DBSDocument doc = mock(DBSDocument.class);
        when(doc.isRecord()).thenReturn(true);
        Instant retainUntil = Instant.now();
        retainUntil = retainUntil.minusSeconds(1);
        when(doc.getRetainUntil()).thenReturn(retainUntil);

        when(doc.isUnderRetentionOrLegalHold()).thenCallRealMethod();

        assertFalse(doc.isUnderRetentionOrLegalHold());
    }

}
