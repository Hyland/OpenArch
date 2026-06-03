/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package com.hyland.hxpr.runtime.pubsub;

import static com.hyland.hxpr.runtime.pubsub.AbstractPubSubBroker.DISCRIMINATOR_SEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.nuxeo.common.utils.TooLargeException;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import java.io.IOException;
import java.io.Writer;

@ExtendWith(FeaturesExtension.class)
@Features({RuntimeFeature.class, MockitoFeature.class})
public class TestAbstractPubSubInvalidator {

    @Mock
    @RuntimeService
    protected PubSubService pubSubService;

    @Test
    public void testDummyInvalidations() throws IOException {
        DummyInvalidations invals = new DummyInvalidations();
        DummyInvalidations invals2 = new DummyInvalidations();

        assertTrue(invals.isEmpty());
        assertTrue(roundTrip(invals).isEmpty());
        invals2.add(invals);
        assertTrue(invals2.isEmpty());

        invals.inval();

        assertFalse(invals.isEmpty());
        assertFalse(roundTrip(invals).isEmpty());
        invals2.add(invals);
        assertFalse(invals2.isEmpty());
    }

    @SuppressWarnings("resource") // StringBuilderWriter doesn't need to be closed
    protected DummyInvalidations roundTrip(DummyInvalidations invalidations) throws IOException {
        DummyInvalidator invalidator = new DummyInvalidator();
        StringBuilderWriter writer = new StringBuilderWriter();
        invalidations.serialize(writer);
        return invalidator.deserialize(writer.toString());
    }

    @Test
    public void testScanDiscriminator() {
        DummyInvalidator invalidator = new DummyInvalidator();
        invalidator.initialize("topic", "di");

        assertEquals(-1, invalidator.scanDiscriminator(null));
        assertEquals(-1, invalidator.scanDiscriminator(""));
        assertEquals(-1, invalidator.scanDiscriminator("d"));
        assertEquals(-1, invalidator.scanDiscriminator("di"));
        assertEquals(-1, invalidator.scanDiscriminator("dis"));
        assertEquals(-1, invalidator.scanDiscriminator("x"));
        assertEquals(-1, invalidator.scanDiscriminator("xy"));
        assertEquals(-1, invalidator.scanDiscriminator("xyz"));

        assertEquals(1, invalidator.scanDiscriminator(":foo"));
        assertEquals(1, invalidator.scanDiscriminator(":"));
        assertEquals(2, invalidator.scanDiscriminator("x:foo"));
        assertEquals(2, invalidator.scanDiscriminator("x:"));
        assertEquals(3, invalidator.scanDiscriminator("xy:foo"));
        assertEquals(3, invalidator.scanDiscriminator("xy:"));
        assertEquals(4, invalidator.scanDiscriminator("xyz:foo"));
        assertEquals(4, invalidator.scanDiscriminator("xyz:"));

        assertEquals(2, invalidator.scanDiscriminator("d:foo"));
        assertEquals(2, invalidator.scanDiscriminator("d:"));
        assertEquals(-1, invalidator.scanDiscriminator("di:foo"));
        assertEquals(-1, invalidator.scanDiscriminator("di:"));
        assertEquals(4, invalidator.scanDiscriminator("dis:foo"));
        assertEquals(4, invalidator.scanDiscriminator("dis:"));
    }

    @Test
    public void invalidDiscriminatorThrowsExceptionAtInit() {
        DummyInvalidator invalidator = new DummyInvalidator();
        var exception = assertThrows(IllegalArgumentException.class, () -> invalidator.initialize("topic", "invalid" + DISCRIMINATOR_SEP));
        assertTrue(exception.getMessage().startsWith("Invalid discriminator, must not contain separator '" + DISCRIMINATOR_SEP + "': "));
        exception = assertThrows(IllegalArgumentException.class, () -> invalidator.initialize("topic", DISCRIMINATOR_SEP + "invalid"));
        assertTrue(exception.getMessage().startsWith("Invalid discriminator, must not contain separator '" + DISCRIMINATOR_SEP + "': "));
        exception = assertThrows(IllegalArgumentException.class, () -> invalidator.initialize("topic", "invalid" + DISCRIMINATOR_SEP + "invalid"));
        assertTrue(exception.getMessage().startsWith("Invalid discriminator, must not contain separator '" + DISCRIMINATOR_SEP + "': "));
    }

    @Test
    public void testSubscriberDiscriminatorComparison() {
        DummyInvalidator invalidator = new DummyInvalidator();
        invalidator.initialize("topic", "d");
        DummyInvalidations invals;

        // use different discriminator
        invalidator.subscriber("topic", "z:Y");
        invals = invalidator.receiveInvalidations();
        assertFalse(invals.isEmpty());

        // use same discriminator
        invalidator.subscriber("topic", "d:Y");
        invals = invalidator.receiveInvalidations();
        assertTrue(invals.isEmpty());
    }

    @Test
    public void testSentInvalidationsMinimizedIfTooLarge() {
        DummyInvalidator invalidator = new DummyInvalidator();
        invalidator.initialize("topic", "d");
        DummyInvalidations invals = new DummyInvalidations();
        invals.inval();

        // ask to throw TooLargeException if there's an inval
        doThrow(TooLargeException.class).when(pubSubService).publish("topic", "d:Y");

        invalidator.sendInvalidations(invals);

        verify(pubSubService).registerSubscriber(eq("topic"), any()); // done by initialize
        verify(pubSubService).publish("topic", "d:Y"); // first call, that we asked to throw above
        verify(pubSubService).publish("topic", "d:"); // second call has minimized the invalidations size
        verifyNoMoreInteractions(pubSubService);
    }

    public static class DummyInvalidations implements SerializableAccumulableInvalidations {

        protected boolean inval;

        public static DummyInvalidations deserialize(String message) {
            DummyInvalidations invals = new DummyInvalidations();
            if (message.equals("Y")) {
                invals.inval();
            }
            return invals;
        }

        public void inval() {
            inval = true;
        }

        @Override
        public void add(SerializableAccumulableInvalidations o) {
            DummyInvalidations other = (DummyInvalidations) o;
            inval = inval || other.inval;
        }

        @Override
        public SerializableAccumulableInvalidations minimize() {
            return new DummyInvalidations();
        }

        @Override
        public boolean isEmpty() {
            return !inval;
        }

        @Override
        public void serialize(Writer writer) throws IOException {
            if (inval) {
                writer.write("Y");
            }
        }
    }

    public static class DummyInvalidator extends AbstractPubSubInvalidationsAccumulator<DummyInvalidations> {

        @Override
        public DummyInvalidations newInvalidations() {
            return new DummyInvalidations();
        }

        @Override
        public DummyInvalidations deserialize(String message) throws IOException {
            return DummyInvalidations.deserialize(message);
        }
    }

}
