/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.event.test;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventContext;
import com.hyland.openarch.ecm.core.event.EventListener;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.EventListenerDescriptor;
import com.hyland.openarch.runtime.api.Framework;

/**
 * {@link EventListener} to use in tests in order to programmatically add this listener to event bus and catch desired
 * events.
 * <p>
 * Usage leverages try-with-resources java feature:
 *
 * <pre>
 * {@code
 *     try (CapturingEventListener listener = new CapturingEventListener("event")) {
 *         Framework.getService(EventService.class).fireEvent(event);
 *         assertTrue(listener.hasBeenFired("event");
 *     }
 * }
 * </pre>
 *
 * Listener will be added to event bus during its instantiation and then it will be removed with help of
 * {@link Closeable#close()}.
 *
 * @since 10.3
 */
public class CapturingEventListener extends EventListenerDescriptor implements EventListener, Closeable {

    private static final String DEFAULT_NAME = "EVENT_LISTENER_FOR_TEST";

    protected List<Event> results = Collections.synchronizedList(new ArrayList<>());

    /**
     * @param events The events to catch, leave empty to catch them all
     */
    public CapturingEventListener(String... events) {
        this.name = DEFAULT_NAME;
        this.className = getClass().getName();
        this.events = events.length == 0 ? null : new HashSet<>(Arrays.asList(events)); // null to meet acceptEvent
        // add listener to event bus
        Framework.getService(EventService.class).addEventListener(this);
    }

    public List<Event> getCapturedEvents() {
        return Collections.unmodifiableList(results);
    }

    /**
     * @since 11.1
     */
    public Stream<Event> streamCapturedEvents(String event) {
        return results.stream().filter(e -> e.getName().equals(event));
    }

    /**
     * @since 11.1
     */
    public Optional<Event> findLastCapturedEvent(String event) {
        List<Event> list = streamCapturedEvents(event).toList();
        return findLast(list);
    }

    /**
     * @since 11.1
     */
    public Optional<EventContext> findLastCapturedEventContext(String event) {
        return findLastCapturedEvent(event).map(Event::getContext);
    }

    /**
     * @since 11.1
     */
    public EventContext findLastCapturedEventContextOrElseThrow(String event) {
        return findLastCapturedEventContext(event).orElseThrow(
                () -> new AssertionError("Unable to find last EventContext for event: " + event));
    }

    protected <C> Optional<C> findLast(List<C> list) {
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(list.get(list.size() - 1));
        }
    }

    public long getCapturedEventCount(String event) {
        return streamCapturedEvents(event).count();
    }

    public void clear() {
        results.clear();
    }

    // EventListenerDescriptor part

    /**
     * Override init cause we already have a listener
     */
    @Override
    public void initListener(Context context) {
        // nothing
    }

    @Override
    public EventListener asEventListener() {
        return this;
    }

    // EventListener part

    @Override
    public void handleEvent(Event event) {
        if (events == null || events.contains(event.getName())) {
            results.add(event);
        }
    }

    // Closeable part

    @Override
    public void close() {
        Framework.getService(EventService.class).removeEventListener(this);
    }
}
