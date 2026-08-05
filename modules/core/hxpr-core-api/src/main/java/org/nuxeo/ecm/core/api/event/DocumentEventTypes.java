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

package org.nuxeo.ecm.core.api.event;

public final class DocumentEventTypes {

    /**
     * At this point the document is filled with data from DocumentModel.
     */
    public static final String DOCUMENT_CREATED = "documentCreated";

    public static final String DOCUMENT_REMOVED = "documentRemoved";

    public static final String VERSION_REMOVED = "versionRemoved";

    public static final String DOCUMENT_UPDATED = "documentModified";

    public static final String DOCUMENT_SECURITY_UPDATED = "documentSecurityUpdated";

    public static final String DOCUMENT_LOCKED = "documentLocked";

    public static final String DOCUMENT_UNLOCKED = "documentUnlocked";

    public static final String DOCUMENT_CREATED_BY_COPY = "documentCreatedByCopy";

    public static final String DOCUMENT_DUPLICATED = "documentDuplicated";

    public static final String DOCUMENT_MOVED = "documentMoved";

    public static final String DOCUMENT_DOWNLOADED = "download";

    public static final String DOCUMENT_RENDITION_CREATED = "documentRenditionCreated";

    public static final String DOCUMENT_RESTORED = "documentRestored";

    public static final String DOCUMENT_CHILDREN_ORDER_CHANGED = "childrenOrderChanged";

    public static final String DOCUMENT_QUERIED = "documentQueried";

    public static final String DOCUMENT_VIEWED = "documentViewed";

    public static final String DOCUMENT_PERMISSION_DENIED = "documentPermissionDenied";

    public static final String RENDITION_PERMISSION_DENIED = "renditionPermissionDenied";

    /**
     * Document checked out. Listeners can increment version numbers. Listeners will be passed a pristine DocumentModel
     * where changes will not be seen by the main DocumentModel being saved.
     */
    public static final String DOCUMENT_CHECKEDOUT = "documentCheckedOut";

    /** This event is too general and should be used with care. */
    public static final String ABOUT_TO_CHECKIN = "aboutToCheckIn";

    /** This event is too general and should be used with care. */
    public static final String DOCUMENT_CHECKEDIN = "documentCheckedIn";

    /**
     * A binary fulltext field has been updated.
     *
     * @since 5.9.3
     */
    public static final String BINARYTEXT_UPDATED = "binaryTextUpdated";

    /**
     * A blob had its digest updated.
     *
     * @since 11.5
     */
    public static final String BLOB_DIGEST_UPDATED = "blobDigestUpdated";

    /**
     * The event property containing the system property updated.
     * <p>
     * Available for the event {@link #BINARYTEXT_UPDATED}.
     *
     * @since 10.3
     */
    public static final String SYSTEM_PROPERTY = "systemProperty";

    /**
     * The event property containing the value of the system property updated.
     * <p>
     * Available for the event {@link #BINARYTEXT_UPDATED}.
     *
     * @since 10.3
     */
    public static final String SYSTEM_PROPERTY_VALUE = "systemPropertyValue";

    /**
     * Event triggered when a document has been turned into a record.
     *
     * @since 11.1
     */
    public static final String AFTER_MAKE_RECORD = "afterMakeRecord";

    /**
     * Event triggered after a retention has been set on a document.
     * <p>
     * The event's options map contains {@link CoreEventConstants#RETAIN_UNTIL} (a {@link java.util.Calendar} object).
     *
     * @since 11.1
     */
    public static final String AFTER_SET_RETENTION = "afterSetRetention";

    /**
     * Event triggered after a retention has been extended on a document.
     * <p>
     * The event's options map contains {@link CoreEventConstants#RETAIN_UNTIL} (a {@link java.util.Calendar} object).
     *
     * @since 11.1
     */
    public static final String AFTER_EXTEND_RETENTION = "afterExtendRetention";

    /**
     * Event triggered (some time) after the retention of a document has expired.
     * <p>
     * The event's options map contains {@link CoreEventConstants#RETAIN_UNTIL} (a {@link java.util.Calendar} object),
     * which was the previous retention date.
     *
     * @since 11.1
     */
    public static final String RETENTION_EXPIRED = "retentionExpired";

    /**
     * Event triggered after a legal hold has been set on a document.
     *
     * @since 11.1
     */
    public static final String AFTER_SET_LEGAL_HOLD = "afterSetLegalHold";

    /**
     * Event triggered after a legal hold has been removed on a document.
     *
     * @since 11.1
     */
    public static final String AFTER_REMOVE_LEGAL_HOLD = "afterRemoveLegalHold";

    /**
     * Event triggered during the save of a session if the current document contains at least one dirty blobs. The event
     * context contains the list of blobs xpath which are dirty.
     */
    public static final String DOCUMENT_BLOB_MODIFIED = "documentBlobModified";

    /**
     * Event triggered when a graph has been updated.
     *
     */
    public static final String GRAPH_UPDATED = "graphUpdated";

    // Constant utility class
    private DocumentEventTypes() {
    }

}
