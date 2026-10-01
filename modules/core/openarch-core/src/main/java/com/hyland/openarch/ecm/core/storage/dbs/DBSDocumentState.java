/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_GENERATION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_USER_GENERATION;

import com.hyland.openarch.ecm.core.api.model.Delta;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.StateHelper;
import com.hyland.openarch.ecm.core.storage.State;

/**
 * Implementation of a {@link InternalDocument} state for Document-Based Storage.
 * <p>
 * It wraps a {@link State}, together with a dirty flag.
 *
 * @since 5.9.4
 */
public class DBSDocumentState {

    private static final String UNDEFINED_PARENT_ID = "_undefined_";
    /**
     * The current state.
     */
    protected State state;

    /**
     * When non-null, the original state (otherwise the state hasn't been modified).
     */
    protected State originalState;

    private String id;

    private String parentId = UNDEFINED_PARENT_ID;

    /**
     * Constructs an empty state.
     */
    public DBSDocumentState() {
        state = new State();
    }

    /**
     * Constructs a document state from the copy of an existing base state.
     */
    public DBSDocumentState(State base) {
        state = StateHelper.deepCopy(base);
    }

    /**
     * This must be called if we're about to directly change the internal state.
     */
    public void markDirty() {
        if (originalState == null) {
            originalState = StateHelper.deepCopy(state);
        }
    }

    /**
     * Checks if the document state has been changed since its construction or the last call to {@link #setNotDirty}.
     */
    public boolean isDirty() {
        // TODO could have optimized hasDiff method to short-circuit this
        return getStateChange() != null;
    }

    public void setNotDirty() {
        originalState = null;
        StateHelper.resetDeltas(state);
        // reset the generation delta even if delta processing is disabled in StateHelper.resetDeltas
        Object value = state.get(SYS_GENERATION);
        if (value instanceof Delta delta) {
            state.put(SYS_GENERATION, delta.getFullValue());
        }
    }

    /**
     * Gets the state. If the caller changes the state, it must also call {@link #markDirty()} to inform this object
     * that the state is dirtied.
     */
    public State getState() {
        return state;
    }

    /**
     * Gets a diff of what changed since this document state was read from database or saved.
     *
     * @return {@code null} if there was no change, or a {@link StateDiff}
     */
    public StateDiff getStateChange() {
        if (originalState == null) {
            return null;
        }
        StateDiff diff = StateHelper.diff(originalState, state);
        if (diff.isEmpty()) {
            return null;
        }
        return diff;
    }

    /**
     * Gets the original state for this, needed when creating an undo log.
     *
     * @return a state that must not be modified
     * @since 7.4
     */
    public State getOriginalState() {
        return originalState == null ? state : originalState;
    }

    public Object get(String key) {
        if (SYS_ID.equals(key)) {
            return getId();
        } else if (SYS_PARENT_ID.equals(key)) {
            return getParentId();
        }
        return state.get(key);
    }

    public void put(String key, Object value) {
        markDirty();

        if (SYS_ID.equals(key)) {
            id = (String) value;
        } else if (SYS_PARENT_ID.equals(key)) {
            parentId = (String) value;
        }
        state.put(key, value);
    }

    public boolean containsKey(String key) {
        return state.get(key) != null;
    }

    public String getId() {
        if (id == null) {
            id = (String) state.get(SYS_ID);
        }
        return id;
    }

    public String getParentId() {
        // use a marker because parentId can be null
        if (parentId == UNDEFINED_PARENT_ID) { // NOSONAR (marker object)
            parentId = (String) state.get(SYS_PARENT_ID);
        }
        return parentId;
    }

    public String getName() {
        return (String) get(SYS_NAME);
    }

    public String getPrimaryType() {
        return (String) get(SYS_PRIMARY_TYPE);
    }

    public Long getGeneration() {
        return (Long) get(SYS_GENERATION);
    }

    public Long getUserGeneration() {
        return (Long) get(SYS_USER_GENERATION);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + (isDirty() ? "dirty," : "") + state.toString() + ')';
    }

}
