/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;

import tools.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestConfigTypesDefault {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CONFIG = "config/types-default.json";

    @Inject
    protected CoreSession session;

    private static String readFile(String filename) {
        File file = FileUtils.getResourceFileFromContext(filename);
        try {
            return new String(Files.readAllBytes(file.toPath()), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testConfig() throws Exception {
        SchemaManager schemaManager = session.getSchemaManager();
        String actual = MAPPER.writeValueAsString(schemaManager.toMap());
        String expected = readFile(CONFIG);
        JSONAssert.assertEquals(actual, expected, actual, true);
    }

    /**
     * The persisted shape is a contract: reading it back and dumping it again must be the identity.
     * This pins both halves of the storage codec against the same reference document.
     */
    @Test
    public void testConfigRoundTrip() throws Exception {
        String expected = readFile(CONFIG);
        // the SchemaManagerDTO constructor consumes the map it is given, so read a fresh copy
        @SuppressWarnings("unchecked")
        Map<String, Object> input = MAPPER.readValue(expected, Map.class);
        SchemaManager schemaManager = new SchemaManagerDTO(input);
        String actual = MAPPER.writeValueAsString(schemaManager.toMap());
        JSONAssert.assertEquals(actual, expected, actual, true);
    }

}
