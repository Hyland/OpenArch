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
package com.hyland.openarch.config;

import java.util.Map;
import java.util.function.UnaryOperator;

import com.hyland.openarch.runtime.api.Framework;

/**
 * Interface implemented by a service that wishes to have its configuration persisted by the
 * {@link ConfigurationService}.
 * <p>
 * {@code T} is how the service holds that configuration in memory — a schema manager, a map of renditions by id, a
 * record of settings — which is what its own code works with. The configuration service only ever needs to convert it
 * to and from the persisted form, and to set it on the service, so those are the four operations here: {@link
 * #getLocalState}, {@link #setLocalState}, {@link #toConfigMap} and {@link #fromConfigMap}. A service whose local form
 * <em>is</em> the persisted map uses {@code Map<String, Object>} for {@code T}, and its conversions are the identity.
 * <p>
 * Setting a configuration goes through {@link #setConfigurationGlobally} or
 * {@link #updateConfigurationGlobally}, which persist it and set it locally as one operation. A service must not set a
 * configuration on itself directly, or its local copy and what is stored can drift apart.
 *
 * @param <T> how this service holds its configuration in memory
 */
public interface Configurable<T> {

    /**
     * Gets the config name used for this {@link Configurable}.
     *
     * @return the config name
     */
    String getConfigurableConfigName();

    /**
     * Gets the configuration this service currently holds for {@code repositoryName}.
     *
     * @param repositoryName the repository name
     * @return the configuration held in memory
     */
    T getLocalState(String repositoryName);

    /**
     * Makes {@code state} the configuration this service holds for {@code repositoryName}.
     * <p>
     * Called by the configuration service only, while it holds the lock of this configuration, so this must not block on
     * anything slow.
     *
     * @param repositoryName the repository name
     * @param state the configuration to hold
     */
    void setLocalState(String repositoryName, T state);

    /**
     * Converts a configuration to its persisted form.
     *
     * @param state the configuration as held in memory
     * @return the configuration to store
     */
    Map<String, Object> toConfigMap(T state);

    /**
     * Reads a configuration back from its persisted form.
     *
     * @param repositoryName the repository name
     * @param config the stored configuration
     * @return the configuration to hold in memory
     */
    T fromConfigMap(String repositoryName, Map<String, Object> config);

    /**
     * Sets the configuration read back from its persisted form as this service's, without persisting anything: what
     * receiving a cluster invalidation amounts to.
     *
     * @param repositoryName the repository name
     * @param config the stored configuration
     */
    default void setLocalFromConfigMap(String repositoryName, Map<String, Object> config) {
        setLocalState(repositoryName, fromConfigMap(repositoryName, config));
    }

    /**
     * Makes {@code state} this service's configuration and persists it (sending cluster invalidations), as one
     * operation, without optimistic locking (last-write-wins).
     *
     * @param repositoryName the repository name
     * @param state the new configuration
     */
    default void setConfigurationGlobally(String repositoryName, T state) {
        Framework.getService(ConfigurationService.class).setConfiguration(repositoryName, this, state);
    }

    /**
     * Makes {@code state} this service's configuration and persists it (sending cluster invalidations), as one
     * operation, with optimistic locking on the version
     *
     * @param repositoryName the repository name
     * @param state the new configuration
     * @param expectedVersion the version the caller last read
     * @throws RuntimeException a concurrent-update exception (HTTP 409) if the persisted version no longer matches
     *             {@code expectedVersion}
     */
    default void setConfigurationGloballyIfVersionMatches(String repositoryName, T state, long expectedVersion) {
        Framework.getService(ConfigurationService.class)
                 .setConfigurationIfVersionMatches(repositoryName, this, state, expectedVersion);
    }

    /**
     * Derives this service's new configuration from the one currently persisted, then persists it and sets it
     * locally, all as one operation.
     * <p>
     * Use this rather than reading the current configuration and calling {@link #setConfigurationGlobally}: {@code
     * update} runs under the lock of this configuration, so what it derives its result from cannot change under it.
     * <p>
     * What {@code update} is applied to is the persisted configuration, which this write replaces, rather than the one
     * this service holds — that one lags behind while a cluster invalidation is in flight. Only when nothing is
     * persisted at all, at bootstrap, is the local configuration the current one.
     * <p>
     * The write is conditioned on the version that configuration was read at, so a write from another node in between
     * is a conflict rather than a silent overwrite, and {@code update} is applied again to what that node wrote. It
     * must therefore be free of side effects, since it may run several times.
     *
     * @param repositoryName the repository name
     * @param update derives the new configuration from the persisted one
     */
    default void updateConfigurationGlobally(String repositoryName, UnaryOperator<T> update) {
        Framework.getService(ConfigurationService.class).updateConfiguration(repositoryName, this, update);
    }

}
