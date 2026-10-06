/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.MemKeyValueStore;

/**
 * Basic implementation of a mark-and-sweep garbage collector, recording in memory the candidate keys and the state of
 * the keys it has seen.
 * <p>
 * A run is framed by {@link #start} and {@link #stop}. {@link #start} snapshots the keys that are candidates for
 * deletion into {@link #toDelete}, the caller then marks every key still referenced by calling {@link #mark}, and
 * {@link #stop} deletes the candidates that were never marked.
 * <h2>Cluster-wide coordination</h2>
 * A garbage collection is cluster-wide but runs on a single node, while blobs are written on all of them. The
 * coordination goes through a key/value store (see {@link #GC_KV_STORE_NAME_PREFIX}) holding a lock and, for the
 * duration of a run, one mark per blob key the run has seen.
 * <p>
 * The lock makes a run exclusive for a store, and its value is the run counter that namespaces that run's marks. A
 * node about to write a blob reads the lock (cached for a short while, see {@link #GC_ACTIVE_CACHE}): if there is no
 * lock then no garbage collection is in progress and the write proceeds unimpeded, otherwise the write must first
 * mark the key in the key/value store, which is the only thing the garbage collecting node can see. If that read
 * fails the write fails too, as proceeding without knowing whether a run is in progress could lose a blob.
 * <h2>Mark states</h2>
 * A mark is not a boolean "in use" flag but the state of a blob key with respect to the run in progress. It is absent
 * until either side touches the key, and then holds one of:
 * <ul>
 * <li>{@link #MARK_IN_USE} — a writer got there first, the blob must survive this run;</li>
 * <li>{@link #MARK_DELETING} — the garbage collector got there first and is deleting the blob right now;</li>
 * <li>{@link #MARK_DELETED} — the garbage collector is done deleting the blob.</li>
 * </ul>
 * The only transitions are from absent to any of the three, and from {@code DELETING} to {@code DELETED}:
 *
 * <pre>
 *            writer wins             GC wins              deletion done
 *  (absent) ------------&gt; IN_USE   (absent) ------&gt; DELETING ------------&gt; DELETED
 * </pre>
 *
 * Both sides compete for the same cell with an atomic compare-and-set from absent, so exactly one of them wins and
 * there is no window between deciding and acting. The loser adapts: a writer that finds {@code IN_USE} has nothing to
 * do, the garbage collector that finds {@code IN_USE} keeps the blob, and a writer that finds {@code DELETING} waits
 * for {@code DELETED} and then writes the blob again.
 * <h2>What this protects against</h2>
 * Without this, a deduplicating write is unsafe during a garbage collection: it checks that the blob already exists
 * and skips writing it, which leaves a reference to a blob the run is about to delete, and because the write was
 * skipped nothing recreates it — a dangling key and an undownloadable document. Marking the key before that check,
 * atomically against the garbage collector's claim, means that either the blob is kept, or the write finds the blob
 * gone and recreates it. The wait on {@code DELETING} is what makes the second case work: it covers the interval
 * during which the blob still exists but is doomed, which is exactly when the deduplication check would lie.
 * <p>
 * The recreation is implicit for most writes, which go on to write the blob anyway, but a copy to the same store is
 * skipped entirely as it has nothing to do. This is why {@link #mark} reports whether the blob survived: a caller
 * that has no write left to perform must be told that the key it was about to reuse now points to nothing.
 */
public abstract class AbstractBlobGarbageCollector implements BinaryGarbageCollector {

    private static final Logger log = LogManager.getLogger(AbstractBlobGarbageCollector.class);

    public static final String GC_RECOVERY_DIRECTORY = ".gcrecovery";

    /**
     * Whether garbage collection with deletion moves blobs to the recovery directory instead of deleting them.
     */
    public static boolean DELETE_WITH_RECOVERY = true;

    /**
     * Whether garbage collection without deletion restores blobs from the recovery directory.
     */
    public static boolean RECOVER_RECOVERY = false;

