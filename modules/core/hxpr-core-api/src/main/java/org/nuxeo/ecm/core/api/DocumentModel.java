/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.model.resolver.PropertyObjectResolver;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.model.InternalDocument.BlobAccessor;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.MixinNames;
import org.nuxeo.ecm.core.schema.TypeConstants;

/**
 * The document model is a representation of a core document.
 * <p>
 * The document model is made from several data models, each data model is bound to a schema. All the information about
 * a document (like security) is expressed using schemas (and implicitly data models).
 * <p>
 * Data models are lazily loaded as they are needed. At document model creation only data models corresponding to the
 * default schemas are loaded. The default schemas are configured in the type manager through extension points.
 *
 * @see CoreSession
 */
public interface DocumentModel extends Serializable {

    int REFRESH_STATE = 1; // "small" state (lock, versioning)

    int REFRESH_CONTENT_IF_LOADED = 64; // refresh now only if already loaded

    int REFRESH_CONTENT_LAZY = 128; // refresh later in lazy mode

    int REFRESH_CONTENT = 256; // refresh now

    int REFRESH_IF_LOADED = REFRESH_STATE | REFRESH_CONTENT_IF_LOADED;

    int REFRESH_LAZY = REFRESH_STATE | REFRESH_CONTENT_LAZY;

    int REFRESH_ALL = REFRESH_STATE | REFRESH_CONTENT;

    int REFRESH_DEFAULT = REFRESH_STATE | REFRESH_CONTENT_LAZY;

    /**
     * Gets the document type object.
     *
     * @return the document type object
     */
    DocumentType getDocumentType();

    /**
     * Gets the principal attached to this document's session.
     *
     * @return the principal
     * @since 11.1
     */
    NuxeoPrincipal getPrincipal();

    /**
     * Gets the core session to which this document is tied.
     * <p>
     * This may be null if the document has been detached from a session.
     *
     * @return the core session
     * @since 5.2.GA
     */
    CoreSession getCoreSession();

    /**
     * Creates the document in its session.
     */
    void create();

    /**
     * Gets a reference to the core document that can be used either remotely or locally (opens the core JVM).
     *
     * @return the document reference
     */
    DocumentRef getRef();

    /**
     * Retrieves the parent reference of the current document.
     *
     * @return the parent reference or null if no parent
     */
    DocumentRef getParentRef();

    /**
     * Gets the list of ancestor ids, starting at the root, up to the document's parent.
     * <p>
     * If the document is placeless or the root, returns an empty list.
     *
     * @return the list of ancestor ids (never {@code null})
     */
    List<String> getAncestorIds();

    /**
     * Gets the document UUID.
     *
     * @return the document UUID
     */
    String getId();

    /**
     * Sets the document id
     * <p>
     * Only allowed before {@link #create} has been called.
     *
     * @param id the document id
     */
    void setId(String id);

    /**
     * Gets the document name.
     *
     * @return the document name
     */
    String getName();

    /**
     * Gets the document's position in its containing folder (if ordered).
     *
     * @return the position, or {@code null} if the containing folder is not ordered
     * @since 6.0
     */
    Long getPos();

    /**
     * Get a text suitable to be shown in a UI for this document.
     *
     * @return the title or the internal name if no title could be found
     */
    String getTitle();

    /**
     * Gets the document path as a string.
     *
     * @return the document path as string
     */
    String getPathAsString();

    /**
     * Gets the document path.
     *
     * @return the document path as string
     */
    Path getPath();

    /**
     * Gets the document type name.
     *
     * @return the document type name
     */
    String getType();

    /**
     * Gets the schemas available on this document (from the type and the mixins).
     *
     * @return the schemas
     * @since 5.4.2
     */
    Set<String> getSchemas();

    /**
     * Checks if the document has the given schema, either from its type or added on the instance through a mixin.
     *
     * @param schema the schema name
     * @return {@code true} if the document has the schema
     */
    boolean hasSchema(String schema);

    /**
     * Sets the primary type and mixin types on this document.
     *
     * @param typeName the primary type, or {@code null} to not change it
     * @param mixins the mixin types, or {@code null} to not change them
     * @throws IllegalArgumentException if the type or a mixin does not exist
     */
    void setPrimaryTypeAndMixinTypes(String typeName, Collection<String> mixins);

