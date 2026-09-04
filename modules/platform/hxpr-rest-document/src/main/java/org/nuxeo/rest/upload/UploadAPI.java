/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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

import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse201;
import org.nuxeo.rest.swagger.annotation.ApiResponse202;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.ApiResponse501;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.SwaggerRequestBody;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Upload Rest API.
 * <p>
 * Inspired by the Google Drive REST API documentation about
 * <a href="https://developers.google.com/drive/web/manage-uploads#resumable">resumable upload</a>.
 */
@RestController
@RequestMapping(path = UploadAPI.PATH, produces = { APP_HXCS_JSON_V1, APP_HXCS_JSON, APPLICATION_JSON_VALUE })
@Tag(name = "Upload", description = "Repository Upload API")
@Authenticated
@Validated
@CrossOrigin
public class UploadAPI {

    public static final String PATH = "/api/upload"; // NOSONAR

    private static final String ID = "{id}";

    private static final String ID_PATTERN = "[A-Za-z]{8}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final String INVALID_UPLOAD_ID = "Invalid upload id";

    private static final String UPLOAD_NOT_FOUND_FORMAT = "Upload not found: %s";

    protected static final String DEFAULT_FILE_NAME = "file.bin";

    private final UploadHandler handler;

    protected UploadService uploadService;

    public UploadAPI(UploadHandler handler, UploadService uploadService) {
        this.handler = handler;
        this.uploadService = uploadService;
    }