    /**
     * Prefix of the name of the key/value store holding the cluster-wide garbage collection locks and marks.
     * <p>
     * The actual store name is this prefix followed by the id of the blob provider ({@link #getId()}), so
     * that each blob provider gets its own, physically separate key/value store: this prevents a run counter, lock,
     * or mark belonging to one store from ever being confused with one belonging to another, even if they happen to
     * compute the same {@code storeId} (see below) or blob key (e.g. for content-addressed/deduplicated storage).
     * <p>
     * The keys stored in it have the following shapes:
     * <ul>
     * <li>{@code runcounter:<storeId>} — persistent counter whose numeric value allocates the next run counter;</li>
     * <li>{@code lock:<storeId>} — current lock, whose value is the active run counter;</li>
     * <li>{@code mark:<runCounter>:<blobKey>} — marker showing the state of that blob during that run.</li>
     * </ul>
     * <p>
     * The run counter is held only by the lock: a node that finds no lock knows that no garbage collection is in
     * progress, and therefore that there are no marks to look at.
     */
    public static final String GC_KV_STORE_NAME_PREFIX = "blobgc:";

    /**
     * Prefix of the key/value store key holding the garbage collection run counter for a store.
     * <p>
     * This counter has no TTL: it must survive for as long as the store itself, so that a run counter is never reused.
     */
    public static final String GC_RUN_COUNTER_KEY_PREFIX = "runcounter:";

    /**
     * Prefix of the key/value store key holding the cluster-wide garbage collection lock for a store.
     */
    public static final String GC_LOCK_KEY_PREFIX = "lock:";

    // TTL of the lock, so that it expires if the node holding it crashes or gets stuck; a heartbeat refreshes it
    // regularly for as long as the garbage collection is in progress, as a garbage collection usually lasts much
    // longer than this
    protected static final Duration GC_LOCK_TTL = Duration.ofMinutes(5);

    protected static final Duration GC_LOCK_HEARTBEAT = Duration.ofMinutes(1);

    /**
     * Prefix of the key/value store keys holding the cluster-wide marks of the blobs seen by a garbage collection run.
     * <p>
     * The full key also contains the run counter, see {@link #getMarkKey}.
     */
    public static final String GC_MARK_KEY_PREFIX = "mark:";

    // Value of a mark meaning that the blob is in use and must not be deleted by this run.
    protected static final String MARK_IN_USE = "1";

    // Value of a mark meaning that this run has claimed the blob and is about to delete it. A blob write that finds
    // this value must wait: the blob is about to disappear, so it can neither be referenced as is nor rewritten yet.
    protected static final String MARK_DELETING = "D";

    // Value of a mark meaning that this run is done deleting the blob. A blob write that finds this value may
    // proceed immediately: the blob is gone, so the write will recreate it, and this run won't consider it again.
    protected static final String MARK_DELETED = "X";

    /**
     * Outcome of marking a blob key as in use.
     */
    protected enum MarkResult {
        /** No garbage collection was in progress, nothing was marked. */
        NO_GC,
        /** The key is marked as in use, the run will not delete the blob. */
        MARKED,
        /**
         * The garbage collection claimed the key first and deleted the blob. The caller must write the blob again
         * instead of just referencing the key, which now points to nothing.
         */
        DELETED
    }

    // TTL of a cluster-wide mark; it only has to outlive the garbage collection run that it protects a key from, as
    // marks are scoped to a run and are therefore meaningless once the run is over. A run that lasts longer than this
    // aborts instead of deleting (see #marksMayHaveExpired), as its marks can no longer be trusted to be complete.
    // Not final so that tests don't have to wait for it.
    protected static Duration GC_MARK_TTL = Duration.ofDays(1); // NOSONAR

    // How long a node caches its view of whether a garbage collection is in progress on this store, to avoid a
    // key/value store read on every blob write. This is also the minimum delay between the acquisition of the lock and
    // the computation of the deletion candidates, so that all nodes see the garbage collection before it snapshots.
    // Not final so that tests don't have to wait for it.
    protected static Duration GC_ACTIVE_CACHE = Duration.ofSeconds(5); // NOSONAR

