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
package org.nuxeo.ecm.core.blob;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.nuxeo.ecm.core.api.Blob;

/**
 * Service managing the storage and retrieval of {@link Blob}s, through internally-registered {@link BlobProvider}s.
 *
 * @since 7.2
 */
public interface BlobService {

    /**
     * Hints for returning {@link URI}s appropriate for the expected usage.
     *
     * @since 7.3
     */
    enum UsageHint {
        /** Obtaining an {@link InputStream}. */
        STREAM, //
        /** Downloading. */
        DOWNLOAD, //
        UPLOAD, //
        /** Viewing. */
        VIEW, //
        /** Editing. */
        EDIT, //
        /** Embedding / previewing. */
        EMBED
    }

    /**
     * Gets the blob provider with the given id.
     *
     * @param id the blob provider id
     * @return the blob provider
     */
    BlobProvider getBlobProvider(String id);

    /**
     * Gets the blob provider with the given id, or, if none has been registered, a namespaced version of the blob
     * provider with the given default id.
     *
     * @param id the blob provider id or namespace
     * @param defaultId the blob provider to use as a fallback to create a namespaced version
     * @return the blob provider
     * @since 11.1
     */
    BlobProvider getBlobProviderWithNamespace(String id, String defaultId);

    /**
     * Gets the blob provider for the given blob.
     *
     * @return the blob provider
     * @since 7.4
     */
    BlobProvider getBlobProvider(Blob blob);

    /**
     * Gets an {@link URI} for the content of a blob.
     *
     * @param blob the blob
     * @param hint {@link UsageHint}
     * @param expirationSeconds the expiration value in seconds
     * @param servletRequest the servlet request, or {@code null}
     * @return the {@link URI}, or {@code null} if none available
     */

    URI getURI(Blob blob, UsageHint hint, long expirationSeconds, HttpServletRequest servletRequest) throws IOException;

    /**
     * Gets a map of available MIME type conversions and corresponding {@link URI} for a blob.
     *
     * @return a map of MIME types and {@link URI}, which may be empty
     */
    Map<String, URI> getAvailableConversions(Blob blob, UsageHint hint) throws IOException;

    /**
     * Get the map of blob providers
     *
     * @return the list of blob providers
     * @since 7.3
     */
    Map<String, BlobProvider> getBlobProviders();

    /**
     * Records the fact that a blob key has been replaced by another one.
     *
     * @param blobProviderId the blob provider id
     * @param key the old blob key
     * @param newKey the new blob key
     * @see #getBlobKeyReplacement(String, String)
     * @since 11.5
     */
    void setBlobKeyReplacement(String blobProviderId, String key, String newKey);

    /**
     * Gets the replacement (if any) for a blob key.
     *
     * @param blobProviderId the blob provider id
     * @param key the old blob key
     * @return the new blob key, or the old one if there was no replacement
     * @see #setBlobKeyReplacement(String, String, String)
     * @since 11.5
     */
    String getBlobKeyReplacement(String blobProviderId, String key);

    /**
     * Marks the given blob for deletion at a later time.
     *
     * @param blobProviderId the blob provider id
     * @param key the blob key
     * @since 2021.9
     */
    void markBlobForDeletion(String blobProviderId, String key);

    /**
     * Deletes the blobs marked for deletion, if enough time has elapsed.
     *
     * @since 2021.9
     */
    void deleteBlobsMarkedForDeletion();

}
