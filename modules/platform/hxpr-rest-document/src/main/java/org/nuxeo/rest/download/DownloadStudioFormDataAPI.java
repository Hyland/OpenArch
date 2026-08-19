/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.download;

import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.download.DownloadManager.getXPathAndFilename;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_OCTET_STREAM;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_OCTET_STREAM_V1;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;

import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse501;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterDocId;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adding a new endpoint to the Document Download Rest API. When no longer conditional on a feature flag, the endpoint
 * should be moved to the {@link DownloadAPI} class and this class removed.
 */
@RestController
@RequestMapping(path = DownloadAPI.PATH, produces = { APPLICATION_OCTET_STREAM_VALUE, APP_HXCS_OCTET_STREAM_V1,
        APP_HXCS_OCTET_STREAM })
@Tag(name = "Download", description = "Repository Download API")
@Authenticated
public class DownloadStudioFormDataAPI {

    private static final String URL_DOC_ID_PROPERTY_X_PATH_AND_FILENAME = "/url/{docId}/{*propertyXPathAndFilename}"; // NOSONAR

    protected DownloadManager downloadManager;

    public DownloadStudioFormDataAPI(DownloadManager downloadManager) {
        this.downloadManager = downloadManager;
    }

    @GetMapping(URL_DOC_ID_PROPERTY_X_PATH_AND_FILENAME)
    @ApiResponse200(description = "Returns the pre-signed S3 Url for a downloadable object.", content = {
            @Content(mediaType = ALL_VALUE, schema = @Schema(type = "string", format = "uri")) })
    @ApiResponse404DocumentNotFound
    @ApiResponse501(description = "Pre-signed URL is not available for the requested blob.")
    @Operation(summary = "Returns the the pre-signed S3 Url for a downloadable object.", operationId = "downloadUrlByIdAndXPath")
    public ResponseEntity<String> doGetUrl(@PathVariable("docId") @ParameterDocId String docId,
            @PathVariable("propertyXPathAndFilename") @DownloadAPI.ParameterXPathAndFilename String propertyXPathAndFilename,
            @RequestParam(name = "expiration", required = false) @Parameter(description = "expiration value for generating the S3 pre-signed url in seconds.") Long expirationSeconds,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId)
            throws IOException {
        DownloadManager.Result xPathAndFilename = getXPathAndFilename(propertyXPathAndFilename, repoId);
        return downloadManager.generatePresignedUrl(repoId, docId, xPathAndFilename.xpath(), xPathAndFilename.filename(),
            expirationSeconds);
    }

}
