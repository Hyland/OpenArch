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
package com.hyland.openarch.rest.upload;

/**
 * Status of the blob attachment phase.
 */
public enum UploadAttachStatus {
    PENDING, PROCESSING, READY, ERROR;

    /**
     * Parses an {@link UploadAttachStatus} from an arbitrary value.
     * <p>
     * Returns {@code null} when the value is absent or unrecognized.
     */
    public static UploadAttachStatus parse(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        try {
            return UploadAttachStatus.valueOf(string);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
