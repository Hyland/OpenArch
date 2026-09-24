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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upload Object to encapsulate all data related to an upload, especially the temporary files used for Blobs.
 * <p>
 * An upload is backed by the {@link TransientStore}.
 */

@Schema(name = "Upload", description = "UploadAPI response object")
public class Upload {

    private static final Logger log = LoggerFactory.getLogger(Upload.class);

    public static final String UPLOAD_STATUS_FIELD_NAME = "status";

    public static final String ATTACH_STATUS_FIELD_NAME = "attachStatus";

    public static final String ATTACH_DOC_ID_FIELD_NAME = "attachDocId";

    /** Parameter set on uploads whose blob storage has been freed after a successful attach. */
    public static final String RELEASED_PARAM_NAME = "released";

    @Schema(name = "id", description = "Upload identifier.")
    private final String id;

    private final String user;

    @JsonIgnore
    private final TransientStore transientStore;

    @JsonIgnore
    private ChunkInfo chunkInfo;

    private final Map<String, Object> properties;

    // cache
    private Boolean hasContent;

    private Blob blob;

    private UploadStatus status;

    private UploadAttachStatus attachStatus;

    private String attachDocId;

    // only used for testing
    @JsonIgnore
    public static volatile boolean _testTriggerUploadError;

    /**
     * Constructs an upload.
     *
     * @param id             the upload id
     * @param transientStore the transient store associated with this upload
     * @param chunkInfo      stored chunk parameters for this upload
     * @param properties     additional properties associated to this upload
     */
    public Upload(String id, TransientStore transientStore, ChunkInfo chunkInfo, Map<String, Object> properties) {
        this.id = id;
        this.user = (String) transientStore.getParameter(id, UploadHandler.USER_PARAM_NAME);
        this.transientStore = transientStore;
        this.chunkInfo = chunkInfo;
        this.properties = new HashMap<>();
        if (properties != null) {
            this.properties.putAll(properties);
        }
        this.status = UploadStatus.parse(transientStore.getParameter(id, UPLOAD_STATUS_FIELD_NAME));
    }

    public String getId() {
        return id;
    }

