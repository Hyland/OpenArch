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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;

/**
 * Basic implementation for a garbage collector recording, in memory, the candidate keys and the keys marked in use.
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

    // volatile as this is designed to be called from another thread
    protected volatile long startTime;

    protected BinaryManagerStatus status;

    // Keys marked as in use during the GC. Positive marking (rather than removing from toDelete) so that a mark
    // arriving before computeToDelete() has added the key as a candidate isn't lost. The set is thread-safe
    // (ConcurrentHashMap.newKeySet) because mark() is called from other threads (e.g. concurrent blob writes); the
    // field is also volatile to publish the reference, which start()/stop() reassign (null when no GC is in progress).
    @SuppressWarnings("java:S3077") // volatile is for reference publication; the value is already a thread-safe type
    protected volatile Set<String> marked;

    // Keys candidate for deletion, computed at start(). Confined to the GC thread (populated by computeToDelete(),
    // consumed by stop()); mark() doesn't touch it, so it needs neither thread-safety nor volatile.
    protected Set<String> toDelete;

    @Override
    public boolean isInProgress() {
        return startTime != 0;
    }

    @Override
    public void start(boolean delete) {
        if (startTime != 0) {
            throw new GCAlreadyStartedException("Already started");
        }
        log.info("Starting blob GC for {}", getId());
        if (!delete && RECOVER_RECOVERY) {
            recoverRecoveryDirectory();
        }
        startTime = System.nanoTime();
        status = new BinaryManagerStatus();
        // allocate the thread-safe marked set before computeToDelete(), so that blobs written concurrently during the
        // traversal are recorded as in use even before their key is added as a candidate
        marked = ConcurrentHashMap.newKeySet();
        toDelete = new HashSet<>();
        computeToDelete();
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
            // delete the candidate blobs that were not marked as in use, and update the status
            Set<String> marked = this.marked;
            for (String key : toDelete) {
                if (marked.contains(key)) {
                    // marked as in use (possibly by a concurrent blob write); keep it
                    continue;
                }
                long length = removeUnmarkedBlob(key, delete);
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
        }
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
    public void mark(String key) {
        // records the key as in use; may be called with no GC in progress (e.g. on every blob write), then a no-op
        Set<String> marked = this.marked;
        if (marked != null) {
            marked.add(key);
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
