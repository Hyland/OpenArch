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

import org.nuxeo.runtime.api.Framework;

/**
 * Interface implemented by a service that wishes to have its configuration persisted by the
 * {@link ConfigurationService}.
 */
public interface Configurable {

    /**
     * Gets the config name used for this {@link Configurable}.
     *
     * @return the config name
     */
    String getConfigurableConfigName();

    /**
     * Gets the local configuration for {@code repositoryName}.
     *
     * @param repositoryName the repository name
     * @return the local configuration.
     */
    Map<String, Object> getLocalConfiguration(String repositoryName);

    /**
     * Sets the configuration locally for {@code repositoryName}.
     *
     * @param repositoryName the repository name
     * @param config the configuration
     */
    void setLocalConfiguration(String repositoryName, Map<String, Object> config);

    /**
     * Internal callback used to write configuration globally after it's been set locally.
     *
     * @param repositoryName the repository name
     */
    default void writeConfigurationGlobally(String repositoryName) {
        Map<String, Object> config = getLocalConfiguration(repositoryName);
        Framework.getService(ConfigurationService.class).writeConfiguration(repositoryName, getConfigurableConfigName(), config);
    }

}
