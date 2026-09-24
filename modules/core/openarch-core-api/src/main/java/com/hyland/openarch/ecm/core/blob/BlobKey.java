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
package com.hyland.openarch.ecm.core.blob;

import static com.hyland.openarch.ecm.core.blob.KeyStrategy.VER_SEP;

/**
 * A blob key split into its key and optional version id.
 * <p>
 * When represented as a string, it has the form {@code key} or {@code key<VER_SEP>versionId}.
 */
public record BlobKey(String key, String versionId) {

    /** Builds a blob key from its key and optional version id. */
    public static BlobKey of(String key, String versionId) {
        return new BlobKey(key, versionId);
    }

    /** Parses a blob key into its key and optional version id. */
    public static BlobKey parse(String blobKey) {
        int i = blobKey.indexOf(VER_SEP);
        if (i < 0) {
            return new BlobKey(blobKey, null);
        } else {
            return new BlobKey(blobKey.substring(0, i), blobKey.substring(i + 1));
        }
    }

    /**
     * Parses a blob key, extracting the version id only when {@code useVersion} is {@code true}; otherwise the whole
     * blob key is the key and the version id is {@code null}.
     */
    public static BlobKey parse(String blobKey, boolean useVersion) {
        return useVersion ? parse(blobKey) : new BlobKey(blobKey, null);
    }

    /** Returns the blob key string: {@code key} or {@code key<VER_SEP>versionId}. */
    @Override
    public String toString() {
        return versionId == null ? key : key + VER_SEP + versionId;
    }
}
