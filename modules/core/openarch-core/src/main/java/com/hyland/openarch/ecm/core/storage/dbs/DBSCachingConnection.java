/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByClause;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.dbs.DBSTransactionState.ConditionalUpdates;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.ClusterService;

/**
 * The DBS Cache layer used to cache some method call of real repository
 *
 * @since 11.1 (introduced in 8.10 as DBSCachingRepository)
 */
public class DBSCachingConnection implements DBSConnection {

    private static final Logger log = LogManager.getLogger(DBSCachingConnection.class);

    protected final DBSConnection connection;

    private final Cache<String, State> cache;

    private final Cache<String, String> childCache;

    /**
     * Monotonic write clock guarding {@link #putInCache} against the read-repopulates-stale race: a reader that
     * repopulates the cache with a value written concurrently drops its own put. Bumped by every write and received
     * invalidation. Global (not per-id): coarser but strictly safe. Same scope as {@link #cache}: repository-wide for a
     * non-transactional backend (MongoDB), connection-local otherwise.
     */
    private final AtomicLong writeClock;

    /**
     * The local invalidations, due to writes to this connection, that should be propagated to other connections (and
     * other cluster nodes) at post-commit time.
     * <p>
     * {@code null} if the repository is not transactional and there is no cluster.
     */
    private final DBSInvalidationsList invalidations;

    /**
     * The cluster invalidator, sending invalidations to other cluster nodes.
     * <p>
     * {@code null} if there is no cluster.
     */
    private final DBSClusterInvalidator clusterInvalidator;

    /**
     * The queue of invalidations received from other connections, to be processed at pre-transaction time.
     * <p>
     * {@code null} if the repository is not transactional.
     */
    private final DBSInvalidationsQueue invalidationsQueue;

    /**
     * The propagator of invalidations to other connections.
     * <p>
     * {@code null} if the repository is not transactional.
     */
    private final DBSInvalidationsPropagator invalidationsPropagator;

    public DBSCachingConnection(DBSConnection connection, DBSCachingRepository repository) {
        this.connection = connection;
        // Init caches
        if (repository.supportsTransactions()) {
            // connection-local cache
            cache = repository.newCache(false);
            childCache = repository.newChildCache(false);
            writeClock = new AtomicLong();
        } else {
            // no transaction, use a repository-wide cache
            cache = repository.getCache();
            childCache = repository.getChildCache();
            writeClock = repository.getWriteClock();
        }
        // local invalidations
        invalidationsPropagator = repository.getInvalidationsPropagator();
        if (invalidationsPropagator == null) {
            invalidationsQueue = null;
        } else {
            invalidationsQueue = new DBSInvalidationsQueue("dbs-" + this);
            invalidationsPropagator.addQueue(invalidationsQueue);
        }
        // cluster invalidations
        clusterInvalidator = repository.getClusterInvalidator();
        // collected invalidations
        if (invalidationsPropagator == null && clusterInvalidator == null) {
            // no transactional backend and no cluster
            invalidations = null;
        } else {
            invalidations = new DBSInvalidationsList();
        }
    }

    @Override
    public void close() {
        connection.close();
        if (invalidationsPropagator != null) {
            invalidationsPropagator.removeQueue(invalidationsQueue);
        }
        if (cache != null) {
            // Clear caches
            cache.invalidateAll();
            childCache.invalidateAll();
        }
        // Send invalidations
        if (clusterInvalidator != null) {
            var invalidation = new DBSInvalidations(true);
            invalidation.setClusterProperties();
            var list = new DBSInvalidationsList();
            list.add(invalidation);
            clusterInvalidator.sendInvalidations(list);
        }
    }

    @Override
    public SchemaManager getSchemaManager() {
        return connection.getSchemaManager();
    }

    @Override
    public List<List<String>> getBlobKeysPaths() {
        return connection.getBlobKeysPaths();
    }

    @Override
    public void begin() {
        connection.begin();
        processReceivedInvalidations();
    }

    @Override
    public void commit() {
        // CIN-12299: Fix race condition where SQS task arrives before HTTP invalidation
        // Send invalidations BEFORE commit to give HTTP a head start over SQS
        // Note: If commit fails after sending invalidations, this is acceptable because
        // cache invalidation is idempotent and pessimistic. Other nodes will have
        // unnecessary cache misses but will read correct data from the database.
        sendInvalidationsToOthers();
        connection.commit();
        processReceivedInvalidations();
    }

    @Override
    public void rollback() {
        connection.rollback();
    }

    @Override
    public State readState(String id) {
        State state = cache.getIfPresent(id);
        if (state == null) {
            // snapshot the write clock BEFORE reading committed state, so a write concurrent with the read makes
            // putInCache drop the possibly-stale value instead of poisoning the shared cache
            long stamp = writeClock.get();
            state = connection.readState(id);
            if (state != null) {
                putInCache(state, stamp);
            }
        }
        return state;
    }

    @Override
    public State readPartialState(String id, Collection<String> keys) {
        // bypass caches, as the goal of this method is to not trash caches for one-shot reads
        return connection.readPartialState(id, keys);
    }

