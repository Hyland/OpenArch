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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.hyland.hxpr.runtime.pubsub.SerializableAccumulableInvalidations;

class InvalidationsPropagatorTest {

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
    void shouldAddQueueOnlyOnce() {
        InvalidationsPropagator<SerializableAccumulableInvalidations> propagator = new InvalidationsPropagator<>();
        TestQueue queue = new TestQueue("q1");

        propagator.addQueue(queue);
        propagator.addQueue(queue);

        assertEquals(1, propagator.queues.size());
    }

    @Test
    void shouldRemoveQueue() {
        InvalidationsPropagator<SerializableAccumulableInvalidations> propagator = new InvalidationsPropagator<>();
        TestQueue queue = new TestQueue("q1");
        propagator.addQueue(queue);

        propagator.removeQueue(queue);

        assertEquals(0, propagator.queues.size());
        assertFalse(propagator.queues.contains(queue));
    }

    @Test
    void shouldPropagateToAllQueuesExceptSkipped() {
        InvalidationsPropagator<SerializableAccumulableInvalidations> propagator = new InvalidationsPropagator<>();
        TestQueue target = spy(new TestQueue("target"));
        TestQueue skipped = spy(new TestQueue("skipped"));
        propagator.addQueue(target);
        propagator.addQueue(skipped);

        SerializableAccumulableInvalidations invalidations = mock(SerializableAccumulableInvalidations.class);
        propagator.propagateInvalidations(invalidations, skipped);

        verify(target).addInvalidations(invalidations);
        verify(skipped, never()).addInvalidations(invalidations);
    }
}
