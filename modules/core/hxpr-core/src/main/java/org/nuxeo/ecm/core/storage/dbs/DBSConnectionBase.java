/*
 * (C) Copyright 2014-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.dbs;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;

import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.storage.State;

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
