/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.upload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.hyland.openarch.rest.upload.handler.UploadHandler;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

/**
 * Releases the blob storage of the uploads consumed by the current request, once its transaction has committed. A
 * request that rolls back releases nothing, so its uploads stay whole and can be attached again.
 * <p>
 * The upload entries themselves are kept alive (with their parameters: status, attachStatus, attachDocId, ...) so
 * clients can poll the upload info endpoint to observe the attach phase outcome. They will eventually be cleaned up by
 * the underlying transient store.
 */
@Component
@Scope("prototype")
public class UploadHandlerCleaner {

    private static final Logger log = LoggerFactory.getLogger(UploadHandlerCleaner.class);

    static ThreadLocal<TrackedUploads> trackedUploads = ThreadLocal.withInitial(TrackedUploads::new);

    private final UploadHandler handler;

    public UploadHandlerCleaner(UploadHandler handler) {
        this.handler = handler;
    }

    public void add(String id) {
        TrackedUploads tracked = trackedUploads.get();
        if (!tracked.cleanupRegistered) {
            if (!TransactionHelper.isTransactionActiveOrPreparing()) {
                // Nothing to hang the cleanup off. Tracking the id anyway would strand it on this pooled thread and
                // have the blobs released during an unrelated later request, so don't track it at all: the blobs
                // just wait for the transient store to expire them.
                log.warn("Not tracking upload {} for blob release: no active transaction", id);
                return;
            }
            // Once per transaction: release on commit, and drop the tracking however the transaction ends, so a
            // rolled-back request leaves nothing behind on the pooled thread.
            TransactionHelper.registerSynchronization(new ReleaseBlobsSynchronization());
            tracked.cleanupRegistered = true;
        }
        tracked.ids.add(id);
    }

    public Set<String> get() {
        return Collections.unmodifiableSet(trackedUploads.get().ids);
    }

    protected void releaseBlobs() {
        trackedUploads.get()
                      .ids
                      .stream()
                      .map(handler::getUpload)
                      .filter(Objects::nonNull)
                      .forEach(Upload::releaseBlobs);
    }

    /** The upload ids consumed by the request running on this thread, and whether their cleanup is registered. */
    protected static final class TrackedUploads {

        protected final Set<String> ids = new HashSet<>();

        protected boolean cleanupRegistered;
    }

    /**
     * Frees the blob storage of the tracked uploads once the transaction that consumed them has committed, and drops
     * the thread-local tracking whatever the outcome, so a pooled thread does not carry orphaned upload ids into the
     * next request.
     */
    protected class ReleaseBlobsSynchronization implements Synchronization {

        @Override
        public void beforeCompletion() {
            // nothing to do
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == Status.STATUS_COMMITTED) {
                    releaseBlobs();
                }
            } catch (RuntimeException e) {
                // the transaction is over, there is nothing left to fail: what was not released is collected by the
                // transient store when its entries expire
                log.error("Failed to release the blob storage of uploads {}", trackedUploads.get().ids, e);
            } finally {
                trackedUploads.remove();
            }
        }
    }

}
