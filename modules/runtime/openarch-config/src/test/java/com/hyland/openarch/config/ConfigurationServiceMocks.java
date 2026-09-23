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
package com.hyland.openarch.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.function.UnaryOperator;

/**
 * Helpers for tests that mock the {@link ConfigurationService}.
 */
public final class ConfigurationServiceMocks {

    private ConfigurationServiceMocks() {
        // utility class
    }

    /**
     * Makes a mocked {@link ConfigurationService} behave like the real one for
     * {@link ConfigurationService#setConfiguration}: persist the configuration, then set it on the
     * {@link Configurable}, so that a write made to fail leaves the configurable holding what it held before, as the
     * real one does.
     * <p>
     * A mock does nothing by default, so without this the service under test never sees the configuration it just set,
     * and {@link ConfigurationService#writeConfiguration} is never reached. With it, stubbing and verifying that method
     * keeps describing what a service does when it sets its configuration.
     * <p>
     * The stubbing is lenient: tests sharing one setup method often include some that never write at all, and this is
     * test scaffolding rather than the behaviour under test.
     *
     * @param configurationService the mocked configuration service
     */
    @SuppressWarnings("unchecked")
    public static void stubSetConfiguration(ConfigurationService configurationService) {
        lenient().doAnswer(invocation -> {
            String repositoryName = invocation.getArgument(0);
            Configurable<Object> configurable = invocation.getArgument(1);
            Object state = invocation.getArgument(2);
            configurationService.writeConfiguration(repositoryName, configurable.getConfigurableConfigName(),
                    configurable.toConfigMap(state));
            configurable.setLocalState(repositoryName, state);
            return null;
        }).when(configurationService).setConfiguration(any(), any(), any());
    }

    /**
     * Makes a mocked {@link ConfigurationService} behave like the real one for
     * {@link ConfigurationService#updateConfiguration}: derive the new configuration from the persisted one, persist
     * it, then set it on the {@link Configurable}, see {@link #stubSetConfiguration}.
     * <p>
     * What the update derives from is what {@link ConfigurationService#readConfiguration} reports, so a test simulating
     * a configuration written by another node stubs that method; with the mock's default answer nothing is persisted,
     * and the configuration the service holds is what the update derives from, as at bootstrap.
     * <p>
     * The stubbing is lenient, see {@link #stubSetConfiguration}.
     *
     * @param configurationService the mocked configuration service
     */
    @SuppressWarnings("unchecked")
    public static void stubUpdateConfiguration(ConfigurationService configurationService) {
        lenient().doAnswer(invocation -> {
            String repositoryName = invocation.getArgument(0);
            Configurable<Object> configurable = invocation.getArgument(1);
            UnaryOperator<Object> update = invocation.getArgument(2);
            ConfigurationWithVersion persisted = configurationService.readConfiguration(repositoryName,
                    configurable.getConfigurableConfigName());
            Object current = persisted == null || persisted.config() == null
                    ? configurable.getLocalState(repositoryName)
                    : configurable.fromConfigMap(repositoryName, persisted.config());
            Object newState = update.apply(current);
            configurationService.writeConfiguration(repositoryName, configurable.getConfigurableConfigName(),
                    configurable.toConfigMap(newState));
            configurable.setLocalState(repositoryName, newState);
            return null;
        }).when(configurationService).updateConfiguration(any(), any(), any());
    }
}
