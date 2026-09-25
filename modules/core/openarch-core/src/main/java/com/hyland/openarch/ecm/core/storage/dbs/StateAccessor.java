/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.dbs;

import java.util.Collection;

import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;

public interface StateAccessor {

    /**
     * Returns a state and marks it as transient, because it's about to be modified or returned to user code (where it
     * may be modified).
     */
    DBSDocumentState getStateForUpdate(String id);

    /**
     * Returns a state which won't be modified.
     */
    // TODO in some cases it's good to have this kept in memory instead of
    // rereading from database every time
    // TODO getStateForReadOneShot
    State getStateForRead(String id);

    /**
     * Reads the partial state of a document, without any caching.
     *
     * @param id the document id
     * @param keys the keys to read
     * @return the document partial state, or {@code null} if not found
     */
    State readPartialStateNoCache(String id, Collection<String> keys);

    /**
     * Updates a document, without any caching.
     *
     * @param id the document id
     * @param diff the diff to apply
     */
    void updateStateNoCache(String id, StateDiff diff);

}
