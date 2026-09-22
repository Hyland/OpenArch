/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.services.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.RuntimeMessage.Level;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class EventService extends DefaultComponent {

    public static final String NAME = "com.hyland.openarch.runtime.EventService";

    private static final Logger log = LogManager.getLogger(EventService.class);

    protected static final String XP = "listeners";

    protected Map<String, List<EventListener>> topics;

    /** map for listeners registered programmatically . */
    protected Map<String, List<EventListener>> programmaticTopics;

    @Override
    public void activate() {
        programmaticTopics = new ConcurrentHashMap<>();
    }

    @Override
    public void deactivate() {
        programmaticTopics = null;
    }

    @Override
    public void start() {
        topics = new HashMap<>();
        this.<ListenerDescriptor> getRegistryContributions(XP).forEach(desc -> {
            try {
                EventListener l = desc.getListener();
                for (String topic : desc.topics) {
                    topics.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(l);
                }
            } catch (ReflectiveOperationException e) {
                addRuntimeMessage(Level.ERROR, e.getMessage());
                log.error(e, e);
            }

        });
    }

    @Override
    public void stop() throws InterruptedException {
        topics = null;
    }

    public void sendEvent(Event event) {
        String topic = event.getTopic();
        List<EventListener> list = topics == null ? null : topics.get(topic);
        List<EventListener> plist = programmaticTopics.get(topic);
        if (list == null && plist == null) {
            if (log.isTraceEnabled()) {
                log.trace("No listener for event topic {}", topic);
            }
        } else {
            sendEvent(list, event);
            sendEvent(plist, event);
        }
    }

    public void addListener(String topic, EventListener listener) {
        programmaticTopics.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeListener(String topic, EventListener listener) {
        List<EventListener> list = programmaticTopics.get(topic);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                programmaticTopics.remove(topic);
            }
        }
    }

    private static void sendEvent(List<EventListener> list, Event event) {
        if (list == null) {
            return;
        }
        list.forEach(listener -> listener.handleEvent(event));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Class<T> adapter) {
        return adapter == getClass() ? (T) this : null;
    }

}