    // Maximum number of marks read from the key/value store in a single batch.
    protected static final int GC_MARK_BATCH_SIZE = 500;

    // How long a blob write waits for this garbage collection run to finish deleting the very blob being written,
    // before giving up and failing the write. A blob usually has a single version, so deleting it is a single backend
    // call: this only has to cover one of them, plus the time for the deleting node to publish the outcome.
    // Not final so that tests can lower it.
    protected static Duration GC_DELETION_WAIT = Duration.ofSeconds(30); // NOSONAR

    // How often a blob write waiting for a deletion re-checks whether it is over.
    protected static final Duration GC_DELETION_POLL = Duration.ofMillis(50);

    // volatile as this is designed to be called from another thread
    protected volatile long startTime;

    // The counter of the garbage collection run in progress, null if there is none. Also used as the value of the
    // cluster-wide lock, so that a node can tell not only that a garbage collection is in progress on a store but also
    // which run it is. Volatile as this is designed to be read from other threads.
    protected volatile String runCounter;

    // Heartbeat refreshing the TTL of the cluster-wide lock for as long as the garbage collection is in progress.
    protected ScheduledExecutorService lockHeartbeat;

    // Conservative lease deadline, measured from the start of the last successful acquisition or renewal request.
    protected volatile long lockDeadline;

    // Set if renewal fails because the lock changed, or the locally tracked lease expires; stop() then aborts as this
    // node can no longer guarantee it has an exclusive, up-to-date view of what is marked, and leaves the remaining
    // blobs to the next garbage collection run. Volatile as both the heartbeat and stop() threads access it.
    protected volatile boolean lockLost;

    // This node's cached view of the counter of the garbage collection run in progress on this store on another node,
    // null if there is none. Volatile as this is read and refreshed from the threads doing blob writes.
    protected volatile String remoteRunCounter;

    // Deadline (in System.nanoTime()) after which the cached view above must be refreshed. Initialized to now, so that
    // it is expired until first read; compared using a difference, which is immune to nanoTime() overflow.
    protected volatile long remoteRunCounterDeadline = System.nanoTime();

    protected BinaryManagerStatus status;

    // State of the keys involved in this GC, keyed by blob key: MARK_IN_USE, MARK_DELETING or MARK_DELETED. Positive
    // marking (rather than removing from toDelete) so that a mark arriving before computeToDelete() has added the key
    // as a candidate isn't lost. The map is thread-safe (ConcurrentHashMap, whose putIfAbsent is also what makes the
    // claim atomic) because mark() is called from other threads (e.g. concurrent blob writes); the field is also
    // volatile to publish the reference, which start()/stop() reassign (null when no GC is in progress).
    @SuppressWarnings("java:S3077") // volatile is for reference publication; the value is already a thread-safe type
    protected volatile Map<String, String> marked;

    // Keys candidate for deletion, computed at start(). Confined to the GC thread (populated by computeToDelete(),
    // consumed by stop()); mark() doesn't touch it, so it needs neither thread-safety nor volatile.
    protected Set<String> toDelete;

    /**
     * Gets the id of the blob provider this garbage collector belongs to, used to pick a key/value store dedicated to
     * this provider (see {@link #GC_KV_STORE_NAME_PREFIX}).
     * <p>
     * Unlike {@link #getStorageAddress()}, which may be a bucket/path-like value unsuitable as a storage name, this is the short,
     * stable id under which the blob provider is registered.
     */
    protected abstract String getId();

    @Override
    public boolean isInProgress() {
        return startTime != 0;
    }

