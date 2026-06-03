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

import static com.hyland.hxpr.config.DummyServiceImpl.CONFIG_DUMMY;
import static com.hyland.hxpr.config.DummyServiceImpl.DUMMY_REPO;
import static com.hyland.hxpr.config.DummyServiceImpl.SETTINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import jakarta.inject.Inject;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("com.hyland.hxpr.config")
@Deploy("com.hyland.hxpr.config.test:OSGI-INF/dummy-service.xml")
public class TestConfigurationService {

    @Inject
    ConfigurationService configurationService;

    @Inject
    DummyService dummyService;

    private IO io = new IO();

    public static class IO implements ConfigurationServiceIO {

        public Map<String, Map<String, Object>> data = new HashMap<>();

        @Override
        public Map<String, Object> readConfiguration(String configName) {
            return data.get(configName);
        }

        @Override
        public void writeConfiguration(String configName, Map<String, Object> config) {
            Map<String, Object> map = new HashMap<>(config); // non-test code would use deep copy
            data.put(configName, map);
        }
    }

    @BeforeEach
    public void before() {
        configurationService.registerConfigurationServiceIO(DUMMY_REPO, io);
    }

    @AfterEach
    public void after() {
        configurationService.unregisterConfigurationServiceIO(DUMMY_REPO);
    }

    @Test
    public void testInitFromPersistedConfigWhenStarting() {
        // stop service as we'll need to restart it later
        ((DummyServiceImpl) dummyService).stop();

        // prepare initial persisted data
        io.data.put(CONFIG_DUMMY, Map.of(SETTINGS, "gee"));

        // restart service
        dummyService = new DummyServiceImpl();
        ((DummyServiceImpl) dummyService).start();

        // check that startup read the persisted config
        assertEquals("gee", dummyService.getSettings());
    }

}
