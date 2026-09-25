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
package com.hyland.openarch.rest.upload;

import java.io.IOException;
import java.io.InputStream;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import org.springframework.http.ResponseEntity;

public interface UploadService {

    /**
     * Uploads the InputStream and returns the Response with status CREATED along with upload object wrapped inside it if upload
     * is successful. Response returned with status ACCEPTED for chunked upload. If upload fails, Response is returned with the Error status code.
     * @param handler UploadHandler Object
     * @param mediaType MediaType of the inputStream to be uploaded
     * @param uploadDTO UploadDTO
     * @param input InputStream to be uploaded
     * @return Upload object wrapped inside {@link ResponseEntity}
     * @throws IOException
     */
    ResponseEntity<Upload> handleUpload(UploadHandler handler, String mediaType, UploadDTO uploadDTO, InputStream input) throws IOException;

    /**
     * Uploads the InputStream and returns the Upload object if upload is successful. If upload fails,
     * this method throws RepositoryException with error status code.
     * @param handler UploadHandler Object
     * @param mediaType MediaType of the inputStream to be uploaded
     * @param uploadDTO UploadDTO
     * @param input InputStream to be uploaded
     * @return Upload Object
     * @throws IOException
     */
    Upload processUpload(UploadHandler handler, String mediaType, UploadDTO uploadDTO, InputStream input) throws IOException, RepositoryException;
}
