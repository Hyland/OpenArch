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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentEventRaiserDescriptorTest {

    @Test
    void setClassInstantiatesRaiser() throws Exception {
        DocumentEventRaiserDescriptor descriptor = new DocumentEventRaiserDescriptor();
        descriptor.name = "DOCUMENT_UPDATED";

        descriptor.setClass(TestEventRaiser.class);

        assertThat(descriptor.getName()).isEqualTo("DOCUMENT_UPDATED");
        assertThat(descriptor.getRaiser()).isInstanceOf(TestEventRaiser.class);
    }

    /** A raiser with an accessible no-arg constructor to exercise the success path. */
    public static class TestEventRaiser implements DocumentEventRaiser {

        @Override
        public void raise(DocumentEventRaiserContext context) {
            // no-op
        }
    }

    /** A raiser without an accessible no-arg constructor to exercise the failure path. */
    public static class NoDefaultCtorRaiser implements DocumentEventRaiser {

        private final String required;

        public NoDefaultCtorRaiser(String required) {
            // no-arg constructor intentionally missing
            this.required = required;
        }

        @Override
        public void raise(DocumentEventRaiserContext context) {
            // no-op
        }
    }

    @Test
    void setClassThrowsWhenNoDefaultConstructor() {
        DocumentEventRaiserDescriptor descriptor = new DocumentEventRaiserDescriptor();

        assertThatThrownBy(() -> descriptor.setClass(NoDefaultCtorRaiser.class))
                .isInstanceOf(ReflectiveOperationException.class);
    }
}
