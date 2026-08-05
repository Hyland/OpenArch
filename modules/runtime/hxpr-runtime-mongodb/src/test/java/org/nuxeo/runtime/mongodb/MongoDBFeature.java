/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.mongodb;

import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import com.mongodb.client.MongoClient;

/**
 * @since 9.1
 */
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.runtime.kv")
@Deploy("org.nuxeo.runtime.mongodb")
@Deploy("org.nuxeo.runtime.mongodb.test")
public class MongoDBFeature implements RunnerFeature {

    @Override
    public void start() {
        // clear the DB
        MongoDBConnectionConfig config = MongoDBConnectionHelper.getMongoClientConfig();
        try (MongoClient client = MongoDBConnectionHelper.newMongoClient(config)) {
            client.getDatabase(config.dbname).drop();
        }
    }

}
