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
package org.nuxeo.ecm.core.storage.dbs;

import java.io.IOException;

import com.hyland.openarch.runtime.pubsub.AbstractPubSubInvalidationsAccumulator;

/**
 * PubSub implementation of {@link DBSClusterInvalidator}.
 *
 * @since 9.1
 */
public class DBSPubSubInvalidator extends AbstractPubSubInvalidationsAccumulator<DBSInvalidationsList> implements DBSClusterInvalidator {

    @Override
    public DBSInvalidationsList newInvalidations() {
        return new DBSInvalidationsList();
    }

    @Override
    public DBSInvalidationsList deserialize(String message) throws IOException {
        return DBSInvalidationsList.deserialize(message);
    }

    @Override
    public void initialize(String nodeId, String repositoryName) {
        super.initialize("dbs:" + repositoryName, nodeId);
    }

}
