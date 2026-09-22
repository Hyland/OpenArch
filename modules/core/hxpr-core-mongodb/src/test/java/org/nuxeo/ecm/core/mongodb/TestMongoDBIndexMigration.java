/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.mongodb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(TestMongoDBIndexMigration.CoreFeatureWithCustomDatabaseInit.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestMongoDBIndexMigration {

    public static class CoreFeatureWithCustomDatabaseInit extends CoreFeature {
        @Override
        public void start() {
            super.start();
            if (storageConfiguration.isDBSMongoDB()) {
                storageConfiguration.runWithMongoDatabase(db -> {
                    String repositoryName = storageConfiguration.getRepositoryName();
                    MongoCollection<Document> coll = db.getCollection(repositoryName);
                    // create an "old" index, that doesn't have the unique characteristic
                    // or the partial filter expression
                    Bson keys = Indexes.ascending(SYS_PARENT_ID, SYS_NAME);
                    coll.dropIndex(keys);
                    coll.createIndex(keys);
                });
            }
        }
    }

    @Inject
    protected CoreSession session;

    @Test
    public void testMigration() {
        assertNotNull(session);
    }

}
