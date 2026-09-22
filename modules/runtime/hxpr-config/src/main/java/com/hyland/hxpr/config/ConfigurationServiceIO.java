/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.config;

import java.util.Map;

/**
 * Interface for reading and writing configuration. One instance of this is instantiated per repository.
 */
public interface ConfigurationServiceIO {

    /**
     * Reads the given configuration together with its optimistic-locking version.
     *
     * @param configName the config name
     * @return the config and its version
     */
    ConfigurationWithVersion readConfiguration(String configName);

    /**
     * Writes the given configuration to the persistence backend.
     *
     * @param configName the config name
     * @param config the config (may not be {@code null})
     */
    void writeConfiguration(String configName, Map<String, Object> config);

    /**
     * Writes the given configuration only if its persisted version still matches {@code expectedVersion} (optimistic
     * locking), and returns the new version.
     *
     * @param configName the config name
     * @param config the config (may not be {@code null})
     * @param expectedVersion the version the caller last read
     * @return the new version
     * @throws RuntimeException a concurrent-update exception (HTTP 409) if the persisted version no longer matches
     *             {@code expectedVersion}
     */
    long writeConfiguration(String configName, Map<String, Object> config, long expectedVersion);

    /**
     * Writes the given configuration exactly as {@link #writeConfiguration(String, Map, long)} does, but reports a
     * version mismatch by returning {@code null} instead of throwing, for a caller that recomputes its update from what
     * the other writer left and tries again.
     * <p>
     * The default is for backends that do not track versions and therefore never report a conflict; a backend that
     * does track them overrides this.
     *
     * @param configName the config name
     * @param config the config (may not be {@code null})
     * @param expectedVersion the version the caller last read
     * @return the new version, or {@code null} if the persisted version no longer matches {@code expectedVersion}
     */
    default Long tryWriteConfiguration(String configName, Map<String, Object> config, long expectedVersion) {
        return writeConfiguration(configName, config, expectedVersion);
    }

}
