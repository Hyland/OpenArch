/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.schema;

import com.hyland.openarch.ecm.core.schema.types.Type;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public final class TypeConstants {

    public static final char PREFIX_SEPARATOR = '_';

    public static final String SYS_CONTENT = "SysContent";

    public static final String SYS_ROOT = "SysRoot";

    public static final String SYS_FOLDER = "SysFolder";

    public static final String SYS_ORDERED_FOLDER = "SysOrderedFolder";

    public static final String SYS_FILE = "SysFile";

    public static final String SYS_EMBEDDINGS = "SysEmbeddings";

    public static final String BLOB_TYPE = "blob";

    // these keys are present in the standard blob schema

    public static final String KEY_BLOB_KEY = "key";

    public static final String KEY_BLOB_DIGEST = "digest";

    public static final String KEY_BLOB_ENCODING = "encoding";

    public static final String KEY_BLOB_FILENAME = "filename";

    public static final String KEY_BLOB_LENGTH = "length";

    public static final String KEY_BLOB_MIME_TYPE = "mimeType";

    // Metadata document types
    public static final String SYS_MD_BOOKMARK = "SysMDBookmark";

    public static final String SYS_MD_ANNOTATION = "SysMDAnnotation";

    public static final String SYS_MD_COMMENT = "SysMDComment";

    // Constant utility class
    private TypeConstants() {
    }

    /**
     * Returns true if given type is named "blob", as it's a reserved type name for blobs.
     */
    public static boolean isBlobType(Type type) {
        return type != null && type.getName().equals(BLOB_TYPE);
    }

}
