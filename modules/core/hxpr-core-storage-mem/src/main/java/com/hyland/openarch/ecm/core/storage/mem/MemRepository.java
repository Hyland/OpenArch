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
package com.hyland.openarch.ecm.core.storage.mem;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.dbs.DBSDocument;
import com.hyland.openarch.ecm.core.storage.dbs.DBSRepository;
import com.hyland.openarch.ecm.core.storage.dbs.DBSRepositoryBase;

/**
 * In-memory implementation of a {@link DBSRepository}.
 * <p>
 * Internally, the repository is a map from id to document object.
 * <p>
 * A document object is a JSON-like document stored as a Map recursively containing the data, see {@link DBSDocument}
 * for the description of the document.
 *
 * @since 5.9.4
 */
public class MemRepository extends DBSRepositoryBase {

    protected static final String NOSCROLL_ID = "noscroll";

    // for debug
    private final AtomicLong temporaryIdCounter = new AtomicLong(0);

    /**
     * The content of the repository, a map of document id -&gt; object.
     */
    protected Map<String, State> states;

    public MemRepository(MemRepositoryDescriptor descriptor) {
        super(descriptor.name, descriptor);
        initialize(descriptor);
        initRepository();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        states = null;
    }

    protected void initRepository() {
        states = new ConcurrentHashMap<>();
        try (MemConnection connection = getConnection()) {
            connection.initRepository();
        }
    }

    protected String generateNewId() {
        if (DBSRepositoryBase.DEBUG_UUIDS) {
            return "UUID_" + temporaryIdCounter.incrementAndGet();
        } else {
            return UUID.randomUUID().toString();
        }
    }

    @Override
    public boolean supportsTransactions() {
        return false;
    }

    @Override
    public MemConnection getConnection() {
        return new MemConnection(this);
    }

    /* synchronized */
    @Override
    public synchronized Lock getLock(String id) {
        State state = states.get(id);
        if (state == null) {
            // document not found
            throw new DocumentNotFoundException(id);
        }
        String owner = (String) state.get(SYS_LOCK_OWNER);
        if (owner == null) {
            return null;
        }
        Instant created = (Instant) state.get(SYS_LOCK_CREATED);
        return new Lock(owner, created);
    }

    /* synchronized */
    @Override
    public synchronized Lock setLock(String id, Lock lock) {
        State state = states.get(id);
        if (state == null) {
            // document not found
            throw new DocumentNotFoundException(id);
        }
        String owner = (String) state.get(SYS_LOCK_OWNER);
        if (owner != null) {
            // return old lock
            Instant created = (Instant) state.get(SYS_LOCK_CREATED);
            return new Lock(owner, created);
        }
        state.put(SYS_LOCK_OWNER, lock.getOwner());
        state.put(SYS_LOCK_CREATED, lock.getCreated());
        return null;
    }

    /* synchronized */
    @Override
    public synchronized Lock removeLock(String id, String owner) {
        State state = states.get(id);
        if (state == null) {
            // document not found
            throw new DocumentNotFoundException(id);
        }
        String oldOwner = (String) state.get(SYS_LOCK_OWNER);
        if (oldOwner == null) {
            // no previous lock
            return null;
        }
        Instant oldCreated = (Instant) state.get(SYS_LOCK_CREATED);
        if (!LockManager.canLockBeRemoved(oldOwner, owner)) {
            // existing mismatched lock, flag failure
            return new Lock(oldOwner, oldCreated, true);
        }
        // remove lock
        state.put(SYS_LOCK_OWNER, null);
        state.put(SYS_LOCK_CREATED, null);
        // return old lock
        return new Lock(oldOwner, oldCreated);
    }

    @Override
    public void markReferencedBlobs(BiConsumer<String, String> markerCallback) {
        for (State state : states.values()) {
            Object blobKeys = state.get(SYS_BLOB_KEYS);
            if (blobKeys instanceof List list) {
                for (Object v : list) {
                    if (v instanceof String key) {
                        markerCallback.accept(key, repositoryName);
                    }
                }
            }
        }
    }

}
