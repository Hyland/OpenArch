/*
 * (C) Copyright 2025 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.api;

public class EmbeddingConstants {
    public static final String EMBEDDING_FIELD_ID = "id";

    public static final String EMBEDDING_FIELD_TYPE = "type";

    public static final String EMBEDDING_FIELD_VECTOR = "vector";

    public static final String EMBEDDING_FIELD_TEXT = "text";

    public static final String EMBEDDING_FIELD_BLOB_REF = "blobref";

    public static final String EMBEDDING_FIELD_LOCATION = "location";

    public static final String EMBEDDING_FIELD_LOCATION_TEXT = "text";

    public static final String EMBEDDING_FIELD_LOCATION_PAGE = "page";

    public static final String EMBEDDING_FIELD_LOCATION_PARAGRAPH = "paragraph";

    public static final String EMBEDDING_FIELD_LOCATION_POSITION = "position";

    public static final String EMBEDDING_FIELD_LOCATION_TOP = "top";

    public static final String EMBEDDING_FIELD_LOCATION_LEFT = "left";

    public static final String EMBEDDING_FIELD_LOCATION_RIGHT = "right";

    public static final String EMBEDDING_FIELD_LOCATION_BOTTOM = "bottom";

    public static final String EMBEDDING_FIELD_LOCATION_TIMESTAMP = "timestamp";

    public static final String EMBEDDING_FIELD_LOCATION_START = "start";

    public static final String EMBEDDING_FIELD_LOCATION_END = "end";

    public static final String EMBEDDING_FIELD_LOCATION_SPREADSHEET = "spreadsheet";

    public static final String EMBEDDING_FIELD_LOCATION_SHEET = "sheet";

    public static final String EMBEDDING_FIELD_LOCATION_COLUMN = "column";

    public static final String EMBEDDING_FIELD_LOCATION_ROW = "row";

    public static final String EMBEDDING_PROPERTY_ID = "sysembed_id";

    public static final String EMBEDDING_PROPERTY_DOCUMENT_ID = "sysembed_docId";

    public static final String EMBEDDING_PROPERTY_TYPE = "sysembed_type";

    public static final String EMBEDDING_PROPERTY_VECTOR = "sysembed_vector";

    public static final String EMBEDDING_PROPERTY_TEXT = "sysembed_text";

    public static final String EMBEDDING_PROPERTY_LOCATION = "sysembed_location";

    public static final String EMBEDDING_PROPERTY_SCORE = "sysembed_score";

    public static final String EMBEDDING_PROPERTY_BLOB_REF = "sysembed_blobref";

    public static final String HYBRID_PIPELINE = "rrf_pipeline";

    private EmbeddingConstants() {
        // Private constructor
    }

}
