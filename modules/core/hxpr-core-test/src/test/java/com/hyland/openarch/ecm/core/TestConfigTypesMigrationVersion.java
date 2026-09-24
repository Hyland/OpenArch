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
package com.hyland.openarch.ecm.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SCHEMA_CINCONTEXT;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.SCHEMAS;
import static com.hyland.openarch.ecm.core.schema.TypeServiceImpl.CONFIG_TYPES;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_CONFIG_VERSION;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.common.utils.FileUtils;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import org.skyscreamer.jsonassert.JSONAssert;

import tools.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import jakarta.inject.Inject;

/**
 * Checks that an on-read content-model migration preserves the optimistic-locking version.
 * <p>
 * The database is seeded with an old-shape {@code types} configuration that <em>also</em> carries a {@code _version}
 * (as it would after earlier v2 writes). Reading it triggers
 * {@code MongoDBRepository.readConfigurationWithVersion}, which migrates the shape and writes it back; the write-back
 * must keep the same version rather than dropping the counter.
 */
@ExtendWith(FeaturesExtension.class)
@Features(TestConfigTypesMigrationVersion.CoreFeatureWithVersionedOldModel.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestConfigTypesMigrationVersion {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OLD_TYPES = "config/types-before-migration.json";

    private static final String NEW_TYPES = "config/types-after-migration.json";

    private static final long SEEDED_VERSION = 3L;

    @Inject
    protected CoreSession session;

    @Inject
    protected CoreFeatureWithVersionedOldModel coreFeature;

    private static String readFile(String filename) {
        File file = FileUtils.getResourceFileFromContext(filename);
        try {
            return new String(Files.readAllBytes(file.toPath()), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class CoreFeatureWithVersionedOldModel extends CoreFeature {
        @Override
        public void start() {
            super.start();
            if (storageConfiguration.isDBSMongoDB()) {
                storageConfiguration.runWithMongoDatabase(db -> {
                    // set an old model in the database that already carries an optimistic-locking version
                    String repositoryName = storageConfiguration.getRepositoryName();
                    MongoCollection<Document> configColl = db.getCollection(repositoryName + ".config");
                    Document doc = Document.parse(readFile(OLD_TYPES));
                    doc.put(MONGODB_CONFIG_VERSION, SEEDED_VERSION);
                    Bson filter = Filters.eq(MONGODB_ID, CONFIG_TYPES);
                    configColl.replaceOne(filter, doc, new ReplaceOptions().upsert(true));
                });
            }
        }
    }

    @Test
    public void testMigrationPreservesVersion() throws Exception {
        assumeTrue(coreFeature.getStorageConfiguration().isDBSMongoDB(),
                "Configuration is only persisted (and migrated) on the MongoDB backend");

        // accessing the schema manager triggers the on-read migration and write-back
        SchemaManager schemaManager = session.getSchemaManager();

        // the model has been migrated to the new shape (so the write-back branch did run)
        String actual = MAPPER.writeValueAsString(schemaManager.toMap());
        String expected = readFile(NEW_TYPES);
        JSONAssert.assertEquals(actual, expected, actual, true);

        // and the migration write-back kept the optimistic-locking version instead of dropping it
        Document[] persisted = new Document[1];
        coreFeature.getStorageConfiguration().runWithMongoDatabase(db -> {
            String repositoryName = coreFeature.getStorageConfiguration().getRepositoryName();
            MongoCollection<Document> configColl = db.getCollection(repositoryName + ".config");
            persisted[0] = configColl.find(Filters.eq(MONGODB_ID, CONFIG_TYPES)).first();
        });
        assertNotNull(persisted[0]);
        // the write-back did reach storage: the schemas the migration backfills are now stored, whereas the seeded
        // configuration didn't have them
        Document schemas = persisted[0].get(SCHEMAS, Document.class);
        assertTrue(schemas.containsKey(SCHEMA_CINCONTEXT),
                "migration must have been persisted, stored schemas: " + schemas.keySet());

        // and it kept the optimistic-locking version instead of dropping it
        assertTrue(persisted[0].containsKey(MONGODB_CONFIG_VERSION),
                "migration write-back must keep the version field");
        assertEquals(SEEDED_VERSION, ((Number) persisted[0].get(MONGODB_CONFIG_VERSION)).longValue());
    }
}