    @Override
    public void start(boolean delete) {
        if (startTime != 0) {
            throw new GCAlreadyStartedException("Already started");
        }
        // acquire the cluster-wide lock for this store, so that two garbage collections (for instance two overlapping
        // scheduled runs) cannot sweep the same store from different nodes at the same time
        String runCounter = acquireLock();
        if (runCounter == null) {
            throw new GCAlreadyStartedException(
                    "Already started for " + getId() + " by another node: " + getLockInfo());
        }
        this.runCounter = runCounter;
        boolean completedAbruptly = true;
        try {
            log.info("Starting blob GC for {} (run {})", getId(), runCounter);
            if (!delete && RECOVER_RECOVERY) {
                recoverRecoveryDirectory();
            }
            startTime = System.nanoTime();
            status = new BinaryManagerStatus();
            // allocate the thread-safe marked map before computeToDelete(), so that blobs written concurrently during
            // the traversal are recorded as in use even before their key is added as a candidate
            marked = new ConcurrentHashMap<>();
            toDelete = new HashSet<>();
            // wait for the other nodes to notice that a garbage collection is in progress, so that the blobs they
            // write from now on are marked in the key/value store before we snapshot the deletion candidates
            awaitRemoteNodes();
            computeToDelete();
            completedAbruptly = false;
        } finally {
            if (completedAbruptly) {
                // don't leave the store locked if the garbage collection could not be started
                startTime = 0;
                marked = null;
                toDelete = null;
                this.runCounter = null;
                releaseLock(runCounter);
            }
        }
    }

    /**
     * Computes the keys candidate for deletion, adding them to {@link #toDelete}.
     */
    public abstract void computeToDelete();

    @Override
    public void stop(boolean delete) {
        if (startTime == 0) {
            throw new RepositoryException("Not started");
        }
        log.info("Stopping blob GC for {} ...", getId());
        try {
            if (hasLostLock()) {
                // the cluster-wide lock was lost or may have expired during this run, this node's view of what is
                // marked can no longer be trusted as exclusive and up-to-date; abort without deleting anything and
                // leave the remaining blobs to the next garbage collection run
                log.warn("Aborting blob GC for {}: the cluster-wide lock was lost during this run", getId());
                return;
            }
            if (marksMayHaveExpired()) {
                log.warn("Aborting blob GC for {}: the run outlasted the mark TTL", getId());
                return;
            }
            // delete the candidate blobs that were not marked as in use, and update the status
            Map<String, String> marked = this.marked;
            // the keys marked locally are known, but blobs written on other nodes during this run were marked in the
            // key/value store, so check there too before deleting anything
            Set<String> unmarked = new HashSet<>();
            for (String key : toDelete) {
                if (!marked.containsKey(key)) {
                    unmarked.add(key);
                }
            }
            retainNotMarkedRemotely(unmarked);
            for (String key : unmarked) {
                if (hasLostLock()) {
                    // lock lost partway through the deletion loop, abort the remainder for the same reason as above
                    log.warn("Aborting blob GC for {}: the cluster-wide lock was lost during this run", getId());
                    return;
                }
                if (marksMayHaveExpired()) {
                    // the deletion loop itself outlasted the mark TTL, abort the remainder for the same reason
                    log.warn("Aborting blob GC for {}: the run outlasted the mark TTL", getId());
                    return;
                }
                if (delete) {
                    // the check above is a snapshot taken before this loop, which may run for a long time; claim the
                    // key atomically instead, so that a blob write cannot start referencing it between the check and
                    // the deletion below. A write that loses the race waits for the deletion and rewrites the blob.
                    if (!claimForDeletion(key)) {
                        log.debug("Keeping blob marked as in use: {}", key);
                        continue;
                    }
                } else if (marked.containsKey(key)) {
                    // marked after the snapshot above (e.g. by a concurrent blob write), don't count it
                    continue;
                }
                long length;
                try {
                    if (delete && hasLostLock()) {
                        log.warn("Aborting blob GC for {}: the cluster-wide lock was lost during this run", getId());
                        return;
                    }
                    length = removeUnmarkedBlob(key, delete);
                } finally {
                    if (delete) {
                        // always publish the outcome, so that a blob write waiting on the claim is released even if
                        // the deletion failed
                        releaseDeletionClaim(key);
                    }
                }
                if (length < 0) {
                    // blob not present (shouldn't happen except if concurrently removed); don't count it
                    continue;
                }
                status.setSizeBinariesGC(status.getSizeBinariesGC() + length);
                status.setNumBinariesGC(status.getNumBinariesGC() + 1);
                status.setSizeBinaries(status.getSizeBinaries() - length);
                status.setNumBinaries(status.getNumBinaries() - 1);
            }
            if (delete && !DELETE_WITH_RECOVERY) {
                removeRecoveryDirectory();
            }
            status.setDuration(Duration.ofNanos(System.nanoTime() - startTime));
            log.info("Completed blob GC for {}: {}", getId(), status);
        } catch (RuntimeException e) {
            log.error("Failed blob GC for {}", getId(), e);
            throw e;
        } finally {
            marked = null;
            toDelete = null;
            startTime = 0;
            String runCounter = this.runCounter;
            this.runCounter = null;
            releaseLock(runCounter);
        }
    }

