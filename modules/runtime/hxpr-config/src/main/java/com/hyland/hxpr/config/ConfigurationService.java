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
import java.util.function.UnaryOperator;

/**
 * Configuration service, responsible for persisting JSON-like sets of configuration and managing cluster-wide
 * notifications when configuration changes.
 * <p>
 * A service changing a configuration both persists it and sets it on itself, and these methods do the two together,
 * taking the configuration in the form the service holds it in (see {@link Configurable}). Which one to call depends on
 * whether the caller holds a version to check the write against:
 * <ul>
 * <li><b>No version</b> — {@link #setConfiguration}, or {@link #updateConfiguration} when the new configuration is
 * derived from the current one. A service simply changing its own configuration, last-write-wins; it cannot be rejected.
 * This is what the version-unaware (v1) endpoints use, and any internal caller that just sets a configuration.</li>
 * <li><b>A version</b> — {@link #setConfigurationIfVersionMatches}. A client-driven update that must not overwrite
 * someone else's, as the optimistic-locking (v2) endpoints require: the caller computes the whole new configuration from
 * the snapshot {@link #readConfigurationForUpdate} returns, and passes it with the version that snapshot had. If the
 * stored version has moved since, the write is rejected with a concurrent-update exception (HTTP 409) and nothing
 * changes.</li>
 * </ul>
 * All of them persist the configuration and set it locally as one critical section per repository and configuration,
 * shared with the read-and-set that a received invalidation performs, so that a node's copy of a configuration cannot end
 * up disagreeing with what that same node has stored. The write always comes first: it is the step that can fail, and a
 * configuration set locally before a write that then fails would be served by this node without being stored anywhere,
 * with no invalidation sent to repair the other nodes.
 * <p>
 * The bare {@link #writeConfiguration} methods persist without touching local state; they are for callers with no local
 * state to keep in step, such as bootstrap.
 */
public interface ConfigurationService {

    /**
     * Writes the configuration {@code configName} for the repository {@code repositoryName}.
     * <p>
     * A configuration identical to the stored one is not written at all (and therefore sends no invalidations).
     *
     * @param repositoryName the repository name
     * @param configName the configuration name
     * @param config the configuration
     */
    void writeConfiguration(String repositoryName, String configName, Map<String, Object> config);

    /**
     * Writes the configuration {@code configName} for the repository {@code repositoryName} using optimistic locking:
     * the write only succeeds if the persisted version still matches {@code expectedVersion}, otherwise a
     * concurrent-update exception (HTTP 409) is thrown.
     * <p>
     * A configuration identical to the stored one is not written at all and its version is returned unchanged, so
     * applying the same configuration repeatedly is free and cannot conflict. A stale {@code expectedVersion} is still
     * rejected, even when the configuration sent is identical.
     *
     * @param repositoryName the repository name
     * @param configName the configuration name
     * @param config the configuration
     * @param expectedVersion the version the caller last read
     * @return the new version, or the current one if nothing had to be written
     * @throws RuntimeException a concurrent-update exception (HTTP 409) if the persisted version no longer matches
     *             {@code expectedVersion}
     */
    long writeConfiguration(String repositoryName, String configName, Map<String, Object> config, long expectedVersion);

    /**
     * Sets {@code state} as {@code configurable}'s configuration and writes it, without optimistic locking
     * (last-write-wins).
     * <p>
     * Writing it and setting it locally are one critical section per repository and configuration, shared with the
     * optimistic-locking write and with the read-and-set of a received invalidation. Left unsynchronized, two concurrent
     * writers can persist in one order and set their configuration in the other, leaving this node holding a
     * configuration that is not the one it stored.
     * <p>
     * It is set on the service only once the write has gone through, as in {@link #setConfigurationIfVersionMatches}: a
     * failed write leaves the local configuration untouched rather than making this node serve something it never
     * stored.
     *
     * @param <T> how the service holds its configuration in memory
     * @param repositoryName the repository name
     * @param configurable the service whose configuration is being set
     * @param state the new configuration
     */
    <T> void setConfiguration(String repositoryName, Configurable<T> configurable, T state);

