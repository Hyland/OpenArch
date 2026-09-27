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
package com.hyland.openarch.runtime.mongodb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.model.ComponentStartOrders;
import com.hyland.openarch.runtime.model.DefaultComponent;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * Component used to get a database connection to MongoDB. Don't expose {@link MongoClient} directly, because it's this
 * component which is responsible for creating and closing it.
 *
 * @since 9.1
 */
public class MongoDBComponent extends DefaultComponent implements MongoDBConnectionService {

    private static final Logger log = LogManager.getLogger(MongoDBComponent.class);

    private MongoClient client;

    private MongoDatabase database;

    @Override
    public void start() {
        MongoDBConnectionConfig config = MongoDBConnectionHelper.getMongoClientConfig();
        client = MongoDBConnectionHelper.newMongoClient(config);
        database = client.getDatabase(config.dbname);
        log.debug("Initializing MongoClient for server {}", () -> maskSecrets(config.server));
    }

    @Override
    public void stop() throws InterruptedException {
        log.debug("Closing MongoClient");
        database = null;
        client.close();
        client = null;
    }

    @Override
    public int getApplicationStartedOrder() {
        // start before repository and directories
        return ComponentStartOrders.REPOSITORY - 10;
    }

    @Override
    public MongoClient getClient() {
        return client;
    }

    @Override
    public MongoDatabase getDatabase() {
        return database;
    }

    /**
     * Masks credentials in MongoDB connection strings.
     * Handles both formats:
     * - mongodb://username:password@host:port
     * - mongodb+srv://username:password@host
     *
     * @param connectionString the MongoDB connection string
     * @return masked connection string with credentials replaced by asterisks
     */
    public static String maskSecrets(String connectionString) {
        if (connectionString == null || connectionString.isEmpty()) {
            return connectionString;
        }

        // Pattern matches: mongodb[+srv]://username:password@
        // Replaces the username:password part with ***:***
        return connectionString.replaceAll(
                "(mongodb(?:\\+srv)?://)[^:]+:(.*)(@[^@]+(?::\\d+)?(?:/|\\?|$))",
                "$1***:***$3"
        );
    }
}
