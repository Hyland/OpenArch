/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
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
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.nuxeo.ecm.core.io.download.DownloadHelper;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse404DocumentNotFound;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterDocId;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Document Download Rest API.
 */
@RestController
@RequestMapping(path = DownloadAPI.PATH, produces = { APPLICATION_OCTET_STREAM_VALUE, APP_HXCS_OCTET_STREAM,
        APP_HXCS_OCTET_STREAM_V1 })
@Tag(name = "Download", description = "Repository Download API")
@Authenticated
public class DownloadAPI {

    public static final String PATH = "/api/download"; // NOSONAR

    public static final String PATH_DOC_ID_X_PATH = "/{docId}/{*propertyXPathAndFilename}"; // NOSONAR

    protected DownloadManager downloadManager;

    public DownloadAPI(DownloadManager downloadManager) {
        this.downloadManager = downloadManager;
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Parameter(description = "The xpath to the property holding the blob and the filename.", example = "sysfile_blob/test.png or sysfiles_blobs/0/test.png", schema = @Schema(pattern = ".*"))
    public @interface ParameterXPathAndFilename {
    }

    @RequestMapping(method = RequestMethod.HEAD, path = PATH_DOC_ID_X_PATH)
    @Operation(summary = "Returns the document blob information.", operationId = "downloadInfoByIdAndXPath")
    @ApiResponse204(description = "Returns the document blob information.", content = {
            @Content(mediaType = ALL_VALUE) })
    @ApiResponse404DocumentNotFound
    @Transactional(property = "nuxeo.rest.download.timeout.seconds")
    public ResponseEntity<StreamingResponseBody> doHead(@PathVariable("docId") @ParameterDocId String docId,
            @PathVariable("propertyXPathAndFilename") @ParameterXPathAndFilename String propertyXPathAndFilename,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId)
            throws IOException {
        // downstream methods will deal with HEAD and avoid sending a body
        return doGet(docId, propertyXPathAndFilename, false, repoId);
    }

    @GetMapping(PATH_DOC_ID_X_PATH)
    @Operation(summary = "Returns the document blob.", operationId = "downloadByIdAndXPath")
    @ApiResponse200(description = "Returns the document blob.", content = {
            @Content(mediaType = ALL_VALUE, schema = @Schema(type = "string", format = "binary")) })
    @ApiResponse404DocumentNotFound
    @Transactional(property = "nuxeo.rest.download.timeout.seconds")
    public ResponseEntity<StreamingResponseBody> doGet(@PathVariable("docId") @ParameterDocId String docId,
            @PathVariable("propertyXPathAndFilename") @ParameterXPathAndFilename String propertyXPathAndFilename,
            @RequestParam(name = "inline", required = false) @Parameter(description = "Inline content disposition enablement.") Boolean inline,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId)
            throws IOException {
        return getStreamingResponseBodyResponseEntity(docId, propertyXPathAndFilename, inline, repoId);
    }

    private ResponseEntity<StreamingResponseBody> getStreamingResponseBodyResponseEntity(String docId,
            String propertyXPathAndFilename, Boolean inline, String repoId) throws IOException {
        DownloadManager.Result xPathAndFilename = getXPathAndFilename(propertyXPathAndFilename, repoId);
        try {
            return downloadManager.download(repoId, docId, xPathAndFilename.xpath(), xPathAndFilename.filename(),
                Boolean.TRUE.equals(inline));
        } catch (IOException ioe) {
            DownloadHelper.handleClientDisconnect(ioe);
        }
        return null;
    }

    // Endpoint exists in DownloadStudioFormDataAPI while it is conditional on a feature flag.
    // When no longer conditional on a feature flag, all the endpoints present in class
    // {@link DownloadStudioFormDataAPI} should be moved here and the class can be removed.

}
