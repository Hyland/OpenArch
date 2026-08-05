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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.DefaultComponent;

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
    protected Map<String, Configurable> configurables = new HashMap<>();

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

        public Map<String, Object> readConfiguration(String configName) {
            ConfigurationServiceIO actualEngine;
            synchronized (this) {
                actualEngine = engine;
                if (actualEngine == null) {
                    log.debug("Getting pending config: {} for repo: {}", configName, repositoryName);
                    return pendingConfig.get(configName);
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
        List<Configurable> actualConfigurables;
        synchronized (this) {
            actualConfigurables = new ArrayList<>(configurables.values());
        }
        actualConfigurables.forEach(configurable -> {
            if (!readConfigurationAndSetLocally(repositoryName, configurable)) {
                // no existing persisted config, persist bootstrap one
                // We write the config without sending global invalidations because at startup all pods will read the
                // same configuration and there is no need to invalidate their local configuration
                service.writeConfiguration(configurable.getConfigurableConfigName(), configurable.getLocalConfiguration(repositoryName));
            }
        });
    }

    @Override
    public void unregisterConfigurationServiceIO(String repositoryName) {
        RepoConfigService service = getServiceForRepo(repositoryName);
        service.unregisterConfigurationServiceIO();
    }

    @Override
    public void registerConfigurable(Configurable configurable) {
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
    public void unregisterConfigurable(Configurable configurable) {
        synchronized (this) {
            configurables.remove(configurable.getConfigurableConfigName());
        }
    }

    protected Configurable getConfigurable(String configName) {
        synchronized (this) {
            return configurables.get(configName);
        }
    }

    // returns true if config exists
    protected boolean readConfigurationAndSetLocally(String repositoryName, Configurable configurable) {
        Map<String, Object> config = readConfiguration(repositoryName, configurable.getConfigurableConfigName());
        if (config == null || config.isEmpty()) {
            return false;
        } else {
            configurable.setLocalConfiguration(repositoryName, config);
            return true;
        }
    }

    @Override
    public Map<String, Object> readConfiguration(String repositoryName, String configName) {
        RepoConfigService service = getServiceForRepo(repositoryName);
        return service.readConfiguration(configName);
    }

    @Override
    public void writeConfiguration(String repositoryName, String configName, Map<String, Object> config) {
        RepoConfigService service = getServiceForRepo(repositoryName);
        service.writeConfiguration(configName, config);
    }

}
