/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.hyland.openarch.common.RetryAfterHint;
import com.hyland.openarch.ecm.core.api.RepositoryException;

/**
 * Thrown when a blob write had to be given up because a garbage collection was deleting that very blob and did not
 * finish in time.
 * <p>
 * The write cannot simply proceed: the blob may still be deleted, so a deduplicating write that found it present
 * could end up referencing a blob that is about to vanish. Failing is therefore deliberate, and the condition is
 * transient: the garbage collection either finishes deleting the blob, after which a retry rewrites it, or the node
 * running it died, after which its lock expires and a retry no longer sees the claim at all.
 */
public class BlobBeingDeletedException extends RepositoryException implements RetryAfterHint {

    private static final long serialVersionUID = 1L;

    private final transient int retryAfterSeconds;

    public BlobBeingDeletedException(String message, int retryAfterSeconds) {
        super(message, SC_SERVICE_UNAVAILABLE);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
