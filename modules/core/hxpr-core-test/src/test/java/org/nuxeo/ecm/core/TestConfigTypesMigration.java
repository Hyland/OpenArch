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
import static org.nuxeo.ecm.core.schema.TypeServiceImpl.CONFIG_TYPES;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.UncheckedIOException;
import tools.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(TestConfigTypesMigration.CoreFeatureWithCustomDatabaseInit.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestConfigTypesMigration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OLD_TYPES = "config/types-before-migration.json";

    private static final String NEW_TYPES = "config/types-after-migration.json";

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

    public static class CoreFeatureWithCustomDatabaseInit extends CoreFeature {
        @Override
        public void start() {
            super.start();
            if (storageConfiguration.isDBSMongoDB()) {
                storageConfiguration.runWithMongoDatabase(db -> {
                    // set an old model in the database instead of starting from nothing
                    String repositoryName = storageConfiguration.getRepositoryName();
                    MongoCollection<Document> configColl = db.getCollection(repositoryName + ".config");
                    String json = readFile(OLD_TYPES);
                    Bson filter = Filters.eq(MONGODB_ID, CONFIG_TYPES);
                    configColl.replaceOne(filter, Document.parse(json), new ReplaceOptions().upsert(true));
                });
            }
        }
    }

    @Test
    public void testMigration() throws Exception {
        SchemaManager schemaManager = session.getSchemaManager();
        Map<String, Object> map = schemaManager.toMap();
        String actual = MAPPER.writeValueAsString(map);
        String expected = readFile(NEW_TYPES);
        JSONAssert.assertEquals(actual, expected, actual, true);
    }

}
