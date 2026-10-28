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
package com.hyland.openarch.ecm.core.blob;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;

import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.ecm.core.blob.binary.BlobKeySlot;
import com.hyland.openarch.ecm.core.model.InternalDocument;

/**
 * Service managing {@link Blob}s associated to a {@link InternalDocument} or a repository.
 *
 * @since 9.2
 */
public interface DocumentBlobService {

    /**
     * Reads a {@link Blob} from storage.
     *
     * @param blobInfo the blob information
     * @param doc the document to which this blob belongs
     * @param xpath the xpath of the blob in the document
     * @return a managed blob
     * @since 11.1
     */
    Blob readBlob(BlobInfo blobInfo, InternalDocument doc, String xpath) throws IOException;

    /**
     * Writes a {@link Blob} to storage and returns its key.
     * <p>
     * The passed blob may be {@code null}, in which case a {@code null} key is returned after checking that deleting
     * this blob is allowed.
     *
     * @param blob the blob
     * @param doc the document to which this blob belongs
     * @param xpath the xpath of blob in doc
     * @return the blob key
     * @since 9.1
     */
    String writeBlob(Blob blob, InternalDocument doc, String xpath) throws IOException;

    /**
     * Gets an {@link InputStream} for a conversion to the given MIME type.
     * <p>
     * Like all {@link InputStream}, the result must be closed when done with it to avoid resource leaks.
     *
     * @param blob the blob
     * @param mimeType the MIME type to convert to
     * @param doc the document that holds the blob
     * @return the stream, or {@code null} if no conversion is available for the given MIME type
     * @since 7.2
     */
    InputStream getConvertedStream(Blob blob, String mimeType, DocumentModel doc) throws IOException;

    /**
     * Freezes the blobs' versions on a document version when it is created via a check in.
     *
     * @param doc the new document version
     * @since 7.3
     */
    void freezeVersion(InternalDocument doc);

    /**
     * Notifies the blob manager that a set of xpaths have changed on a document.
     *
     * @param doc the document
     * @param xpaths the set of changed xpaths
     * @since 7.3
     */
    void notifyChanges(InternalDocument doc, Set<String> xpaths);

    /**
     * Notifies the blob manager that the document was made a record.
     *
     * @param doc the document
     * @since 11.1
     */
    void notifyMakeRecord(InternalDocument doc);

    /**
     * Notifies the blob manager that the document has been copied.
     *
     * @param doc the new document, the result of the copy
     * @since 11.1
     */
    void notifyAfterCopy(InternalDocument doc);

    /**
     * Notifies the blob manager that the document is about to be removed.
     *
     * @param doc the document
     * @since 11.1
     */
    void notifyBeforeRemove(InternalDocument doc);

    /**
     * Notifies the blob manager that the document's retention date was changed.
     *
     * @param doc the document
     * @since 11.1
     */
    void notifySetRetainUntil(InternalDocument doc, Instant retainUntil);

    /**
     * Notifies the blob manager that the document's legal hold status was changed.
     *
     * @param doc the document
     * @since 11.1
     */
    void notifySetLegalHold(InternalDocument doc, boolean hold);

    /**
     * Garbage collect the unused binaries.
     * <p>
     * If key space partitioning is enabled, a full cycle is done: each slot of the partition is garbage collected in
     * turn by an independent run.
     *
     * @param delete if {@code false} don't actually collect the garbage binaries (but still return statistics about
     *            them), if {@code true} move them to recovery storage or permanently delete them according to the
     *            hardcoded policy
     * @return a status about the number of garbage collected binaries
     * @since 7.4
     */
    BinaryManagerStatus garbageCollectBinaries(boolean delete);

    /**
     * Garbage collect the unused binaries of a single slot of the key space, in a single run, regardless of the
     * configured key space partitioning.
     *
     * @param delete if {@code false} don't actually collect the garbage binaries (but still return statistics about
     *            them), if {@code true} move them to recovery storage or permanently delete them according to the
     *            hardcoded policy
     * @param slot the slot of the key space to garbage collect, {@link BlobKeySlot#ALL} for the whole key space
     * @return a status about the number of garbage collected binaries
     */
    BinaryManagerStatus garbageCollectBinaries(boolean delete, BlobKeySlot slot);

    /**
     * Checks if a garbage collection of the binaries in progress.
     *
     * @return {@code true} if a garbage collection of the binaries is in progress
     * @since 7.4
     */
    boolean isBinariesGarbageCollectionInProgress();

}
