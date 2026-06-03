/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_FOLDERISH;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_FULL_TEXT_INDEXABLE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_ORDERABLE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_FILISH;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.CompositeTypeImpl;

public class TestDocumentType {

    private static CompositeType mixin(String name) {
        return new CompositeTypeImpl(null, name, null, null);
    }

    @Test
    public void testTrivialDocumentType() {
        DocumentType docType = new DocumentTypeImpl("doc type");

        assertEquals("doc type", docType.getName());

        assertFalse(docType.isFile());
        assertFalse(docType.isFolder());
        assertFalse(docType.isOrdered());
    }

    @Test
    public void testFileDocumentType() {
        DocumentType docType = new DocumentTypeImpl("doc type", null, null, List.of(mixin(SYS_FILISH)), null);

        assertTrue(docType.isFile());
        assertFalse(docType.isFolder());
        assertFalse(docType.isOrdered());
    }

    @Test
    public void testFolderDocumentType() {
        DocumentType docType = new DocumentTypeImpl("doc type", null, null, List.of(mixin(SYS_FOLDERISH)), null);

        assertFalse(docType.isFile());
        assertTrue(docType.isFolder());
        assertFalse(docType.isOrdered());
    }

    @Test
    public void testOrderedFolderDocumentType() {
        DocumentType docType = new DocumentTypeImpl("doc type", null, null,
                List.of(mixin(SYS_FOLDERISH), mixin(SYS_ORDERABLE)), null);

        assertFalse(docType.isFile());
        assertTrue(docType.isFolder());
        assertTrue(docType.isOrdered());
    }

    @Test
    public void testFolderDocumentTypeForSpecificProject() {
        DocumentType docType = new DocumentTypeImpl("doc type", null, null, List.of(mixin(SYS_FOLDERISH)), "P123");

        assertFalse(docType.isFile());
        assertTrue(docType.isFolder());
        assertFalse(docType.isOrdered());
        assertEquals("P123", docType.getProjectId());
    }

    @Test
    public void testOrderedFolderDocumentTypeForSpecificProject() {
        DocumentType docType = new DocumentTypeImpl("doc type", null, null,
                List.of(mixin(SYS_FOLDERISH), mixin(SYS_ORDERABLE)), "P234");

        assertFalse(docType.isFile());
        assertTrue(docType.isFolder());
        assertTrue(docType.isOrdered());
        assertEquals("P234", docType.getProjectId());
    }

    @Test
    public void testFullTextIndexableDocumentType() {
        DocumentType docType = new DocumentTypeImpl("doc type", null, null,
                List.of(mixin(SYS_FOLDERISH), mixin(SYS_ORDERABLE), mixin(SYS_FULL_TEXT_INDEXABLE)), "P234");
        assertTrue(docType.isFullTextIndexable());
    }
}