    @Override
    public List<State> readStates(List<String> ids) {
        ImmutableMap<String, State> statesMap = cache.getAllPresent(ids);
        List<String> idsToRetrieve = new ArrayList<>(ids);
        idsToRetrieve.removeAll(statesMap.keySet());
        // snapshot the write clock BEFORE reading committed state (see readState)
        long stamp = writeClock.get();
        // Read missing states from repository
        List<State> states = connection.readStates(idsToRetrieve);
        // Cache them
        states.forEach(state -> putInCache(state, stamp));
        // Add previous cached one
        states.addAll(statesMap.values());
        return states;
    }

    @Override
    public void createState(State state) {
        connection.createState(state);
        // don't cache new state, it is inefficient on mass import
    }

    @Override
    public void createStates(List<State> states) {
        connection.createStates(states);
        // don't cache new states, it is inefficient on mass import
    }

    @Override
    public void updateState(String id, StateDiff diff, ConditionalUpdates conditionalUpdates) {
        connection.updateState(id, diff, conditionalUpdates);
        invalidate(id);
    }

    @Override
    public void deleteStates(Set<String> ids) {
        connection.deleteStates(ids);
        invalidate(ids);
    }

    @Override
    public State readChildState(String parentId, String name, Set<String> ignored) {
        processReceivedInvalidations();

        String childCacheKey = computeChildCacheKey(parentId, name);
        String stateId = childCache.getIfPresent(childCacheKey);
        if (stateId != null) {
            State state = cache.getIfPresent(stateId);
            if (state != null) {
                // As we don't have invalidation for childCache we need to check if retrieved state is the right one
                // and not a previous document which was moved or renamed
                if (parentId.equals(state.get(SYS_PARENT_ID)) && name.equals(state.get(SYS_NAME))) {
                    return state;
                } else {
                    // We can invalidate the entry in cache as the document seemed to be moved or renamed
                    childCache.invalidate(childCacheKey);
                }
            }
        }
        // snapshot the write clock BEFORE reading committed state, so a write concurrent with the read makes
        // putInCache drop the possibly-stale value instead of poisoning the shared cache (see readState)
        long stamp = writeClock.get();
        State state = connection.readChildState(parentId, name, ignored);
        putInCache(state, stamp);
        return state;
    }

    /**
     * Puts a state read from the backend into the cache, then drops it again if a write happened during the read, so a
     * concurrent write is never masked by a stale repopulated entry (the read-repopulates-stale race). The state is
     * still returned to the caller; only the shared cache is protected.
     * <p>
     * Correctness relies on {@link #evictAndBumpWriteClock} evicting <em>before</em> bumping the clock, so a put that
     * lands after a concurrent eviction observes the moved clock and removes its entry.
     *
     * @param state the state to cache, may be {@code null}
     * @param readStamp the write clock snapshotted before the backend read
     */
    private void putInCache(State state, long readStamp) {
        if (state == null) {
            return;
        }
        String stateId = state.get(SYS_ID).toString();
        doPutInCache(stateId, state);
        long current = writeClock.get();
        if (current != readStamp) {
            // a write occurred between the read and this put: the value may be stale, do not leave it in the cache
            cache.invalidate(stateId);
            log.debug("Write eviction detected between read and putInCache for id={}, dropping possibly "
                    + "stale cache entry (readStamp={}, writeClock={})", stateId, readStamp, current);
        }
    }

    private void doPutInCache(String stateId, State state) {
        cache.put(stateId, state);
        Object stateParentId = state.get(SYS_PARENT_ID);
        if (stateParentId != null) {
            childCache.put(computeChildCacheKey(stateParentId.toString(), state.get(SYS_NAME).toString()), stateId);
        }
    }

    private String computeChildCacheKey(String parentId, String name) {
        return parentId + '_' + name;
    }

    private void invalidate(String id) {
        invalidate(Set.of(id));
    }

    private void invalidate(Set<String> ids) {
        evictAndBumpWriteClock(ids);
        if (invalidations != null) {
            var invalidation = new DBSInvalidations();
            invalidation.addAll(ids);
            invalidation.setClusterProperties();
            invalidations.add(invalidation);
        }
    }

    /** Evicts the given ids from the cache and bumps the write clock, closing the read-repopulates-stale race. */
    private void evictAndBumpWriteClock(Collection<String> ids) {
        // evict BEFORE bumping so a putInCache landing after this eviction observes the higher clock and drops its
        // entry
        cache.invalidateAll(ids);
        bumpWriteClock();
    }

    /** Bumps the write clock, if any, so in-flight reads that snapshotted an earlier value drop their put. */
    private void bumpWriteClock() {
        if (writeClock != null) {
            writeClock.incrementAndGet();
        }
    }

    protected void sendInvalidationsToOthers() {
        if (invalidations != null && !invalidations.isEmpty()) {
            if (clusterInvalidator != null) {
                // send to other cluster nodes
                clusterInvalidator.sendInvalidations(invalidations);
            }
            if (invalidationsPropagator != null) {
                // send to other connections
                invalidationsPropagator.propagateInvalidations(invalidations, invalidationsQueue);
            }
            invalidations.clear();
        }
    }