    /**
     * Writes {@code state} as {@code configurable}'s configuration using optimistic locking, exactly as
     * {@link #writeConfiguration(String, String, Map, long)} does, and then, only if the write succeeded, sets it on
     * the configurable.
     * <p>
     * The two steps are one critical section per repository and configuration, so writers set their configuration
     * locally in the order they persisted. Left unsynchronized, a writer that persisted an older version can set its
     * content <em>after</em> a newer writer already set theirs: storage keeps the newer version, but this node goes on
     * serving the older content indefinitely, because the invalidations that would repair it are only sent to the other
     * nodes.
     * <p>
     * The critical section is per node, which is the right scope: what it protects is this node's own in-memory copy.
     * Consistency of the stored configuration between nodes is what {@code expectedVersion} is for, and the same critical
     * section also covers the read-and-set a received invalidation performs, so a node's local copy cannot end up behind
     * the configuration it has itself stored.
     * <p>
     * {@code state} is set on the service while the lock is held, so setting it must not block on anything slow. It is
     * not set at all when the write is rejected, which is what leaves a rejected update with its local state untouched.
     *
     * @param <T> how the service holds its configuration in memory
     * @param repositoryName the repository name
     * @param configurable the service whose configuration is being set
     * @param state the new configuration
     * @param expectedVersion the version the caller last read
     * @return the new version, or the current one if nothing had to be written
     * @throws RuntimeException a concurrent-update exception (HTTP 409) if the persisted version no longer matches
     *             {@code expectedVersion}
     */
    <T> long setConfigurationIfVersionMatches(String repositoryName, Configurable<T> configurable, T state,
            long expectedVersion);

    /**
     * Derives {@code configurable}'s new configuration from the one currently persisted, then writes it and sets it
     * locally, without optimistic locking, exactly as {@link #setConfiguration} does.
     * <p>
     * {@code update} is applied to the persisted configuration — the one this write replaces — and not to the one the
     * service holds, which lags behind it while a cluster invalidation is in flight; this is the snapshot
     * {@link #readConfigurationForUpdate} describes, and an update merging into or comparing against the local copy
     * instead would drop what this node has not seen yet. Only when nothing is persisted at all, at bootstrap, is the
     * local configuration the current one.
     * <p>
     * {@code update} runs inside the critical section, so the configuration it derives its result from cannot change
     * under it. That is the difference from reading the configuration and calling {@link #setConfiguration}, which leaves
     * room for another writer in between.
     * <p>
     * The write is conditioned on the version the configuration was read at, so a write from another node in between is
     * a conflict rather than a silent overwrite; {@code update} is then applied again to what that node wrote. It must
     * therefore be free of side effects, since it may run several times. A conflict that outlives the retries is
     * reported to the caller as a concurrent-update exception (HTTP 409).
     *
     * @param <T> how the service holds its configuration in memory
     * @param repositoryName the repository name
     * @param configurable the service whose configuration is being set
     * @param update derives the new configuration from the current one
     */
    <T> void updateConfiguration(String repositoryName, Configurable<T> configurable, UnaryOperator<T> update);

    /**
     * Reads the configuration {@code configName} for the repository {@code repositoryName} together with its
     * optimistic-locking version.
     *
     * @param repositoryName the repository name
     * @param configName the configuration name
     * @return the configuration and its version (version {@code 0} when unknown or absent)
     */
    ConfigurationWithVersion readConfiguration(String repositoryName, String configName);

    /**
     * Reads the configuration snapshot that an optimistic-locking update must be computed from: the persisted
     * configuration, together with the version the write will be conditioned on.
     * <p>
     * An update has to be computed from this snapshot rather than from the service's own local configuration. A node's
     * local configuration lags behind the persisted one while a cluster invalidation is in flight, so an update computed
     * from it would be written under a version that describes different content: the write would be accepted, since the
     * version does match, and would silently revert everything this node had not seen yet. Validation and conflict
     * checks have to run against this snapshot for the same reason.
     * <p>
     * When nothing has been persisted yet — at bootstrap, or on a backend that does not persist configuration — the
     * {@code configurable}'s local configuration is the state to build on, and the version is {@code 0}.
     *
     * @param repositoryName the repository name
     * @param configurable the service whose configuration is being updated
     * @return the configuration to compute the update from, and the version to condition the write on
     */
    ConfigurationWithVersion readConfigurationForUpdate(String repositoryName, Configurable<?> configurable);

    // admin

    void registerConfigurationServiceIO(String repositoryName, ConfigurationServiceIO engine);

    void unregisterConfigurationServiceIO(String repositoryName);

    void registerConfigurable(Configurable<?> configurable);

    void unregisterConfigurable(Configurable<?> configurable);

}
