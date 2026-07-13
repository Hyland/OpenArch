/*
 * (C) Copyright 2017-2025 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.opensearch.api;

import java.util.Collection;

import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.json.spi.JsonProvider;

/**
 * @since 9.3
 */
public interface OpenSearchClient extends AutoCloseable {

    // -------------------------------------------------------------------
    // Admin
    //
    boolean waitForYellowStatus(String[] indexNames, int timeoutSecond);

    HealthStatus getHealthStatus(String[] indexNames);

    void refresh(String indexName);

    void flush(String indexName);

    void optimize(String indexName);

    boolean indexExists(String indexName);

    boolean mappingExists(String indexName, String type);

    void deleteIndex(String indexName, int timeoutSecond);

    void createIndex(String indexName, String jsonSettings);

    void createMapping(String indexName, String type, String jsonMapping);

    boolean aliasExists(String aliasName);

    /**
     * Returns the name of the index referenced by the alias. Returns null if the alias does not exists.
     */
    String getFirstIndexForAlias(String aliasName);

    void updateAlias(String aliasName, String indexName);

    void updateIndexDynamicSettings(String indexName, String jsonSettings);

    // -------------------------------------------------------------------
    // Search
    //

    BulkResponse bulk(BulkRequest request);

    void delete(String indexName, String docId);

    DeleteByQueryResponse deleteByQuery(DeleteByQueryRequest request);

    SearchResponse<DocumentDTO> search(SearchRequest request);

    SearchResponse<DocumentDTO> searchScroll(ScrollRequest request);

    GetResponse<DocumentDTO> get(GetRequest request);

    /**
     * Performs the indexing request.
     *
     * @throws ConcurrentUpdateException if a more recent version of the document exits.
     */
    IndexResponse index(IndexRequest<? extends JsonpSerializable> request);

    void clearScroll(String scrollId);

    JsonpMapper getJsonpMapper();

    JsonProvider getJsonProvider();

    ObjectMapper getObjectMapper();

    OpenSearchIndexStoreStats getIndexStoreStats(Collection<String> indexNames);

    /**
     * @return the number of open scroll contexts in the cluster, 0 if the stats are not available for any reason
     * (e.g. security restrictions). This is used to monitor the number of open scroll contexts and detect
     * potential leaks, but should not be used for any critical logic as it may return 0
     * even if there are actually open scroll contexts.
     */
    long getOpenScrollContexts();

    /**
     * Ensures the search pipeline with the given ID and configuration exists, creating it if necessary.
     *
     * @param pipelineId the pipeline ID to ensure exists
     * @param pipelineConfig structured pipeline configuration as OpenSearch API JSON tree
     */
    void ensurePipelineWithConfig(String pipelineId, JsonNode pipelineConfig);
}
