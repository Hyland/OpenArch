/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.event.impl.document;

import org.nuxeo.ecm.core.event.GenericEvent;

public class UploadEvent extends GenericEvent {

    public static final String GENERIC_TYPE_KEY_SUFFIX = "upload";

    private final long length;
    private final String mimeType;
    private final boolean isPresignedUrl;

    public UploadEvent(long length, String mimeType, boolean isPresignedUrl) {
        this.length = length;
        this.mimeType = mimeType;
        this.isPresignedUrl = isPresignedUrl;
    }

    public UploadEvent(long length, String mimeType) {
        this(length, mimeType, false);
    }

    public long getLength() {
        return length;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isPresignedUrl() {
        return isPresignedUrl;
    }

    @Override
    public String getGenericType() {
        return GENERIC_TYPE_KEY_SUFFIX;
    }

}
