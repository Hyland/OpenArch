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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * Service used to get a database connection to MongoDB.
 * <p>
 * The service also keeps track of the MongoDB session, if any, in which the MongoDB-based stores running on the
 * current thread must do their work, so that they all take part in the same MongoDB transaction.
 *
 * @since 9.1
 */
public interface MongoDBConnectionService {

    /**
     * Gets the MongoDB client.
     */
    MongoClient getClient();

    /**
     * Gets the MongoDB database.
     */
    MongoDatabase getDatabase();

    /**
     * Gets the MongoDB session in which the work of the current thread must take place, lazily starting one if there
     * is none yet.
     * <p>
     * Whichever MongoDB-based store or repository connection is the first to call this method for a given transaction
     * creates the session and starts the MongoDB transaction. The ones coming later in the same transaction all share
     * that session, and therefore that MongoDB transaction. The session is committed or aborted, then closed, when
     * the enclosing transaction completes.
     *
     * @return the session, never {@code null}
     * @throws IllegalStateException if there is no active transaction to attach the session's lifecycle to
     */
    ClientSession getOrCreateSession();

}
