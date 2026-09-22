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
package com.hyland.openarch.runtime.trackers.concurrent;

import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.services.event.Event;
import com.hyland.openarch.runtime.services.event.EventService;
import com.hyland.openarch.runtime.trackers.files.FileEventTracker;

/**
 * Runtime events that be fired once a thread is about to enter in the OpenArch runtime or leave it. Could be used for
 * cleaning resource such as the {@link FileEventTracker}. Producers should use the static
 * {@link ThreadEvent#onEnter(Object, boolean)} and {@link ThreadEvent#onLeave(Object)} factory methods and fire events
 * by invoking the event's {@link ThreadEvent#send()} method. Consumers should implements the {@link ThreadEventHandler}
 * interface and register it in the {@link EventService} using the {@link ThreadEventListener} wrapper.
 *
 * @since 6.0
 * @author Stephane Lacoin at Nuxeo (aka matic)
 */
public abstract class ThreadEvent extends Event {

    protected ThreadEvent(Class<? extends ThreadEvent> type, Object source, Object data) {
        super(ThreadEvent.class.getName(), type.getSimpleName(), source, data);
    }

    protected static class EnterEvent extends ThreadEvent {

        public EnterEvent(Object source, boolean isLongRunning) {
            super(EnterEvent.class, source, isLongRunning);
        }

        public boolean isLongRunning() {
            return (Boolean) getData();
        }

        @Override
        public void handle(ThreadEventHandler handler) {
            handler.onEnter((Boolean) getData());
        }
    }

    protected static class LeaveEvent extends ThreadEvent {
        public LeaveEvent(Object source) {
            super(LeaveEvent.class, source, null);
        }

        @Override
        public void handle(ThreadEventHandler handler) {
            handler.onLeave();
        }
    }

    public abstract void handle(ThreadEventHandler handler);

    public void send() {
        Framework.getService(EventService.class).sendEvent(this);
    }

    public static ThreadEvent onEnter(Object source, boolean isLongRunning) {
        return new EnterEvent(source, isLongRunning);
    }

    public static ThreadEvent onLeave(Object source) {
        return new LeaveEvent(source);
    }

    public static void listen(ThreadEventListener aListener) {
        Framework.getService(EventService.class).addListener(ThreadEvent.class.getName(), aListener);
    }

    public static void ignore(ThreadEventListener aListener) {
        Framework.getService(EventService.class).removeListener(ThreadEvent.class.getName(), aListener);
    }
}