    /**
     * Sets the mixins on this document.
     *
     * @param mixins the mixin types
     * @throws IllegalArgumentException if a mixin does not exist
     */
    void setMixins(Collection<String> mixins);

    /**
     * Adds a mixin on this document.
     *
     * @param mixin the mixin
     * @throws IllegalArgumentException if the mixin does not exist
     */
    void addMixin(String mixin);

    /**
     * Gets the mixins available on this document (from the type and the instance mixins).
     *
     * @return the mixins
     * @since 5.4.2
     */
    Set<String> getEffectiveMixins();

    /**
     * Checks if the document has a mixin, either from its type or added on the instance.
     *
     * @param mixin the mixin
     * @return {@code true} if the document has the mixin
     */
    boolean hasMixin(String mixin);

    /**
     * Tests if the document is locked.
     * <p>
     * Lock info is cached on the document for performance. Use {@link CoreSession#getLockInfo} to get the non-cached
     * status.
     *
     * @return the lock key if the document is locked or null otherwise
     */
    boolean isLocked();

    /**
     * Sets a lock on the document.
     *
     * @return the lock info that was set
     * @throws LockException if the document is already locked
     * @since 5.4.2
     */
    Lock setLock() throws LockException;

    /**
     * Gets the lock info on the document.
     * <p>
     * Lock info is cached on the document for performance. Use {@link CoreSession#getLockInfo} to get the non-cached
     * status.
     *
     * @return the lock info if the document is locked, or {@code null} otherwise
     * @since 5.4.2
     */
    Lock getLockInfo();

    /**
     * Removes the lock on the document.
     * <p>
     * The caller principal should be the same as the one who set the lock or to belongs to the administrator group,
     * otherwise an exception will be throw.
     * <p>
     * If the document was not locked, does nothing.
     * <p>
     * Returns the previous lock info.
     *
     * @return the removed lock info, or {@code null} if there was no lock
     * @throws LockException if the document is locked by someone else
     * @since 5.4.2
     */
    Lock removeLock() throws LockException;

    /**
     * Tests if the document is checked out.
     * <p>
     * A checked out document can be modified normally. A checked in document is identical to the last version that it
     * created, and not modifiable.
     * <p>
     * Only applicable to documents that are live (not versions).
     *
     * @return {@code true} if the document is checked out, {@code false} if it is checked in
     * @since 5.4
     */
    boolean isCheckedOut();

    /**
     * Checks out a document.
     * <p>
     * A checked out document can be modified normally.
     * <p>
     * Only applicable to documents that are live (not versions).
     *
     * @since 5.4
     */
    void checkOut();

    /**
     * Checks in a document and returns the created version.
     * <p>
     * A checked in document is identical to the last version that it created, and not modifiable.
     * <p>
     * Only applicable to documents that are live (not versions).
     *
     * @return the version just created
     * @since 5.4
     */
    DocumentRef checkIn();

    /**
     * Checks in a document and returns the created version (maybe minor).
     * <p>
     * A checked in document is identical to the last version that it created, and not modifiable.
     * <p>
     * Only applicable to documents that are live (not versions).
     *
     * @param minor whether this should be a minor version
     * @return the version just created
     * @since 5.4
     */
    DocumentRef checkIn(boolean minor);

    /**
     * Gets the version.
     *
     * @return the version, or {@code null}
     */
    Long getVersion();

    /**
     * Gets the minor version.
     *
     * @return the minor version, or {@code null}
     */
    Long getMinorVersion();

    /**
     * Checks if the document is a record.
     *
     * @return {@code true} if the document is a record, {@code false} otherwise
     * @since 11.1
     */
    default boolean isRecord() {
        return false;
    }

    /**
     * Gets the retention date for the document.
     *
     * @return the retention date, or {@value org.nuxeo.ecm.core.api.security.SecurityConstants#MANAGE_RETENTION} for a
     *         retention in the indeterminate future, or {@code null} if there is no retention date
     * @see CoreSession#RETAIN_UNTIL_INDETERMINATE
     * @see #isUnderRetentionOrLegalHold
     * @since 11.1
     */
    default Instant getRetainUntil() {
        return null;
    }

    /**
     * Checks if the document has a legal hold set.
     *
     * @return {@code true} if a legal hold has been set on the document, {@code false} otherwise
     * @see #isUnderRetentionOrLegalHold
     * @since 11.1
     */
    default boolean hasLegalHold() {
        return false;
    }

