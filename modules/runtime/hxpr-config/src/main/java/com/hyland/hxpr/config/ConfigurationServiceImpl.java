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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * Implementation of the {@link ConfigurationService}.
 */
public class ConfigurationServiceImpl extends DefaultComponent implements ConfigurationService {

    private static final Logger log = LogManager.getLogger(ConfigurationServiceImpl.class);

    public static final int APPLICATION_STARTED_ORDER = -500;

    public static final String CONFIG_INVAL_PUBSUB_TOPIC = "config";

    // @GuardedBy("this")
    protected Map<String, RepoConfigService> services = new HashMap<>();

    // @GuardedBy("this")
    protected Map<String, Configurable<?>> configurables = new HashMap<>();

    /**
     * How many times an update is recomputed and written before the conflict is reported to the caller. Contention on
     * a single configuration document is admin-level, so exhausting them means something pathological is going on.
     */
    protected static final int UPDATE_CONFIG_ATTEMPTS = 5;

    /**
     * Locks ordering the writes of each configuration and the local changes that go with them, keyed by repository name
     * and configuration name.
     */
    private final Map<String, Object> configurationLocks = new ConcurrentHashMap<>();

    /**
     * Prefix of the top-level names a persistence engine keeps for the metadata of the document holding a configuration,
     * and which a configuration therefore cannot use for its own entries: the document id and the optimistic-locking
     * version are {@code _id} and {@code _version} (see {@code MongoDBRepository}).
     * <p>
     * Most configurations have fixed top-level names and cannot collide, but some are keyed by entity name — the security
     * policies by policy name, the renditions by rendition id — and those names come from the caller. An entry named like
     * a metadata field would be overwritten by that metadata when written and dropped when read again, so such a
     * configuration is rejected here rather than being silently mangled. The whole prefix is reserved rather than the
     * exact names, so that metadata added later cannot collide with configurations already stored.
     */
    private static final String RESERVED_PREFIX = "_";

    @Override
    public int getApplicationStartedOrder() {
        return APPLICATION_STARTED_ORDER;
    }

    /** Config service for one repository. */
    private static class RepoConfigService {

        // for logs
        private final String repositoryName;

        // config kept locally pending the registration of a persistence engine
        // @GuardedBy("this")
        private Map<String, Map<String, Object>> pendingConfig = new HashMap<>();

        // @GuardedBy("this")
        private ConfigurationServiceIO engine;

        private RepoConfigService(String repositoryName) {
            this.repositoryName = repositoryName;
        }

        public void registerConfigurationServiceIO(ConfigurationServiceIO engine) {
            synchronized (this) {
                if (this.engine != null) {
                    throw new RuntimeServiceException("Engine already registered for repo: " + repositoryName);
                }
                this.engine = engine;
                log.debug("Registered persistence engine: {} for repo: {}", engine, repositoryName);

                // now flush pending config written before engine was registered
                pendingConfig.forEach((configName, config) -> {
                    log.debug("Flushing pending config: {} to persistence engine for repo: {}", configName,
                            repositoryName);
                    engine.writeConfiguration(configName, config);
                });
                pendingConfig.clear();
            }
        }

        public void unregisterConfigurationServiceIO() {
            log.debug("Unregistered persistence engine: {} for repo: {}", engine, repositoryName);
            synchronized (this) {
                engine = null;
            }
        }

        public ConfigurationWithVersion readConfiguration(String configName) {
            ConfigurationServiceIO actualEngine;
            synchronized (this) {
                actualEngine = engine;
                if (actualEngine == null) {
                    log.debug("Getting pending config: {} for repo: {}", configName, repositoryName);
                    // no persistence engine yet: no version is tracked (treated as 0)
                    return new ConfigurationWithVersion(pendingConfig.get(configName), 0L);
                }
            }
            log.debug("Getting persistent config: {} for repo: {}", configName, repositoryName);
            return actualEngine.readConfiguration(configName);
        }

        public void writeConfiguration(String configName, Map<String, Object> config) {
            ConfigurationServiceIO actualEngine;
            synchronized (this) {
                if (engine == null) {
                    log.debug("Keeping pending config: {} for repo: {}", configName, repositoryName);
                    pendingConfig.put(configName, config);
                    return;
                }
                actualEngine = engine;
            }
            log.debug("Writing persistent config: {} for repo: {}", configName, repositoryName);
            actualEngine.writeConfiguration(configName, config);
        }

