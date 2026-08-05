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
package com.hyland.hxpr.runtime.pubsub;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * In-Memory implementation of {@link PubSubProvider}.
 *
 * @since 9.1
 */
public class MemPubSubProvider extends AbstractPubSubProvider {

    private final Logger log = LogManager.getLogger(MemPubSubProvider.class);

    @Override
    public void initialize(Map<String, List<BiConsumer<String, String>>> subscribers) {
        super.initialize(subscribers);
        log.debug("Initialized");
    }

    @Override
    public void close() {
        log.debug("Closed");
    }

    @Override
    public void publish(String topic, String message) {
        dispatchToSubscribers(topic, message);
    }

}
