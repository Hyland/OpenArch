/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Thierry Delprat <tdelprat@nuxeo.com>
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */

package org.nuxeo.ecm.core.transientstore.api;

import java.util.Map;
import org.nuxeo.ecm.core.api.Blob;

/**
 * Service Interface for managing a transient store.
 * <p>
 * Allows to store entries in 2 sub parts: a single blob stored on a file system along with a map of parameters.
 *
 * @since 7.2
 */
public interface TransientStore {

    /**
     * Returns {@code true} if an entry exists with the given {@code key}.
     *
     * @since 7.10
     */
    boolean exists(String key);

    /**
     * Sets {@code parameter} to {@code value} in the entry with the given {@code key}.
     * <p>
     * If entry does not exist a new entry is created. If {@code parameter} already exists in the entry it is
     * overwritten.
     *
     * @since 7.10
     */
    void putParameter(String key, String parameter, Object value);

    /**
     * Gets the value of {@code parameter} in the entry with the given {@code key}.
     * <p>
     * Returns {@code null} if entry or parameter does not exist.
     *
     * @since 7.10
     */
    Object getParameter(String key, String parameter);

    /**
     * Puts {@code parameters} in the entry with the given {@code key}. Overwrites any existing parameter in the entry.
     * <p>
     * If entry does not exist a new entry is created.
     *
     * @since 7.10
     */
    void putParameters(String key, Map<String, Object> parameters);

    /**
     * Gets values of the parameters in the entry with the given {@code key}.
     * <p>
     * Returns {@code null} if entry does not exist.
     *
     * @since 7.10
     */
    Map<String, Object> getParameters(String key);

    /**
     * Associates {@code blob} with the entry with the given {@code key}. Pass {@code null} to clear any existing blob.
     * <p>
     * If entry does not exist a new entry is created.
     *
     * @since 7.10
     */
    void putBlob(String key, Blob blob);

    /**
     * Gets the blob associated with the entry with the given {@code key}.
     * <p>
     * Returns {@code null} if entry does not exist or has no blob.
     *
     * @since 7.10
     */
    Blob getBlob(String key);

    /**
     * Returns the size of the blob associated with the entry with the given {@code key}, or {@code 0} if the entry
     * does not exist or has no blob.
     *
     * @since 7.10
     */
    long getSize(String key);

    /**
     * Returns {@code true} if the entry with the given {@code key} is ready.
     *
     * @since 7.10
     */
    boolean isCompleted(String key);

    /**
     * Marks the entry with the given {@code key} as ready.
     * <p>
     * If entry does not exist a new entry is created.
     *
     * @since 7.10
     */
    void setCompleted(String key, boolean completed);

    /**
     * Removes entry with the given {@code key}.
     * <p>
     * Has no effect if entry does not exist.
     */
    void remove(String key);

    /**
     * Releases the entry with the given {@code key} by applying the {@code secondLevelTTL}.
     * Call this after a blob has been transferred to permanent storage so the entry survives only
     * long enough for clients to poll the attach status.
     * <p>
     * Has no effect if entry does not exist.
     */
    void release(String key);

}
