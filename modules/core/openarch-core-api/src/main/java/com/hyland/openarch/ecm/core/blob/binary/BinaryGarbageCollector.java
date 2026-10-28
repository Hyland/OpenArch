/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob.binary;

/**
 * A Garbage Collector for a {@link BinaryManager}.
 * <p>
 * First, inform the GC that it is started by calling {@link #start(boolean)}.
 * <p>
 * The run may be restricted to a {@link BlobKeySlot slot} of the key space using
 * {@link #start(boolean, BlobKeySlot)}, in which case only the binaries
 * whose key belongs to that slot are candidates for deletion, and marks for keys outside of it are ignored.
 * <p>
 * Then for all binaries to mark, call {@link #mark}.
 * <p>
 * Finally when all binaries have been marked, call{@link #stop} to delete the non-marked binaries.
 * <p>
 * After this, {@link #getStatus} returns information about the binaries remaining and those that have been GCed.
 */
public interface BinaryGarbageCollector {

    /**
     * Gets the address of the underlying storage used by this garbage collector. Two garbage collectors that share
     * the same storage (and therefore would impact the same files) must return the same address.
     *
     * @return the storage address
     */
    String getStorageAddress();

    /**
     * Starts the garbage collection process.
     * <p>
     * After this, all active binaries must be fed to the {@link #mark} method.
     *
     * @param delete the deletion mode that will be passed to {@link #stop(boolean)}
     */
    default void start(boolean delete) {
        start(delete, BlobKeySlot.ALL);
    }

    /**
     * Starts the garbage collection process, restricted to a slot of the key space.
     * <p>
     * After this, all active binaries must be fed to the {@link #mark} method. Only the binaries whose key belongs to
     * the slot are candidates for deletion.
     *
     * @param delete the deletion mode that will be passed to {@link #stop(boolean)}
     * @param slot the slot of the key space to garbage collect, {@link BlobKeySlot#ALL} for the whole key space
     */
    void start(boolean delete, BlobKeySlot slot);

    /**
     * Returns {@code true} if the blob store (or slot) is empty after {@link #start(boolean, BlobKeySlot)}, meaning
     * the mark phase can be skipped. Only meaningful to call just after {@link #start(boolean, BlobKeySlot)}.
     */
    default boolean isToDeleteEmpty() {
        return false;
    }

    /**
     * Marks a binary as being in use.
     *
     * @param key the binary's key
     * @return {@code true} if the binary was left alone and its key may be referenced as is, {@code false} if a
     *         garbage collection deleted it instead and the caller must write it again
     */
    boolean mark(String key);

    /**
     * Stops the garbage collection process and deletes all binaries that have not been marked (sweep).
     *
     * @param delete {@code true} if actual deletion must be performed, {@code false} if the binaries to delete should
     *            simply be counted in the status
     */
    void stop(boolean delete);

    /**
     * Gets the status of the binaries to GC and of those that won't be.
     * <p>
     * Available after {@link #stop}.
     *
     * @return the status
     */
    BinaryManagerStatus getStatus();

    /**
     * Checks if a GC is in progress.
     * <p>
     * A GC is in progress is {@code #start} has been called but not {@code #stop}.
     * <p>
     * It's only useful to call this from a separate thread from the one that calls {@link #mark}.
     *
     * @return {@code true} if a GC is in progress
     */
    boolean isInProgress();

}
