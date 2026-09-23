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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import com.hyland.hxpr.config.ConfigurationService;
import com.hyland.hxpr.config.ConfigurationWithVersion;

import jakarta.inject.Inject;

/**
 * Checks how configurations are persisted, independently of any {@code Configurable} keeping a local copy in memory.
 * <p>
 * The versions are only tracked by the MongoDB backend, so these tests are skipped on the others.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestConfigurationStorage {

    private static final String CONFIG_NAME = "test-storage";

    private static final Map<String, Object> TWO_ENTRIES = Map.of("first", Map.of("value", "one"), //
            "second", Map.of("value", "two"));

    private static final Map<String, Object> ONE_ENTRY = Map.of("first", Map.of("value", "one"));

    @Inject
    protected CoreSession session; // makes sure the repository, hence the configuration storage, is initialized

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected ConfigurationService configurationService;

    private String repositoryName() {
        return coreFeature.getStorageConfiguration().getRepositoryName();
    }

    private ConfigurationWithVersion read() {
        return configurationService.readConfiguration(repositoryName(), CONFIG_NAME);
    }

    /**
     * A version-unaware (v1) write replaces the whole configuration, so an entry the new configuration no longer has is
     * really removed from storage. Anything less (a merge) would leave it persisted and it would reappear on the next
     * read from storage, after a restart or a cluster invalidation.
     */
    @Test
    public void testWriteRemovesEntries() {
        assumeTrue(coreFeature.getStorageConfiguration().isDBSMongoDB(), "Configuration is only persisted on MongoDB");

        configurationService.writeConfiguration(repositoryName(), CONFIG_NAME, TWO_ENTRIES);
        assertEquals(TWO_ENTRIES, read().config());

        configurationService.writeConfiguration(repositoryName(), CONFIG_NAME, ONE_ENTRY);
        assertEquals(ONE_ENTRY, read().config());
    }

    /**
     * A version-unaware (v1) write still advances the version, so a concurrent optimistic-locking (v2) writer holding
     * the version read before it gets a conflict instead of silently overwriting.
     */
    @Test
    public void testWriteAdvancesVersion() {
        assumeTrue(coreFeature.getStorageConfiguration().isDBSMongoDB(), "Configuration is only persisted on MongoDB");

        configurationService.writeConfiguration(repositoryName(), CONFIG_NAME, TWO_ENTRIES);
        long version = read().version();

        configurationService.writeConfiguration(repositoryName(), CONFIG_NAME, ONE_ENTRY);
        assertEquals(version + 1, read().version());

        // an unchanged configuration is not written at all, so the version stays put
        configurationService.writeConfiguration(repositoryName(), CONFIG_NAME, ONE_ENTRY);
        assertEquals(version + 1, read().version());
    }
}
