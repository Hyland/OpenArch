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
package org.nuxeo.ecm.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.common.utils.Path;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.model.DocumentModelResolver.MODE;
import org.nuxeo.ecm.core.model.DocumentModelResolver.REF;

class DocumentModelResolverTest {

    private static DocumentModelResolver configured(String store) {
        DocumentModelResolver resolver = new DocumentModelResolver();
        Map<String, String> params = new HashMap<>();
        params.put("name", "documentResolver");
        if (store != null) {
            params.put(DocumentModelResolver.PARAM_STORE, store);
        }
        resolver.configure(params);
        return resolver;
    }

    @Test
    void shouldDefaultToRepoAndIdMode() {
        DocumentModelResolver resolver = configured(null);

        assertEquals(MODE.REPO_AND_ID_REF, resolver.getMode());
        assertEquals(DocumentModelResolver.STORE_REPO_AND_ID, resolver.getParameters().get("store"));
    }

    @Test
    void shouldConfigureAllStoreModes() {
        assertEquals(MODE.PATH_ONLY_REF, configured(DocumentModelResolver.STORE_PATH_ONLY).getMode());
        assertEquals(MODE.ID_ONLY_REF, configured(DocumentModelResolver.STORE_ID_ONLY).getMode());
        assertEquals(MODE.REPO_AND_PATH_REF, configured(DocumentModelResolver.STORE_REPO_AND_PATH).getMode());
        assertEquals(MODE.REPO_AND_ID_REF, configured(DocumentModelResolver.STORE_REPO_AND_ID).getMode());
    }

    @Test
    void shouldFallBackToDefaultForUnknownStore() {
        assertEquals(MODE.REPO_AND_ID_REF, configured("garbage").getMode());
    }

    @Test
    void shouldRejectReconfiguration() {
        DocumentModelResolver resolver = configured("id");

        assertThrows(IllegalStateException.class, () -> resolver.configure(new HashMap<>()));
    }

    @Test
    void shouldManageDocumentModelClassAndCacheResult() {
        DocumentModelResolver resolver = configured("id");

        List<Class<?>> managed = resolver.getManagedClasses();
        assertEquals(List.of(DocumentModel.class), managed);
        // the list is computed lazily and cached
        assertSame(managed, resolver.getManagedClasses());
    }

    @Test
    void shouldBuildReferenceForIdOnlyMode() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("123");

        assertEquals("123", configured(DocumentModelResolver.STORE_ID_ONLY).getReference(doc));
    }

    @Test
    void shouldBuildReferenceForPathOnlyMode() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPath()).thenReturn(new Path("/a/b"));

        assertEquals("/a/b", configured(DocumentModelResolver.STORE_PATH_ONLY).getReference(doc));
    }

    @Test
    void shouldBuildReferenceForRepoAndIdMode() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getRepositoryName()).thenReturn("default");
        when(doc.getId()).thenReturn("123");

        assertEquals("default:123", configured(DocumentModelResolver.STORE_REPO_AND_ID).getReference(doc));
    }

    @Test
    void shouldBuildReferenceForRepoAndPathMode() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getRepositoryName()).thenReturn("default");
        when(doc.getPath()).thenReturn(new Path("/a/b"));

        assertEquals("default:/a/b", configured(DocumentModelResolver.STORE_REPO_AND_PATH).getReference(doc));
    }

    @Test
    void shouldReturnNullReferenceForNonDocument() {
        assertNull(configured("id").getReference("not a document"));
    }

    @Test
    void shouldProvideConstraintErrorMessageForEachMode() {
        assertNotNull(configured(DocumentModelResolver.STORE_ID_ONLY).getConstraintErrorMessage("bad", Locale.ENGLISH));
        assertNotNull(
                configured(DocumentModelResolver.STORE_PATH_ONLY).getConstraintErrorMessage("bad", Locale.ENGLISH));
    }

    @Test
    void shouldNotValidateNonStringValues() {
        assertFalse(configured("id").validate(123));
    }

    @Test
    void shouldReturnNullWhenFetchingNonStringValues() {
        assertNull(configured("id").fetch(123));
    }

    @Test
    void shouldSkipValidationWhenDisabled() {
        DocumentModelResolver resolver = new DocumentModelResolver();
        Map<String, String> params = new HashMap<>();
        params.put("name", "documentResolver");
        params.put("validation", "false");
        resolver.configure(params);

        assertTrue(resolver.validate("anything"));
    }

    @Test
    void refShouldParseValueWithoutRepository() {
        REF ref = REF.fromValue("abc");

        assertNotNull(ref);
        assertNull(ref.repo);
        assertEquals("abc", ref.ref);
    }

    @Test
    void refShouldParseValueWithRepository() {
        REF ref = REF.fromValue("default:abc");

        assertNotNull(ref);
        assertEquals("default", ref.repo);
        assertEquals("abc", ref.ref);
    }

    @Test
    void refShouldRejectMalformedValue() {
        assertNull(REF.fromValue("a:b:c"));
    }
}
