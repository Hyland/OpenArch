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
package com.hyland.openarch.opensearch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

public class OpenSearchIndexConfigTest {

    @Test
    public void shouldKeepSettingsWhenNumberOfShardsIsNotConfigured() {
        OpenSearchIndexConfig config = new OpenSearchIndexConfig();
        config.settings = "{\"number_of_shards\":2}";

        assertThat(config.getSettings()).isSameAs(config.settings);
        assertThat(config.getNumberOfShards()).isNull();
    }

    @Test
    public void shouldOverrideNumberOfShards() {
        OpenSearchIndexConfig config = new OpenSearchIndexConfig();
        config.settings = "{\"number_of_shards\":1,\"number_of_replicas\":0}";
        config.numberOfShards = 3;

        JsonNode settings = OpenSearchIndexConfig.MAPPER.readTree(config.getSettings());

        assertThat(settings.get("number_of_shards").asInt()).isEqualTo(3);
        assertThat(settings.get("number_of_replicas").asInt()).isZero();
        assertThat(config.getNumberOfShards()).isEqualTo(3);
    }

    @Test
    public void shouldRejectNonPositiveNumberOfShards() {
        OpenSearchIndexConfig config = new OpenSearchIndexConfig();
        config.name = "invalid-shards";
        config.settings = "{}";
        config.numberOfShards = 0;

        assertThatIllegalArgumentException().isThrownBy(config::getSettings)
                                            .withMessage(
                                                    "Invalid number_of_shards for index: invalid-shards, must be at" + " least 1 but is: 0");
    }

    @Test
    public void shouldRejectSettingsThatAreNotAJsonObject() {
        OpenSearchIndexConfig config = new OpenSearchIndexConfig();
        config.name = "array-settings";
        config.settings = "[]";
        config.numberOfShards = 1;

        assertThatIllegalArgumentException().isThrownBy(config::getSettings)
                                            .withMessageContaining(
                                                    "Cannot override number_of_shards for index: array-settings")
                                            .withMessageContaining("settings is not a JSON object: []");
    }

    @Test
    public void shouldRejectMalformedSettings() {
        OpenSearchIndexConfig config = new OpenSearchIndexConfig();
        config.name = "malformed-settings";
        config.settings = "{";
        config.numberOfShards = 1;

        assertThatIllegalArgumentException().isThrownBy(config::getSettings)
                                            .withMessage(
                                                    "Cannot override number_of_shards for index: malformed-settings")
                                            .withCauseInstanceOf(JacksonException.class);
    }
}
