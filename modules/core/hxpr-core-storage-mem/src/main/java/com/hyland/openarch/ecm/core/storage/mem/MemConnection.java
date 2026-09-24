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

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.storage.State.NOP;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.ConditionFailedException;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.api.ScrollResultImpl;
import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.api.model.Delta;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByClause;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.ListDiff;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.StateHelper;
import com.hyland.openarch.ecm.core.storage.dbs.DBSConnection;
import com.hyland.openarch.ecm.core.storage.dbs.DBSConnectionBase;
import com.hyland.openarch.ecm.core.storage.dbs.DBSExpressionEvaluator;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession.OrderByComparator;
import com.hyland.openarch.ecm.core.storage.dbs.DBSTransactionState.ConditionalUpdates;

/**
 * In-memory implementation of a {@link DBSConnection}.
 *
 * @since 11.1 (introduced in 5.9.4 as MemRepository)
 */
public class MemConnection extends DBSConnectionBase {

    private static final Logger log = LogManager.getLogger(MemConnection.class);
    private static final String MEM_QUERY = "Mem: QUERY {} - {}";
    private static final String MEM_CONST = "Mem:    -> {}";

    protected static final String NOSCROLL_ID = "noscroll";

    // the global state, from the repository (thread-safe map)
    protected Map<String, State> states;

    public MemConnection(MemRepository repository) {
        super(repository);
        states = repository.states;
    }

    @Override
    public SchemaManager getSchemaManager() {
        return repository.getSchemaManager();
    }

    @Override
    public void begin() {
        // nothing
    }

    @Override
    public void commit() {
        // nothing
    }

    @Override
    public void rollback() {
        // nothing
    }

    protected void initRepository() {
        initRoot();
    }

    @Override
    public void close() {
        // nothing
    }

    @Override
    public String generateNewId() {
        return ((MemRepository) repository).generateNewId();
    }

    @Override
    public State readState(String id) {
        return readPartialState(id, null);
    }

    @Override
    public State readPartialState(String id, Collection<String> keys) {
        if (id == null) {
            return null;
        }
        State state = states.get(id);
        if (state != null) {
            if (keys != null && !keys.isEmpty()) {
                State partialState = new State();
                for (String key : keys) {
                    Object value = state.get(key);
                    if (value != null) {
                        partialState.put(key, value);
                    }
                }
                state = partialState;
            }
            if (log.isTraceEnabled()) {
                log.trace("Mem: READ  {}: {}", id, state);
            }
        }
        return state;
    }

    @Override
    public List<State> readStates(List<String> ids) {
        List<State> list = new ArrayList<>();
        for (String id : ids) {
            list.add(readState(id));
        }
        return list;
    }

    @Override
    public void createState(State state) {
        String id = (String) state.get(SYS_ID);
        if (log.isTraceEnabled()) {
            log.trace("Mem: CREATE {}: {}", id, state);
        }
        if (states.containsKey(id)) {
            throw new NuxeoException("Already exists: " + id);
        }
        state = StateHelper.deepCopy(state, true); // thread-safe
        StateHelper.resetDeltas(state);
        states.put(id, state);
    }

    @Override
    public void updateState(String id, StateDiff diff, ConditionalUpdates conditionalUpdates) {
        if (log.isTraceEnabled()) {
            log.trace("Mem: UPDATE {}: {}", id, diff);
        }
        State state = states.get(id);
        if (state == null) {
            throw new ConcurrentUpdateException("Missing: " + id);
        }
        synchronized (state) { // synchronization needed for atomic conditions
            if (conditionalUpdates != null) {
                // condition on change token
                String key = conditionalUpdates.getConditionKey();
                if (key != null) {
                    Object value = conditionalUpdates.getConditionValue();
                    if (!Objects.equals(state.get(key), value)) {
                        throw new ConcurrentUpdateException((String) state.get(SYS_ID));
                    }
                }
                // conditional updates from user predicate
                Predicate conditionPredicate = conditionalUpdates.getConditionPredicate();
                if (conditionPredicate != null) {
                    Statement query = new Statement(new SelectClause(), null, new WhereClause(conditionPredicate));
                    DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
                    evaluator.parse(getSchemaManager());
                    if (evaluator.matches(state).isEmpty()) {
                        throw new ConditionFailedException((String) state.get(SYS_ID));
                    }
                }
                for (Entry<String, Object> en : conditionalUpdates.getUpdates().entrySet()) {
                    applyDiff(state, en.getKey(), en.getValue());
                }
            }
            applyDiff(state, diff);
        }
    }

