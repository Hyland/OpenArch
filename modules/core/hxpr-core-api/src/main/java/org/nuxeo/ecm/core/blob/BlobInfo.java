/*
 * (C) Copyright 2015-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.blob;

import org.nuxeo.ecm.core.api.Blob;

/**
 * Class describing information from a {@link Blob}, suitable for serialization and storage.
 *
 * @since 7.2
 */
public class BlobInfo {

    private String key;

    private String mimeType;

    private String encoding;

    private String filename;

    private Long length;

    private String digest;

    /** Empty constructor. */
    public BlobInfo() {
    }

    /**
     * Copy constructor.
     *
     * @since 7.10
     */
    public BlobInfo(BlobInfo other) {
        setKey(other.getKey());
        setMimeType(other.getMimeType());
        setEncoding(other.getEncoding());
        setFilename(other.getFilename());
        setLength(other.getLength());
        setDigest(other.getDigest());
    }

    public String getKey() {
        return key;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getFilename() {
        return filename;
    }

    public Long getLength() {
        return length;
    }

    public String getDigest() {
        return digest;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setLength(Long length) {
        this.length = length;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }
}
