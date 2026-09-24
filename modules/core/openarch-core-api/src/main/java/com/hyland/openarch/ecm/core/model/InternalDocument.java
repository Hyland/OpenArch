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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.ConditionFailedException;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.ValuesVisitor;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.SchemaManager;

/**
 * A low-level document from a {@link InternalSession}.
 */
public interface InternalDocument {

    /**
     * Gets the session that owns this document.
     *
     * @return the session
     */
    InternalSession getInternalSession();

    /**
     * Gets the schema manager for this document.
     *
     * @return the schema manager
     */
    SchemaManager getSchemaManager();

    /**
     * Gets the name of this document.
     *
     * @return the document name
     */
    String getName();

    /**
     * Gets the document's position in its containing folder (if ordered).
     *
     * @return the position
     * @since 6.0
     */
    Long getPos();

    /**
     * Gets this document's id.
     *
     * @return the document id
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
     * Gets the parent document id, or {@code null} if this is the root document.
     *
     * @return the parent document id, or {@code null}
     */
    String getParentId();

    /**
     * Gets the list of ancestor ids, starting at the root, up to the document's parent.
     * <p>
     * If the document is placeless or the root, returns an empty list.
     *
     * @return the list of ancestor ids (never {@code null})
     */
    List<String> getAncestorIds();

    /**
     * Gets the parent document, or {@code null} if this is the root document.
     *
     * @return the parent document, or {@code null}
     */
    InternalDocument getParent();

    /**
     * Gets the parent document with allowing unknownType, or {@code null} if this is the root document.
     *
     * @return the parent document, or {@code null}
     */
    InternalDocument getParent(boolean allowUnknownType);

    /**
     * Gets the type of this document.
     *
     * @return the document type
     */
    DocumentType getType();

    /**
     * Gets the path of this document.
     *
     * @return the path
     */
    String getPath();

    /**
     * Gets the ACL for this document (without any inheritance).
     *
     * @return the ACL (an empty list if no local ACLs are defined)
     */
    ACL getLocalACL();

    /**
     * Gets the effective ACL for this document (including inheritance).
     *
     * @return the effective ACL (an empty list if no local ACLs are defined or inherited)
     */
    ACL getEffectiveACL();

    /**
     * Sets the ACL for this document.
     *
     * @param acl the ACL
     * @param overwrite whether to overwrite any previous ACL or just add to it
     * @param principal the caller, for event purposes
     */
    WriteResult setACL(ACL acl, boolean overwrite, NuxeoPrincipal principal);

    /**
     * Checks if the principal has the given permission on this document.
     */
    boolean hasPermission(NuxeoPrincipal principal, String permission);

    /**
     * Gets the effective permissions on this document for the given principal.
     * <p>
     * The returned set contains all permissions (including groups of permissions) that the principal has on the
     * document.
     *
     * @param principal the principal
     * @return the effective permissions for the principal
     */
    Set<String> getEffectivePermissions(NuxeoPrincipal principal);

    /**
     * Sets a simple property value.
     * <p>
     * For more generic properties described by an xpath, use {@link #setValue} instead.
     *
     * @param name the name of the property to set
     * @param value the value to set
     * @see #setValue
     */
    void setPropertyValue(String name, Object value);

    /**
     * Sets a property value.
     * <p>
     * The xpath may point to a partial path, in which case the value may be a complex {@link List} or {@link Map}.
     *
     * @param xpath the xpath of the property to set
     * @param value the value to set
     * @throws PropertyException if the property does not exist or the value is of the wrong type
     * @since 7.3
     */
    void setValue(String xpath, Object value) throws PropertyException;

    /**
     * Caller context for a property write.
     */
    record WriteContext( //
            NuxeoPrincipal principal, //
            String changeToken, //
            boolean userChange) {
    }

    /**
     * Sets a value with the caller context needed for permissions and repository-managed behavior.
     *
     * @return the write result, including any events that the caller must publish
     */
    WriteResult setValue(String xpath, Object value, WriteContext context) throws PropertyException;

    /**
     * Gets a simple property value.
     * <p>
     * For more generic properties described by an xpath, use {@link #getValue} instead.
     *
     * @param name the name of the property to get
     * @return the property value or {@code null} if the property is not set
     * @see #getValue
     */
    Object getPropertyValue(String name);

    /**
     * Gets a property value.
     * <p>
     * The xpath may point to a partial path, in which case the value may be a complex {@link List} or {@link Map}.
     *
     * @param xpath the xpath of the property to set
     * @return the property value or {@code null} if the property is not set
     * @throws PropertyException if the property does not exist
     */
    Object getValue(String xpath) throws PropertyException;

    /**
     * Gets a value with the caller context needed for permission-aware repository-managed fields.
     */
    Object getValue(String xpath, NuxeoPrincipal principal) throws PropertyException;

    /**
     * Visits this document's values, passing information to the {@link ValuesVisitor}.
     */
    void visitValues(ValuesVisitor visitor);

