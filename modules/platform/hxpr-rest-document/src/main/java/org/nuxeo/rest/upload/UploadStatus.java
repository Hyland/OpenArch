/*
 * (C) Copyright 2025 Hyland (https://hyland.com/) and others.
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
 *     Adam Dunajewski
 */
package org.nuxeo.rest.upload;

public enum UploadStatus {
    INCOMPLETE, PROCESSING, READY, ERROR;

    public static UploadStatus parse(Object value) {
        if (!(value instanceof String string)) {
            return INCOMPLETE;
        }
        try {
            return UploadStatus.valueOf(string);
        } catch (IllegalArgumentException e) {
            return INCOMPLETE;
        }
    }
}