    /**
     * Checks if the document has a retention date in the future or has a legal hold.
     *
     * @return {@code true} if the document has a retention date in the future or if it has a legal hold, {@code false}
     *         otherwise
     * @see #getRetainUntil
     * @see #hasLegalHold
     * @since 11.1
     */
    default boolean isUnderRetentionOrLegalHold() {
        return false;
    }

    /**
     * Gets the security policies for this document.
     *
     * @return the list of security policies (never {@code null})
     */
    List<String> getSecurityPolicies();

    /**
     * Sets the security policies for this document.
     *
     * @param securityPolicies the list of security policies
     */
    void setSecurityPolicies(List<String> securityPolicies);

    /**
     * Gets the local ACL for this document.
     * <p>
     * This does not take into account inherited ACLs.
     * <p>
     * Note that modifying the returned ACL will not affect in any way the stored document ACL. To modify the ACL you
     * must explicitly set it by calling {@link CoreSession#setACL(DocumentRef, ACL, boolean)}
     *
     * @return the local ACL (never {@code null})
     */
    ACL getLocalACL();

    /**
     * Gets the effective ACL for this document.
     * <p>
     * The returned ACL is the ACL defined on this document plus the inherited ACL if any.
     * <p>
     * Note that modifying the returned ACL will not affect in any way the stored document ACL. To modify the ACL you
     * must explicitly set it by calling {@link CoreSession#setACL(DocumentRef, ACL, boolean)}
     *
     * @return the effective ACL (an empty list if no local ACLs are defined or inherited)
     */
    ACL getEffectiveACL();

    /**
     * Sets the ACL for this document model.
     *
     * @see CoreSession#setACL(DocumentRef, ACL, boolean)
     * @param acl the ACL to set
     * @param overwrite whether to overwrite the old ACL or not
     */
    void setACL(ACL acl, boolean overwrite);

    /**
     * Gets the effective permissions on this document for the current user.
     * <p>
     * The returned set contains all permissions (including groups of permissions) that the current user has on this
     * document.
     *
     * @return the effective permissions for the current user
     */
    Set<String> getEffectivePermissions();

    /**
     * Checks if this document is in the trash.
     *
     * @return true if the document is in the trash, false otherwise.
     * @since 10.1
     */
    boolean isTrashed();

    /**
     * Checks if this document is a folder.
     *
     * @return true if the document is a folder, false otherwise
     */
    boolean isFolder();

    /**
     * Checks if this document can have versions.
     *
     * @return true if the document can have versions, false otherwise
     */
    boolean isVersionable();

    boolean isGovernable();

    /**
     * Checks if this document is an embeddings document (i.e is of type {@link TypeConstants#SYS_EMBEDDINGS})).
     *
     * @return true if the document is of embeddings doctype, false otherwise
     */
    default boolean isEmbeddings() {
        return TypeConstants.SYS_EMBEDDINGS.equals(getType());
    }

    /**
     * Checks if this document contains embeddings (i.e has the {@link MixinNames#SYS_HAS_EMBEDDINGS} mixin).
     *
     * @return true if the document has the hasEmbeddings mixin, false otherwise
     */
    default boolean hasEmbeddings() {
        return hasMixin(MixinNames.SYS_HAS_EMBEDDINGS);
    }

    /**
     * Checks if this document is a version.
     *
     * @return true if the document is an older version of another document, false otherwise
     */
    boolean isVersion();

    /**
     * Checks if the document has actual data to write (dirty parts).
     *
     * @since 5.5
     */
    boolean isDirty();

    /**
     * Gets the context data associated to this document.
     *
     * @return a map of context data
     */
    Map<String, Serializable> getContextData();

    /**
     * Gets the context data using the default scope.
     *
     * @param key the context data key
     * @return the value
     */
    Serializable getContextData(String key);

    /**
     * Sets a context data in the default scope.
     *
     * @param key the context data key
     * @param value the value
     */
    void putContextData(String key, Serializable value);

    /**
     * Copies the context data from given document to this document.
     */
    void copyContextData(DocumentModel otherDocument);

    /**
     * Sets the condition (an HXQL predicate) under which the document is updated on {@link CoreSession#save save}.
     * <p>
     * A {@link ConditionFailedException} will be thrown at {@link CoreSession#save save} time if the condition is not
     * met, after which the transaction must be rolled back.
     *
     * @param condition the condition (an HXQL predicate)
     */
    void setUpdateCondition(String condition);