    /**
     * Checks whether this run has been going on for long enough that the cluster-wide marks written at its beginning
     * may already have expired.
     * <p>
     * Marks protect keys written on other nodes for {@link #GC_MARK_TTL} only, while the lock is heartbeated for as
     * long as the run lasts. A run that outlives its own marks would see a referenced key as unmarked and delete its
     * blob, so it must stop being destructive instead; the remaining blobs are left to the next run.
     */
    protected boolean marksMayHaveExpired() {
        return System.nanoTime() - startTime >= GC_MARK_TTL.toNanos();
    }

    /**
     * Gets the counter of the garbage collection run in progress, or {@code null} if there is none.
     * <p>
     * A run counter is unique to a single garbage collection run of a single store, cluster-wide, and never reused.
     */
    public String getRunCounter() {
        return runCounter;
    }

    /**
     * Gets the key under which the cluster-wide garbage collection lock for this store is held.
     */
    protected String getLockKey() {
        return GC_LOCK_KEY_PREFIX + getId();
    }

    /**
     * Gets the key holding the cluster-wide counter from which this store's run counters are allocated.
     */
    protected String getRunCounterKey() {
        return GC_RUN_COUNTER_KEY_PREFIX + getId();
    }

    /**
     * Gets the key/value store holding the cluster-wide garbage collection locks and marks, or {@code null} if there
     * is none.
     * <p>
     * There is always one in production. There is none in plain unit tests running without a runtime, where there is
     * no cluster either, so nothing has to be coordinated.
     */
    protected KeyValueStore getKVStore() {
        KeyValueService service;
        if (!Framework.isInitialized() || (service = Framework.getService(KeyValueService.class)) == null) {
            return null;
        }
        return service.getKeyValueStore(GC_KV_STORE_NAME_PREFIX + getId());
    }

    /**
     * Gets the current value of the cluster-wide garbage collection lock for this store, for logging purposes.
     */
    protected String getLockInfo() {
        return getKVStore().getString(getLockKey());
    }

