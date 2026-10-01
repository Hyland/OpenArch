/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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

/**
 * Constants for documents system fields.
 */
public class DocumentConstants {

    private DocumentConstants() {
        // nothing
    }

    public static final char PREFIX_SEPARATOR = '_';

    public static final String PREFIX_SEPARATOR_S = "_";

    public static final String SCHEMA_NAME = "Document";

    // name of system schema
    public static final String SYSTEM = "system";

    public static final String SYS_PREFIX = "sys_";

    public static final String SYS_ACL = "sys_acl";

    public static final String SYS_ANCESTOR_IDS = "sys_ancestorIds";

    public static final String SYS_BLOB_KEYS = "sys_blobKeys";

    public static final String SYS_CHANGE_TOKEN = "sys_changeToken";

    public static final String SYS_CONTRIBUTORS = "sys_contributors";

    public static final String SYS_CREATED = "sys_created";

    public static final String SYS_CREATOR = "sys_creator";

    public static final String SYS_DESCRIPTION = "sys_description";

    public static final String SYS_EFFECTIVE_ACL = "sys_effectiveAcl";

    public static final String SYS_EFFECTIVE_PERMISSIONS = "sys_effectivePermissions";

    public static final String SYS_FULLTEXT = "sys_fulltext";

    public static final String SYS_FULLTEXT_BINARY = "sys_fulltextBinary";

    public static final String SYS_FULLTEXT_JOB_ID = "sys_fulltextJobId";

    public static final String SYS_FULLTEXT_SCORE = "sys_fulltextScore";

    public static final String SYS_GENERATION = "sys_gen";

    public static final String SYS_ID = "sys_id";

    public static final String SYS_IS_FOLDERISH = "sys_isFolderish";

    public static final String SYS_IS_TRASHED = "sys_isTrashed";

    public static final String SYS_LAST_CONTRIBUTOR = "sys_lastContributor";

    public static final String SYS_LIFECYCLE_STATE = "sys_lifecycleState";

    public static final String SYS_LOCK_CREATED = "sys_lockCreated";

    public static final String SYS_LOCK_OWNER = "sys_lockOwner";

    public static final String SYS_MIXIN_TYPES = "sys_mixinTypes";

    public static final String SYS_MODIFIED = "sys_modified";

    public static final String SYS_NAME = "sys_name";

    public static final String SYS_PARENT_ID = "sys_parentId";

    public static final String SYS_PARENT_PATH = "sys_parentPath";

    public static final String SYS_PATH = "sys_path";

    public static final String SYS_PATH_DEPTH = "sys_pathDepth";

    public static final String SYS_POS = "sys_pos";

    public static final String SYS_PRIMARY_TYPE = "sys_primaryType";

    public static final String SYS_READ_ACL = "sys_racl";

    public static final String SYS_REPOSITORY = "sys_repository";

    public static final String SYS_SECURITY_POLICIES = "sys_secpol";

    public static final String SYS_TITLE = "sys_title";

    public static final String SYS_USER_GENERATION = "sys_ugen";

    public static final String KEY_ACE_PRINCIPAL = "principal";

    public static final String KEY_ACE_PERMISSION = "perm";

    public static final String KEY_ACE_GRANT = "grant";

    public static final String KEY_ACE_CREATOR = "creator";

    public static final String KEY_ACE_BEGIN = "begin";

    public static final String KEY_ACE_END = "end";

    public static final String KEY_ACE_STATUS = "status";

    // SysGovernable

    public static final String SYSGOV_PREFIX = "sysgov_";

    public static final String SYSGOV_RETAIN_UNTIL = "sysgov_retainUntil";

    public static final String SYSGOV_HAS_LEGAL_HOLD = "sysgov_hasLegalHold";

    public static final String SYSGOV_IS_RECORD = "sysgov_isRecord";

    // SysVersionable

    public static final String SYSVER_PREFIX = "sysver_";

    public static final String SYSVER_CREATED = "sysver_created";

    public static final String SYSVER_CREATOR = "sysver_creator";

    public static final String SYSVER_DESCRIPTION = "sysver_description";

    public static final String SYSVER_IS_CHECKED_IN = "sysver_isCheckedIn";

    public static final String SYSVER_IS_TEMP = "sysver_isTemp";

    public static final String SYSVER_IS_VERSION = "sysver_isVersion";

    public static final String SYSVER_MINOR_VERSION = "sysver_minorVersion";

    public static final String SYSVER_TITLE = "sysver_title";

    public static final String SYSVER_VERSION = "sysver_version";

    public static final String SYSFILE = "sysfile";

    public static final String SYSFILE_BLOB = "sysfile_blob";

    public static final String SYSFILES_BLOBS = "sysfiles_blobs";

    public static final String DESTINATION_FOLDER_ID = "destinationFolderId";

    public static final String DESTINATION_FOLDER_PATH = "destinationFolderPath";

