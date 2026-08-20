/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.opensearch.api;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;

import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.query.OpenSearchQueryBuilder;

/**
 * Interface to index or search documents, or administer indexes.
 */
public interface OpenSearchService {

    // search

    /**
     * Returns a document list using an {@link OpenSearchQueryBuilder}.
     */
    DocumentModelList query(OpenSearchQueryBuilder queryBuilder);

    /**
     * Returns documents and aggregates.
     */
    OpenSearchResult queryAndAggregate(OpenSearchQueryBuilder queryBuilder);

    /**
     * Performs the initial search of a scrollable search request using an {@link OpenSearchQueryBuilder}.
     *
     * @param keepAlive the search context lifetime
     * @return an {@link OpenSearchScrollResult} including the search results and a scroll id, to be passed to the subsequent
     *         calls to {@link #scroll(OpenSearchScrollResult)}
     */
    OpenSearchScrollResult scroll(OpenSearchQueryBuilder queryBuilder, long keepAlive);

    /**
     * Retrieves the next batch of results of a scrollable search request for the given {@link OpenSearchScrollResult}.
     *
     * @return an {@link OpenSearchScrollResult} including the search results and a scroll id, to be passed to the subsequent
     *         calls to {code scroll}.
     */
    OpenSearchScrollResult scroll(OpenSearchScrollResult scrollResult);

    /**
     * Clear scroll on OpenSearch cluster for the given {@link OpenSearchScrollResult}.
     */
    void clearScroll(OpenSearchScrollResult scrollResult);

    // index

    /**
     * Run a worker to process the {@link IndexingCommand}.
     * <p>
     * Asynchronous command schedules an indexing job and return.
     * <p>
     * Synchronous command execute an indexing job using a new Tx then refresh the index so the document is searchable
     * immediately. if the command is also recursive the children are processed asynchronously.
     * <p>
     * If there is more than one cmd the OpenSearch request is done in bulk mode.
     */
    void runIndexingWorker(List<IndexingCommand> cmds);

    /**
     * Reindex documents matching the NXQL query, This is done in an asynchronous job.
     */
    default void runReindexingWorker(String repositoryName, String nxql) {
        runReindexingWorker(repositoryName, nxql, false);
    }

    /**
     * Reindex documents matching the NXQL query, This is done in an asynchronous job. When syncAlias is true a call is
     * made to sync the search alias with write alias once indexing is done.
     */
    void runReindexingWorker(String repositoryName, String nxql, boolean syncAlias);

    /**
     * Recreate an index and run an async reindexing worker.
     */
    void reindexRepository(String repositoryName);

    /**
     * Process the {@link IndexingCommand}.
     * <p>
     * Send indexing command to OpenSearch, if the command is synchronous the index is refreshed so the document is
     * searchable immediately. Recursive indexing is not taken in account except for deletion. This is not a
     * transactional operation, a rollback will not discard the executed commands.
     */
    void indexNonRecursive(IndexingCommand cmd, boolean isManualReindexing);

    /**
     * Same as {@link #indexNonRecursive(IndexingCommand)} but
     * process the list command using a bulk request.
     */
    void indexNonRecursive(List<IndexingCommand> cmds, boolean isManualReindexing);

    // admin

    /**
     * Retrieves the {@link Client} that can be used to access OpenSearch API
     */
    OpenSearchClient getClient();

    /**
     * Initialize OpenSearch indexes. Setup the index settings and mapping for each index that has been registered.
     *
     * @param dropIfExists if {true} remove an existing index
     */
    void initIndexes(boolean dropIfExists);

    /**
     * Reinitialize an index. This will drop the existing index, recreate it with its settings and mapping, the index
     * will be empty.
     */
    void dropAndInitIndex(String indexName);

    /**
     * Reinitialize the index of a repository. This will drop the existing index, recreate it with its settings and
     * mapping, the index will be empty.
     */
    default void dropAndInitRepositoryIndex(String repositoryName) {
        dropAndInitRepositoryIndex(repositoryName, true);
    }