        public Long tryWriteConfiguration(String configName, Map<String, Object> config, long expectedVersion) {
            ConfigurationServiceIO actualEngine;
            synchronized (this) {
                if (engine == null) {
                    // no persistence engine yet (startup): nothing to conflict with, see writeConfiguration
                    log.debug("Keeping pending config: {} for repo: {}", configName, repositoryName);
                    pendingConfig.put(configName, config);
                    return 0L;
                }
                actualEngine = engine;
            }
            log.debug("Writing persistent config: {} for repo: {} (expected version {})", configName, repositoryName,
                    expectedVersion);
            return actualEngine.tryWriteConfiguration(configName, config, expectedVersion);
        }

        public long writeConfiguration(String configName, Map<String, Object> config, long expectedVersion) {
            ConfigurationServiceIO actualEngine;
            synchronized (this) {
                if (engine == null) {
                    // no persistence engine yet (startup): nothing to conflict with, keep pending. No version is
                    // tracked here (pending reads report 0), so report 0 too rather than a version a read won't confirm.
                    log.debug("Keeping pending config: {} for repo: {}", configName, repositoryName);
                    pendingConfig.put(configName, config);
                    return 0L;
                }
                actualEngine = engine;
            }
            log.debug("Writing persistent config: {} for repo: {} (expected version {})", configName, repositoryName,
                    expectedVersion);
            return actualEngine.writeConfiguration(configName, config, expectedVersion);
        }
    }

    private RepoConfigService getServiceForRepo(String repositoryName) {
        synchronized (this) {
            return services.computeIfAbsent(repositoryName, RepoConfigService::new);
        }
    }

    @Override
    public void registerConfigurationServiceIO(String repositoryName, ConfigurationServiceIO engine) {
        RepoConfigService service = getServiceForRepo(repositoryName);
        service.registerConfigurationServiceIO(engine);

        // now that we have a persistence engine
        // init all Configurables from persisted config if any, or persist initial config
        List<Configurable<?>> actualConfigurables;
        synchronized (this) {
            actualConfigurables = new ArrayList<>(configurables.values());
        }
        actualConfigurables.forEach(configurable -> {
            if (!readConfigurationAndSetLocally(repositoryName, configurable)) {
                // no existing persisted config, persist bootstrap one
                // We write the config without sending global invalidations because at startup all pods will read the
                // same configuration and there is no need to invalidate their local configuration
                service.writeConfiguration(configurable.getConfigurableConfigName(),
                        localConfigMap(repositoryName, configurable));
            }
        });
    }

    @Override
    public void unregisterConfigurationServiceIO(String repositoryName) {
        RepoConfigService service = getServiceForRepo(repositoryName);
        service.unregisterConfigurationServiceIO();
    }

    @Override
    public void registerConfigurable(Configurable<?> configurable) {
        synchronized (this) {
            configurables.put(configurable.getConfigurableConfigName(), configurable);
        }
        // now init this Configurable from persisted config
        List<String> repositoryNames;
        synchronized (this) {
            repositoryNames = new ArrayList<>(services.keySet());
        }
        repositoryNames.forEach(repositoryName -> readConfigurationAndSetLocally(repositoryName, configurable));
    }

    @Override
    public void unregisterConfigurable(Configurable<?> configurable) {
        synchronized (this) {
            configurables.remove(configurable.getConfigurableConfigName());
        }
    }

    protected Configurable<?> getConfigurable(String configName) {
        synchronized (this) {
            return configurables.get(configName);
        }
    }

    // returns true if config exists
    protected <T> boolean readConfigurationAndSetLocally(String repositoryName, Configurable<T> configurable) {
        String configName = configurable.getConfigurableConfigName();
        // The read and what it feeds are the same critical section a write sets its state under. This is the path a
        // cluster invalidation takes, and the invalidation carries no content: what gets set is whatever storage holds
        // when it is read. Reading outside the lock would let a slow handler set content read before a write that has
        // already set its own on this node, leaving the node behind its own storage with no further invalidation to
        // repair it.
        synchronized (getConfigurationLock(repositoryName, configName)) {
            Map<String, Object> config = readConfiguration(repositoryName, configName).config();
            if (config == null) {
                return false;
            }
            try {
                configurable.setLocalFromConfigMap(repositoryName, config);
            } catch (RuntimeException e) {
                // a configuration that cannot be read back is left alone rather than taking the service down: the
                // service keeps the configuration it holds, and the problem is reported for the stored one to be fixed
                log.error("Cannot read the stored configuration: {} for repo: {}, keeping the current one", configName,
                        repositoryName, e);
                return false;
            }
            return true;
        }
    }