    /**
     * Drains the invalidations received from other connections and cluster nodes and evicts them from the cache.
     */
    protected void processReceivedInvalidations() {
        // serialize claim+apply against other drainers on the cache monitor: without it a drainer could claim ids from
        // the buffer, be descheduled before evicting, and another drainer find the buffer empty and never evict them.
        // This does NOT make the drain atomic against readers (lock-free getIfPresent/getAllPresent); that reader race
        // is handled by the write-clock guard in putInCache.
        synchronized (cache) {
            DBSInvalidationsList invals;
            // invalidations from other cluster nodes
            if (clusterInvalidator != null) {
                invals = clusterInvalidator.receiveInvalidations();
                // send cluster invalidations to all other connections
                if (invals != null && !invals.isEmpty() && invalidationsPropagator != null) {
                    invalidationsPropagator.propagateInvalidations(invals, invalidationsQueue);
                }
            } else {
                invals = null;
            }
            // invalidations from other connections
            if (invalidationsQueue != null) {
                DBSInvalidationsList inv = invalidationsQueue.getInvalidations();
                if (invals == null) {
                    invals = new DBSInvalidationsList();
                }
                invals.add(inv);
            }
            // apply invalidations to the cache (connection-local or repository-wide)
            if (invals != null && !invals.isEmpty()) {
                // evict for ALL the invalidations first. They have already been drained from the cluster buffer and
                // from the connection queue above, so they cannot be redelivered: anything we fail to evict here
                // stays stale in the cache forever. Nothing between the drain and the last eviction may throw.
                invals.getInvalidations().forEach(this::applyToCache);
                // only then persist the status, once the evictions it vouches for have actually been done. A failure
                // here is bookkeeping loss, not cache corruption, so it must never propagate to the caller.
                invals.getInvalidations().forEach(inv -> {
                    try {
                        updateCachesStatus(inv);
                    } catch (RuntimeException e) { // NOSONAR
                        log.warn("Failed to update the caches status for node {}, invalidations were still applied",
                                inv.nodeId, e);
                    }
                });
            }
        }
    }

    /** Evicts one set of received invalidations from the caches. */
    protected void applyToCache(DBSInvalidations inv) {
        if (inv.all) {
            cache.invalidateAll();
            childCache.invalidateAll();
            // bump so any in-flight read that snapshotted an earlier clock drops its put
            bumpWriteClock();
        } else if (inv.ids != null) {
            evictAndBumpWriteClock(inv.ids);
        }
    }

    /** Records that the invalidations sent by that cluster node have been applied here. */
    protected void updateCachesStatus(DBSInvalidations inv) {
        var nodeId = inv.nodeId;
        if (nodeId == null) {
            return;
        }
        var clusterService = Framework.getService(ClusterService.class);
        if (clusterService == null) {
            // the runtime is shutting down, there is nobody left to read the status
            log.debug("No cluster service available, skipping the caches status for node {}", nodeId);
            return;
        }
        clusterService.updateCachesStatusPerNode(nodeId, inv.timestamp);
    }

    @Override
    public String getRootId() {
        return connection.getRootId();
    }

    @Override
    public String generateNewId() {
        return connection.generateNewId();
    }

    @Override
    public boolean hasChild(String parentId, String name, Set<String> ignored) {
        return connection.hasChild(parentId, name, ignored);
    }

    @Override
    public List<State> queryKeyValue(String key, Object value, Set<String> ignored) {
        return connection.queryKeyValue(key, value, ignored);
    }

    @Override
    public List<State> queryKeyValue(String key1, Object value1, String key2, Object value2, Set<String> ignored) {
        return connection.queryKeyValue(key1, value1, key2, value2, ignored);
    }

    @Override
    public Stream<State> getDescendants(String id, Set<String> keys) {
        return connection.getDescendants(id, keys);
    }

    @Override
    public Stream<State> getDescendants(String id, Set<String> keys, int limit) {
        return connection.getDescendants(id, keys, limit);
    }

    @Override
    public boolean queryKeyValuePresence(String key, String value, Set<String> ignored) {
        return connection.queryKeyValuePresence(key, value, ignored);
    }

    @Override
    public PartialList<Map<String, Object>> queryAndFetch(DBSExpressionEvaluator evaluator,
            OrderByClause orderByClause, boolean distinctDocuments, int limit, int offset, int countUpTo) {
        return connection.queryAndFetch(evaluator, orderByClause, distinctDocuments, limit, offset, countUpTo);
    }

    @Override
    public ScrollResult<String> scroll(DBSExpressionEvaluator evaluator, int batchSize, int keepAliveSeconds) {
        return connection.scroll(evaluator, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        return connection.scroll(scrollId);
    }

    @Override
    public Lock getLock(String id) {
        return connection.getLock(id);
    }

    @Override
    public Lock setLock(String id, Lock lock) {
        Lock res = connection.setLock(id, lock);
        invalidate(id);
        return res;
    }

    @Override
    public Lock removeLock(String id, String owner) {
        Lock res = connection.removeLock(id, owner);
        invalidate(id);
        return res;
    }

}
