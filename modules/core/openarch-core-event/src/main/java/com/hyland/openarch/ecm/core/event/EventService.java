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
package com.hyland.openarch.ecm.core.event;

import com.hyland.openarch.ecm.core.event.impl.EventListenerDescriptor;
import com.hyland.openarch.ecm.core.event.impl.EventListenerList;

/**
 * The event service manages listener registries and notifies listeners about core events.
 * <p>
 * The service is able to run in a transactional mode where all events are recorded and fired after the transaction
 * commits in one step as an event bundle.
 * <p>
 * To start a transaction, the framework calls the {@code transactionStarted()} method, and at transaction commit the
 * framework calls {@code transactionCommitted()} to fire the event bundle. Upon rollback the framework calls
 * {@code transactionRolledback()} to clean up recorded events.
 * <p>
 * Events are recorded in a thread variable so they are valid only in the current thread.
 * <p>
 * An event marked {@link Event#isInline()} is dispatched immediately, otherwise it is recorded in a thread-based bundle
 * of current events. If no transaction was started, an event marked {@link Event#isCommitEvent()} is used to flush the
 * event bundle to its listeners, otherwise the transaction commit does the flush.
 * <p>
 * Listeners are of two types: {@link EventListener} notified as the event is raised and {@link PostCommitEventListener}
 * notified after the transaction was committed.
 */
public interface EventService {

    /**
     * Adds a new event listener. Used by the framework.
     * <p>
     * The event listener is described by a {@link EventListenerDescriptor} that may specify a priority. Both types of
     * listeners (immediate and post-commit) are registered.
     *
     * @param listener the listener to add
     */
    void addEventListener(EventListenerDescriptor listener);

    /**
     * Removes an event listener. Used by the framework.
     *
     * @param listener the listener to remove
     */
    void removeEventListener(EventListenerDescriptor listener);

    /**
     * Fires an event.
     * <p>
     * If a transaction was started, the event is registered if needed to be sent after the transaction commit.
     *
     * @param event the event to fire
     */
    void fireEvent(Event event);

    /**
     * Fires all recorded events in a transaction. Used by the framework.
     * <p>
     * The events are fired to {@link PostCommitEventListener} listeners. Events are fired in the form of an event
     * bundle.
     *
     * @param event the event bundle
     */
    void fireEventBundle(EventBundle event);

    /**
     * Gets the event listener descriptor corresponding to the give name.
     *
     * @since 5.8
     * @param name the event listener name
     * @return the descriptor, or {@code null} if not found
     */
    EventListenerDescriptor getEventListener(String name);

    // admin

    EventListenerList getListenerList();

    void setBulkModeEnabled(boolean bulkModeEnabled);

}
