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
 * Configuration service, responsible for persisting JSON-like sets of configuration and managing cluster-wide
 * notifications when configuration changes.
 */
public interface ConfigurationService {

    /**
     * Writes the configuration {@code configName} for the repository {@code repositoryName}.
     *
     * @param repositoryName the repository name
     * @param configName the configuration name
     * @return the configuration, or {@code null}
     */
    void writeConfiguration(String repositoryName, String configName, Map<String, Object> config);

    Map<String, Object> readConfiguration(String repositoryName, String configName);

    // admin

    void registerConfigurationServiceIO(String repositoryName, ConfigurationServiceIO engine);

    void unregisterConfigurationServiceIO(String repositoryName);

    void registerConfigurable(Configurable configurable);

    void unregisterConfigurable(Configurable configurable);

}
