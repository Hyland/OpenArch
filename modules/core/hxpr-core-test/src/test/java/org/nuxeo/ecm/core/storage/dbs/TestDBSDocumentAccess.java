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
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_ORDERED_FOLDER;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.SessionImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestDBSDocumentAccess {

    @Inject
    protected CoreSession coreSession;

    private InternalSession session() {
        return ((SessionImpl) coreSession).getSession();
    }

    private DBSDocument createChild(InternalDocument parent, String name, String type) {
        DBSDocument doc = DBSDocument.newUnCreatedDocument(parent, name, type, session());
        doc.create();
        return doc;
    }

    @Test
    public void getChildrenIdsThrowsWhenDocumentNotYetCreated() {
        InternalDocument root = session().getRootDocument();
        DBSDocument uncreated = DBSDocument.newUnCreatedDocument(root, "uncreated", SYS_FILE, session());

        assertThrows(NuxeoException.class, uncreated::getChildrenIds);
    }

    @Test
    public void getChildrenIdsReturnsChildrenOfRegularFolder() {
        DBSDocument folder = createChild(session().getRootDocument(), "folder", SYS_FOLDER);
        DBSDocument child1 = createChild(folder, "child1", SYS_FILE);
        DBSDocument child2 = createChild(folder, "child2", SYS_FILE);

        List<String> ids = folder.getChildrenIds();

        assertEquals(Set.of(child1.getId(), child2.getId()), new HashSet<>(ids));
    }

    @Test
    public void getChildrenIdsPreservesCreationOrderForOrderedFolder() {
        DBSDocument orderedFolder = createChild(session().getRootDocument(), "ordered", SYS_ORDERED_FOLDER);
        DBSDocument first = createChild(orderedFolder, "first", SYS_FILE);
        DBSDocument second = createChild(orderedFolder, "second", SYS_FILE);

        List<String> ids = orderedFolder.getChildrenIds();

        assertEquals(List.of(first.getId(), second.getId()), ids);
    }

    @Test
    public void documentModelExposesTypeSchemas() {
        DocumentModel doc = coreSession.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();

        Set<String> schemas = doc.getSchemas();

        assertFalse(schemas.isEmpty());
        String knownSchema = schemas.iterator().next();
        assertTrue(doc.hasSchema(knownSchema));
        assertFalse(doc.hasSchema("no_such_schema_xyz"));
    }

}