    /**
     * Annotation for upload id path variable.
     * <p>
     * The upload id must be a UUID.
     * <p>
     * This annotation is used to validate the upload id path variable.
     * <p>
     * ConstraintViolationException is thrown if the upload id does not match the pattern.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Parameter(description = "The upload id.", schema = @Schema(pattern = ID_PATTERN))
    @Constraint(validatedBy = {})
    @Pattern(regexp = ID_PATTERN, message = INVALID_UPLOAD_ID)
    private @interface PathVariableId {
        @AliasFor(annotation = Pattern.class, attribute = "regexp")
        String regexp() default ID_PATTERN;

        @AliasFor(annotation = Pattern.class, attribute = "message")
        String message() default INVALID_UPLOAD_ID;

        @AliasFor(annotation = Pattern.class, attribute = "groups")
        Class<?>[] groups() default {};

        @AliasFor(annotation = Pattern.class, attribute = "payload")
        Class<? extends Payload>[] payload() default {};
    }

    @PostMapping("create")
    @ResponseStatus(CREATED)
    @Operation(summary = "Creates a new upload and return its generated identifier and properties.", operationId = "createUpload")
    @ApiResponse201(description = "Returns the upload information.")
    public Upload createUpload() {
        return handler.newUpload();
    }

    @PostMapping
    @Operation(summary = "Uploads a file or a chunk to a given upload.", description = "Upload can be done in several parts, chunk by chunk.", operationId = "upload")
    @ApiResponse201(description = "Returns the upload information after upload is finished.")
    @ApiResponse202(description = "Returns the upload information after chunk addition to the upload.")
    @ApiResponse404(description = "Upload with the given id not found or cannot be accessed by user.")
    @SwaggerRequestBody(content = { @Content(mediaType = ALL_VALUE, schema = @Schema(type = "string", format = "binary")) })
    public ResponseEntity<Upload> upload(@Parameter(hidden = true) @RequestHeader HttpHeaders headers,
            @RequestParam(value = "id", required = false) @Parameter(description = "The upload id. A new upload is created if null.") String id,
            @RequestParam(value = "fileName", required = false) @Parameter(description = "The file name.") String fileName,
            @RequestParam(value = "mimeType", required = false) @Parameter(description = "The file mimetype. For a text/uri-list upload, it takes precedence over the mimetype advertised by the external resource.") String queryMimeType,
            @RequestParam(value = "encoding", required = false) @Parameter(description = "The file encoding (for text files).") String queryEncoding,
            @RequestParam(value = "bodyEncoding", required = false) @Parameter(description = "The body encoding (only base64 allowed).") String bodyEncoding,
            @RequestParam(value = "chunked", required = false, defaultValue = "false") @Parameter(description = "Indicates that the upload is chunked.") boolean chunked,
            @RequestParam(value = "chunkCount", required = false) @Parameter(description = "The total number of chunks.") Integer chunkCount,
            @RequestParam(value = "chunkIndex", required = false) @Parameter(description = "This chunk index, starting at 0..") Integer chunkIndex,
            HttpServletRequest request) throws IOException {
        if (headers == null || headers.getFirst(HttpHeaders.CONTENT_TYPE) == null) {
            headers = new HttpHeaders();
        } else if (headers.getFirst(HttpHeaders.CONTENT_TYPE).equals(MediaType.MULTIPART_FORM_DATA)) {
            return ResponseEntity.status(UNSUPPORTED_MEDIA_TYPE).build();
        }
        UploadDTO uploadDTO = new UploadDTO(id, fileName, queryMimeType, queryEncoding, bodyEncoding, chunked,
                chunkCount, chunkIndex);
        return uploadService.handleUpload(handler, headers.getFirst(HttpHeaders.CONTENT_TYPE), uploadDTO,
                request.getInputStream());
    }

    @GetMapping(ID)
    @Operation(summary = "Returns the upload information for given id.", operationId = "getUploadInfo")
    @ApiResponse200(description = "Returns the upload information and upload is finished when upload has content.")
    @ApiResponse202(description = "Returns the upload information and upload by chunk is not finished.")
    @ApiResponse204(description = "Upload with the given id has no content.")
    @ApiResponse404(description = "Upload with the given id not found or cannot be accessed by user.")
    public ResponseEntity<Upload> info(@PathVariable("id") @PathVariableId String id) {
        Upload upload = getUpload(id);
        if (!upload.hasContent()) {
            return ResponseEntity.noContent().build();
        }
        if (upload.isChunked() && !upload.isChunksCompleted()) { // NOSONAR
            return ResponseEntity.accepted().body(upload);
        }
        return ResponseEntity.ok(upload);
    }

    @DeleteMapping(ID)
    @ResponseStatus(NO_CONTENT)
    @Operation(summary = "Cancels the upload with the given id.", operationId = "deleteUpload")
    @ApiResponse204(description = "Upload has been removed.", content = { @Content(mediaType = APP_HXCS_JSON_V1),
            @Content(mediaType = APP_HXCS_JSON), @Content(mediaType = APPLICATION_JSON_VALUE) })
    @ApiResponse404(description = "Upload with the given id not found or cannot be accessed by user.")
    public void cancel(@PathVariable("id") @PathVariableId String id) {
        Upload upload = getUpload(id);
        upload.clean();
    }

    @PostMapping(ID + "/renewCredentials")
    @Operation(summary = "Renews the credentials for the upload with the given id.", operationId = "renewCredentials")
    @ApiResponse200(description = "Token has been refreshed.", content = @Content)
    @ApiResponse404(description = "Upload with the given id not found.")
    @ApiResponse501(description = "Token cannot be refreshed with current upload handler.")
    public Map<String, Object> renewCredentials(@PathVariable("id") @PathVariableId String id) {
        getUpload(id);
        Map<String, Object> result = handler.renewCredentials(id);
        result.put("id", id);
        return result;
    }

    @PostMapping(path = ID + "/complete", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Completes the upload with the given id. ", description = """
            Completion is mainly useful for s3 direct upload, where
            the upload has been created thanks to this API, but download has been done directly on the target s3 bucket.""", operationId = "complete")
    @ApiResponse200(description = "Upload has been completed.", content = @Content)
    @ApiResponse400(description = "Missing or incorrect upload information.", content = @Content)
    @ApiResponse404(description = "Upload with the given id not found or cannot be accessed by user.")
    @ApiResponse409(description = "Upload cannot be completed with given information.")
    @ApiResponse501(description = "Upload cannot be completed for current upload handler.")
    public ResponseEntity<Map<String, Serializable>> complete(@PathVariable("id") @PathVariableId String id,
            @RequestBody(required = false) UploadFileInfo fileInfo) {
        if (fileInfo == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!handler.completeUpload(id, fileInfo)) {
            return ResponseEntity.status(CONFLICT).build();
        }
        return ResponseEntity.ok(Map.of("id", id, "uploaded", true));
    }

    private Upload getUpload(String id) {
        Upload upload = handler.getUpload(id);
        if (upload == null) {
            throw new NuxeoException(UPLOAD_NOT_FOUND_FORMAT, NOT_FOUND.value());
        }
        return upload;
    }
}
