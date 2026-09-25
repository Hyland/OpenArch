/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.bulkimport;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.DESTINATION_FOLDER_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.DESTINATION_FOLDER_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;

import java.io.Serial;
import java.io.Serializable;

import com.hyland.openarch.rest.document.DocumentDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public class BulkImportMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty(BulkImportConstants.PROP_DOC_UNIQUE_KEY)
    private String docUniqueKey;

    @JsonProperty(BulkImportConstants.PROP_JOB_ID)
    private String jobId;

    @JsonUnwrapped
    private transient DocumentDTO document;

    @JsonProperty(SYSFILE_BLOB)
    private BulkImportUploadDTO upload;

    @JsonProperty(DESTINATION_FOLDER_ID)
    private String destinationFolderId;

    @JsonProperty(DESTINATION_FOLDER_PATH)
    private String destinationFolderPath;

    private transient BulkImport bulkImport;

    public BulkImportMetadata() {
        // Empty constructor needed by Jackson
    }

    public String getDocUniqueKey() {
        return docUniqueKey;
    }

    public void setDocUniqueKey(String docUniqueKey) {
        this.docUniqueKey = docUniqueKey;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public DocumentDTO getDocument() {
        return document;
    }

    public void setDocument(DocumentDTO document) {
        this.document = document;
    }

    public BulkImportUploadDTO getBulkImportUpload() {
        return upload;
    }

    public void setBulkImportUpload(BulkImportUploadDTO upload) {
        this.upload = upload;
    }

    public BulkImport getBulkImport() {
        return bulkImport;
    }

    public void setBulkImport(BulkImport bulkImport) {
        this.bulkImport = bulkImport;
    }

    public String getDestinationFolderId() {
        return destinationFolderId;
    }

    public void setDestinationFolderId(String destinationFolderId) {
        this.destinationFolderId = destinationFolderId;
    }

    public String getDestinationFolderPath() {
        return destinationFolderPath;
    }

    public void setDestinationFolderPath(String destinationFolderPath) {
        this.destinationFolderPath = destinationFolderPath;
    }
}