    /**
     * Reinitialize the index of a repository. This will drop the existing index, recreate it with its settings and
     * mapping, the index will be empty. When syncAlias is false then search alias is not updated with the new index,
     * you need to explicitly call {@link #syncSearchAndWriteAlias(String)}
     */
    void dropAndInitRepositoryIndex(String repositoryName, boolean syncAlias);

    /**
     * List repository names that have OpenSearch support.
     */
    List<String> getRepositoryNames();

    /**
     * Get the search index name associated with the repository name.
     *
     * @throws NoSuchElementException if there is no OpenSearch index associated with the requested repository.
     */
    String getIndexNameForRepository(String repositoryName);

    /**
     * Gets the repository name associated with the index.
     */
    String getRepositoryForIndex(String indexName);

    /**
     * Get the embeddings index name associated with the repository name.
     *
     * @throws NoSuchElementException if there is no embeddings index associated with the requested repository.
     */
    String getEmbeddingsIndexNameForRepository(String repositoryName);

    /**
     * Returns the index to use for any write operations.
     */
    String getWriteIndexName(String searchIndexName);

    /**
     * Make sure that the search alias point to the same index as the write alias.
     */
    void syncSearchAndWriteAlias(String searchIndexName);

    /**
     * Returns true if there are indexing activities scheduled or running.
     */
    boolean isIndexingInProgress();

    /**
     * A {@link Future} that accepts callback on completion when all the indexing worker are done.
     */
    Future<Boolean> prepareWaitForIndexing();

    /**
     * Refresh all document indexes, immediately after the operation occurs, so that the updated document appears in
     * search results immediately. There is no fsync thus doesn't guarantee durability.
     */
    void refresh();

    /**
     * Refresh document index for the specific repository, immediately after the operation occurs, so that the updated
     * document appears in search results immediately. There is no fsync thus doesn't guarantee durability.
     */
    void refreshRepositoryIndex(String repositoryName);

    /**
     * Refresh embeddings index for the specific repository, immediately after the operation occurs, so that the updated
     * embeddings appear in search results immediately. There is no fsync thus doesn't guarantee durability.
     */
    void refreshRepositoryEmbeddingsIndex(String repositoryName);

    /**
     * OpenSearch flush on all document indexes, triggers a lucene commit, empties the transaction log. Data is flushed
     * to disk.
     */
    void flush();

    /**
     * OpenSearch flush on document index for a specific repository, triggers a lucene commit, empties the transaction
     * log. Data is flushed to disk.
     */
    void flushRepositoryIndex(String repositoryName);

    /**
     * OpenSearch flush on embeddings index for a specific repository, triggers a lucene commit, empties the transaction
     * log. Data is flushed to disk.
     */
    void flushRepositoryEmbeddingsIndex(String repositoryName);

    /**
     * OpenSearch run {@link #optimizeRepositoryIndex} on all document indexes,
     */
    void optimize();

    /**
     * OpenSearch optimize operation allows to reduce the number of segments to one, Note that this can potentially be a
     * very heavy operation.
     */
    void optimizeRepositoryIndex(String repositoryName);

    /**
     * OpenSearch optimize operation allows to reduce the number of segments to one, Note that this can potentially be a
     * very heavy operation.
     */
    void optimizeIndex(String indexName);

    /**
     * Returns the total number of command processed by OpenSearch for this Nuxeo instance. Useful for test assertion.
     */
    int getTotalCommandProcessed();

    /**
     * Returns the OpenSearch index store statistics for the existing repository indexes.
     */
    OpenSearchIndexStoreStats getIndexStoreStats();

    /**
     * Search for documents and aggregate property names matching a wildcard pattern.
     */
    OpenSearchResult queryWithTermsAggregation(OpenSearchQueryBuilder queryBuilder, String termsAggregationField,
            String searchTerm, int aggregationLimit);
}
