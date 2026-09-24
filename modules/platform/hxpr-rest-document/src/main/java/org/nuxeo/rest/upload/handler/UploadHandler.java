/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.upload.handler;

import java.util.Map;

import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.UploadFileInfo;

/**
 * Upload Handler encapsulates features to handle uploads.
 */
public interface UploadHandler {

    public static final String USER_PARAM_NAME = "user";

    /**
     * Initiates a new upload.
     *
     * @throws DocumentSecurityException when current user is null.
     */
    Upload newUpload() throws DocumentSecurityException;

    /**
     * Attempts to fetch a upload with the given id.
     *
     * @param id the upload id to fetch
     * @return the upload with the given id, or {@code null} if not found
     * @throws DocumentSecurityException when current user does not match {@link Upload#getUser()} for this upload.
     */
    Upload getUpload(String id) throws DocumentSecurityException;

    /**
     * Gets the transient store used by this upload handler.
     *
     * @since 10.10
     */
    TransientStore getTransientStore();

    /**
     * Attempts to renew the credentials associated to this upload. This is only typically used in third-party upload
     * handlers.
     *
     * @param id the upload id
     * @return the new credentials
     * @since 11.1
     * @throws DocumentSecurityException when current user does not match {@link Upload#getUser()} for this upload.
     */
    default Map<String, Object> renewCredentials(String id) throws DocumentSecurityException {
        throw new UnsupportedOperationException("Renewal of credentials is not supported.");
    }

    /**
     * Callback for the upload handler to execute post-upload actions. This is only typically used in third-party upload
     * handlers.
     *
     * @param id the upload id
     * @param fileInfo file information regarding the uploaded file
     * @return {@code true} if the action was success
     * @throws DocumentSecurityException when current user does not match {@link Upload#getUser()} for this upload.
     */
    default boolean completeUpload(String id, UploadFileInfo fileInfo) throws DocumentSecurityException {
        throw new UnsupportedOperationException("Complete upload is not supported.");
    }

}
