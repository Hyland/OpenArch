/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.api;

import static com.hyland.openarch.ecm.core.schema.TypeConstants.PREFIX_SEPARATOR;

/**
 * Constants for Renditions document models.
 */
public class RenditionsConstants {

    private RenditionsConstants() {
        // nothing
    }

    public enum RENDITIONS_STATUS {
        COMPLETED, PENDING, FAILED, PRE_CONDITIONS_FAILED
    };

    public static final String RENDITIONS_API_PATH = "/api/documents/%s/renditions";

    public static final String RENDITIONS_API_RENDITION_PATH = RENDITIONS_API_PATH + "/%s";

    public static final String RENDITIONS_CONFIG_API_PATH = "/api/repository/renditions";

    public static final String RENDITIONS_CONFIG_PROJECT_API_PATH = RENDITIONS_CONFIG_API_PATH + "/project/%s";

    public static final String RENDITIONS_CONTAINER_DOCUMENT_TYPE = "SysRenditionsContainer";

    public static final String RENDITIONS_CONTAINER_NAME = "SysRenditionsContainer";

    public static final String RENDITION_DOCUMENT_TYPE = "SysRendition";

    public static final String RENDITION_PREFIX = "sysrendition";

    public static final String RENDITION_PROPERTY_ID = RENDITION_PREFIX + PREFIX_SEPARATOR + "id";

    public static final String RENDITION_PROPERTY_STATUS = RENDITION_PREFIX + PREFIX_SEPARATOR + "status";

    public static final String RENDITION_PROPERTY_BLOB = RENDITION_PREFIX + PREFIX_SEPARATOR + "blob";

    public static final String RENDITION_PROPERTY_SOURCE_DOC = RENDITION_PREFIX + PREFIX_SEPARATOR + "sourceDoc";

    public static final String RENDITION_PROPERTY_IS_EXTERNAL = RENDITION_PREFIX + PREFIX_SEPARATOR + "isExternal";

    public static final String RENDITION_PROPERTY_BLOB_PATH = RENDITION_PREFIX + PREFIX_SEPARATOR + "blobPath";
}
