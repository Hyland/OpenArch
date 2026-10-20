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

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.apache.commons.codec.CodecPolicy;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.rest.blob.ContentTypes;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
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

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

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
    public Upload processUpload(UploadHandler handler, String mediaType, UploadDTO uploadDTO, InputStream input) throws IOException, RepositoryException {
        Upload upload = getUploadFromId(handler, uploadDTO.id());
        boolean isLastChunkBeingUploaded = lastChunkIsBeingUploaded(upload, uploadDTO);

        String mimeType;
        String encoding;
        String bodyEncoding;
        if (CONTENT_TYPE_URI_LIST.equals(mediaType)) {
            UploadExternalUriClient externalUriManager = new UploadExternalUriClient(HTTP_CLIENT);
            UriWithHeaders uriWithHeaders;
            try {
                uriWithHeaders = UriWithHeaders.of(input);
            } catch (URISyntaxException e) { // NOSONAR
                throw new RepositoryException(e.getInput(), e, BAD_REQUEST.getStatusCode());
            }
            HttpResponse<InputStream> response = externalUriManager.downloadResourceFromURI(uriWithHeaders);
            input = response.body();
            UploadExternalUriClient.MimeTypeAndEncoding mimeTypeAndEncoding = externalUriManager.getMimeTypeAndEncoding(response.headers());
            // an explicit mimeType request parameter wins over the one advertised by the external resource, which
            // may be generic (application/octet-stream) when the caller knows the actual type
            // mimetype text/uri-list is excluded from this rule, as this is used sometimes declared as mimetype by mistake
            if (StringUtils.isNotBlank(uploadDTO.queryMimeType()) && !uploadDTO.queryMimeType().equals(CONTENT_TYPE_URI_LIST)) {
                mimeType = uploadDTO.queryMimeType();
            } else {
                mimeType = mimeTypeAndEncoding.mimeType();
            }
            encoding = mimeTypeAndEncoding.encoding();
            bodyEncoding = null;
        } else {
            mimeType = uploadDTO.queryMimeType();
            encoding = uploadDTO.queryEncoding();
            bodyEncoding = uploadDTO.bodyEncoding();
        }

        if (BODY_ENCODING_BASE64.equals(bodyEncoding)) {
            // decode Base64 with STRICT decoding policy (forbid truncated trailing bits)
            input = Base64InputStream.builder()
                                     .setInputStream(input)
                                     .setBaseNCodec(Base64.builder().setDecodingPolicy(CodecPolicy.STRICT).get())
                                     .get();
        } else if (StringUtils.isNotBlank(bodyEncoding)) {
            throw new RepositoryException("Invalid bodyEncoding", BAD_REQUEST.getStatusCode());
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

        // the content type and encoding are stored on the blob and sent back when it is downloaded, the encoding
        // appended to the content type as a charset, so refuse a pair that could not be sent back as a header.
        // Both branches below put them on a blob, hence the check here.
        if (!ContentTypes.isWellFormed(mimeType)) {
            throw new RepositoryException("Invalid mimeType", BAD_REQUEST.getStatusCode());
        }
        if (!ContentTypes.isWellFormed(mimeType, encoding)) {
            // the content type passed on its own just above, so the encoding is what this request got wrong
            throw new RepositoryException("Invalid encoding", BAD_REQUEST.getStatusCode());
        }

        // only now is the request going to change the upload. Publishing PROCESSING before this point would have
        // to be undone when a request is refused, and a refused request cannot tell whether the state it would be
        // undoing is still its own: a concurrent retry of the same final chunk may already have finished.
        if (isLastChunkBeingUploaded) {
            upload.setStatus(UploadStatus.PROCESSING);
            if (_testProcessingStatusBarrier != null) {
                try {
                    // inform test thread that processing status is set
                    _testProcessingStatusBarrier.await(10, SECONDS);
                    // wait for test thread to have observed status
                    _testProcessingStatusBarrier.await(10, SECONDS);
                } catch (Exception e) {
                    throw new RepositoryException(e);
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
                    throw new RepositoryException(
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
                throw new RepositoryException(NOT_FOUND.getStatusCode());
            }
        }
        return upload;
    }
}
