/*
 * (C) Copyright 2023 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
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
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-dbsdocument.xml")
public class TestDBSDocument {
    @Inject
    private TypeService typeService;

    private SchemaManager schemaManager;

    private DBSSession dbsSession;

    @Mock
    @RuntimeService
    private SecurityService securityService;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
        dbsSession = mock(DBSSession.class);
        when(dbsSession.getSchemaManager()).thenReturn(schemaManager);
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
