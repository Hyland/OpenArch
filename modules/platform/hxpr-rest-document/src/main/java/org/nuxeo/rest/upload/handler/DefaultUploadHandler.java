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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.nuxeo.rest.upload.handler.UploadHandlerConfig.TRANSIENT_STORE_NAME;

import java.util.Map;
import java.util.UUID;

import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.rest.upload.ChunkInfo;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.UploadStatus;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default upload handler: common code.
 */
public class DefaultUploadHandler implements UploadHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultUploadHandler.class);

    @Override
    public TransientStore getTransientStore() {
        return Framework.getService(TransientStoreService.class).getStore(TRANSIENT_STORE_NAME);
    }

    protected String getCurrentUser() {
        NuxeoPrincipal pal = NuxeoPrincipal.getCurrent();
        if (pal == null) {
            return null;
        }
        return pal.getId();
    }

    @Override
    public Upload newUpload() {
        // security check
        String user = getCurrentUser();
        if (user == null) {
            throw new DocumentSecurityException("Username cannot be null");
        }
        TransientStore transientStore = getTransientStore();
        String id = generateUploadId();
        log.debug("Initializing upload with id: {}", id);
        // Batch the initial param writes through putParameters so they share a
        // single atomicUpdate on the per-upload params doc.
        transientStore.setCompleted(id, false);
        transientStore.putParameters(id, Map.of(
                USER_PARAM_NAME, user,
                Upload.UPLOAD_STATUS_FIELD_NAME, UploadStatus.INCOMPLETE.toString()));
        // fetch again after creation (and fetch s3 properties)
        return getUpload(id, getUploadProperties(id));
    }

    @Override
    public Upload getUpload(String id) {
        // don't fetch properties on get
        return getUpload(id, null);
    }

    protected Map<String, Object> getUploadProperties(String id) {
        return null;
    } // NOSONAR (method overridden)

    protected Upload getUpload(String id, Map<String, Object> properties) {
        if (!uploadExists(id)) {
            return null;
        }
        // create the upload
        Upload upload = new Upload(id, getTransientStore(), getChunkInfo(id), properties);
        // security check
        String user = getCurrentUser();
        if (user == null || !user.equals(upload.getUser())) {
            throw new DocumentSecurityException(String.format("User '%s' does not match upload '%s'", user, id));
        }
        return upload;
    }

    protected String generateUploadId() {
        return "uploadId-" + UUID.randomUUID();
    }

    protected boolean uploadExists(String id) {
        if (isBlank(id)) {
            return false;
        }
        return getTransientStore().exists(id);
    }

    /** Gets the upload parameters, or {@code null} if the upload is not found. */
    protected ChunkInfo getChunkInfo(String id) {
        if (!uploadExists(id)) {
            return null;
        }
        TransientStore transientStore = getTransientStore();
        Map<String, Object> params = transientStore.getParameters(id);
        if (!Boolean.TRUE.equals(params.get(ChunkInfo.CHUNKED_PARAM_NAME))) {
            return null;
        }
        return new ChunkInfo(params);
    }

}