    @Override
    public ConfigurationWithVersion readConfiguration(String repositoryName, String configName) {
        RepoConfigService service = getServiceForRepo(repositoryName);
        return service.readConfiguration(configName);
    }

    @Override
    public ConfigurationWithVersion readConfigurationForUpdate(String repositoryName, Configurable<?> configurable) {
        ConfigurationWithVersion persisted = readConfiguration(repositoryName,
                configurable.getConfigurableConfigName());
        Map<String, Object> config = persisted.config();
        if (config == null) {
            // nothing persisted yet: the local (bootstrap) configuration is the state to build on, at version 0, which
            // is also the version a first write has to expect.
            return new ConfigurationWithVersion(localConfigMap(repositoryName, configurable), 0L);
        }
        return persisted;
    }

    /**
     * Rejects a configuration having an entry whose name is reserved for the metadata of the document it is stored in,
     * which would otherwise be silently lost. See {@link #RESERVED_PREFIX}.
     *
     * @throws IllegalArgumentException (HTTP 400) if the configuration uses a reserved name
     */
    private static void checkNoReservedKeys(String repositoryName, String configName, Map<String, Object> config) {
        config.keySet().stream().filter(key -> key.startsWith(RESERVED_PREFIX)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Configuration '" + configName + "' of repository '" + repositoryName
                    + "' cannot have an entry named '" + key + "': names starting with '" + RESERVED_PREFIX
                    + "' are reserved by the system");
        });
    }

    @Override
    public void writeConfiguration(String repositoryName, String configName, Map<String, Object> config) {
        checkNoReservedKeys(repositoryName, configName, config);
        ConfigurationWithVersion current = readConfiguration(repositoryName, configName);
        if (config.equals(current.config())) {
            log.debug("Configuration unchanged: {} for repo: {}, skipping write", configName, repositoryName);
            return;
        }
        RepoConfigService service = getServiceForRepo(repositoryName);
        service.writeConfiguration(configName, config);
        onConfigurationWritten(repositoryName, configName);
    }

    @Override
    public long writeConfiguration(String repositoryName, String configName, Map<String, Object> config,
            long expectedVersion) {
        checkNoReservedKeys(repositoryName, configName, config);
        ConfigurationWithVersion current = readConfiguration(repositoryName, configName);
        if (config.equals(current.config()) && current.version() == expectedVersion) {
            // nothing to write and the caller's version is current, so the version stays where it is. A caller whose
            // version is stale falls through to the conditional write below and still gets a conflict, even when the
            // configuration it sends is identical.
            log.debug("Configuration unchanged: {} for repo: {}, skipping write", configName, repositoryName);
            return current.version();
        }
        RepoConfigService service = getServiceForRepo(repositoryName);
        long newVersion = service.writeConfiguration(configName, config, expectedVersion);
        onConfigurationWritten(repositoryName, configName);
        return newVersion;
    }

    @Override
    public <T> void setConfiguration(String repositoryName, Configurable<T> configurable, T state) {
        String configName = configurable.getConfigurableConfigName();
        Map<String, Object> config = configurable.toConfigMap(state);
        checkNoReservedKeys(repositoryName, configName, config);
        // writing the configuration and setting it locally are one critical section, so that writers set it in the
        // order they persisted. It is set only once the write has gone through: a write that fails would otherwise
        // leave this node serving a configuration it did not store, with no invalidation sent to repair the others.
        synchronized (getConfigurationLock(repositoryName, configName)) {
            writeConfiguration(repositoryName, configName, config);
            configurable.setLocalState(repositoryName, state);
        }
    }

    @Override
    public <T> void updateConfiguration(String repositoryName, Configurable<T> configurable, UnaryOperator<T> update) {
        String configName = configurable.getConfigurableConfigName();
        RepoConfigService service = getServiceForRepo(repositoryName);
        for (int attempt = 1;; attempt++) {
            // Reading the configuration to update, deriving the new one from it, writing it and setting it locally are
            // one critical section, so that this node's own copy cannot end up older than what it stored, see
            // setConfiguration. That lock is per node, which is not what protects the update between nodes: the write
            // is conditioned on the version the configuration was just read at, so another node writing in between
            // loses the race rather than being silently overwritten, and the update is recomputed from what it wrote.
            synchronized (getConfigurationLock(repositoryName, configName)) {
                ConfigurationWithVersion snapshot = readConfiguration(repositoryName, configName);
                T newState = update.apply(stateToUpdate(repositoryName, configurable, snapshot));
                Map<String, Object> newConfig = configurable.toConfigMap(newState);
                checkNoReservedKeys(repositoryName, configName, newConfig);
                if (newConfig.equals(snapshot.config())) {
                    // the update left the configuration as stored: nothing to write (and therefore nothing to
                    // invalidate), but this node still adopts what it read
                    log.debug("Configuration unchanged: {} for repo: {}, skipping write", configName, repositoryName);
                    configurable.setLocalState(repositoryName, newState);
                    return;
                }
                // the last attempt writes through the throwing variant, so that exhausted contention is reported as
                // the conflict it is rather than as a silent last-write-wins
                Long written;
                if (attempt == UPDATE_CONFIG_ATTEMPTS) {
                    written = service.writeConfiguration(configName, newConfig, snapshot.version());
                } else {
                    written = service.tryWriteConfiguration(configName, newConfig, snapshot.version());
                }
                if (written != null) {
                    onConfigurationWritten(repositoryName, configName);
                    configurable.setLocalState(repositoryName, newState);
                    return;
                }
            }
            log.debug("Configuration written concurrently: {} for repo: {}, recomputing the update (attempt {})",
                    configName, repositoryName, attempt);
        }
    }

    /**
     * The configuration an update derives its result from: the persisted one, not the one this node holds.
     * <p>
     * This node's copy lags behind while a cluster invalidation is in flight, and the write that follows replaces the
     * whole persisted configuration. An update derived from the local copy would therefore be merged into, or compared
     * against, a configuration other than the one it replaces: it would drop what this node has not seen yet, and it
     * would call a configuration unchanged that is not the one stored. This is the same snapshot an optimistic-locking
     * update is computed from, see {@link #readConfigurationForUpdate}; the write is unconditional here, so no version
     * is needed with it.
     * <p>
     * When nothing has been persisted yet — at bootstrap, or on a backend that does not persist configuration — the
     * configuration this node holds is the current one.
     */
    private <T> T stateToUpdate(String repositoryName, Configurable<T> configurable,
            ConfigurationWithVersion snapshot) {
        return snapshot.config() == null ? configurable.getLocalState(repositoryName)
                : configurable.fromConfigMap(repositoryName, snapshot.config());
    }

    /** The configuration a service currently holds, in its persisted form. */
    private static <T> Map<String, Object> localConfigMap(String repositoryName, Configurable<T> configurable) {
        return configurable.toConfigMap(configurable.getLocalState(repositoryName));
    }

    @Override
    public <T> long setConfigurationIfVersionMatches(String repositoryName, Configurable<T> configurable, T state,
            long expectedVersion) {
        String configName = configurable.getConfigurableConfigName();
        // one critical section per repository and configuration, so that the local changes happen in the order the
        // writers persisted: an older writer must not set its content after a newer one has set theirs
        synchronized (getConfigurationLock(repositoryName, configName)) {
            long newVersion = writeConfiguration(repositoryName, configName, configurable.toConfigMap(state),
                    expectedVersion);
            configurable.setLocalState(repositoryName, state);
            return newVersion;
        }
    }

    /**
     * The lock ordering the writes of one configuration and the local changes that go with them. There is one per
     * repository and configuration name, both of which come from a fixed, small set, so the map does not grow
     * unboundedly.
     */
    private Object getConfigurationLock(String repositoryName, String configName) {
        return configurationLocks.computeIfAbsent(repositoryName + '/' + configName, k -> new Object());
    }

    /**
     * Called after a configuration has actually been written, for both the plain and the optimistic-locking write. It is
     * <em>not</em> called when the write was skipped because the configuration was already what is stored.
     * <p>
     * Subclasses override this to propagate the change beyond this node, typically by sending cluster invalidations.
     *
     * @param repositoryName the repository name
     * @param configName the configuration name
     */
    protected void onConfigurationWritten(String repositoryName, String configName) {
        // nothing by default: a single node has nothing to notify
    }

}
