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

/**
 * A specialized event listener that is notified after the user operation is committed.
 * <p>
 * This type of listener can be notified either in a synchronous or asynchronous mode.
 *
 * @see EventListener
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface PostCommitEventListener {

    /**
     * Handles the set of events that were raised during the life of an user operation.
     * <p>
     * The events are fired as a {@link EventBundle} after the transaction is committed.
     *
     * @param events the events to handle
     */
    void handleEvent(EventBundle events);

}
