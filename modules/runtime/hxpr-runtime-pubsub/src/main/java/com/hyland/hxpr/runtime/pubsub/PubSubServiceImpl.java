/*
 * (C) Copyright 2017-2018 Nuxeo (http://nuxeo.com/) and others.
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

import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.DefaultComponent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Implementation for the Publish/Subscribe Service.
 *
 * @since 9.1
 */
public class PubSubServiceImpl extends DefaultComponent implements PubSubService {

    public static final String XP_CONFIG = "configuration";

    /** The currently-configured provider. */
    protected PubSubProvider provider;

    /** List of subscribers for each topic. */
    protected Map<String, List<BiConsumer<String, String>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void deactivate() {
        subscribers.clear();
        provider.close();
        provider = null;
    }

    @Override
    public void start() {
        if (provider != null) {
            provider.close();
        }
        List<PubSubProviderDescriptor> descs = getRegistryContributions(XP_CONFIG);
        PubSubProviderDescriptor providerDescriptor = descs.isEmpty() ? null : descs.get(descs.size() - 1);
        if (providerDescriptor == null) {
            provider = new MemPubSubProvider(); // default implementation
        } else {
            try {
                Class<?> klass = Class.forName(providerDescriptor.klass);
                // dynamic class check, the generics aren't enough
                if (!PubSubProvider.class.isAssignableFrom(klass)) {
                    throw new RuntimeServiceException("Class does not implement PubSubProvider: " + klass.getName());
                }
                provider = (PubSubProvider) klass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeServiceException(e);
            }
        }
        provider.initialize(subscribers);
    }

    @Override
    public void stop() throws InterruptedException {
        if (provider == null) {
            return;
        }
        provider.close();
    }

    @Override
    public int getApplicationStartedOrder() {
        return -500 + 10;
    }

    // ===== delegation to actual implementation =====

    @Override
    public void publish(String topic, String message) {
        provider.publish(topic, message);
    }

    @Override
    public void registerSubscriber(String topic, BiConsumer<String, String> subscriber) {
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    @Override
    public void unregisterSubscriber(String topic, BiConsumer<String, String> subscriber) {
        // use computeIfAbsent also for removal to avoid thread-safety issues
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).remove(subscriber);
    }

    @Override
    public PubSubProvider getProvider() {
        return provider;
    }
}
