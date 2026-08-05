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
package org.nuxeo.ecm.core.schema;

/**
 * Defines base mixin names used in the core.
 *
 * @author <a href="mailto:dm@nuxeo.com">Dragos Mihalache</a>
 */
public final class MixinNames {

    /**
     * Document may have an associated blob
     */
    public static final String SYS_FILISH = "SysFilish";

    /**
     * Document may have children
     */
    public static final String SYS_FOLDERISH = "SysFolderish";

    /**
     * It will make it possible to create versions for this document type
     */
    public static final String SYS_VERSIONABLE = "SysVersionable";

    /**
     * Document is included in the copy when its parent is versioned (copied along the working copy when the version is created, or
     * copied along the version when it is restored).
     */
    public static final String SYS_VERSIONED_WITH_PARENT = "SysVersionedWithParent";

    /**
     * This document will be governable. This document can be retained or put on legal hold.
     */
    public static final String SYS_GOVERNABLE = "SysGovernable";

    /**
     * Marker mixin indicating a document has embeddings.
     */
    public static final String SYS_HAS_EMBEDDINGS = "SysHasEmbeddings";

    /**
     * Mixin indicating a document participates in the Knowledge Graph with ontology routing.
     */
    public static final String SYS_GRAPHABLE = "SysGraphable";

    /**
     * Content Intelligence mixin carrying the remote (source-system) representation of a document.
     */
    public static final String CIN_REMOTE = "CinRemote";

    /**
     * Content Intelligence mixin carrying context (knowledge-graph processing) properties.
     */
    public static final String CIN_CONTEXT = "CinContext";

    /**
     * If your Nuxeo target version is 5.3.2 or higher, you'll be able to manage the order of this document children
     */
    public static final String SYS_ORDERABLE = "SysOrderable";

    public static final String SYS_FULL_TEXT_INDEXABLE = "SysFullTextIndexable";

    // Mixin for test documents, useful to skip integrations like Insight, Audit, etc.
    public static final String TEST_DOCUMENT_MIXIN = "SysTestDocument";

    // Metadata annotation mixins
    public static final String SYS_MD_HL = "SysMDHL";

    public static final String SYS_MD_IMAGE = "SysMDImage";

    public static final String SYS_MD_LINE = "SysMDLine";

    public static final String SYS_MD_PATH = "SysMDPath";

    public static final String SYS_MD_TEXT = "SysMDText";

    public static final String SYS_MD_ANNOTATABLE = "SysMDAnnotatable";

    public static final String SYS_MD_REDACTABLE = "SysMDRedactable";

    private MixinNames() {
    }

}
