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
package org.nuxeo.rest.utils;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Shared pieces of the optimistic locking used by the v2 configuration APIs.
 * <p>
 * A v2 payload carries a {@value #VERSION} counter, incremented on every write that changes the configuration. A client
 * reads it, computes its update, and sends it back with the version it read; the write is then conditioned on that
 * version, so a caller working from a stale read is rejected with a {@code 409 Conflict} rather than silently
 * overwriting a concurrent change. The conflict itself needs nothing here: it is raised by the storage layer as a
 * {@link org.nuxeo.ecm.core.api.ConcurrentUpdateException}, which already maps to 409.
 * <p>
 * A configuration identical to the stored one and carrying the current version is not written and keeps its version.
 * An identical configuration carrying a stale version is still rejected with {@code 409 Conflict}.
 */
public final class OptimisticLocking {

    /** Name of the version property carried by the v2 payloads. */
    public static final String VERSION = "version";

    private OptimisticLocking() {
        // this is a utility class
    }

    /**
     * Returns the version a v2 write must be conditioned on, or fails with {@code 400 Bad Request} when the payload does
     * not carry one: without it there is nothing to detect a concurrent change against.
     *
     * @param version the version read from the request payload, possibly {@code null}
     * @return the version to condition the write on
     */
    public static long requireVersion(Long version) {
        if (version == null) {
            throw new NuxeoException("Missing required '" + VERSION + "' field for optimistic locking", SC_BAD_REQUEST);
        }
        return version;
    }

}
