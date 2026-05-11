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

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

public class DummyServiceImpl extends DefaultComponent implements DummyService, Configurable {

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

    private void setLocalSettings(String settings) {
        this.settings = settings;
    }

    @Override
    public void setSettings(String settings) {
        // set locally
        setLocalSettings(settings);
        // persist it (sends cluster invalidations)
        writeConfigurationGlobally(DUMMY_REPO);
    }

    // Configurable
    @Override
    public String getConfigurableConfigName() {
        return CONFIG_DUMMY;
    }

    // Configurable
    @Override
    public Map<String, Object> getLocalConfiguration(String repositoryName) {
        return Map.of(SETTINGS, settings);
    }

    // Configurable
    @Override
    public void setLocalConfiguration(String repositoryName, Map<String, Object> config) {
        settings = (String) config.get(SETTINGS);
    }

}
