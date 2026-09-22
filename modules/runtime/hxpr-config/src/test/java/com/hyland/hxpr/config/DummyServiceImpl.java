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
import org.nuxeo.runtime.model.DefaultComponent;

public class DummyServiceImpl extends DefaultComponent implements DummyService, Configurable<String> {

    public static final String CONFIG_DUMMY = "dummy";

    public static final String SETTINGS = "settings";

    public static final String DUMMY_REPO = "repo";

    // the config for this dummy service
    private String settings = "";

    @Override
    public void start() {
        Framework.getService(ConfigurationService.class).registerConfigurable(this);
    }

    @Override
    public void stop() {
        Framework.getService(ConfigurationService.class).unregisterConfigurable(this);
    }

    @Override
    public String getSettings() {
        return settings;
    }

    @Override
    public void setSettings(String settings) {
        // set locally and persist it (sends cluster invalidations), as one operation
        setConfigurationGlobally(DUMMY_REPO, settings);
    }

    // Configurable
    @Override
    public String getConfigurableConfigName() {
        return CONFIG_DUMMY;
    }

    // Configurable
    @Override
    public String getLocalState(String repositoryName) {
        return settings;
    }

    // Configurable
    @Override
    public void setLocalState(String repositoryName, String state) {
        settings = state;
    }

    // Configurable
    @Override
    public Map<String, Object> toConfigMap(String state) {
        return Map.of(SETTINGS, state);
    }

    // Configurable
    @Override
    public String fromConfigMap(String repositoryName, Map<String, Object> config) {
        return (String) config.get(SETTINGS);
    }

}
