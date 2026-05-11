/*
 * (C) Copyright 2023 Hyland (http://hyland.com/) and others.
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
 *     Florent Guillaume
 */
package com.hyland.hxpr.config;

import java.util.Map;

/**
 * Interface for reading and writing configuration. One instance of this is instantiated per repository.
 */
public interface ConfigurationServiceIO {

    /**
     * Reads the given configuration from the persistence backend.
     *
     * @param configName the config name
     * @return the config, or {@code null}
     */
    Map<String, Object> readConfiguration(String configName);

    /**
     * Writes the given configuration to the persistence backend.
     *
     * @param configName the config name
     * @param config the config (may not be {@code null})
     */
    void writeConfiguration(String configName, Map<String, Object> config);

}
