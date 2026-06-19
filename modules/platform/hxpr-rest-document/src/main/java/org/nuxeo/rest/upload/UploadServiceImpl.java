/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 *     Abdul Mohammed <abdul.mohammed@hyland.com>
 */
package org.nuxeo.rest.upload;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import org.apache.commons.codec.CodecPolicy;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.BaseNCodec;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);

    protected static final String DEFAULT_FILE_NAME = "file.bin";

    private static final String CONTENT_TYPE_URI_LIST = "text/uri-list";

    private static final String BODY_ENCODING_BASE64 = "base64";

    // only used for testing
    public static volatile CyclicBarrier _testProcessingStatusBarrier;

    @Override
    public ResponseEntity<Upload> handleUpload(UploadHandler handler, String mimetype, UploadDTO uploadDTO, InputStream input) throws IOException {
        Upload upload = processUpload(handler, mimetype, uploadDTO, input);

        HttpStatus status = HttpStatus.CREATED;
        if (uploadDTO.chunked() && !upload.isChunksCompleted()) { // NOSONAR
            status = HttpStatus.ACCEPTED;
        }
        return ResponseEntity.status(status).body(upload);
    }

    @Override
    public Upload processUpload(UploadHandler handler, String mediaType, UploadDTO uploadDTO, InputStream input) throws IOException, NuxeoException {
        Upload upload = getUploadFromId(handler, uploadDTO.id());
        boolean isLastChunkBeingUploaded = lastChunkIsBeingUploaded(upload, uploadDTO);
        if (isLastChunkBeingUploaded) {
            upload.setStatus(UploadStatus.PROCESSING);
            if (_testProcessingStatusBarrier != null) {
                try {
                    // inform test thread that processing status is set
                    _testProcessingStatusBarrier.await(10, SECONDS);
                    // wait for test thread to have observed status
                    _testProcessingStatusBarrier.await(10, SECONDS);
                } catch (Exception e) {
                    throw new NuxeoException(e);
                }
            }
        }

        String mimeType;
        String encoding;
        String bodyEncoding;
        if (CONTENT_TYPE_URI_LIST.equals(mediaType)) {
            UploadExternalUriClient externalUriManager = new UploadExternalUriClient(input);
            URI uri;
            try {
                uri = externalUriManager.getURI();
            } catch (URISyntaxException e) { // NOSONAR
                throw new NuxeoException(BAD_REQUEST.getStatusCode());
            }
            HttpResponse<InputStream> response = externalUriManager.downloadResourceFromURI(uri);
            input = response.body();
            UploadExternalUriClient.MimeTypeAndEncoding mimeTypeAndEncoding = externalUriManager.getMimeTypeAndEncoding(response.headers());
            mimeType = mimeTypeAndEncoding.mimeType();
            encoding = mimeTypeAndEncoding.encoding();
            bodyEncoding = null;
        } else {
            mimeType = uploadDTO.queryMimeType();
            encoding = uploadDTO.queryEncoding();
            bodyEncoding = uploadDTO.bodyEncoding();
        }

        if (BODY_ENCODING_BASE64.equals(bodyEncoding)) {
            // decode Base64 with STRICT decoding policy (forbid truncated trailing bits)
            input = new Base64InputStream(input, false, BaseNCodec.MIME_CHUNK_SIZE, BaseNCodec.getChunkSeparator(),
                    CodecPolicy.STRICT);
        } else if (StringUtils.isNotBlank(bodyEncoding)) {
            throw new NuxeoException("Invalid bodyEncoding", BAD_REQUEST.getStatusCode());
        }

        Blob blob = Blobs.createBlob(input); // also closes input stream
        if (StringUtils.isBlank(mimeType)) {
            if (mediaType == null || mediaType.isBlank()) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            mimeType = mediaType;
        }
        // sanity check on mimeType value, in case client uses
        // "mimeType=text/plain;charset=UTF-8" as a single request parameter (see NXP-30357)
        if (StringUtils.isNotBlank(mimeType)) {
            HeaderElement headerElement = BasicHeaderValueParser.parseHeaderElement("mimeType=" + mimeType, null);
            mimeType = headerElement.getValue();
            for (NameValuePair param : headerElement.getParameters()) {
                if (param.getName().equalsIgnoreCase("charset")) {
                    String s = param.getValue();
                    if (!isBlank(s)) {
                        encoding = s;
                    }
                    break;
                }
            }
        }

        long uploadedSize = blob.getLength();
        String fileName = uploadDTO.fileName();
        boolean chunked = uploadDTO.chunked();
        Integer chunkIndex = uploadDTO.chunkIndex();
        Integer chunkCount = uploadDTO.chunkCount();
        if (uploadedSize >= 0) {
            var filenameSanitized = fileName == null ? fileName : fileName.replaceAll("[\n\r]", "_");
            if (chunked) {
                if (chunkIndex == null || chunkCount == null) {
                    throw new NuxeoException(
                            "Both chunkIndex and chunkCount must be provided to perform an upload in chunks.",
                            BAD_REQUEST.getStatusCode());
                }
                log.debug("Uploading chunk [index={} / total={}] ({}) for file {}", chunkIndex,
                        chunkCount, uploadedSize, filenameSanitized);
                upload.addChunk(blob, fileName, mimeType, encoding, chunkCount, chunkIndex);
                if (isLastChunkBeingUploaded) {
                    upload.getBlob(); // trigger chunks concatenation, which takes some time
                    upload.setStatus(UploadStatus.READY);
                }
            } else {
                log.debug("Uploading file {} ({})", filenameSanitized, uploadedSize);
                if (StringUtils.isBlank(fileName)) {
                    fileName = DEFAULT_FILE_NAME;
                }
                blob.setFilename(fileName);
                blob.setMimeType(mimeType);
                blob.setEncoding(encoding);
                upload.addBlob(blob);
                upload.setStatus(UploadStatus.READY);
            }
        }
        return upload;
    }

    private boolean lastChunkIsBeingUploaded(Upload upload, UploadDTO uploadDTO) {
        if (!uploadDTO.chunked() || uploadDTO.chunkIndex() == null || uploadDTO.chunkCount() == null) {
            return false;
        }
        Map<Integer, String> chunks = upload.getChunks();
        return chunks.size() + 1 == uploadDTO.chunkCount() && !chunks.containsKey(uploadDTO.chunkIndex());
    }

    private Upload getUploadFromId(UploadHandler handler, String uploadId) {
        Upload upload;
        if (StringUtils.isBlank(uploadId)) {
            upload = handler.newUpload();
            upload.clearProperties();
        } else {
            upload = handler.getUpload(uploadId);
            if (upload == null) {
                throw new NuxeoException(NOT_FOUND.getStatusCode());
            }
        }
        return upload;
    }
}