    /**
     * An accessor that can read or write a blob and know its xpath.
     *
     * @since 7.3
     */
    interface BlobAccessor {
        /** Gets the blob's xpath. */
        String getXPath();

        /** Gets the blob. */
        Blob getBlob();

        /** Sets the blob. */
        void setBlob(Blob blob);
    }

    /**
     * Visits all the blobs of this document and calls the passed blob visitor on each one.
     *
     * @since 7.3
     */
    void visitBlobs(Consumer<BlobAccessor> blobVisitor) throws PropertyException;

    /**
     * Visits this document's fields, passing information to the {@link ValuesVisitor}.
     */
    void visitFields(ValuesVisitor visitor);

    /**
     * Visits the blobs of this document and, for those with a matching key, replace their key and digest with new ones.
     *
     * @param key the bob key to look for
     * @param newKey the new key
     * @param newDigest the new digest
     * @return the old digest if at least one replacement was done, {@code null} otherwise
     * @since 11.5
     */
    String replaceBlobDigest(String key, String newKey, String newDigest);

    /**
     * Checks whether this document is a folder.
     *
     * @return {@code true} if the document is a folder, {@code false} otherwise
     */
    boolean isFolder();

    /**
     * Sets this document as readonly or not.
     *
     * @since 5.9.2
     */
    void setReadOnly(boolean readonly);

    /**
     * Checks whether this document is readonly or not.
     *
     * @since 5.9.2
     */
    boolean isReadOnly();

    /**
     * Removes this document and all its children, if any.
     *
     * @param principal the caller
     * @since 11.1
     */
    void remove(NuxeoPrincipal principal);

    /**
     * Removes this document only, without its children.
     *
     * @since 11.1
     */
    void removeSingleton(NuxeoPrincipal principal);

    /**
     * Turns the document into a record.
     * <p>
     * A record is a document with specific capabilities related to mandatory retention until a given date, and legal
     * holds. In addition, its main blob receives special treatment from the document blob manager to make sure it's
     * never shared with another blob at the storage level, and is deleted as soon as the record is deleted.
     * <p>
     * If the document is already a record, this method has no effect.
     * <p>
     * The permission {@value SecurityConstants#MANAGE_RETENTION} is required.
     *
     * @see #isRecord
     * @since 11.1
     */
    void makeRecord();

    /**
     * Checks if the document is a record.
     *
     * @return {@code true} if the document is a record, {@code false} otherwise
     * @see #makeRecord
     * @since 11.1
     */
    boolean isRecord();

    /**
     * Sets a retention date for the document (a record).
     * <p>
     * If no previous retention date was set, or if the previous retention date was
     * {@linkplain CoreSession#RETAIN_UNTIL_INDETERMINATE indeterminate}, or if the previous retention date was
     * <em>before</em> the given value, then the retention date is set to the given value.
     * <p>
     * If the previous retention date was <em>after</em> the given value (that is, if trying to reduce the retention
     * time), an exception is thrown.
     * <p>
     * If the given value is {@code null} and the previous retention date is in the past (it has already expired), then
     * the retention date is set to {@code null}.
     * <p>
     * The permission {@value SecurityConstants#MANAGE_RETENTION} is required.
     *
     * @param retainUntil the new retention date
     * @throws PropertyException if trying to reduce the retention time, or if the document is not a record
     * @see #getRetainUntil
     * @see CoreSession#RETAIN_UNTIL_INDETERMINATE
     * @since 11.1
     */
    void setRetainUntil(Instant retainUntil) throws PropertyException;

    /**
     * Gets the retention date for the document.
     *
     * @return the retention date, or {@value SecurityConstants#MANAGE_RETENTION} for a
     *         retention in the indeterminate future, or {@code null} if there is no retention date
     * @see #setRetainUntil
     * @see CoreSession#RETAIN_UNTIL_INDETERMINATE
     * @since 11.1
     */
    Instant getRetainUntil();

    /**
     * Sets or removes a legal hold on the document (a record).
     * <p>
     * The permission {@value SecurityConstants#MANAGE_RETENTION} is required.
     *
     * @param hold {@code true} to set a legal hold, {@code false} to remove it
     * @see #hasLegalHold
     * @throws PropertyException if the document is not a record
     * @since 11.1
     */
    void setLegalHold(boolean hold);

    /**
     * Checks if the document has a legal hold set.
     *
     * @return {@code true} if a legal hold has been set on the document, {@code false} otherwise
     * @see #setLegalHold
     * @since 11.1
     */
    boolean hasLegalHold();

    /**
     * Checks if the document has a retention date in the future or has a legal hold.
     *
     * @return {@code true} if the document has a retention date in the future or if it has a legal hold, {@code false}
     *         otherwise
     * @see #getRetainUntil
     * @see #hasLegalHold
     * @since 11.1
     */
    boolean isUnderRetentionOrLegalHold();

    /**
     * Gets the repository in which the document lives.
     *
     * @return the repository name.
     */
    String getRepositoryName();

