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
package org.nuxeo.ecm.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.hyland.hxpr.runtime.pubsub.SerializableAccumulableInvalidations;

class InvalidationsQueueTest {

    private static class TestQueue extends InvalidationsQueue<SerializableAccumulableInvalidations> {

        TestQueue(String name) {
            super(name);
        }

        @Override
        public SerializableAccumulableInvalidations newInvalidations() {
            return mock(SerializableAccumulableInvalidations.class);
        }
    }

    @Test
    void shouldInitializeWithFreshQueueAndName() {
        TestQueue queue = new TestQueue("myqueue");

        assertNotNull(queue.queue);
        assertEquals("myqueue", queue.name);
    }

    @Test
    void shouldAccumulateInvalidationsInCurrentQueue() {
        TestQueue queue = new TestQueue("myqueue");
        SerializableAccumulableInvalidations current = queue.queue;
        SerializableAccumulableInvalidations added = mock(SerializableAccumulableInvalidations.class);

        queue.addInvalidations(added);

        verify(current).add(added);
    }

    @Test
    void shouldReturnAndResetQueueOnGet() {
        TestQueue queue = new TestQueue("myqueue");
        SerializableAccumulableInvalidations current = queue.queue;

        SerializableAccumulableInvalidations returned = queue.getInvalidations();

        assertSame(current, returned);
        assertNotSame(current, queue.queue);
        assertNotNull(queue.queue);
    }

    @Test
    void shouldExposeNameInToString() {
        assertEquals("TestQueue(myqueue)", new TestQueue("myqueue").toString());
    }
}