    // Document metadata schemas fields:

    public static final String SYSMD_BOOKMARK_PREFIX = "sysmdbm";
    public static final String SYSMD_BOOKMARK_NAME = "sysmdbm_name";
    public static final String SYSMD_BOOKMARK_PAGE_ID = "sysmdbm_pageId";
    public static final String SYSMD_BOOKMARK_ADDITIONAL_DATA = "sysmdbm_additionalData";

    public static final String SYSMD_COMMENT_PREFIX = "sysmdcomment";
    public static final String SYSMD_COMMENT_CONTENT = "sysmdcomment_content";
    public static final String SYSMD_COMMENT_EXTERNAL_AUTHOR = "sysmdcomment_externalAuthor";
    public static final String SYSMD_COMMENT_STATE = "sysmdcomment_state";
    public static final String SYSMD_COMMENT_ADDITIONAL_DATA = "sysmdcomment_additionalData";
    public static final String SYSMD_COMMENT_DATE = "sysmdcomment_date";
    public static final String SYSMD_COMMENT_UPDATED_AT = "sysmdcomment_updatedAt";
    public static final String SYSMD_COMMENT_IS_EXTERNAL_COMMENT = "sysmdcomment_isExternalComment";
    public static final String SYSMD_COMMENT_EXTERNAL_ID = "sysmdcomment_externalId";

    public static final String SYSMD_ANNOTATION_PREFIX = "sysmdanno";
    public static final String SYSMD_ANNOTATION_NAME = "sysmdanno_name";
    public static final String SYSMD_ANNOTATION_PAGE_ID = "sysmdanno_pageId";
    public static final String SYSMD_ANNOTATION_TYPE = "sysmdanno_type";
    public static final String SYSMD_ANNOTATION_CONTENT_ITEM_ID = "sysmdanno_contentItemId";
    public static final String SYSMD_ANNOTATION_ID = "sysmdanno_id";
    public static final String SYSMD_ANNOTATION_ADDITIONAL_DATA = "sysmdanno_additionalData";
    public static final String SYSMD_ANNOTATION_X = "sysmdanno_x";
    public static final String SYSMD_ANNOTATION_Y = "sysmdanno_y";
    public static final String SYSMD_ANNOTATION_HEIGHT = "sysmdanno_height";
    public static final String SYSMD_ANNOTATION_WIDTH = "sysmdanno_width";
    public static final String SYSMD_ANNOTATION_FILL_COLOR = "sysmdanno_fillColor";
    public static final String SYSMD_ANNOTATION_FILLABLE = "sysmdanno_fillable";
    public static final String SYSMD_ANNOTATION_STROKE = "sysmdanno_stroke";
    public static final String SYSMD_ANNOTATION_OPACITY = "sysmdanno_opacity";
    public static final String SYSMD_ANNOTATION_RESIZABLE = "sysmdanno_resizable";
    public static final String SYSMD_ANNOTATION_MOVABLE = "sysmdanno_movable";
    public static final String SYSMD_ANNOTATION_DATA = "sysmdanno_data";
    public static final String SYSMD_ANNOTATION_SCALE = "sysmdanno_scale";
    public static final String SYSMD_ANNOTATION_FIX_ROTATION = "sysmdanno_fixRotation";

    public static final String SYSMD_TEXT_PREFIX = "sysmdtext";
    public static final String SYSMD_TEXT = "sysmdtext_text";
    public static final String SYSMD_TEXT_FONT_FAMILY = "sysmdtext_fontFamily";
    public static final String SYSMD_TEXT_FONT_SIZE = "sysmdtext_fontSize";
    public static final String SYSMD_TEXT_FONT_COLOR = "sysmdtext_fontColor";
    public static final String SYSMD_TEXT_ALIGNMENT = "sysmdtext_textAlignment";
    public static final String SYSMD_TEXT_VERTICALLY_CENTER = "sysmdtext_verticallyCenterText";
    public static final String SYSMD_TEXT_HORIZONTALLY_CENTER_BOX = "sysmdtext_horizontallyCenterTextBox";
    public static final String SYSMD_TEXT_ITALIC = "sysmdtext_italic";
    public static final String SYSMD_TEXT_BOLD = "sysmdtext_bold";
    public static final String SYSMD_TEXT_UNDERLINE = "sysmdtext_underline";
    public static final String SYSMD_TEXT_STRIKE_THROUGH = "sysmdtext_strikeThrough";
    public static final String SYSMD_TEXT_OVERFLOW = "sysmdtext_overflow";
    public static final String SYSMD_TEXT_PADDING = "sysmdtext_padding";
    public static final String SYSMD_TEXT_RADIUS = "sysmdtext_radius";
    public static final String SYSMD_TEXT_LINE_HEIGHT = "sysmdtext_lineHeight";
    public static final String SYSMD_TEXT_LINE_HEIGHT_MULTIPLIER = "sysmdtext_lineHeightMultiplier";

