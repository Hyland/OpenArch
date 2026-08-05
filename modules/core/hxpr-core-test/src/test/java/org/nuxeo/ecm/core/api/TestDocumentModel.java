/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestDocumentModel {

    @Inject
    protected CoreSession session;

    /**
     * Tests on a DocumentModel that hasn't been created in the session yet.
     */
    @Test
    public void testDocumentModelNotYetCreated() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        assertTrue(doc.isCheckedOut());
        assertEquals(Long.valueOf(0), doc.getVersion()); // null for DocumentModelImpl
    }

    @Test
    public void testContextDataOfCreatedDocument() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.putContextData("key", "value");
        doc.create();
        assertEquals("value", doc.getContextData("key"));
    }

    /**
     * NXP-21866
     */
    @Test
    public void testUIDAndPathOfCreatedDocument() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        assertNotNull(doc.getId());
        assertEquals("/doc", doc.getPathAsString());
    }

    @Test
    public void forbidSlashOnCreate() {
        assertThrows(IllegalArgumentException.class, () -> session.createDocumentModel("/", "doc/doc", SYS_FILE));
    }

    @Test
    public void forbidSlashOnMove() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        assertThrows(IllegalArgumentException.class, () -> session.move(doc.getRef(), new PathRef("/"), "toto/tata"));
    }

    @Test
    public void forbidSlashOnCopy() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        assertThrows(IllegalArgumentException.class, () -> session.copy(doc.getRef(), new PathRef("/"), "toto/tata"));
    }

}
