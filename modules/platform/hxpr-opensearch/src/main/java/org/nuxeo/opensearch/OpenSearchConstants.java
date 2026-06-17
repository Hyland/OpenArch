/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.opensearch;

public final class OpenSearchConstants {

    public static final String ID_FIELD = "_id";

    public static final String FULLTEXT_FIELD = "all_field";

    /**
     * OpenSearch type name used to index Nuxeo documents
     */
    public static final String DOC_TYPE = "doc";

    /**
     * OpenSearch type name used to index Nuxeo audit entries
     */
    public static final String ENTRY_TYPE = "entry";

    /**
     * OpenSearch type name used for the UID sequencer index
     */
    public static final String SEQ_ID_TYPE = "seqId";

    /**
     * OpenSearch type name used to index embeddings
     */
    public static final String EMBEDDINGS_TYPE = "embeddings";

    public static final String ALL_FIELDS = "*";

    public static final String REINDEX_BUCKET_READ_PROPERTY = "opensearch.reindex.bucketReadSize";

    public static final String REINDEX_BUCKET_WRITE_PROPERTY = "opensearch.reindex.bucketWriteSize";

    public static final String REINDEX_ON_STARTUP_PROPERTY = "opensearch.reindex.onStartup";

    public static final String INDEX_BULK_MAX_SIZE_PROPERTY = "opensearch.index.bulkMaxSize";

    public static final String DISABLE_AUTO_INDEXING = "disableAutoIndexing";

    public static final String OPENSEARCH_SYNC_INDEXING_FLAG = "OpenSearchSyncIndexing";

    public static final String EPOCH_MILLIS_FORMAT = "epoch_millis";

    /** @since 10.2 */
    public static final String OPENSEARCH_SCORE_FIELD = "_score";

    private OpenSearchConstants() {
    }

}
