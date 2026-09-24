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
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.api.repository.FulltextConfiguration;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeConstants;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.schema.types.ComplexType;
import com.hyland.openarch.ecm.core.schema.types.CompositeType;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.ListType;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.storage.FullTextConfigurationService;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Provides sharing behavior for repository sessions and other basic functions.
 *
 * @since 5.9.4
 */
public abstract class DBSRepositoryBase implements DBSRepository {

    // change to have deterministic pseudo-UUID generation for debugging
    public static final boolean DEBUG_UUIDS = false;

    public static final String UUID_ZERO = "00000000-0000-0000-0000-000000000000";

    public static final String UUID_ZERO_DEBUG = "UUID_0";

    protected final String repositoryName;

    protected FulltextConfiguration fulltextConfiguration;

    protected LockManager lockManager;

    protected List<List<String>> blobKeysPaths;

    /**
     * Constructor. Callers must still call {@link #initialize}.
     */
    protected DBSRepositoryBase(String repositoryName, DBSRepositoryDescriptor descriptor) {
        this.repositoryName = repositoryName;
    }

    protected void initialize(DBSRepositoryDescriptor descriptor) {
        // these require a schema manager
        initFulltextConfiguration(descriptor);
        initBlobsPaths();
    }

    private void initFulltextConfiguration(DBSRepositoryDescriptor descriptor) {
        if (descriptor.isFulltextSearchDisabled()) {
            fulltextConfiguration = null;
        } else {
            var fullTextConfigurationService = Framework.getService(FullTextConfigurationService.class);
            fulltextConfiguration = fullTextConfigurationService.makeConfiguration(getSchemaManager(),
                    descriptor.getFulltextFieldSizeLimit(), descriptor.isFullTextStoredInBlob());
        }
    }

    @Override
    public SchemaManager getSchemaManager() {
        return Framework.getService(TypeService.class).getSchemaManager(repositoryName);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public String getName() {
        return repositoryName;
    }

    @Override
    public FulltextConfiguration getFulltextConfiguration() {
        return fulltextConfiguration;
    }

    @Override
    public Lock getLock(String id) {
        try (DBSConnection connection = getConnection()) {
            return connection.getLock(id);
        }
    }

    @Override
    public Lock setLock(String id, Lock lock) {
        try (DBSConnection connection = getConnection()) {
            return connection.setLock(id, lock);
        }
    }

    @Override
    public Lock removeLock(String id, String owner) {
        try (DBSConnection connection = getConnection()) {
            return connection.removeLock(id, owner);
        }
    }

    @Override
    public List<List<String>> getBlobKeysPaths() {
        return blobKeysPaths;
    }

    protected void initBlobsPaths() {
        BlobFinder finder = new BlobFinder(getSchemaManager());
        finder.visit();
        if (isFulltextStoredInBlob()) {
            finder.visitFulltextStoredInBlob();
        }
        blobKeysPaths = finder.blobKeysPaths;
    }

    /** Finds the paths for all blobs in all document types. */
    protected static class BlobFinder {

        private final SchemaManager schemaManager;

        protected final Set<String> schemaDone = new HashSet<>();

        protected final Deque<String> path = new ArrayDeque<>();

        protected List<List<String>> blobKeysPaths = new ArrayList<>();

        public BlobFinder(SchemaManager schemaManager) {
            this.schemaManager = schemaManager;
        }

        public void visit() {
            // document types
            for (DocumentType docType : schemaManager.getDocumentTypes()) {
                visitSchemas(docType.getSchemas());
            }
            // mixins
            for (CompositeType type : schemaManager.getMixins()) {
                visitSchemas(type.getSchemas());
            }
        }

        public void visitFulltextStoredInBlob() {
            path.addLast(SYS_FULLTEXT_BINARY);
            recordBlobPath();
            path.removeLast();
        }

        protected void visitSchemas(Collection<Schema> schemas) {
            for (Schema schema : schemas) {
                if (schemaDone.add(schema.getName())) {
                    visitComplexType(schema);
                }
            }
        }

        protected void visitComplexType(ComplexType complexType) {
            if (TypeConstants.isBlobType(complexType)) {
                path.addLast(KEY_BLOB_KEY);
                recordBlobPath();
                path.removeLast();
                return;
            }
            for (Field field : complexType.getFields()) {
                visitField(field);
            }
        }

        /** Records a blob path, stored in the {@link #path} field. */
        protected void recordBlobPath() {
            blobKeysPaths.add(new ArrayList<>(path));
        }

        protected void visitField(Field field) {
            Type type = field.getType();
            if (type.isSimpleType()) {
                // scalar
                // assume no bare binary exists
            } else if (type.isComplexType()) {
                // complex property
                String name = field.getName();
                path.addLast(name);
                visitComplexType((ComplexType) type);
                path.removeLast();
            } else if (type.isListType()) {
                // array or list
                Type fieldType = ((ListType) type).getFieldType();
                if (fieldType.isSimpleType()) {
                    // array
                    // assume no array of bare binaries exist
                } else {
                    // complex list
                    String name = field.getName();
                    path.addLast(name);
                    visitComplexType((ComplexType) fieldType);
                    path.removeLast();
                }
            } else {
                // any: ignore (assume contains no binaries)
            }
        }
    }

    @Override
    public boolean isFulltextDisabled() {
        return fulltextConfiguration == null;
    }

    @Override
    public boolean isFulltextStoredInBlob() {
        return fulltextConfiguration != null && fulltextConfiguration.isFulltextStoredInBlob();
    }

    @Override
    public InternalSession getInternalSession() {
        return new DBSSession(this);
    }

    @Override
    public void reloadFullTextConfiguration(FulltextConfiguration updatedConfiguration) {
        fulltextConfiguration = updatedConfiguration;
    }
}
