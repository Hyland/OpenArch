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
package org.nuxeo.rest.bulkimport;

import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BulkImportUploadDTO(
        @JsonProperty(KEY_BLOB_DIGEST) String digest, //
        @JsonProperty(KEY_BLOB_ENCODING) String encoding, //
        @JsonProperty(KEY_BLOB_FILENAME) String filename, //
        @JsonProperty(KEY_BLOB_MIME_TYPE) String mimeType, //
        @JsonProperty(KEY_BLOB_LENGTH) long length, //
        @JsonProperty(BulkImportConstants.PROP_IMPORT_KEY) String importKey) implements Serializable {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String digest;

        private String encoding;

        private String filename;

        private String mimeType;

        private long length;

        private String importKey;

        public Builder digest(String digest) {
            this.digest = digest;
            return this;
        }

        public Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder length(long length) {
            this.length = length;
            return this;
        }

        public Builder importKey(String importKey) {
            this.importKey = importKey;
            return this;
        }

        public BulkImportUploadDTO build() {
            return new BulkImportUploadDTO(digest, encoding, filename, mimeType, length, importKey);
        }
    }
}
