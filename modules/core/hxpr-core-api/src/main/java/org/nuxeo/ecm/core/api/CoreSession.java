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
package org.nuxeo.ecm.core.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel.DocumentModelRefresh;
import org.nuxeo.ecm.core.query.sql.model.Statement;
import org.nuxeo.ecm.core.schema.SchemaManager;

/**
 * A session to the Nuxeo Core.
 *
 * @see DocumentModel
 * @see DocumentRef
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public interface CoreSession {

    /**
     * Context data to make sure the sys_name passed during creation is either kept as is, or a
     * {@link ConcurrentUpdateException} is thrown if there's already a document with that name in the folder.
     * <p>
     * Value is a {@link Boolean}.
     */
    String ENFORCE_SYS_NAME = "enforceSysName";

    /**
     * A context data key which represents the source of current session calls. For example:
     * <ul>
     * <li>fileimporter-NoteImporter</li>
     * <li>drive</li>
     * <li>rest</li>
     * </ul>
     *
     * @since 9.1
     */
    String SOURCE = "source";

    /**
     * Change token, a String parameter passed in context data at {@link #saveDocument} time.
     *
     * @see DocumentModel#putContextData
     * @see #getChangeToken
     * @see DocumentModel#getChangeToken
     * @since 9.1
     */
    String CHANGE_TOKEN = "changeToken";

    /**
     * User change, a Boolean parameter passed in context data at {@link #saveDocument} time.
     * <p>
     * When {@linkplain Boolean#TRUE TRUE}, it marks the document as being modified by a user change. This causes an
     * additional change token increment and check during save.
     *
     * @since 9.2
     */
    String USER_CHANGE = "userChange";

    /**
     * Cancels any pending change made through this session.
     */
    void cancel();

    /**
     * Saves any pending changes done until now through this session.
     */
    void save();

    /**
     * Gets the principal that created the client session.
     *
     * @return the principal
     */
    NuxeoPrincipal getPrincipal();

    /**
     * Checks if the principal that created the client session has the given privilege on the referred document.
     */
    boolean hasPermission(DocumentRef docRef, String permission);

    /**
     * Gets the root document of this repository.
     *
     * @return the root document. cannot be null
     */
    DocumentModel getRootDocument();

    /**
     * Gets a document model given its reference.
     * <p>
     * The default schemas are used to populate the returned document model. Default schemas are configured via the
     * document type manager.
     * <p>
     * Any other data model not part of the default schemas will be lazily loaded as needed.
     *
     * @param docRef the document reference
     * @return the document
     * @throws DocumentNotFoundException if the document cannot be found
     */
    DocumentModel getDocument(DocumentRef docRef) throws DocumentNotFoundException;

    /**
     * Gets a list of documents given their references.
     * <p>
     * Documents that are not accessible are skipped.
     *
     * @throws DocumentNotFoundException if a document cannot be found
     */
    DocumentModelList getDocuments(DocumentRef[] docRefs) throws DocumentNotFoundException;

    /**
     * Gets a child document given its name and the parent reference.
     * <p>
     * Throws an exception if the document could not be found.
     * <p>
     * If the supplied id is null, returns the default child of the document if any, otherwise raises an exception.
     * <p>
     * If the parent is null or its path is null, then root is considered.
     *
     * @param parent the reference to the parent document
     * @param name the name of the child document to retrieve
     * @return the named child if exists
     * @throws DocumentNotFoundException if there is no child with the given name
     */
    DocumentModel getChild(DocumentRef parent, String name);

    /**
     * Tests if the document has a child with the given name.
     * <p>
     * This operation silently ignores non-folder documents: If the document is not a folder then returns false.
     *
     * @param parent the document
     * @param name the child name
     * @return {@code true} if the document has a child with the given name
     * @since 7.3
     */
    boolean hasChild(DocumentRef parent, String name);

    /**
     * Gets the children of the given parent.
     *
     * @param parent the parent reference
     * @return the children if any, an empty list if no children or null if the specified parent document is not a
     *         folder
     */
    DocumentModelList getChildren(DocumentRef parent);

    /**
     * Gets the parent document or null if this is the root document.
     *
     * @return the parent document or null if this is the root document
     */
    DocumentModel getParentDocument(DocumentRef docRef);

    /**
     * Gets the ancestor documents in path from the root to the given document or empty list if this is the root document.
     * <p>
     * Documents the principal is not allowed to browse are filtered out the parents list.
     *
     * @return the list with ancestor documents or empty list if this is the root document
     */
    List<DocumentModel> getAncestorDocuments(DocumentRef docRef);

    /**
     * Tests if the document pointed by the given reference exists and is accessible.
     * <p>
     * This operation makes no difference between non-existence and permission problems.
     * <p>
     * If the parent is null or its path is null, then root is considered.
     *
     * @param docRef the reference to the document to test for existence
     * @return true if the referenced document exists, false otherwise
     */
    boolean exists(DocumentRef docRef);

    /**
     * Tests if the document has any children.
     * <p>
     * This operation silently ignores non-folder documents: If the document is not a folder then returns false.
     * <p>
     * If the parent is null or its path is null, then root is considered.
     *
     * @param docRef the reference to the document to test
     * @return true if document has children, false otherwise
     */
    boolean hasChildren(DocumentRef docRef);

    /**
     * Creates a document model using required information.
     * <p>
     * Used to fetch initial datamodels from the type definition.
     *
     * @param parentPath the parent path
     * @param name The destination name
     * @param typeName the type name
     * @return the initial document model
     */
    DocumentModel createDocumentModel(String parentPath, String name, String typeName);

    /**
     * Creates a new document model using required information.
     * <p>
     * Used to fetch initial datamodels from the type definition.
     *
     * @param parentRef the parent document ref {@link DocumentRef}
     * @param name The destination name
     * @param typeName the type name
     * @return the initial document model
     * @since 11.1
     */
    DocumentModel newDocumentModel(DocumentRef parentRef, String name, String typeName);

    /**
     * Check if a document can be removed. This needs the REMOVE permission on the document and the REMOVE_CHILDREN
     * permission on the parent.
     * <p>
     * For an archived version to be removeable,
     * the REMOVE permission must be available on the working document (or the user must be an
     * administrator if no working document exists).
     *
     * @param docRef the document
     * @return true if the document can be removed
     */
    boolean canRemoveDocument(DocumentRef docRef);

    /**
     * Removes this document and all its children, if any.
     *
     * @param docRef the reference to the document to remove
     */
    void removeDocument(DocumentRef docRef);

    /**
     * Bulk method to remove documents.
     * <p>
     * This method is safe with respect to orderings: it doesn't fail if an ancestor of a document occurs before the
     * document.
     * </p>
     *
     * @param docRefs the refs to the document to remove
     */
    void removeDocuments(DocumentRef[] docRefs);

    /**
     * Removes all children from the given document.
     *
     * @param docRef the reference to the document to remove
     */
    void removeChildren(DocumentRef docRef);

    /**
     * Copies the source document to the destination folder under the given name. If the name is null the original name
     * is preserved.
     * <p>
     * If the destination document is not a folder or it doesn't exists then throws an exception.
     *
     * @param src the source document reference
     * @param dst the destination folder reference
     * @param name the new name of the file or null if the original name must be preserved
     */
    DocumentModel copy(DocumentRef src, DocumentRef dst, String name);

    /**
     * Bulk copy. Destination must be a folder document.
     *
     * @param src the documents to copy
     * @param dst the destination folder
     * @since 8.2
     */
    List<DocumentModel> copy(List<DocumentRef> src, DocumentRef dst);

    /**
     * Moves the source document to the destination folder under the given name. If the name is {@code null} or if there
     * is a collision, a suitable new name is found.
     *
     * @param src the source document reference
     * @param dst the destination folder reference
     * @param name the new name of the file, or {@code null}
     */
    DocumentModel move(DocumentRef src, DocumentRef dst, String name);

    /**
     * Moves the source document to the destination folder under the given name, with an optional condition.
     * <p>
     * If the name is {@code null} or if there is a collision, a suitable new name is found.
     *
     * @param src the source document reference
     * @param dst the destination folder reference
     * @param name the new name of the file, or {@code null}
     * @param condition the condition under which the document is moved, or {@code null} for no condition
     */
    DocumentModel move(DocumentRef src, DocumentRef dst, String name, UpdateCondition condition);

    /**
     * Bulk move. Destination must be a folder document.
     *
     * @param src the documents to move
     * @param dst the destination folder
     * @deprecated unused
     */
    @Deprecated
    void move(List<DocumentRef> src, DocumentRef dst);

    /**
     * Updates the denormalized properties for some documents.
     * <p>
     * For INTERNAL use by the core.
     *
     * @param ids the document ids
     */
    void updateDenormalizedPropertiesNoCache(Collection<String> ids);

    // -------- Versioning API ---------------

    /**
     * Restores the given version to be the new working copy.
     *
     * @param versionRef the reference to the version
     */
    DocumentModel restoreVersion(DocumentRef versionRef);

    /**
     * Checks out a versioned document.
     *
     * @param docRef the reference to the document
     */
    void checkOut(DocumentRef docRef);

    /**
     * Checks in a modified document, creating a new version.
     *
     * @param docRef the reference to the document
     * @return the version just created
     * @since 5.4
     */
    DocumentRef checkIn(DocumentRef docRef);

    /**
     * Checks in a modified document, creating a new (maybe minor) version.
     *
     * @param docRef the reference to the document
     * @param minor whether this should be a minor version
     * @return the version just created
     * @since 5.4
     */
    DocumentRef checkIn(DocumentRef docRef, boolean minor);

    /**
     * Returns whether the current document is checked-out or not.
     *
     * @param docRef the reference to the document
     */
    boolean isCheckedOut(DocumentRef docRef);

    /**
     * Gets the working copy for a version, otherwise {@code null}.
     *
     * @param docRef the document reference
     * @return the working copy, or {@code null} if not found
     * @since 5.4
     */
    DocumentModel getWorkingCopy(DocumentRef docRef);

    /** -------------------------- Query API --------------------------- * */

    /**
     * Executes the given NXQL query an returns the result.
     *
     * @param query the query to execute
     * @return the query result
     */
    DocumentModelList query(String query);

    /**
     * Executes the given NXQL query and returns the result that matches the filter.
     *
     * @param query the query to execute
     * @param limit the maximum number of documents to retrieve, or 0 for all of them
     * @param offset the offset (starting at 0) into the list of documents
     * @param countUpTo if {@code -1}, count the total size without offset/limit.<br>
     *            If {@code 0}, don't count the total size.<br>
     *            If {@code n}, count the total number if there are less than n documents otherwise set the size to
     *            {@code -1}.
     * @return the query result
     * @since 5.6
     */
    DocumentModelList query(String query, long limit, long offset, long countUpTo);

    /**
     * Executes the given HXQL query, binding the values of its {@code :name} parameters, and returns the result that
     * matches the filter.
     *
     * @param query the query to execute, which may contain {@code :name} parameters
     * @param parameters the values for those parameters
     * @param limit the maximum number of documents to retrieve, or 0 for all of them
     * @param offset the offset (starting at 0) into the list of documents
     * @param countUpTo if {@code -1}, count the total size without offset/limit.<br>
     *            If {@code 0}, don't count the total size.<br>
     *            If {@code n}, count the total number if there are less than n documents otherwise set the size to
     *            {@code -1}.
     * @return the query result
     * @since 12.0
     */
    DocumentModelList query(String query, Map<String, Object> parameters, long limit, long offset, long countUpTo);

    /**
     * Executes an already parsed query and returns the result that matches the filter.
     * <p>
     * A caller holding a {@link Statement} does not have to render it as text for it to be executed, which is what
     * lets a query be written in something other than HXQL.
     *
     * @param statement the query to execute, with its parameters already bound
     * @param limit the maximum number of documents to retrieve, or 0 for all of them
     * @param offset the offset (starting at 0) into the list of documents
     * @param countUpTo if {@code -1}, count the total size without offset/limit.<br>
     *            If {@code 0}, don't count the total size.<br>
     *            If {@code n}, count the total number if there are less than n documents otherwise set the size to
     *            {@code -1}.
     * @return the query result
     */
    DocumentModelList query(Statement statement, long limit, long offset, long countUpTo);

    /**
     * Executes the given query and returns an iterable of maps containing the requested properties (which must be
     * closed when done).
     *
     * @param query the query to execute
     * @return an {@link IterableQueryResult}, which <b>must</b> be closed after use
     */
    IterableQueryResult queryAndFetch(String query);

    /**
     * Executes an already parsed query and returns an iterable of maps containing the requested properties (which
     * must be closed when done).
     *
     * @param statement the query to execute, with its parameters already bound
     * @return an {@link IterableQueryResult}, which <b>must</b> be closed after use
     */
    IterableQueryResult queryAndFetch(Statement statement);

    /**
     * Executes the given NXQL query and returns the result that matches the filter.
     *
     * @param query the query to execute
     * @param distinctDocuments if {@code true} then a maximum of one row per document will be returned
     * @param limit the maximum number of documents to retrieve, or 0 for all of them
     * @param offset the offset (starting at 0) into the list of documents
     * @param countUpTo if {@code -1}, return a {@link PartialList} that includes a total size of the underlying list
     *            (size if there was no limit or offset). <br>
     *            If {@code 0}, don't return the total size of the underlying list. <br>
     *            If {@code n}, return the total size of the underlying list when the size is smaller than {@code n}
     *            else return a total size of {@code -1}.
     * @param params optional query-type-dependent parameters
     * @return the query result
     * @since 7.10-HF25, 8.10-HF06, 9.2
     */
    PartialList<Map<String, Object>> queryProjection(String query, boolean distinctDocuments, long limit, long offset,
            long countUpTo);

    /**
     * Executes an already parsed query and returns the requested properties.
     *
     * @param statement the query to execute, with its parameters already bound
     */
    PartialList<Map<String, Object>> queryProjection(Statement statement, boolean distinctDocuments, long limit,
            long offset, long countUpTo);

    /**
     * Executes the given query and returns the first batch of results containing id of documents, next batch must be
     * requested within the {@code keepAliveSeconds} delay.
     *
     * @param query The NXQL query to execute
     * @param batchSize The expected result batch size, note that more results can be returned when the backend don't
     *            implement properly this feature
     * @param keepAliveSeconds The scroll context lifetime in seconds
     * @return A {@link ScrollResult} including the search results and a scroll id, to be passed to the subsequent calls
     *         to {@link #scroll(String)}
     * @since 8.4
     */
    ScrollResult<String> scroll(String query, int batchSize, int keepAliveSeconds);

    /**
     * Executes an already parsed query and returns the first batch of results containing id of documents.
     *
     * @param statement the query to execute, with its parameters already bound
     */
    ScrollResult<String> scroll(Statement statement, int batchSize, int keepAliveSeconds);

    /**
     * Get the next batch of results containing id of documents, the {@code scrollId} is part of the previous
     * {@link ScrollResult} response.
     *
     * @throws NuxeoException when the {@code scrollId} is unknown or when the scroll operation has timed out
     * @since 8.4
     */
    ScrollResult<String> scroll(String scrollId);

    /** -------------------------- Security API --------------------------- * */

    /**
     * Turns the document into a record.
     * <p>
     * A record is a document with specific capabilities related to mandatory retention until a given date, and legal
     * holds. In addition, its main blob receives special treatment from the document blob manager to make sure it's
     * never shared with another blob at the storage level, and is deleted as soon as the record is deleted.
     * <p>
     * If the document is already a record, this method has no effect.
     * <p>
     * The permission {@value org.nuxeo.ecm.core.api.security.SecurityConstants#MANAGE_RETENTION} is required.
     *
     * @param docRef the document
     * @see #isRecord
     * @since 11.1
     */
    void setRecord(DocumentRef docRef, boolean isRecord);

    /**
     * Checks if the document is a record.
     *
     * @param docRef the document
     * @return {@code true} if the document is a record, {@code false} otherwise
     * @see #setRecord
     * @since 11.1
     */
    boolean isRecord(DocumentRef docRef);

    /**
     * The special date that corresponds to a retention date in the indeterminate future.
     *
     * @see #setRetainUntil
     * @see #RETAIN_UNTIL_INDETERMINATE
     * @since 11.1
     */
    String RETAIN_UNTIL_INDETERMINATE_ISO = "9999-01-01T00:00:00Z";

    /**
     * The special date that corresponds to a retention date in the indeterminate future.
     *
     * @see #setRetainUntil
     * @see #RETAIN_UNTIL_INDETERMINATE_ISO
     * @since 11.1
     */
    Instant RETAIN_UNTIL_INDETERMINATE = Instant.parse(RETAIN_UNTIL_INDETERMINATE_ISO);

    /**
     * Sets the retention date on the document (a record).
     * <p>
     * If no previous retention date was set, or if the previous retention date was
     * {@linkplain #RETAIN_UNTIL_INDETERMINATE indeterminate}, or if the previous retention date was <em>before</em>
     * the given value, then the retention date is set to the given value.
     * <p>
     * If the previous retention date was <em>after</em> the given value (that is, if trying to reduce the retention
     * time), an exception is thrown.
     * <p>
     * If the given value is {@code null} and the previous retention date is in the past (it has already expired), then
     * the retention date is set to {@code null}.
     * <p>
     * The permission {@value org.nuxeo.ecm.core.api.security.SecurityConstants#MANAGE_RETENTION} is required.
     *
     * @param docRef the document (a record)
     * @param retainUntil the new retention date
     * @throws PropertyException if trying to reduce the retention time, or if the document is not a record
     * @see #getRetainUntil
     * @see #RETAIN_UNTIL_INDETERMINATE
     * @since 11.1
     */
    void setRetainUntil(DocumentRef docRef, Instant retainUntil) throws PropertyException;

    /**
     * Gets the retention date for the document.
     *
     * @param docRef the document
     * @return the retention date, or {@value org.nuxeo.ecm.core.api.security.SecurityConstants#MANAGE_RETENTION} for a
     *         retention in the indeterminate future, or {@code null} if there is no retention date
     * @see #setRetainUntil
     * @see #RETAIN_UNTIL_INDETERMINATE
     * @since 11.1
     */
    Instant getRetainUntil(DocumentRef docRef);

    /**
     * Sets or removes the legal hold on the document (a record).
     * <p>
     * The permission {@value org.nuxeo.ecm.core.api.security.SecurityConstants#MANAGE_RETENTION} is required.
     *
     * @param docRef the document (a record)
     * @param hold {@code true} to set a legal hold, {@code false} to remove it
     * @see #hasLegalHold
     * @throws PropertyException if the document is not a record
     * @since 11.1
     */
    void setLegalHold(DocumentRef docRef, boolean hold);

    /**
     * Checks if the document has a legal hold set.
     *
     * @param docRef the document
     * @return {@code true} if a legal hold has been set on the document, {@code false} otherwise
     * @see #setLegalHold
     * @since 11.1
     */
    boolean hasLegalHold(DocumentRef docRef);

    /**
     * Checks if the document has a retention date in the future or has a legal hold.
     *
     * @param docRef the document
     * @return {@code true} if the document has a retention date in the future or if it has a legal hold, {@code false}
     *         otherwise
     * @see #getRetainUntil
     * @see #hasLegalHold
     * @since 11.1
     */
    boolean isUnderRetentionOrLegalHold(DocumentRef docRef);

    /**
     * Checks if this document is in the trash.
     *
     * @param docRef the document reference
     * @return true if the document is in the trash, false otherwise.
     * @since 10.1
     */
    boolean isTrashed(DocumentRef docRef);

    /**
     * Returns the repository name against which this core session is bound.
     *
     * @return the repository name used currently used as an identifier
     */
    String getRepositoryName();

    /**
     * Given a parent document, order the source child before the destination child. The source and destination must be
     * name of child documents of the given parent document. (a document name can be retrieved using
     * <code>docModel.getName()</code>) To place the source document at the end of the children list use a null
     * destination node.
     *
     * @param parent the parent document
     * @param src the document to be moved (ordered)
     * @param dest the document before which the reordered document will be placed If null the source document will be
     *            placed at the end of the children list
     */
    void orderBefore(DocumentRef parent, String src, String dest);

    /**
     * Internal method - it is used internally by {@link DocumentModel#refresh()}
     * <p>
     * Get fresh data from a document given a description of what kind of data should be refetched.
     * <p>
     * The refresh information is specified using a bit mask. See {@link DocumentModel} for all accepted flags.
     * <p>
     * When the flag {@link DocumentModel#REFRESH_CONTENT_IF_LOADED} is specified a third argument must be passed
     * representing the schema names for document parts to refresh. This argument is ignored if the flag is not
     * specified or no schema names are provided
     *
     * @param ref the document reference
     * @param refreshFlags refresh flags as defined in {@link DocumentModel}
     * @param schemas the schema names if a partial content refresh is required
     * @return a DocumentModelRefresh object
     */
    DocumentModelRefresh refreshDocument(DocumentRef ref, int refreshFlags, String[] schemas);

    /**
     * Gets the current change token for the document.
     * <p>
     * The change token is an opaque string which is modified every time the document is changed.
     * <p>
     * Before saving a document through {@link CoreSession#saveDocument} it's possible to pass an expected change token
     * in the document context data through {@code doc.putContextData(CoreSession.CHANGE_TOKEN, expectedChangeToken)}.
     * If the change token does not match the stored one, it means that a concurrent update happened, and a
     * {@link org.nuxeo.ecm.core.api.ConcurrentUpdateException ConcurrentUpdateException} will be thrown.
     *
     * @param ref the document reference
     * @return the change token
     * @since 9.1
     * @see DocumentModel#putContextData
     * @see #CHANGE_TOKEN
     * @see #getChangeToken
     */
    String getChangeToken(DocumentRef ref);

    /**
     * Visits the blobs of a document and, for those with a matching key, replace their key and digest with new ones.
     *
     * @param docRef the document reference
     * @param key the bob key to look for
     * @param newKey the new key
     * @param newDigest the new digest
     * @return the old digest if at least one replacement was done, {@code null} otherwise
     * @since 11.5
     */
    String replaceBlobDigest(DocumentRef docRef, String key, String newKey, String newDigest);

    /**
     * Gets the schema manager for this session.
     */
    SchemaManager getSchemaManager();

    List<String> getDirtyBinariesPaths(DocumentRef ref);
}
