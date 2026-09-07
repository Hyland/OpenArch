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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEventRaiserComponentTest {

    private DocumentEventRaiserComponent component;

    /** Simple raiser that records the last context it received. */
    private static final class RecordingRaiser implements DocumentEventRaiser {

        private final AtomicReference<DocumentEventRaiserContext> lastContext = new AtomicReference<>();


        @Override
        public void raise(DocumentEventRaiserContext context) {
            lastContext.set(context);
        }
    }

    @BeforeEach
    void setUp() {
        component = new DocumentEventRaiserComponent();
    }

    private void setRaisers(List<DocumentEventRaiser> raisers) throws Exception {
        Field field = DocumentEventRaiserComponent.class.getDeclaredField("raisers");
        field.setAccessible(true);
        field.set(component, raisers);
    }

    private DocumentEventRaiserContext newContext() {
        return new DocumentEventRaiserContext(null, null, null, null, Set.of(), null, null, null, 0L);
    }

    @Test
    void raiseEventDelegatesToAllRaisers() throws Exception {
        RecordingRaiser updated = new RecordingRaiser();
        RecordingRaiser blob = new RecordingRaiser();
        setRaisers(List.of(updated, blob));

        DocumentEventRaiserContext context = newContext();
        component.raiseEvent(context);

        assertThat(updated.lastContext.get()).isSameAs(context);
        assertThat(blob.lastContext.get()).isSameAs(context);
    }
}