    /**
     * Acquires the cluster-wide lock for this store, allocating a new run counter recorded as its value, and starts
     * sending the heartbeat that refreshes its TTL.
     *
     * @return the run counter, or {@code null} if the lock could not be acquired
     */
    protected String acquireLock() {
        KeyValueStore store = getKVStore();
        // allocate a run counter from a cluster-wide counter; it is monotonic and never reused, even across restarts,
        // so the marks of this run (see getMarkKey) can never be confused with those of any other run
        String runCounter = Long.toString(store.addAndGet(getRunCounterKey(), 1));
        long now = System.nanoTime();
        if (!store.compareAndSet(getLockKey(), null, runCounter, GC_LOCK_TTL.toSeconds())) {
            // another node is garbage collecting this store; the allocated run counter is simply skipped
            return null;
        }
        lockDeadline = now + GC_LOCK_TTL.toNanos();
        lockLost = false;
        // a garbage collection lasts much longer than the lock TTL, so a heartbeat refreshes it regularly; if this
        // node dies the heartbeat stops and the lock expires, which lets another node garbage collect this store
        lockHeartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "blob-gc-lock-heartbeat-" + getId());
            thread.setDaemon(true);
            return thread;
        });
        lockHeartbeat.scheduleWithFixedDelay(() -> heartbeatLock(store), GC_LOCK_HEARTBEAT.toSeconds(),
                GC_LOCK_HEARTBEAT.toSeconds(), TimeUnit.SECONDS);
        return runCounter;
    }

    // never throws, otherwise the scheduled heartbeat would stop
    protected void heartbeatLock(KeyValueStore store) {
        try {
            long now = System.nanoTime();
            if (hasLostLock()) {
                return;
            }
            // atomically refresh only if the lock still holds this run's own value, so that a stale node whose lock
            // expired and was taken over by another node doesn't keep extending that other node's lock
            if (!store.compareAndSet(getLockKey(), runCounter, runCounter, GC_LOCK_TTL.toSeconds())) {
                log.warn("Lost blob GC lock for {} (run {})", getId(), runCounter);
                lockLost = true;
                remoteRunCounterDeadline = System.nanoTime();
            } else if (!hasLostLock()) {
                // A renewal must not revive a run whose lease may have lapsed while the request was in flight.
                lockDeadline = now + GC_LOCK_TTL.toNanos();
            }
        } catch (RuntimeException e) { // NOSONAR
            log.warn("Failed to send blob GC lock heartbeat for {}", getId(), e);
        }
    }

    protected boolean hasLostLock() {
        if (System.nanoTime() - lockDeadline >= 0) {
            lockLost = true;
            remoteRunCounterDeadline = System.nanoTime();
        }
        return lockLost;
    }

    /**
     * Releases the cluster-wide lock for this store, if it is still held by the given run.
     */
    protected void releaseLock(String runCounter) {
        if (lockHeartbeat != null) {
            lockHeartbeat.shutdownNow();
            lockHeartbeat = null;
        }
        if (runCounter != null) {
            // only release our own lock, in case it expired and was acquired by another node in the meantime
            getKVStore().compareAndSet(getLockKey(), runCounter, null);
        }
        // the lock changed, don't let blob writes on this node use a stale view of it
        remoteRunCounterDeadline = System.nanoTime();
    }

    /**
     * Removes from the backend the unmarked blob with the given key if {@code delete} is true, and returns its length.
     *
     * @return the blob length, or a negative value if the blob is not present (then it isn't counted in the status)
     */
    protected abstract long removeUnmarkedBlob(String key, boolean delete);

    /**
     * Permanently removes the recovery directory.
     */
    protected void removeRecoveryDirectory() {
        // no recovery directory by default
    }

    /**
     * Restores blobs from the recovery directory.
     */
    protected void recoverRecoveryDirectory() {
        // no recovery directory by default
    }

    @Override
    public boolean mark(String key) {
        // records the key as in use; may be called with no GC in progress (e.g. on every blob write), then a no-op
        MarkResult result = markLocally(key);
        if (result == MarkResult.NO_GC) {
            // no GC in progress in this JVM, but one may be running on another node, in which case the key must be
            // marked in the key/value store, which is the only thing that node can see
            result = markRemotely(key);
        }
        return result != MarkResult.DELETED;
    }

    /**
     * Marks the key as in use for a garbage collection running in this JVM, waiting if it is being deleted right now.
     */
    protected MarkResult markLocally(String key) {
        // read the volatile field once: stop() clears it, and the map must not become null under our feet
        Map<String, String> marked = this.marked;
        if (marked == null || lockLost) {
            return MarkResult.NO_GC;
        }
        // a GC is in progress in this JVM: the in-memory map is enough, it's the one stop() consults
        long deadline = System.nanoTime() + GC_DELETION_WAIT.toNanos();
        do {
            String previous = marked.putIfAbsent(key, MARK_IN_USE);
            if (!MARK_DELETING.equals(previous)) {
                // we either marked the key, or it was already marked in use, or this run is done deleting it
                return MARK_DELETED.equals(previous) ? MarkResult.DELETED : MarkResult.MARKED;
            }
            // the garbage collection claimed this key and is deleting the blob; wait for it to be done, after which
            // the caller writes the blob again (it can't just reference a blob that is being deleted)
            sleepBeforeRetry();
        } while (System.nanoTime() - deadline < 0);
        throw deletionTimedOut(key);
    }

    /**
     * Marks the key as in use for a garbage collection possibly running on another node, waiting if it is being
     * deleted right now.
     */
    protected MarkResult markRemotely(String key) {
        fetchRemoteRunCounter();
        String runCounter = this.remoteRunCounter;
        if (runCounter == null) {
            return MarkResult.NO_GC;
        }
        KeyValueStore store = getKVStore();
        long deadline = System.nanoTime() + GC_DELETION_WAIT.toNanos();
        String markKey = getMarkKey(runCounter, key);
        do {
            // the mark is set atomically, so that it is either seen by the garbage collecting node before it claims
            // the key for deletion, or refused because that node claimed it first; there is no window in between
            if (store.compareAndSet(markKey, null, MARK_IN_USE, GC_MARK_TTL.toSeconds())) {
                return MarkResult.MARKED;
            }
            String current = store.getString(markKey);
            if (current != null && !MARK_DELETING.equals(current)) {
                // already marked in use, or that run is done deleting it
                return MARK_DELETED.equals(current) ? MarkResult.DELETED : MarkResult.MARKED;
            }
            // either the garbage collection claimed this key and is deleting the blob, or the mark went away
            // (expired) between the two calls above; in both cases wait a bit and try again
            sleepBeforeRetry();
        } while (System.nanoTime() - deadline < 0);
        throw deletionTimedOut(key);
    }

    /**
     * Sleeps for one poll interval before checking again whether a garbage collection is done deleting the blob
     * being written.
     */
    protected void sleepBeforeRetry() {
        try {
            Thread.sleep(GC_DELETION_POLL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RepositoryException(e);
        }
    }

    /**
     * Returns the exception to throw when a garbage collection has been deleting the blob being written for too long.
     */
    protected BlobBeingDeletedException deletionTimedOut(String key) {
        // We waited for the whole deletion timeout and the key is still claimed. Two situations lead here and the
        // mark cannot tell them apart: a deletion that is simply slow, which is still in flight, or a node that
        // died between claiming the key and publishing the outcome, which left the claim behind forever. A blob
        // usually has a single version, so its deletion is a single backend call: only an unusually slow network
        // or a throttled backend can take this long.
        //
        // Proceeding would mean giving up on the very guarantee this claim exists for: a deduplicating write
        // would find the blob still present, skip writing it, and reference a blob the in-flight deletion is
        // about to remove, leaving a dangling key and a document that cannot be downloaded. That is silent data
        // loss, so fail the write instead.
        //
        // The consequences of failing are bounded and transient: only writes of this one key are affected, marks
        // are scoped to a run, and a dead node stops its lock heartbeat, so once the cluster-wide lock expires
        // the writers no longer look at this run's marks at all. Hence a retry after the lock TTL always gets
        // through, either rewriting the blob the garbage collection did delete or finding the stale claim gone.
        return new BlobBeingDeletedException("Blob GC of " + getId() + " is still deleting the blob being written: "
                + key + ", retry later", (int) GC_LOCK_TTL.toSeconds());
    }

    /**
     * Atomically claims the key for deletion by this run, so that no blob write can start referencing its blob while
     * it is being deleted.
     *
     * @return {@code false} if the key was marked as in use instead, and must therefore not be deleted
     */
    protected boolean claimForDeletion(String key) {
        if (marked.putIfAbsent(key, MARK_DELETING) != null) {
            return false; // marked in use on this node
        }
        if (!getKVStore().compareAndSet(getMarkKey(runCounter, key), null, MARK_DELETING, GC_MARK_TTL.toSeconds())) {
            // marked in use on another node; drop the local claim so that the key is simply left alone
            marked.put(key, MARK_IN_USE);
            return false;
        }
        return true;
    }

    /**
     * Publishes the fact that this run is done deleting the key, releasing any blob write waiting on the claim.
     */
    protected void releaseDeletionClaim(String key) {
        marked.put(key, MARK_DELETED);
        getKVStore().put(getMarkKey(runCounter, key), MARK_DELETED, GC_MARK_TTL.toSeconds());
    }

    /**
     * Refreshes the cached counter of the garbage collection run in progress on this store on <em>another</em> node,
     * or {@code null} if there is none.
     * <p>
     * The result is cached for a short while, so that blob writes don't each pay a key/value store read.
     * <p>
     * A failure to read the lock is propagated to the caller, which fails the blob write: we cannot tell whether a
     * garbage collection is running, so we cannot mark the key, and letting the write proceed would let it reference
     * a blob that a run may be deleting right now. Failing the write is recoverable, losing the blob is not. This
     * does not apply when there is no runtime at all (unit tests), as there is then no cluster either.
     */
    protected void fetchRemoteRunCounter() {
        long now = System.nanoTime();
        if (now - remoteRunCounterDeadline >= 0) {
            KeyValueStore store = getKVStore();
            if (store == null) {
                // no key/value store, therefore no cluster and no other node that could be garbage collecting
                return;
            }
            remoteRunCounter = store.getString(getLockKey());
            remoteRunCounterDeadline = now + GC_ACTIVE_CACHE.toNanos();
        }
    }

    /**
     * Gets the key under which a blob key is marked as in use for the given run.
     * <p>
     * The run counter is part of the key because a mark is not global state about the blob: it records the outcome of
     * a race against one run's deletion candidates. A blob in use during a run may be garbage in the next one, and a
     * mark saying that a run already deleted the blob would tell writers that they don't have to coordinate with the
     * next run at all. In addition both the writer and the garbage collector claim a mark by a compare-and-set from
     * {@code null}, which a value left over from a previous run would defeat. Namespacing the marks by run makes all
     * of them unreachable as soon as the next run starts, without having to delete them, which also means that a node
     * dying mid-run cannot leave behind marks that affect later runs.
     */
    protected String getMarkKey(String runCounter, String key) {
        return GC_MARK_KEY_PREFIX + runCounter + ':' + key;
    }

    /**
     * Retains in the given keys only those that were not marked as in use in the key/value store by another node
     * during this garbage collection run.
     */
    protected void retainNotMarkedRemotely(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        KeyValueStore store = getKVStore();
        String runCounter = this.runCounter;
        Set<String> markedRemotely = new HashSet<>();
        // map of markKey to blob key, to avoid reparsing the markKey when collecting
        Map<String, String> markKeys = new HashMap<>();
        for (String key : keys) {
            markKeys.put(getMarkKey(runCounter, key), key);
            if (markKeys.size() == GC_MARK_BATCH_SIZE) {
                collectMarkedRemotely(store, markKeys, markedRemotely);
                markKeys.clear();
            }
        }
        collectMarkedRemotely(store, markKeys, markedRemotely);
        keys.removeAll(markedRemotely);
    }

    protected void collectMarkedRemotely(KeyValueStore store, Map<String, String> markKeys,
            Set<String> markedRemotely) {
        if (markKeys.isEmpty()) {
            return;
        }
        for (String markKey : store.getStrings(markKeys.keySet()).keySet()) {
            String key = markKeys.get(markKey); // use map to avoid reparsing markKey
            log.debug("Keeping blob marked as in use by another node: {}", key);
            markedRemotely.add(key);
        }
    }

    /**
     * Waits long enough for all the other nodes to have noticed that a garbage collection is in progress on this
     * store, so that the blobs they write from now on are marked in the key/value store.
     */
    protected void awaitRemoteNodes() {
        if (getKVStore() instanceof MemKeyValueStore) {
            // an in-memory store is private to this JVM, so there is no other node that could be writing blobs and
            // nothing to wait for; blobs written here are recorded directly in the in-memory marks
            return;
        }
        try {
            // the other nodes refresh their view at most GC_ACTIVE_CACHE after we took the lock
            Thread.sleep(GC_ACTIVE_CACHE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Interrupted while starting blob GC for " + getId(), e);
        }
    }

    @Override
    public BinaryManagerStatus getStatus() {
        return status;
    }

    @Override
    public boolean isToDeleteEmpty() {
        if (toDelete != null) {
            return toDelete.isEmpty();
        }
        return false;
    }

}
