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
package org.nuxeo.ecm.core.event.raiser;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentEventRaiserContextTest {

    @Test
    void constructorStoresAllFields() {
        CoreSession session = mock(CoreSession.class);
        NuxeoPrincipal principal = mock(NuxeoPrincipal.class);
        Map<String, Map<String, Serializable>> props = Map.of("doc-1", Map.of("k", "v"));
        Map<String, Map<String, Serializable>> blobs = Map.of("doc-2", Map.of("b", "w"));
        Set<String> dirtyPropIds = Set.of("doc-1");
        Map<String, Object> diff = Map.of("doc-1", "diffValue");
        Map<String, Object> original = Map.of("doc-1", "originalValue");
        Map<String, Object> current = Map.of("doc-1", "currentValue");

        DocumentEventRaiserContext context = new DocumentEventRaiserContext(session, principal, props, blobs,
                dirtyPropIds, diff, original, current, 99L);

        assertThat(context.getSession()).isSameAs(session);
        assertThat(context.getPrincipal()).isSameAs(principal);
        assertThat(context.getDirtyPropDocIds()).containsExactly("doc-1");
        assertThat(context.getDirtyBlobDocIds()).containsExactly("doc-2");
        assertThat(context.getContextForDocsWithDirtyProps()).containsEntry("doc-1", Map.of("k", "v"));
        assertThat(context.getContextForDocsWithDirtyBlobs()).containsEntry("doc-2", Map.of("b", "w"));
        assertThat(context.getDiff()).containsEntry("doc-1", "diffValue");
        assertThat(context.getOriginalDocument()).containsEntry("doc-1", "originalValue");
        assertThat(context.getCurrentDocument()).containsEntry("doc-1", "currentValue");
        assertThat(context.getTime()).isEqualTo(99L);
    }

    @Test
    void constructorCopiesProvidedProps() {
        Map<String, Map<String, Serializable>> props = new java.util.HashMap<>();
        props.put("doc-1", Map.of("k", "v"));

        DocumentEventRaiserContext context = new DocumentEventRaiserContext(null, null, props, null, Set.of(),
                null, null, null, 0L);
        props.put("mutated", Map.of("x", "y"));

        assertThat(context.getContextForDocsWithDirtyProps()).containsOnlyKeys("doc-1");
    }

    @Test
    void constructorCopiesProvidedBlobs() {
        Map<String, Map<String, Serializable>> blobs = new java.util.HashMap<>();
        blobs.put("doc-2", Map.of("b", "w"));

        DocumentEventRaiserContext context = new DocumentEventRaiserContext(null, null, null, blobs, Set.of(),
                null, null, null, 0L);
        blobs.put("mutated", Map.of("x", "y"));

        assertThat(context.getContextForDocsWithDirtyBlobs()).containsOnlyKeys("doc-2");
    }
}
