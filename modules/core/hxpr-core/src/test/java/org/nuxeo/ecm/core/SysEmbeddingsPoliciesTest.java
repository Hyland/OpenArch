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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.schema.DocumentType;

class SysEmbeddingsPoliciesTest {

    private static final String SYS_EMBEDDINGS = "SysEmbeddings";

    private static final String SYS_HAS_EMBEDDINGS = "SysHasEmbeddings";

    private static InternalDocument mockDoc(String typeName) {
        InternalDocument doc = mock(InternalDocument.class);
        DocumentType type = mock(DocumentType.class);
        when(type.getName()).thenReturn(typeName);
        when(doc.getType()).thenReturn(type);
        return doc;
    }

    @Test
    void shouldAddPrefixWhenMissing() {
        assertEquals("_e_foo", SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("foo"));
    }

    @Test
    void shouldKeepNameWhenPrefixAlreadyPresent() {
        assertEquals("_e_foo", SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("_e_foo"));
    }

    @Test
    void shouldExtractTypeFromPrefixedName() {
        assertEquals("Section", SysEmbeddingsPolicies.getEmbeddingsTypeFromName("_e_Section"));
    }

    @Test
    void shouldRejectTypeExtractionFromUnprefixedName() {
        assertThrows(NuxeoException.class, () -> SysEmbeddingsPolicies.getEmbeddingsTypeFromName("Section"));
    }

    @Test
    void handleNamePolicyShouldIgnoreNonEmbeddingsDocument() {
        InternalDocument doc = mockDoc("File");

        assertEquals("anyName", SysEmbeddingsPolicies.handleEmbeddingsNamePolicy(doc, "anyName", true));
    }

    @Test
    void handleNamePolicyShouldReturnValidNameWhenEnforcing() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);

        assertEquals("_e_x", SysEmbeddingsPolicies.handleEmbeddingsNamePolicy(doc, "_e_x", true));
    }

    @Test
    void handleNamePolicyShouldRejectInvalidNameWhenEnforcing() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);

        assertThrows(DocumentValidationException.class,
                () -> SysEmbeddingsPolicies.handleEmbeddingsNamePolicy(doc, "x", true));
    }

    @Test
    void handleNamePolicyShouldAutoPrefixWhenNotEnforcing() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);

        assertEquals("_e_x", SysEmbeddingsPolicies.handleEmbeddingsNamePolicy(doc, "x", false));
    }

    @Test
    void enforceNameShouldIgnoreNonEmbeddingsDocument() {
        InternalDocument doc = mockDoc("File");

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceEmbeddingsNameOrThrow(doc, "x"));
    }

    @Test
    void enforceNameShouldAcceptValidName() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceEmbeddingsNameOrThrow(doc, "_e_x"));
    }

    @Test
    void enforceNameShouldRejectInvalidName() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);

        assertThrows(DocumentValidationException.class,
                () -> SysEmbeddingsPolicies.enforceEmbeddingsNameOrThrow(doc, "x"));
    }

    @Test
    void enforceParentMixinShouldIgnoreNonEmbeddingsDocument() {
        InternalDocument doc = mockDoc("File");

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceEmbeddingsParentMixinOrThrow(doc));
    }

    @Test
    void enforceParentMixinShouldAcceptParentWithMixin() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);
        InternalDocument parent = mock(InternalDocument.class);
        when(parent.hasMixin(SYS_HAS_EMBEDDINGS)).thenReturn(true);
        when(doc.getParent()).thenReturn(parent);

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceEmbeddingsParentMixinOrThrow(doc));
    }

    @Test
    void enforceParentMixinShouldRejectParentWithoutMixin() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);
        InternalDocument parent = mock(InternalDocument.class);
        when(parent.hasMixin(SYS_HAS_EMBEDDINGS)).thenReturn(false);
        when(doc.getParent()).thenReturn(parent);

        assertThrows(DocumentValidationException.class,
                () -> SysEmbeddingsPolicies.enforceEmbeddingsParentMixinOrThrow(doc));
    }

    @Test
    void enforceParentMixinShouldRejectMissingParent() {
        InternalDocument doc = mockDoc(SYS_EMBEDDINGS);
        when(doc.getParent()).thenReturn(null);

        assertThrows(DocumentValidationException.class,
                () -> SysEmbeddingsPolicies.enforceEmbeddingsParentMixinOrThrow(doc));
    }

    @Test
    void mixinUpdateShouldAllowKeepingMixin() {
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.hasMixin(SYS_HAS_EMBEDDINGS)).thenReturn(true);

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceHasEmbeddingsMixinUpdateOrThrow(doc,
                List.of(SYS_HAS_EMBEDDINGS)));
    }

    @Test
    void mixinUpdateShouldAllowWhenMixinWasNotPresent() {
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.hasMixin(SYS_HAS_EMBEDDINGS)).thenReturn(false);

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceHasEmbeddingsMixinUpdateOrThrow(doc, List.of()));
    }

    @Test
    void mixinUpdateShouldAllowRemovalWhenNoEmbeddingsChildren() {
        InternalDocument child = mockDoc("File");
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.hasMixin(SYS_HAS_EMBEDDINGS)).thenReturn(true);
        when(doc.getChildren()).thenReturn(List.of(child));

        assertDoesNotThrow(() -> SysEmbeddingsPolicies.enforceHasEmbeddingsMixinUpdateOrThrow(doc, List.of()));
    }

    @Test
    void mixinUpdateShouldRejectRemovalWhenEmbeddingsChildrenExist() {
        InternalDocument child = mockDoc(SYS_EMBEDDINGS);
        InternalDocument doc = mock(InternalDocument.class);
        when(doc.hasMixin(SYS_HAS_EMBEDDINGS)).thenReturn(true);
        when(doc.getChildren()).thenReturn(List.of(child));

        assertThrows(NuxeoException.class,
                () -> SysEmbeddingsPolicies.enforceHasEmbeddingsMixinUpdateOrThrow(doc, List.of()));
    }
}
