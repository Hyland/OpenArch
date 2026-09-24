/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;

/**
 * Internal Session accessing the low-level storage.
 */
public interface InternalSession {

    /**
     * Gets the repository that created this session.
     *
     * @return the repository
     */
    String getRepositoryName();

    /**
     * Does a query.
     * <p>
     * A query arrives here as a {@link Statement}, parsed and with its parameters bound, rather than as text: what
     * syntax it was written in is settled above this level.
     *
     * @since 5.9.4
     */
    PartialList<InternalDocument> query(Statement statement, QueryParameters queryParams, long countUpTo);

    /**
     * Does a query and fetch the individual results as maps.
     */
    IterableQueryResult queryAndFetch(Statement statement, QueryParameters queryParams);

    /**
     * Does a query and fetch the individual results as maps.
     *
     * @since 7.10-HF25, 8.10-HF06, 9.2
     */
    PartialList<Map<String, Object>> queryProjection(Statement statement, QueryParameters queryParams,
            boolean distinctDocuments, long countUpTo);

    /**
     * Executes the given query and returns the first batch of results containing id of documents, next batch must be
     * requested within the keepAliveSeconds delay.
     *
     * @since 10.3
     */
    ScrollResult<String> scroll(Statement statement, QueryParameters queryParams, int batchSize, int keepAliveSeconds);

    /**
     * Get the next batch of result containing id of documents.
     *
     * @since 8.4
     */
    ScrollResult<String> scroll(String scrollId);

    /**
     * Gets the lock manager for this session.
     *
     * @return the lock manager
     * @since 7.4
     */
    LockManager getLockManager();

    /**
     * Saves this session.
     */
    void save(RepositoryPrincipal principal);

    /**
     * Destroys this session.
     */
    void destroy();

    /**
     * Gets the document at the given path, if any.
     *
     * @throws DocumentNotFoundException if the document doesn't exist
     */
    InternalDocument resolvePath(String path) throws DocumentNotFoundException;

    /**
     * Gets a document given its ID.
     *
     * @param uuid the document id
     * @return the document
     * @throws DocumentNotFoundException if the document doesn't exist
     */
    InternalDocument getDocumentByUUID(String uuid) throws DocumentNotFoundException;

    /**
     * Gets the root document in this repository.
     *
     * @return the root document
     */
    InternalDocument getRootDocument();

    /**
     * Gets the null document, to be used as a fake parent to add placeless children.
     *
     * @return the null document
     */
    InternalDocument getNullDocument();

    /**
     * Copies the source document to the given folder.
     * <p>
     * If the destination document is not a folder, an exception is thrown.
     */
    InternalDocument copy(InternalDocument src, InternalDocument dst, String name, RepositoryPrincipal principal);

    /**
     * Moves the source document to the given folder.
     * <p>
     * If the destination document is not a folder an exception is thrown.
     *
     * @param src the source document to move
     * @param dst the destination folder
     * @param condition the condition, or {@code null}
     * @param name the new name of the document or null if the original name should be preserved
     */
    InternalDocument move(InternalDocument src, InternalDocument dst, String name, UpdateCondition condition,
            RepositoryPrincipal principal);

    /**
     * Restores the given version to be the new working copy.
     */
    void restoreVersion(InternalDocument version, RepositoryPrincipal principal);

    /**
     * Updates the denormalized properties for some documents.
     *
     * @param ids the document ids
     */
    void updateDenormalizedPropertiesNoCache(Collection<String> ids);

    /**
     * Removes a document. This removal is done without checking if the document has any children.
     *
     * @param id the document id
     * @since 11.1
     */
    void removeDocument(String id, RepositoryPrincipal principal);

    /**
     * INTERNAL framework property allowing deletion of undeletable documents. Should only be used in tests.
     * <p>
     * Note that even when this is set, a low-level blob provider may still disallow blob removal.
     *
     * @since 11.1
     */
    String PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS = "com.hyland.openarch.core.allowDeleteUndeletableDocuments";

    /**
     * Property to enable retention compliance mode. By default, retention runs in governance mode.
     * <p>
     * Compliance mode is the strictest where nobody can delete documents under retention or legal hold.
     * <p>
     * Governance (or standard) mode is less strict and allows users member of the
     * {@link SecurityConstants#RECORDS_CLEANER_GROUP} group to delete records.
     * <p>
     *
     * @since 11.5
     */
    String PROP_RETENTION_COMPLIANCE_MODE_ENABLED = "nuxeo.retention.compliance.enabled";

    /*
     * ----- Transaction management -----
     */

    /**
     * Start the transaction.
     *
     * @since 11.1
     */
    void start();

    /**
     * Called just before the transaction is committed or rolled back.
     *
     * @since 11.1
     */
    void end();

    /**
     * Commit the transaction.
     *
     * @since 11.1
     */
    void commit();

    /**
     * Rollback the transaction.
     *
     * @since 11.1
     */
    void rollback();

    /**
     * Gets the schema manager for this session.
     */
    SchemaManager getSchemaManager();

    /**
     * Can undeletable documents be deleted. Undeletable documents are documents under legal hold or retention.They are
     * deletable only if on of these conditions is true:
     * <ul>
     * <li>{@link InternalSession#PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS} is true for unit tests purpose</li>
     * <li>Retention is active in governance mode and the principal is member of
     * {@link SecurityConstants#RECORDS_CLEANER_GROUP}</li>
     * </ul>
     *
     * @param principal the OpenArch principal
     * @return {@code true} if undeletable documents can be deleted, {@code false} otherwise
     * @since 11.5
     */
    public static boolean canDeleteUndeletable(RepositoryPrincipal principal) {
        if (Framework.isBooleanPropertyTrue(PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS)) {
            return true;
        }
        Objects.requireNonNull(principal);
        boolean governanceMode = !Framework.isBooleanPropertyTrue(PROP_RETENTION_COMPLIANCE_MODE_ENABLED);
        boolean recordCleaner = principal.isMemberOf(SecurityConstants.RECORDS_CLEANER_GROUP);
        return governanceMode && recordCleaner;
    }

    List<String> getDirtyBinariesPaths(String id);

}