    /**
     * Gets the current change token for this document.
     *
     * @return the change token
     * @since 9.1
     */
    String getChangeToken();

    /**
     * Validates that the passed user-visible change token is compatible with the one for this document.
     *
     * @return {@code false} if the change token is not valid
     * @since 9.2
     */
    boolean validateUserVisibleChangeToken(String changeToken);

    /**
     * Marks the document as being modified by a user change.
     * <p>
     * This causes an additional change token increment and check during save.
     *
     * @since 9.2
     */
    void markUserChange();

    /**
     * Sets the condition under which the document is updated on {@link InternalSession#save save}.
     * <p>
     * A {@link ConditionFailedException} is thrown immediately when an expression is statically false; otherwise it is
     * thrown at {@link InternalSession#save save} time if the condition is not met. After either failure, the
     * transaction must be rolled back.
     *
     * @param condition the condition
     */
    void setUpdateCondition(UpdateCondition condition, NuxeoPrincipal principal);

    /**
     * Gets the mixins available on this document (from the type and the instance mixins).
     *
     * @return the mixins
     * @since 5.4.2
     */
    Set<String> getEffectiveMixins();

    /**
     * Gets the mixins defined on this document instance. The type mixins are not returned.
     *
     * @return the mixins
     * @since 5.4.2
     */
    List<String> getMixins();

    /**
     * Checks whether this document has a given mixin, either from its type or added on the instance.
     *
     * @param mixin the mixin name
     * @return {@code true} if the document has the mixin
     * @since 5.4.2
     */
    boolean hasMixin(String mixin);

    /**
     * Sets the primary type and mixin types on this document.
     *
     * @param typeName the primary type, or {@code null} to not change it
     * @param mixins the mixin types, or {@code null} to not change them
     * @return {@code true} if something in the document has changed, or {@code false} if not
     * @throws IllegalArgumentException if the type or a mixin does not exist
     */
    boolean setPrimaryTypeAndMixinTypes(String typeName, Collection<String> mixins);

    /**
     * Sets a lock on this document.
     *
     * @param lock the lock to set
     * @return {@code null} if locking succeeded, or the existing lock if locking failed
     */
    Lock setLock(Lock lock);

    /**
     * Removes a lock from this document.
     *
     * @param owner the owner to check, or {@code null} for no check
     * @return {@code null} if there was no lock or if removal succeeded, or a lock if it blocks removal due to owner
     *         mismatch
     */
    Lock removeLock(String owner);

    /**
     * Gets the lock if one set on this document.
     *
     * @return the lock, or {@code null} if no lock is set
     */
    Lock getLock();

    /**
     * Gets a child document given its name.
     * <p>
     * Throws {@link DocumentNotFoundException} if the document could not be found.
     *
     * @param name the name of the child to retrieve
     * @return the child if exists
     * @throws DocumentNotFoundException if the child does not exist
     */
    InternalDocument getChild(String name);

    /**
     * Gets the children of the document.
     *
     * @return the children
     */
    List<InternalDocument> getChildren();

    /**
     * Gets a list of the children ids.
     *
     * @return a list of children ids.
     * @since 1.4.1
     */
    List<String> getChildrenIds();

    /**
     * Checks whether this document has a child of the given name.
     *
     * @param name the name of the child to check
     * @return {@code true} if the child exists, {@code false} otherwise
     */
    boolean hasChild(String name);

    /**
     * Tests if the document has any children.
     *
     * @return {@code true} if the document has children, {@code false} otherwise
     */
    boolean hasChildren();

    /**
     * Orders the given source child before the destination child.
     * <p>
     * Both source and destination must be names that point to child documents of this document. The source document
     * will be placed before the destination one. If destination is {@code null}, the source document will be appended
     * at the end of the children list.
     *
     * @param src the document to move
     * @param dest the document before which to place the source document
     */
    void orderBefore(String src, String dest);

    /**
     * Creates a new version.
     *
     * @return the created version
     */
    InternalDocument checkIn(NuxeoPrincipal principal);

    /**
     * Creates a new (maybe minor) version.
     *
     * @param minor whether this should be a minor version
     * @return the created version
     */
    InternalDocument checkIn(NuxeoPrincipal principal, boolean minor);

    /**
     * Checks out a working copy.
     */
    void checkOut();

    /**
     * Checks whether this document can have versions.
     *
     * @return {@code true} if it's versionable, {@code false} otherwise
     */
    boolean isVersionable();

    /**
     * Checks whether this document is a version document.
     *
     * @return {@code true} if it's a version, {@code false} otherwise
     */
    boolean isVersion();

    /**
     * Checks whether this document is checked out.
     *
     * @return {@code true} if the document is checked out, or {@code false} otherwise
     */
    boolean isCheckedOut();

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
     * Gets the version creation date of this document if it's a version.
     *
     * @return the version creation date, or {@code null} if it's not a version
     */
    Instant getVersionCreationDate();

    /**
     * Gets the working copy for this version.
     *
     * @return the working copy
     */
    InternalDocument getWorkingCopy();

}