    @Override
    public void deleteStates(Set<String> ids) {
        if (log.isTraceEnabled()) {
            log.trace("Mem: REMOVE {}", ids);
        }
        for (String id : ids) {
            if (states.remove(id) == null) {
                log.debug("Missing on remove: {}", id);
            }
        }
    }

    @Override
    public State readChildState(String parentId, String name, Set<String> ignored) {
        // TODO optimize by maintaining a parent/child index
        for (State state : states.values()) {
            if (ignored.contains(state.get(SYS_ID))) {
                continue;
            }
            if (!parentId.equals(state.get(SYS_PARENT_ID))) {
                continue;
            }
            if (!name.equals(state.get(SYS_NAME))) {
                continue;
            }
            return state;
        }
        return null;
    }

    @Override
    public boolean hasChild(String parentId, String name, Set<String> ignored) {
        return readChildState(parentId, name, ignored) != null;
    }

    @Override
    public List<State> queryKeyValue(String key, Object value, Set<String> ignored) {
        if (log.isTraceEnabled()) {
            log.trace(MEM_QUERY, key, value);
        }
        List<State> list = new ArrayList<>();
        for (State state : states.values()) {
            String id = (String) state.get(SYS_ID);
            if (ignored.contains(id)) {
                continue;
            }
            if (!value.equals(state.get(key))) {
                continue;
            }
            list.add(state);
        }
        if (log.isTraceEnabled() && !list.isEmpty()) {
            log.trace(MEM_CONST, list.size());
        }
        return list;
    }

    @Override
    public List<State> queryKeyValue(String key1, Object value1, String key2, Object value2, Set<String> ignored) {
        if (log.isTraceEnabled()) {
            log.trace("Mem: QUERY {} - {} AND {} - {}", key1, value1, key2, value2);
        }
        List<State> list = new ArrayList<>();
        for (State state : states.values()) {
            String id = (String) state.get(SYS_ID);
            if (ignored.contains(id)) {
                continue;
            }
            if (!(value1.equals(state.get(key1)) && value2.equals(state.get(key2)))) {
                continue;
            }
            list.add(state);
        }
        if (log.isTraceEnabled() && !list.isEmpty()) {
            log.trace(MEM_CONST, list.size());
        }
        return list;
    }

    @Override
    public Stream<State> getDescendants(String rootId, Set<String> keys) {
        return getDescendants(rootId, keys, 0);
    }

    @Override
    public Stream<State> getDescendants(String rootId, Set<String> keys, int limit) {
        if (log.isTraceEnabled()) {
            log.trace(MEM_QUERY, SYS_ANCESTOR_IDS, rootId);
        }
        Stream<State> stream = states.values() //
                                     .stream()
                                     .filter(state -> hasAncestor(state, rootId));
        if (limit != 0) {
            stream = stream.limit(limit);
        }
        return stream;
    }

