/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.api.event;

/**
 * Core event constants.
 *
 * @author <a href="mailto:ja@nuxeo.com">Julien Anguenot</a>
 */
public final class CoreEventConstants {

    public static final String REPOSITORY_NAME = "repositoryName";

    public static final String IS_PRESIGNED_URL = "isPresignedUrl";

    public static final String DOCUMENT_PARENT_ID = "docParentID";

    public static final String SOURCE_REF = "sourceRef";

    public static final String SOURCE_PARENT_REF = "sourceParentRef";

    public static final String SOURCE_PATH = "sourcePath";

    public static final String SOURCE_PARENT_PATH = "sourceParentPath";

    /**
     * Passed with {@value DocumentEventTypes#BEFORE_SET_RETENTION},{@value DocumentEventTypes#BEFORE_EXTEND_RETENTION},
     * {@value DocumentEventTypes#AFTER_SET_RETENTION} and {@value DocumentEventTypes#AFTER_EXTEND_RETENTION} events,
     * the retention datetime (a {@link java.util.Calendar} object).
     *
     * @since 11.1
     */
    public static final String RETAIN_UNTIL = "retainUntil";

    /**
     * Passed with blobDigestUpdated event, old key.
     *
     * @since 11.5
     */
    public static final String BLOB_DIGEST_UPDATED_OLD_KEY = "oldKey";

    /**
     * Passed with blobDigestUpdated event, old digest.
     *
     * @since 11.5
     */
    public static final String BLOB_DIGEST_UPDATED_OLD_DIGEST = "oldDigest";

    /**
     * Passed with blobDigestUpdated event, new key.
     *
     * @since 11.5
     */
    public static final String BLOB_DIGEST_UPDATED_NEW_KEY = "newKey";

    /**
     * Passed with blobDigestUpdated event, new digest.
     *
     * @since 11.5
     */
    public static final String BLOB_DIGEST_UPDATED_NEW_DIGEST = "newDigest";

    public static final String DIRTY_XPATHS = "dirtyPaths";

    public static final String DIRTY_BLOB_XPATHS = "dirtyBlobsPaths";

    /**
     * Passed with events involving multiple documents (query results, bulk operations, ...). The value is e.g.
     * an ID for all documents involved in one operation.
     */
    public static final String RELATED_OBJECT = "relatedObject";

    // Constant utility class
    private CoreEventConstants() {
    }

}