    @JsonIgnore
    public String getUser() {
        return user;
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public void clearProperties() {
        this.properties.clear();
    }

    @JsonProperty
    @Schema(name = "hasContent", description = "If the upload has associated content is true", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean hasContent() {
        if (hasContent == null) {
            hasContent = transientStore.getParameter(id, ChunkInfo.CHUNKED_PARAM_NAME) != null;
        }
        return hasContent;
    }

    @JsonIgnore
    @Schema(name = "isChunked", description = "If the upload has chunks is true, false otherwise", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isChunked() {
        return chunkInfo != null;
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "fileName", description = "Name of the associated file of the upload.", nullable = true)
    public String getFileName() {
        if (isChunked()) {
            return chunkInfo.getFileName();
        } else {
            Blob localBlob = getBlob();
            return localBlob == null ? null : localBlob.getFilename();
        }
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "mimeType", description = "Mime type of the content.", nullable = true, example = "plain/text")
    public String getMimeType() {
        if (isChunked()) {
            return chunkInfo.getMimeType();
        } else {
            Blob localBlob = getBlob();
            return localBlob == null ? null : localBlob.getMimeType();
        }
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "encoding", description = "Encoding of the content.", nullable = true, example = "UTF-8")
    public String getEncoding() {
        if (isChunked()) {
            return chunkInfo.getEncoding();
        } else {
            Blob localBlob = getBlob();
            return localBlob == null ? null : localBlob.getEncoding();
        }
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "status", description = "Status of the upload.", nullable = true, example = "INCOMPLETE")
    public UploadStatus getStatus() {
        this.status = UploadStatus.parse(transientStore.getParameter(id, UPLOAD_STATUS_FIELD_NAME));
        return status;
    }

    @Schema(name = "fileSize", description = "File size in bytes; -1 is returned if the size in unknown.", nullable = true)
    public long getFileSize() {
        if (isChunked() && !isChunksCompleted()) { // NOSONAR (never null)
            return -1;
        }
        Blob localBlob = getBlob();
        return localBlob == null ? -1 : localBlob.getLength();
    }

    @Schema(name = "partialFileSize", description = "Partial size in bytes. Amount of bytes for all uploaded chunks.", nullable = true)
    public long getPartialFileSize() {
        if (isChunked()) {
            return chunkInfo.getPartialFileSize();
        } else if (!isChunked() && hasContent()) {
            return getFileSize();
        }
        return 0;
    }

    public void addBlob(Blob blob) {
        transientStore.putBlob(id, blob);
        // mark upload as non-chunked
        transientStore.putParameter(id, ChunkInfo.CHUNKED_PARAM_NAME, false);
        hasContent = true;
        // re-fetch blob
        this.blob = getBlobFromStore(id);
    }

    public void addChunk(Blob blob, String fileName, String mimeType, String encoding, int chunkCount, int chunkIndex) {
        // sanity check
        Object chunkBoolean = transientStore.getParameter(id, ChunkInfo.CHUNKED_PARAM_NAME);
        if (Boolean.FALSE.equals(chunkBoolean)) {
            throw new NuxeoException("Cannot add a chunk to a non chunked file entry.", BAD_REQUEST.value());
        }
        if (chunkIndex < 0) {
            throw new NuxeoException(String.format("Cannot add chunk with negative index %d.", chunkIndex),
                    BAD_REQUEST.value());
        }
        if (chunkIndex >= chunkCount) {
            throw new NuxeoException(
                    String.format("Cannot add chunk with index %d to file entry %s as chunk count is %d.", chunkIndex,
                            id, chunkCount), BAD_REQUEST.value());
        }
        hasContent = true;
        // The chunk's blob entry lives at its own KV key (no conflict with the
        // params doc), so stage it first and then merge every param update into a
        // single putParameters call to avoid serialising on the per-upload params
        // doc CAS. The PARTIAL_FILESIZE read-modify-write must stay sequential.
        String chunkEntryKey = id + "_" + chunkIndex;
        transientStore.putBlob(chunkEntryKey, blob);

        Map<String, Object> params = new HashMap<>();
        // mark entry as chunked on first chunk
        if (chunkBoolean == null) {
            params.put(ChunkInfo.CHUNKED_PARAM_NAME, true);
        }
        if (fileName != null) {
            params.put(ChunkInfo.FILE_NAME_PARAM_NAME, fileName);
        }
        if (mimeType != null) {
            params.put(ChunkInfo.MIME_TYPE_PARAM_NAME, mimeType);
        }
        if (encoding != null) {
            params.put(ChunkInfo.ENCODING_PARAM_NAME, encoding);
        }
        params.put(ChunkInfo.CHUNK_COUNT_PARAM_NAME, (long) chunkCount);
        if (blob.getLength() >= 0) {
            long partialSize = 0;
            Long sizeObj = (Long) transientStore.getParameter(id, ChunkInfo.PARTIAL_FILESIZE);
            if (sizeObj != null) {
                partialSize = sizeObj;
            }
            partialSize += blob.getLength();
            params.put(ChunkInfo.PARTIAL_FILESIZE, partialSize);
        }
        params.put(String.valueOf(chunkIndex), chunkEntryKey);
        transientStore.putParameters(id, params);

        // sync local chunk info
        chunkInfo = new ChunkInfo(transientStore.getParameters(id));
    }

    @JsonIgnore
    public Blob getBlob() {
        if (isReleased()) {
            // blob storage has been freed after attach: nothing to return
            return null;
        }
        if (blob != null) {
            return blob;
        }
        blob = getBlobFromStore(id);
        if (blob == null && isChunked()) {
            blob = buildChunkedBlob();
        }
        return blob;
    }

    protected Blob getBlobFromStore(String key) {
        return transientStore.getBlob(key);
    }

    protected Blob buildChunkedBlob() {
        File tmpChunkedFile = null;
        try {
            if (_testTriggerUploadError) {
                throw new IOException("simulated failure");
            }
            Map<Integer, String> chunks = getChunks();
            int uploadedChunkCount = chunks.size();
            int chunkCount = getChunkCount();
            if (uploadedChunkCount != chunkCount) {
                log.warn("Cannot get blob for file entry {} as there are only {} uploaded chunks out of {}.", id,
                        uploadedChunkCount, chunkCount);
                return null;
            }
            Blob localBlob = Blobs.createBlobWithExtension(null);
            // Temporary file made from concatenated chunks
            tmpChunkedFile = localBlob.getFile();
            // Sort chunk indexes and concatenate them to build the entire blob
            List<Integer> sortedChunkIndexes = getChunkIndexes();
            for (int index : sortedChunkIndexes) {
                Blob chunk = getBlobFromStore(chunks.get(index));
                if (chunk != null) {
                    appendTo(chunk, tmpChunkedFile);
                }
            }
            localBlob.setMimeType(getMimeType());
            localBlob.setFilename(getFileName());
            localBlob.setEncoding(getEncoding());
            // store the final file
            transientStore.putBlob(id, localBlob);
            return getBlobFromStore(id);
        } catch (IOException ioe) {
            setStatus(UploadStatus.ERROR);
            throw new NuxeoException(ioe);
        } finally {
            // delete the tmp file
            if (tmpChunkedFile != null && tmpChunkedFile.exists()) {
                try {
                    Files.delete(tmpChunkedFile.toPath());
                } catch (IOException e) {
                    log.error(e.toString(), e);
                }
            }
        }
    }

    /**
     * Appends the given blob to the given file.
     */
    protected void appendTo(Blob blob, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file, true)) {
            try (InputStream in = blob.getStream()) {
                in.transferTo(out);
            }
        }
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "chunksCompleted", description = "True if the number of loaded chunks is equals to the number of chunks size.", type = "boolean")
    public Boolean isChunksCompleted() {
        if (!isChunked()) {
            return null;
        }
        return getChunks().size() == getChunkCount();
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "chunkCount", description = "Number of chunks uploaded.")
    public Integer getChunkCount() {
        if (!isChunked()) {
            return null;
        }
        return chunkInfo.getChunkCount();
    }

    @JsonIgnore
    @Schema(name = "chunks", description = "The chunks that compose the Upload.")
    public Map<Integer, String> getChunks() {
        if (!isChunked()) {
            return Map.of();
        }
        return chunkInfo.getChunks();
    }

    @JsonInclude(Include.NON_NULL)
    @ArraySchema(arraySchema = @Schema(name = "chunkIndexes", description = "The chunks that compose the Upload."))
    public List<Integer> getChunkIndexes() {
        if (!isChunked()) {
            return null;
        }
        return getChunks().keySet().stream().sorted().toList();
    }

    /**
     * Removes this upload from the store.
     * <p>
     * Security checks should be done ahead of calling this method.
     */
    public void clean() {
        // Remove upload and all related storage entries from transient store, GC will clean up the files
        var idSanitized = id.replaceAll("[\n\r]", "_");
        log.debug("Cleaning upload {}", idSanitized);

        if (isChunked()) {
            for (String chunkEntryKey : getChunks().values()) {
                transientStore.remove(chunkEntryKey);
            }
        }

        // Remove upload entry
        transientStore.remove(id);
    }

    /**
     * Releases the disk storage of this upload's blobs (chunks and concatenated blob) but keeps the upload entry alive
     * with its parameters (status, attachStatus, attachDocId, etc.) so clients can keep polling the upload info
     * endpoint to observe the attach phase outcome. The entry itself will eventually be purged by the transient store's
     * TTL.
     * <p>
     * Used after a successful document attach, instead of {@link #clean()} which would remove all data including the
     * attach status metadata.
     */
    public void releaseBlobs() {
        log.debug("Releasing blob storage for upload {}", id);
        if (isChunked()) {
            for (String chunkEntryKey : getChunks().values()) {
                transientStore.remove(chunkEntryKey);
            }
        }
        // remove the concatenated blob, but keep parameters (status, attachStatus, attachDocId, ...)
        transientStore.putBlob(id, null);
        // mark this upload's blobs as released
        transientStore.putParameter(id, RELEASED_PARAM_NAME, true);
        // forget any locally cached blob
        blob = null;
        transientStore.release(id);
    }

    /** Whether this upload's blob storage has been freed via {@link #releaseBlobs()}. */
    @JsonIgnore
    public boolean isReleased() {
        return Boolean.TRUE.equals(transientStore.getParameter(id, RELEASED_PARAM_NAME));
    }

    public void setStatus(UploadStatus newStatus) {
        log.debug("Marking upload as {}, id: {}", newStatus, id);
        transientStore.putParameter(id, UPLOAD_STATUS_FIELD_NAME, newStatus.toString());
        status = newStatus;
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "attachStatus", description = "Status of the blob attachment phase.", nullable = true)
    public UploadAttachStatus getAttachStatus() {
        Object value = transientStore.getParameter(id, ATTACH_STATUS_FIELD_NAME);
        if (value == null) {
            attachStatus = UploadAttachStatus.PENDING;
        } else {
            attachStatus = UploadAttachStatus.parse(value);
        }
        return attachStatus;
    }

    public void setAttachStatus(UploadAttachStatus newAttachStatus) {
        log.debug("Marking upload attach as {}, id: {}", newAttachStatus, id);
        transientStore.putParameter(id, ATTACH_STATUS_FIELD_NAME, newAttachStatus.toString());
        this.attachStatus = newAttachStatus;
    }

    @JsonInclude(Include.NON_NULL)
    @Schema(name = "attachDocId", description = "Id of the document to which the blob has been attached. Available when attachStatus is READY.", nullable = true)
    public String getAttachDocId() {
        this.attachDocId = (String) transientStore.getParameter(id, ATTACH_DOC_ID_FIELD_NAME);
        return attachDocId;
    }

    public void setAttachDocId(String docId) {
        log.debug("Marking upload attach doc id as {}, id: {}", docId, id);
        transientStore.putParameter(id, ATTACH_DOC_ID_FIELD_NAME, docId);
        this.attachDocId = docId;
    }

}
