/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.dbs;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import java.util.ArrayList;
import java.util.List;

import com.hyland.openarch.ecm.core.schema.TypeConstants;
import com.hyland.openarch.ecm.core.storage.State;

/**
 * Interface for a connection to a {@link DBSRepository}. The connection maintains state when it is transactional.
 *
 * @since 11.1
 */
public abstract class DBSConnectionBase implements DBSConnection {

    protected final DBSRepositoryBase repository;

    protected DBSConnectionBase(DBSRepositoryBase repository) {
        this.repository = repository;
    }

    @Override
    public List<List<String>> getBlobKeysPaths() {
        List<List<String>> blobKeysPaths = getSchemaManager().getBlobKeysPaths();
        if (repository.isFulltextStoredInBlob()) {
            // the extracted fulltext blob isn't part of the content model, it depends on the repository configuration
            blobKeysPaths = new ArrayList<>(blobKeysPaths);
            blobKeysPaths.add(List.of(SYS_FULLTEXT_BINARY));
        }
        return blobKeysPaths;
    }

    @Override
    public String getRootId() {
        if (DBSRepositoryBase.DEBUG_UUIDS) {
            return DBSRepositoryBase.UUID_ZERO_DEBUG;
        }
        return DBSRepositoryBase.UUID_ZERO;
    }

    /**
     * Initializes the root.
     */
    public void initRoot() {
        State state = new State();
        state.put(SYS_ID, getRootId());
        state.put(SYS_NAME, "");
        state.put(SYS_TITLE, "");
        state.put(SYS_PRIMARY_TYPE, TypeConstants.SYS_ROOT);
        createState(state);
    }

}
