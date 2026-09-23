/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.pubsub;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Abstract implementation of {@link PubSubProvider}.
 * <p>
 * This deals with subscribers registration and dispatch.
 *
 * @since 9.1
 */
public abstract class AbstractPubSubProvider implements PubSubProvider {

    private final Logger log = LogManager.getLogger(AbstractPubSubProvider.class);

    protected String namespace;

    /** List of subscribers for each topic. */
    protected Map<String, List<BiConsumer<String, String>>> subscribers;

    @Override
    public void initialize(Map<String, List<BiConsumer<String, String>>> subscribers) {
        this.subscribers = subscribers;
    }

    @Override
    public void close() {
        // DO NOT subscribers.clear(), we do not own this map
    }

    @Override
    public void dispatchToSubscribers(String topic, String message) {
        if (subscribers == null) {
            // not yet initialized
            return;
        }
        List<BiConsumer<String, String>> subs = subscribers.get(topic);
        if (subs != null) {
            for (BiConsumer<String, String> subscriber : subs) {
                try {
                    subscriber.accept(topic, message);
                } catch (RuntimeException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw e;
                    }
                    // don't break everything if a subscriber is ill-behaved
                    log.error("Exception in subscriber for topic: {}", topic, e);
                }
            }
        }
    }

}