    protected static boolean hasAncestor(State state, String id) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) state.get(SYS_ANCESTOR_IDS);
        return list != null && list.contains(id);
    }

    @Override
    public boolean queryKeyValuePresence(String key, String value, Set<String> ignored) {
        if (log.isTraceEnabled()) {
            log.trace(MEM_QUERY, key, value);
        }
        for (State state : states.values()) {
            String id = (String) state.get(SYS_ID);
            if (ignored.contains(id)) {
                continue;
            }
            if (value.equals(state.get(key))) {
                if (log.isTraceEnabled()) {
                    log.trace("Mem:    -> present");
                }
                return true;
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Mem:    -> absent");
        }
        return false;
    }

    @Override
    public PartialList<Map<String, Object>> queryAndFetch(DBSExpressionEvaluator evaluator,
            OrderByClause orderByClause, boolean distinctDocuments, int limit, int offset, int countUpTo) {
        if (log.isTraceEnabled()) {
            log.trace("Mem: QUERY {} OFFSET {} LIMIT {}", evaluator, offset, limit);
        }
        evaluator.parse(getSchemaManager());
        List<Map<String, Object>> projections = new ArrayList<>();
        for (State state : states.values()) {
            List<Map<String, Object>> matches = evaluator.matches(state);
            if (!matches.isEmpty()) {
                if (distinctDocuments) {
                    projections.add(matches.get(0));
                } else {
                    projections.addAll(matches);
                }
            }
        }
        // ORDER BY
        // orderByClause may be null and different from evaluator.getOrderByClause() in case we want to post-filter
        if (orderByClause != null) {
            projections.sort(new OrderByComparator(orderByClause));
        }
        // LIMIT / OFFSET
        int totalSize = projections.size();
        if (countUpTo == -1) {
            // count full size
        } else if (countUpTo == 0) {
            // no count
            totalSize = -1; // not counted
        } else {
            // count only if less than countUpTo
            if (totalSize > countUpTo) {
                totalSize = -2; // truncated
            }
        }
        if (limit != 0) {
            int size = projections.size();
            projections.subList(0, offset > size ? size : offset).clear();
            size = projections.size();
            if (limit < size) {
                projections.subList(limit, size).clear();
            }
        }
        // TODO DISTINCT

        if (log.isTraceEnabled() && !projections.isEmpty()) {
            log.trace(MEM_CONST, projections.size());
        }
        return new PartialList<>(projections, totalSize);
    }

    @Override
    public ScrollResult<String> scroll(DBSExpressionEvaluator evaluator, int batchSize, int keepAliveSeconds) {
        if (log.isTraceEnabled()) {
            log.trace("Mem: QUERY {}", evaluator);
        }
        evaluator.parse(getSchemaManager());
        List<String> ids = new ArrayList<>();
        for (State state : states.values()) {
            List<Map<String, Object>> matches = evaluator.matches(state);
            if (!matches.isEmpty()) {
                String id = matches.get(0).get(SYS_ID).toString();
                ids.add(id);
            }
        }
        return new ScrollResultImpl<>(NOSCROLL_ID, ids);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        if (NOSCROLL_ID.equals(scrollId)) {
            // Id are already in memory, they are returned as a single batch
            return ScrollResultImpl.emptyResult();
        }
        throw new NuxeoException("Unknown or timed out scrollId");
    }

    /**
     * Applies a {@link StateDiff} in-place onto a base {@link State}.
     * <p>
     * Uses thread-safe datastructures.
     */
    public static void applyDiff(State state, StateDiff stateDiff) {
        for (Entry<String, Object> en : stateDiff.entrySet()) {
            applyDiff(state, en.getKey(), en.getValue());
        }
    }

    /**
     * Applies a key/value diff in-place onto a base {@link State}.
     * <p>
     * Uses thread-safe datastructures.
     */
    protected static void applyDiff(State state, String key, Object value) {
        if (value instanceof StateDiff stateDiff) {
            Object old = state.get(key);
            if (old == null) {
                old = new State(true); // thread-safe
                state.put(key, old);
                // enter the next if
            }
            if (!(old instanceof State)) {
                throw new UnsupportedOperationException("Cannot apply StateDiff on non-State: " + old);
            }
            applyDiff((State) old, stateDiff);
        } else if (value instanceof ListDiff listDiff) {
            state.put(key, applyDiff(state.get(key), listDiff));
        } else if (value instanceof Delta delta) {
            Number oldValue = (Number) state.get(key);
            Number newValue;
            if (oldValue == null) {
                newValue = delta.getFullValue();
            } else {
                newValue = delta.add(oldValue);
            }
            state.put(key, newValue);
        } else {
            state.put(key, StateHelper.deepCopy(value, true)); // thread-safe
        }
    }

    /**
     * Applies a {@link ListDiff} onto an array or {@link List}, and returns the resulting value.
     * <p>
     * Uses thread-safe datastructures.
     */
    public static Object applyDiff(Object value, ListDiff listDiff) {
        if (value == null) {
            value = new CopyOnWriteArrayList<>();
        }
        if (!(value instanceof List list)) {
            throw new UnsupportedOperationException("Cannot apply ListDiff on non-List: " + value);
        }
        if (listDiff.diff != null) {
            int i = 0;
            for (Object diffElem : listDiff.diff) {
                if (i >= list.size()) {
                    // TODO log error applying diff to shorter list
                    break;
                }
                if (diffElem instanceof StateDiff stateDiff) {
                    applyDiff((State) list.get(i), stateDiff);
                } else if (diffElem != NOP) {
                    list.set(i, StateHelper.deepCopy(diffElem, true)); // thread-safe
                }
                i++;
            }
        }
        if (listDiff.rpush != null) {
            // deepCopy of what we'll add
            List<Object> add = new ArrayList<>(listDiff.rpush.size());
            for (Object v : listDiff.rpush) {
                add.add(StateHelper.deepCopy(v, true)); // thread-safe
            }
            // update CopyOnWriteArrayList in one step
            list.addAll(add);
        }
        if (listDiff.pull != null) {
            list.removeAll(listDiff.pull);
        }
        if (list.isEmpty()) {
            return null;
        }
        return list;
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

}