    /**
     * Returns the name of the repository in which the document is stored.
     *
     * @return the repository name as a string.
     */
    String getRepositoryName();

    /**
     * Gets a property value given a xpath.
     * <p>
     * Note that what's called xpath in this context is not an actual XPath as specified by the w3c. Main differences
     * are that in our xpath:
     * <ul>
     * <li>Indexes start at 0 instead of 1</li>
     * </ul>
     * The latter is possible because in Nuxeo lists of complex elements are homogenous, so the name of the second-level
     * element is implied.
     */
    Object getPropertyValue(String xpath) throws PropertyException;

    /**
     * Sets a property value given a xpath.
     */
    void setPropertyValue(String xpath, Object value) throws PropertyException;

    /**
     * Visits this document's values, passing information to the {@link ValuesVisitor}.
     */
    void visitValues(ValuesVisitor visitor);

    /**
     * Visits all the blobs of this document and calls the passed blob visitor on each one.
     */
    void visitBlobs(Consumer<BlobAccessor> blobVisitor) throws PropertyException;

    /**
     * Visits this document's fields, passing information to the {@link ValuesVisitor}.
     */
    void visitFields(ValuesVisitor visitor);

    /**
     * Clears any prefetched or cached document data.
     * <p>
     * This will force the document to lazily update its data when required.
     */
    void reset();

    /**
     * Refresh document data from server.
     * <p>
     * The data models will be removed and all prefetch and system data will be refreshed from the server
     * <p>
     * The refreshed data contains:
     * <ul>
     * <li>document life cycle
     * <li>document lock state
     * <li>document parts if required - otherwise parts data will be removed to be refreshed lazy
     * </ul>
     * The refresh flags are:
     * <ul>
     * <li>{@link DocumentModel#REFRESH_STATE}
     * <li>{@link DocumentModel#REFRESH_CONTENT_IF_LOADED}
     * <li>{@link DocumentModel#REFRESH_CONTENT_LAZY}
     * <li>{@link DocumentModel#REFRESH_CONTENT}
     * <li>{@link DocumentModel#REFRESH_DEFAULT} same as REFRESH_STATE | REFRESH_DEFAULT |
     * REFRESH_CONTENT_IF_LOADED
     * <li>{@link DocumentModel#REFRESH_ALL} same as REFRESH_STATE | REFRESH_PREFTECH | REFRESH_CONTENT
     * </ul>
     * If XX_IF_LOADED is used then XX will be refreshed only if already loaded in the document - otherwise a lazy
     * refresh will be done
     *
     * @param refreshFlags the refresh flags
     * @param schemas the document parts (schemas) that should be refreshed now
     */
    void refresh(int refreshFlags, String[] schemas);

    /** Info fetched internally during a refresh. */
    interface DocumentModelRefresh {
    }

    /**
     * Same as {@code DocumentModel.refresh(REFRESH_DEFAULT)}.
     */
    void refresh();

    /**
     * Clone operation. Must be made public instead of just protected as in Object.
     */
    DocumentModel clone() throws CloneNotSupportedException;

    /**
     * Gets the current change token for this document.
     * <p>
     * The change token is an opaque string which is modified every time the document is changed.
     * <p>
     * Before saving a document through {@link CoreSession#saveDocument} it's possible to pass an expected change token
     * in the document context data through {@code doc.putContextData(CoreSession.CHANGE_TOKEN, expectedChangeToken)}.
     * If the change token does not match the stored one, it means that a concurrent update happened, and a
     * {@link org.nuxeo.ecm.core.api.ConcurrentUpdateException ConcurrentUpdateException} will be thrown.
     *
     * @return the change token
     * @since 5.5
     * @see #putContextData
     * @see CoreSession#CHANGE_TOKEN
     * @see CoreSession#getChangeToken
     */
    String getChangeToken();

    /**
     * Gets the fulltext extracted from the binary fields.
     *
     * @since 5.9.3
     */
    String getBinaryFulltext();

    /**
     * @param xpath the property xpath
     * @return A {@link PropertyObjectResolver} to manage the property reference to external entities, null if this
     *         property's type has no resolver.
     * @since 7.1
     */
    PropertyObjectResolver getObjectResolver(String xpath);

}
