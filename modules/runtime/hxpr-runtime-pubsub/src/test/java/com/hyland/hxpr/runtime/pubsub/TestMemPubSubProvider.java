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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("com.hyland.hxpr.runtime.pubsub")
public class TestMemPubSubProvider {

    @Inject
    protected PubSubService pubSubService;

    protected List<String> messages = new CopyOnWriteArrayList<>();

    protected volatile CountDownLatch messageReceivedLatch;

    @BeforeEach
    public void setUp() {
        messages.clear();
    }

    @Test
    public void testNoSubscriber() throws Exception { // NOSONAR (no need to assert anything)
        // nothing to receive it but we can still send something into the void
        pubSubService.publish("topicVoid", "something");
    }

    @Test
    public void testPublish() throws Exception {
        messageReceivedLatch = new CountDownLatch(1);
        BiConsumer<String, String> subscriber = this::subscriber;
        pubSubService.registerSubscriber("topicTest", subscriber);
        pubSubService.publish("topicTest", "foo");
        if (!messageReceivedLatch.await(5, TimeUnit.SECONDS)) {
            fail("message not received in 5s");
        }
        assertEquals(Collections.singletonList("topicTest=foo"), messages);

        // with subscriber unregistered it receives nothing anymore
        pubSubService.unregisterSubscriber("topicTest", subscriber);
        messages.clear();
        pubSubService.publish("topicTest", "bar");
        Thread.sleep(500);
        assertEquals(Collections.emptyList(), messages);
    }

    public void subscriber(String topic, String message) {
        messages.add(topic + "=" + message);
        messageReceivedLatch.countDown();
    }

}
