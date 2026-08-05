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
package org.nuxeo.rest.upload;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Structure that holds metadata about an uploaded file.
 */
@Schema(title = "Upload File Info", description = "Represents an uploaded file.")
public class UploadFileInfo {

    @Schema(name = "fileKey", description = "file key")
    protected String fileKey;

    @Schema(name = "fileName", description = "file name", example = "myFile.txt")
    protected String fileName;

    @Schema(name = "mimeType", description = "mimeType", example = "text/plain")
    protected String mimeType;

    @JsonCreator
    private UploadFileInfo() {
    }

    public UploadFileInfo(String fileKey, String fileName, String mimeType) {
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

}