    public static final String SYSMD_LINE_PREFIX = "sysmdline";
    public static final String SYSMD_LINE_REVERSE_X = "sysmdline_reverseX";
    public static final String SYSMD_LINE_REVERSE_Y = "sysmdline_reverseY";
    public static final String SYSMD_LINE_MARKER_START = "sysmdline_markerStart";
    public static final String SYSMD_LINE_MARKER_END = "sysmdline_markerEnd";
    public static final String SYSMD_LINE_CAP = "sysmdline_lineCap";

    public static final String SYSMD_IMAGE_PREFIX = "sysmdimage";
    public static final String SYSMD_IMAGE_HREF = "sysmdimage_href";
    public static final String SYSMD_IMAGE_PADDING = "sysmdimage_padding";
    public static final String SYSMD_IMAGE_RADIUS = "sysmdimage_radius";

    public static final String SYSMD_HIGHLIGHT_PREFIX = "sysmdhl";
    public static final String SYSMD_HIGHLIGHT_HIGHLIGHTS = "sysmdhl_highlights";

    public static final String SYSMD_PATH_PREFIX = "sysmdpath";
    public static final String SYSMD_PATH_PATH = "sysmdpath_path";

    public static final String SYSMD_ANNOTATABLE_PREFIX = "sysmdannotatable";
    public static final String SYSMD_ANNOTATABLE_STATE = "sysmdannotatable_state";

    public static final String SYSMD_REDACTABLE_PREFIX = "sysmdredactable";
    public static final String SYSMD_REDACTABLE_SOURCE_DOCUMENT_ID = "sysmdredactable_sourceDocumentId";
    public static final String SYSMD_REDACTABLE_STATE = "sysmdredactable_state";

    public static final String SCHEMA_SYSMD_ANNOTATABLE = "sysmdannotatable";
    public static final String SCHEMA_SYSMD_REDACTABLE = "sysmdredactable";

    public static final String SCHEMA_SYSMD_BOOKMARK = "sysmdbm";
    public static final String SCHEMA_SYSMD_ANNOTATION = "sysmdanno";
    public static final String SCHEMA_SYSMD_COMMENT = "sysmdcomment";
    public static final String SCHEMA_SYSMD_TEXT = "sysmdtext";
    public static final String SCHEMA_SYSMD_LINE = "sysmdline";
    public static final String SCHEMA_SYSMD_IMAGE = "sysmdimage";
    public static final String SCHEMA_SYSMD_HIGHLIGHT = "sysmdhl";
    public static final String SCHEMA_SYSMD_PATH = "sysmdpath";
    public static final String SCHEMA_SYSTEM = "system";

    // Embeddings
    public static final String SCHEMA_SYSEMB = "sysembeddings";
    public static final String SYSEMB_PREFIX = "sysemb";
    public static final String SYSEMB_EMBEDDINGS = "sysemb_embeddings";
    public static final String SYSEMB_CHUNKS = "sysemb_chunks";
    public static final String SYSEMB_RESIDENCY = "sysemb_residency";
    public static final String SYSEMB_RESIDENCY_ON_PREM = "onPrem";
    public static final String SYSEMB_RESIDENCY_CLOUD = "cloud";
    public static final String SYSEMB_ON_PREM_PROCESSING_SUCCESS = "sysemb_onPremProcessingSuccess";

    // SysGraphable (ontology routing)
    public static final String SCHEMA_SYSGRAPH = "sysgraph";
    public static final String SYSGRAPH_PREFIX = "sysgraph";
    public static final String SYSGRAPH_GRAPH_DB_IDS = "sysgraph_graphDBIds";
    public static final String SYSGRAPH_ONTOLOGY_REFS = "sysgraph_ontologyRefs";

    // Content Intelligence
    public static final String SCHEMA_CINREMOTE = "cinremote";
    public static final String CINREMOTE_PREFIX = "cin";
    public static final String CINREMOTE_INGEST_EVENT_VERSION = "cin_ingestEventVersion";
    public static final String CINREMOTE_INGEST_RAW = "cin_ingestRaw";
    public static final String CIN_SOURCE_ID = "cin_sourceId";

    public static final String SCHEMA_CINCONTEXT = "cincontext";
    public static final String CINCONTEXT_PREFIX = "cinctx";
    public static final String CONTEXT_PROPERTIES = "cinctx_contextProperties";
    public static final String CONTEXT_PROPERTY_NAMES = "cinctx_contextPropertyNames";
    public static final String CONTEXT_KG_PROCESSING_STATES = "cinctx_kgProcessingStates";

    //Fulltext
    public static final String FULLTEXT_EXTRACTOR_TASK_NAME = "fulltextExtractor";
    public static final String FULLTEXT_EXTRACTOR_SCROLLER_TASK_NAME = "scrollerDocument";

}
