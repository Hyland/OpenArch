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
package com.hyland.openarch.opensearch;

import static com.hyland.openarch.common.concurrent.ThreadFactories.newThreadFactory;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.opensearch.OpenSearchConstants.ALL_FIELDS;
import static com.hyland.openarch.opensearch.OpenSearchConstants.INDEX_BULK_MAX_SIZE_PROPERTY;
import static com.hyland.openarch.opensearch.OpenSearchConstants.REINDEX_BUCKET_READ_PROPERTY;
import static com.hyland.openarch.opensearch.OpenSearchConstants.REINDEX_ON_STARTUP_PROPERTY;
import static com.hyland.openarch.opensearch.tasks.IndexingBatchTaskProcessor.BATCH_INDEXING_TASK_NAME;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.HYBRID_PIPELINE;
import static com.hyland.openarch.opensearch.tasks.IndexingScrollerTaskProcessor.INDEXING_SCROLLER_TASK_NAME;
import static com.hyland.openarch.opensearch.tasks.IndexingScrollerTaskProcessor.PROPERTY_SYNC_ALIAS;
import static com.hyland.openarch.opensearch.tasks.IndexingTaskProcessor.INDEXING_TASK_NAME;
import static com.hyland.openarch.runtime.api.login.LoginService.SYSTEM_USERNAME;

import java.io.InputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.IOException;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.ScrollerTask;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;
import io.dropwizard.metrics5.Timer.Context;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.utils.BatchUtils;
import com.hyland.openarch.common.xmap.registry.MapRegistry;
import com.hyland.openarch.ecm.core.api.BulkIndexingException;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.impl.DocumentModelListImpl;
import com.hyland.openarch.ecm.core.api.model.BlobNotFoundException;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.opensearch.api.OpenSearchClient;
import com.hyland.openarch.opensearch.api.OpenSearchIndexStoreStats;
import com.hyland.openarch.opensearch.api.OpenSearchResult;
import com.hyland.openarch.opensearch.api.OpenSearchScrollResult;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.client.OpenSearchRestClientFactory;
import com.hyland.openarch.opensearch.commands.IndexingCommand;
import com.hyland.openarch.opensearch.commands.IndexingCommand.Type;
import com.hyland.openarch.opensearch.config.EmbeddingsParserDescriptor;
import com.hyland.openarch.opensearch.config.OpenSearchIndexConfig;
import com.hyland.openarch.opensearch.core.OpenSearchResultSetImpl;
import com.hyland.openarch.opensearch.fetcher.Fetcher;
import com.hyland.openarch.opensearch.io.DocumentDTO;
import com.hyland.openarch.opensearch.query.OpenSearchQueryBuilder;
import com.hyland.openarch.opensearch.query.WildcardUtils;
import com.hyland.openarch.opensearch.tasks.IndexingTaskProcessor;
import com.hyland.openarch.opensearch.tasks.message.IndexingTask;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.metrics.MetricsService;
import com.hyland.openarch.runtime.model.Component;
import com.hyland.openarch.runtime.model.ComponentManager;
import com.hyland.openarch.runtime.model.DefaultComponent;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.SamplerAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.WildcardQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHits;

/**
 * Component used to configure and manage OpenSearch integration
 */
public class OpenSearchServiceImpl extends DefaultComponent implements OpenSearchService, ComponentManager.Listener {

    private static final Logger log = LogManager.getLogger(OpenSearchServiceImpl.class);

    private static final String EP_INDEX = "openSearchIndex";

    private static final String EP_EMBEDDINGS_PARSER = "embeddingsParser"; // kept for EmbeddingsIndexer access

    private static final long REINDEX_TIMEOUT = 20;

    public static final String DEFAULT_BUCKET_SIZE = "500";

    private static final int TIMEOUT_WAIT_FOR_CLUSTER_SECOND = 30;

    private static final int TIMEOUT_DELETE_SECOND = 300;

    // send the bulk indexing command when this size is reached, optimal is 5-10m
    private static final int DEFAULT_MAX_BULK_SIZE = 5 * 1024 * 1024;

    private static final List<String> EMBEDDINGS_DELETION_CONTEXT_FIELDS = List.of(SYS_PRIMARY_TYPE, SYS_MIXIN_TYPES,
            SYS_PARENT_ID, SYS_NAME);

    private static final java.lang.String LOG_MIN_DURATION_FETCH_KEY = "org.nuxeo.OpenSearch.core.log_min_duration_fetch_ms";

    private static final long LOG_MIN_DURATION_FETCH_NS = Long.parseLong(
            Framework.getProperty(LOG_MIN_DURATION_FETCH_KEY, "200")) * 1000000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Classpath location of the Reciprocal Rank Fusion pipeline definition used to merge the sub-query
     * result sets of a hybrid search into a single ranked list.
     */
    private static final String RRF_PIPELINE_CONFIG_RESOURCE = "opensearch/rrf-hybrid-search-pipeline.json";

    private static final String OPENSEARCH_REINDEXING_COMMAND_FAILURE_MONITOR_TAG = "OpenSearchService indexing task failed. Re-Indexing Command";

    private static final String OPENSEARCH_RUNTIME_INDEXING_FAILURE_MONITOR_TAG = "OpenSearchService indexing task failed. Runtime Indexing";

    public static final String PROPERTY_REINDEXING_COMMAND = "hxpr.commands.reindexingCommand";

    private static final String DOCUMENT_DEFAULT_VERSION = "0-0";

    // An arbitrary figure for sampling shard size
    public static final int SAMPLE_SHARD_SIZE = 1000;

    // Aggregation names for terms aggregation queries
    public static final String SAMPLE_AGGREGATION_NAME = "top_N_sample";

    public static final String TERMS_AGGREGATION_NAME = "top_occurrences";

    private final Timer deleteTimer;

    private final Timer indexTimer;

    private final Timer bulkIndexTimer;

    private final Timer searchTimer;

    private final Timer scrollTimer;

    private final Timer fetchTimer;

    // Indexing commands that where received before the index initialization
    private final List<IndexingCommand> stackedCommands = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger runIndexingWorkerCount = new AtomicInteger(0);

    private final AtomicInteger totalCommandProcessed = new AtomicInteger(0);

    private final Map<String, String> indexNames = new HashMap<>();

    private final Map<String, String> embeddingsIndexNames = new HashMap<>();

    private final Map<String, String> repoNames = new HashMap<>();

    private final Map<String, String> writeIndexNames = new HashMap<>();

    private Map<String, OpenSearchIndexConfig> indexConfig;

    private EmbeddingsIndexer embeddingsIndexer; // Initialized on startup and now owns the embeddings parser

    private OpenSearchClient client;

    private boolean indexInitDone;

    private ExecutorService waiterExecutorService;

    private String[] includeSourceFields;

    private String[] excludeSourceFields;

    public OpenSearchServiceImpl() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        indexTimer = registry.timer(MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "index"));
        deleteTimer = registry.timer(MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "delete"));
        bulkIndexTimer = registry.timer(
                MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "bulkIndex"));
        searchTimer = registry.timer(MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "search"));
        scrollTimer = registry.timer(MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "scroll"));
        fetchTimer = registry.timer(MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "fetch"));
    }

    // only for testing purposes
    public void setOpenSearchClient(OpenSearchClient client) {
        this.client = client;
    }

    // OpenArch Component impl ====================================================
    @Override
    public void start() {
        indexConfig = this.<MapRegistry> getExtensionPointRegistry(EP_INDEX).getContributions();
        // embeddings parser now loaded inside EmbeddingsIndexer
        embeddingsIndexer = new EmbeddingsIndexer(this);
        connect();
        initializeIndexes();
        ensureHybridSearchPipeline();
        initListenerThreadPool();
        processStackedCommands();
    }

    @Override
    public void stop() {
        try {
            shutdownListenerThreadPool();
        } finally {
            disconnect();
        }
    }

    protected void reindexOnStartup() {
        boolean reindexOnStartup = Boolean.parseBoolean(Framework.getProperty(REINDEX_ON_STARTUP_PROPERTY, "false"));
        if (!reindexOnStartup) {
            return;
        }
        getRepositoryNames().forEach(repositoryName -> {
            log.warn("Indexing repository: {} on startup", repositoryName);
            runReindexingWorker(repositoryName, "SELECT sys_id FROM SysContent");
            try {
                prepareWaitForIndexing().get(REINDEX_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            } catch (TimeoutException e) {
                log.warn(String.format("Indexation of repository %s not finished after %d s, continuing in background",
                        repositoryName, REINDEX_TIMEOUT));
            }
        });
    }

    @Override
    public int getApplicationStartedOrder() {
        Component component = Framework.getRuntime().getComponent(RepositoryManager.REPOSITORY_COMPONENT_NAME);
        return component.getApplicationStartedOrder() / 2;
    }

    @Override
    public void afterRuntimeStart(ComponentManager mgr) {
        reindexOnStartup();
    }

    void processStackedCommands() {
        if (!stackedCommands.isEmpty()) {
            log.info("Processing {} indexing commands stacked during startup", stackedCommands::size);
            runIndexingWorker(stackedCommands);
            stackedCommands.clear();
            log.debug("Done");
        }
    }

    // Admin ================================================================

    protected void connect() {
        if (client != null) {
            return;
        }
        log.info("Connecting to OpenSearch");
        client = new OpenSearchRestClientFactory().create();
        try {
            checkClusterHealth();
            log.info("OpenSearch Connected");
        } catch (Exception e) {
            disconnect();
            throw new IllegalStateException("Unable to check cluster health", e);
        }
    }

    public void disconnect() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.error("Failed to close client: {}", e.getMessage(), e);
            }
            client = null;
            indexInitDone = false;
            log.info("OpenSearch Disconnected");
        }

    }

    protected void checkClusterHealth(String... indexNames) {
        if (client == null) {
            throw new IllegalStateException("No OpenSearch Client available");
        }
        client.waitForYellowStatus(indexNames, TIMEOUT_WAIT_FOR_CLUSTER_SECOND);
    }

    /**
     * Ensures the hybrid search RRF pipeline exists in OpenSearch.
     * <p>
     * Hybrid (vector + chunkFTS) queries reference the {@code rrf_pipeline} search pipeline (see
     * {@code OpenSearchQueryBuilder}). Creating it here at startup removes the per-request existence check that
     * used to run on every hybrid query.
     * <p>
     * Called from {@link #start()} after indexes are initialized, ensuring the OpenSearch service and
     * client are fully available.
     */
    protected void ensureHybridSearchPipeline() {
        if (client == null) {
            log.warn("OpenSearchClient is not available; skipping hybrid search pipeline initialization");
            return;
        }

        try {
            JsonNode pipelineConfig = loadPipelineConfig();
            log.info("Ensuring OpenSearch hybrid search pipeline exists: {}", HYBRID_PIPELINE);
            client.ensurePipelineWithConfig(HYBRID_PIPELINE, pipelineConfig);
            log.info("OpenSearch pipeline initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize OpenSearch hybrid search pipeline", e);
            throw new RepositoryException("Failed to initialize OpenSearch hybrid search pipeline: " + HYBRID_PIPELINE, e);
        }
    }

    /**
     * Loads the Reciprocal Rank Fusion pipeline definition from classpath.
     */
    private JsonNode loadPipelineConfig() {
        try (InputStream in = getClass().getClassLoader()
                                        .getResourceAsStream(RRF_PIPELINE_CONFIG_RESOURCE)) {
            if (in == null) {
                throw new RepositoryException("Missing OpenSearch pipeline config resource: " + RRF_PIPELINE_CONFIG_RESOURCE);
            }
            return MAPPER.readTree(in);
        } catch (IOException | JacksonException e) {
            throw new RepositoryException("Failed to load OpenSearch pipeline config: " + RRF_PIPELINE_CONFIG_RESOURCE, e);
        }
    }

    protected void initializeIndexes() {
        for (OpenSearchIndexConfig conf : indexConfig.values()) {
            if (conf.isDocumentIndex()) {
                log.info("Associate index: {} with repository: {}", conf::getName, conf::getRepositoryName);
                indexNames.put(conf.getRepositoryName(), conf.getName());
                repoNames.put(conf.getName(), conf.getRepositoryName());
                Set<String> set = new LinkedHashSet<>();
                if (includeSourceFields != null) {
                    set.addAll(Arrays.asList(includeSourceFields));
                }
                set.addAll(Arrays.asList(conf.getIncludes()));
                if (set.contains(ALL_FIELDS)) {
                    set.clear();
                    set.add(ALL_FIELDS);
                }
                includeSourceFields = set.toArray(new String[0]);
                set.clear();
                if (excludeSourceFields != null) {
                    set.addAll(Arrays.asList(excludeSourceFields));
                }
                set.addAll(Arrays.asList(conf.getExcludes()));
                excludeSourceFields = set.toArray(new String[0]);
            } else if (conf.isEmbeddingsIndex()) {
                log.info("Associate embeddings index: {} with repository: {}", conf::getName, conf::getRepositoryName);
                embeddingsIndexNames.put(conf.getRepositoryName(), conf.getName());
                repoNames.put(conf.getName(), conf.getRepositoryName());
            }

        }
        initIndexes(false);
    }

    @Override
    public void refreshRepositoryIndex(String repositoryName) {
        log.debug("Refreshing index associated with repo: {}", repositoryName);
        getClient().refresh(getWriteIndexName(getIndexNameForRepository(repositoryName)));
        log.debug("Refreshing index done");
    }

    @Override
    public void refreshRepositoryEmbeddingsIndex(String repositoryName) {
        log.debug("Refreshing embeddings index associated with repo: {}", repositoryName);
        getClient().refresh(getWriteIndexName(getEmbeddingsIndexNameForRepository(repositoryName)));
        log.debug("Refreshing embeddings index done");
    }

    @Override
    public String getIndexNameForRepository(String repositoryName) {
        String ret = indexNames.get(repositoryName);
        if (ret == null) {
            throw new NoSuchElementException("No index defined for repository: " + repositoryName);
        }
        return ret;
    }

    @Override
    public String getEmbeddingsIndexNameForRepository(String repositoryName) {
        String ret = embeddingsIndexNames.get(repositoryName);
        if (ret == null) {
            throw new NoSuchElementException("No embeddings index defined for repository: " + repositoryName);
        }
        return ret;
    }

    /**
     * Returns {@code true} if an embeddings index is configured for the given repository.
     * <p>
     * Not all repositories (e.g. {@code default_vocabularies}) have an associated embeddings index,
     * so callers performing optional embeddings work should check this before resolving the index name.
     */
    protected boolean hasEmbeddingsIndex(String repositoryName) {
        return embeddingsIndexNames.containsKey(repositoryName);
    }

    @Override
    public String getRepositoryForIndex(String indexName) {
        return repoNames.get(indexName);
    }

    @Override
    public String getWriteIndexName(String searchIndexName) {
        return writeIndexNames.getOrDefault(searchIndexName, searchIndexName);
    }

    @Override
    public void syncSearchAndWriteAlias(String searchIndexName) {
        OpenSearchIndexConfig conf = indexConfig.values()
                                                .stream()
                                                .filter(item -> item.getName().equals(searchIndexName))
                                                .findFirst()
                                                .orElseThrow(IllegalStateException::new);
        syncSearchAndWriteAlias(conf);
    }

    @Override
    public void flushRepositoryIndex(String repositoryName) {
        log.warn("Flushing index associated with repo: {}", repositoryName);
        getClient().flush(getWriteIndexName(getIndexNameForRepository(repositoryName)));
        log.info("Flushing index done");
    }

    @Override
    public void flushRepositoryEmbeddingsIndex(String repositoryName) {
        log.warn("Flushing embeddings index associated with repo: {}", repositoryName);
        getClient().flush(getWriteIndexName(getEmbeddingsIndexNameForRepository(repositoryName)));
        log.info("Flushing embeddings index done");
    }

    @Override
    public void refresh() {
        for (String repositoryName : indexNames.keySet()) {
            refreshRepositoryIndex(repositoryName);
        }

        // Also refresh embeddings indices
        for (String repositoryName : embeddingsIndexNames.keySet()) {
            refreshRepositoryEmbeddingsIndex(repositoryName);
        }
    }

    @Override
    public void flush() {
        for (String repositoryName : indexNames.keySet()) {
            flushRepositoryIndex(repositoryName);
        }

        // Also flush embeddings indices
        for (String repositoryName : embeddingsIndexNames.keySet()) {
            flushRepositoryEmbeddingsIndex(repositoryName);
        }
    }

    @Override
    public void optimizeIndex(String indexName) {
        log.warn("Optimizing index: {}", indexName);
        for (OpenSearchIndexConfig conf : indexConfig.values()) {
            if (conf.getName().equals(indexName)) {
                getClient().optimize(indexName);
            }
        }
        log.info("Optimize done");
    }

    @Override
    public void optimizeRepositoryIndex(String repositoryName) {
        optimizeIndex(getIndexNameForRepository(repositoryName));
    }

    @Override
    public void optimize() {
        for (OpenSearchIndexConfig conf : indexConfig.values()) {
            optimizeIndex(conf.getName());
        }
    }

    @Override
    public OpenSearchClient getClient() {
        return client;
    }

    @Override
    public void initIndexes(boolean dropIfExists) {
        indexInitDone = false;
        for (OpenSearchIndexConfig conf : indexConfig.values()) {
            initIndex(conf, dropIfExists);
        }
        log.info("OpenSearch Service ready");
        indexInitDone = true;
    }

    @Override
    public void dropAndInitIndex(String indexName) {
        log.info("Drop and init index: {}", indexName);
        indexInitDone = false;
        for (OpenSearchIndexConfig conf : indexConfig.values()) {
            if (conf.getName().equals(indexName)) {
                initIndex(conf, true);
            }
        }
        indexInitDone = true;
    }

    @Override
    public void dropAndInitRepositoryIndex(String repositoryName, boolean syncAlias) {
        log.info("Drop and init index of repository: {}", repositoryName);
        indexInitDone = false;
        for (OpenSearchIndexConfig conf : indexConfig.values()) {
            if (conf.isDocumentIndex() && repositoryName.equals(conf.getRepositoryName())) {
                initIndex(conf, true, syncAlias);
            }
        }
        indexInitDone = true;
    }

    @Override
    public List<String> getRepositoryNames() {
        return List.copyOf(indexNames.keySet());
    }

    protected void initIndex(OpenSearchIndexConfig conf, boolean dropIfExists) {
        initIndex(conf, dropIfExists, true);
    }

    protected void initIndex(OpenSearchIndexConfig conf, boolean dropIfExists, boolean syncAlias) {
        if (conf.manageAlias()) {
            initWriteAlias(conf, dropIfExists);
            initSearchAlias(conf);
            writeIndexNames.put(conf.getName(), conf.writeIndexOrAlias());
            if (syncAlias) {
                syncSearchAndWriteAlias(conf);
            }
        } else if (conf.hasExplicitWriteIndex()) {
            initIndex(conf.writeIndexOrAlias(), conf, dropIfExists);
            writeIndexNames.put(conf.getName(), conf.writeIndexOrAlias());
        } else {
            initIndex(conf.getName(), conf, dropIfExists);
            writeIndexNames.put(conf.getName(), conf.getName());
        }
    }

    protected void initWriteAlias(OpenSearchIndexConfig conf, boolean dropIfExists) {
        // init the write index and alias
        String writeAlias = conf.writeIndexOrAlias();
        String writeIndex = getClient().getFirstIndexForAlias(writeAlias);
        String nextWriteIndex = conf.newWriteIndexForAlias(conf.getName(), writeIndex);
        if (writeIndex != null && !dropIfExists) {
            // alias exists make sure the index is well configured
            initIndex(writeIndex, conf, false);
        } else {
            // create a new write index and update the alias, we don't drop anything
            if (getClient().indexExists(nextWriteIndex)) {
                throw new IllegalStateException(
                        String.format("New index name %s for the alias %s already exists", nextWriteIndex, writeAlias));
            }
            initIndex(nextWriteIndex, conf, false);
            getClient().updateAlias(writeAlias, nextWriteIndex);
        }
    }

    protected void initSearchAlias(OpenSearchIndexConfig conf) {
        // init the search alias
        String searchAlias = conf.getName();
        String searchIndex = getClient().getFirstIndexForAlias(searchAlias);
        String writeAlias = conf.writeIndexOrAlias();
        String writeIndex = getClient().getFirstIndexForAlias(writeAlias);
        if (searchIndex == null) {
            if (getClient().indexExists(searchAlias)) {
                if (Framework.isTestModeSet()) {
                    // in test mode we drop an index that have the target alias name
                    getClient().deleteIndex(searchAlias, TIMEOUT_DELETE_SECOND);
                }
                searchIndex = searchAlias;
            } else {
                // search alias is not created, point to the write index
                getClient().updateAlias(searchAlias, writeIndex);
                searchIndex = writeIndex;
            }
        }
        log.info("Managed index aliases: Alias: {} ->  index: {}, alias: {} ->  index: {}", searchAlias, searchIndex,
                writeAlias, writeIndex);
    }

    /**
     * Update the search index to point to the write index.
     */
    protected void syncSearchAndWriteAlias(OpenSearchIndexConfig conf) {
        if (!conf.manageAlias()) {
            return;
        }
        String searchAlias = conf.getName();
        String searchIndex = getClient().getFirstIndexForAlias(searchAlias);
        String writeAlias = conf.writeIndexOrAlias();
        String writeIndex = getClient().getFirstIndexForAlias(writeAlias);
        if (!writeIndex.equals(searchIndex)) {
            log.warn("Updating search alias {} -> {} (previously {})", searchAlias, writeIndex, searchIndex);
            getClient().updateAlias(searchAlias, writeIndex);
            searchIndex = writeIndex;
        }
        repoNames.put(searchIndex, conf.getRepositoryName());
    }

    protected void initIndex(String indexName, OpenSearchIndexConfig conf, boolean dropIfExists) {
        if (!conf.mustCreate()) {
            return;
        }
        log.info("Initialize index: {} with conf: {}, type: {}", indexName, conf.getName(), conf.getType());
        boolean mappingExists = false;
        boolean indexExists = getClient().indexExists(indexName);
        if (indexExists) {
            // apply any index dynamic settings if added post index creation
            try {
                JsonNode dynamicSettings = MAPPER.readTree(conf.getDynamicSettings());
                if (dynamicSettings != null && !dynamicSettings.isEmpty()) {
                    log.debug("Updating dynamic settings for index: {}", indexName);
                    getClient().updateIndexDynamicSettings(indexName, conf.getDynamicSettings());
                }
            } catch (JacksonException e) {
                log.error("Failed to update dynamic settings for index: {}", indexName, e);
            }
            if (!dropIfExists) {
                log.debug("Index: {} already exists", indexName);
                mappingExists = getClient().mappingExists(indexName, conf.getType());
                if (conf.isDocumentIndex()) {
                    // Check if the index is actually an alias.
                    String realIndexForAlias = getClient().getFirstIndexForAlias(conf.getName());
                    if (realIndexForAlias != null) {
                        repoNames.put(realIndexForAlias, conf.getRepositoryName());
                    }
                }
            } else {
                if (!Framework.isTestModeSet()) {
                    log.warn("Initializing index: {}, type: {} with dropIfExists flag, deleting an existing index",
                            indexName, conf.getType());
                }
                getClient().deleteIndex(indexName, TIMEOUT_DELETE_SECOND);
                indexExists = false;
            }
        }
        if (!indexExists) {
            log.info("Creating index: {}", indexName);
            log.debug("Using settings: {}", conf::getSettings);
            try {
                getClient().createIndex(indexName, conf.getSettings());
            } catch (RepositoryException e) {
                String message = e.getMessage();
                if (message != null && message.contains("resource_already_exists_exception")) {
                    log.warn("Index: {} has been concurrently created", indexName);
                } else {
                    throw e;
                }
            }
        }
        if (!mappingExists) {
            log.info("Creating mapping type: {} on index: {}", indexName, conf.getName());
            log.debug("Using mapping: {}", conf::getMapping);
            getClient().createMapping(indexName, conf.getType(), conf.getMapping());
        }
        // make sure the index is ready before returning
        checkClusterHealth(indexName);
    }

    @Override
    public int getTotalCommandProcessed() {
        return totalCommandProcessed.get();
    }

    @Override
    public boolean isIndexingInProgress() {
        return (runIndexingWorkerCount.get() > 0);
    }

    @Override
    public Future<Boolean> prepareWaitForIndexing() {
        return waiterExecutorService.submit(() -> {
            TaskService asyncTasksService = Framework.getService(TaskService.class);
            boolean completed;
            do {
                completed = asyncTasksService.awaitCompletion(Duration.ofSeconds(30L), INDEXING_TASK_NAME,
                        INDEXING_SCROLLER_TASK_NAME, BATCH_INDEXING_TASK_NAME);

            } while (!completed);
            return true;
        });
    }

    protected void initListenerThreadPool() {
        waiterExecutorService = Executors.newCachedThreadPool(newThreadFactory("waitForOpenSearchIndexing"));
    }

    protected void shutdownListenerThreadPool() {
        try {
            waiterExecutorService.shutdown();
        } finally {
            waiterExecutorService = null;
        }
    }

    // Indexing =============================================================

    private void processBulkDeleteCommands(List<IndexingCommand> cmds) {
        // Can be optimized with a single delete by query
        for (IndexingCommand cmd : cmds) {
            if (cmd.getType() == Type.DELETE) {
                try (Context ignored = deleteTimer.time()) {
                    processDeleteCommand(cmd);
                }
            }
        }
    }

    private void processBulkIndexCommands(List<IndexingCommand> cmds, boolean isCommandReindexing) {
        List<BulkOperation> bulkOperations = new ArrayList<>();
        Set<String> docIds = new HashSet<>(cmds.size());

        // Deferred embeddings indexing approach:
        // We collect all commands requiring embeddings indexing and process them AFTER all document indexing
        // is complete. This differs from the previous approach which processed embeddings immediately when
        // each batch was sent.
        // The deferred approach was chosen for code maintainability and to support byte-size based batching
        // in the embeddings indexer, which requires processing all embeddings for a document together.
        List<IndexingCommand> commandsRequiringEmbeddingsIndexing = new ArrayList<>();
        int bulkSize = 0;
        final int maxBulkSize = getMaxBulkSize();
        for (IndexingCommand cmd : cmds) {
            if (cmd.getType() == Type.DELETE || cmd.getType() == Type.UPDATE_DIRECT_CHILDREN
                    || !docIds.add(cmd.getTargetDocumentId())) {
                // do not submit the same doc 2 times
                continue;
            }
            DocumentModel doc = getTargetDocument(cmd.getRepositoryName(), cmd.getTargetDocumentId());
            try {
                IndexRequest<DocumentDTO> idxRequest = buildEsIndexingRequest(cmd.getRepositoryName(), doc, cmd.getOrder());
                if (idxRequest != null) {
                    DocumentDTO dto = idxRequest.document();
                    bulkSize += dto.size(getClient().getJsonpMapper());
                    bulkOperations.add(BulkOperation.of(b -> b.index(iob -> iob.index(idxRequest.index()) //
                                                                               .id(idxRequest.id())
                                                                               .document(dto))));
                    if (doc.isEmbeddings()) {
                        commandsRequiringEmbeddingsIndexing.add(cmd);
                    }
                }
            } catch (BlobNotFoundException be) {
                log.info("Ignore indexing command in bulk, blob does not exists anymore: {}", cmd);
            } catch (ConcurrentUpdateException e) {
                throw e; // bubble up, usually until AbstractWork catches it and maybe retries
            } catch (DocumentNotFoundException e) {
                log.info("Ignore indexing command in bulk, doc does not exists anymore: {}", cmd);
            } catch (IllegalArgumentException e) {
                log.error("{} Failed to create request for indexing command in build: {}",
                        isCommandReindexing ? OPENSEARCH_REINDEXING_COMMAND_FAILURE_MONITOR_TAG : OPENSEARCH_RUNTIME_INDEXING_FAILURE_MONITOR_TAG, cmd, e);
            }
            if (bulkSize > maxBulkSize) {
                log.warn("Max bulk size reached {}, sending bulk command ", bulkSize);
                BulkRequest bulkRequest = BulkRequest.of(b -> b.operations(bulkOperations));
                sendBulkCommand(bulkRequest, isCommandReindexing);
                bulkOperations.clear();
                bulkSize = 0;
            }
        }
        if (!bulkOperations.isEmpty()) {
            BulkRequest bulkRequest = BulkRequest.of(b -> b.operations(bulkOperations));
            try {
                sendBulkCommand(bulkRequest, isCommandReindexing);
            } catch (RepositoryException e) {
                log.error("{} Bulk request failed.", isCommandReindexing ? OPENSEARCH_REINDEXING_COMMAND_FAILURE_MONITOR_TAG : OPENSEARCH_RUNTIME_INDEXING_FAILURE_MONITOR_TAG);
                throw e;
            }
        }

        // Process embeddings indexing (deferred until after all document batches are complete)
        // Each embedding type uses differential indexing (delete stale + upsert new) with byte-size batching
        commandsRequiringEmbeddingsIndexing.forEach(
                cmd -> embeddingsIndexer.process(cmd, isCommandReindexing, maxBulkSize));
    }

    private int getMaxBulkSize() {
        String value = Framework.getProperty(INDEX_BULK_MAX_SIZE_PROPERTY, String.valueOf(DEFAULT_MAX_BULK_SIZE));
        return Integer.parseInt(value);
    }

    private void sendBulkCommand(BulkRequest bulkRequest, boolean isCommandReindexing) {
        if (bulkRequest.operations().isEmpty()) {
            return;
        }
        BulkResponse response = getClient().bulk(bulkRequest);
        if (log.isDebugEnabled()) {
            log.debug("Process bulk index command response {}", response.toJsonString());
        }
        if (response.errors()) {
            logBulkFailure(response, isCommandReindexing);
        }
    }

    void logBulkFailure(BulkResponse response, boolean isCommandReindexing) {
        boolean isError = false;
        StringBuilder sb = new StringBuilder();
        sb.append("Ignore indexing of some docs more recent versions has already been indexed");
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                if (item.status() == HttpStatus.SC_CONFLICT) {
                    sb.append("\n  ").append(item.error().type());
                } else {
                    isError = true;
                }
            }
        }
        if (isError) {
            log.error("{} {}", isCommandReindexing ? OPENSEARCH_REINDEXING_COMMAND_FAILURE_MONITOR_TAG : OPENSEARCH_RUNTIME_INDEXING_FAILURE_MONITOR_TAG,
                    response.errors() ? "Bulk indexing failed" : "Bulk indexing partially failed");
            Map<String, String> errors = new HashMap<>();
            response.items()
                    .stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> {
                        log.error("Error for index {}, id {}: {}", item.index(), item.id(),
                                item.error().type());
                        String id = item.id();
                        String errorInfo = item.error().type() + ": " + item.error().reason();
                        errors.put(id, errorInfo);
                    });
            throw new BulkIndexingException("Bulk indexing task failed in OpenSearch: " + errors);
        } else {
            log.debug(sb);
        }
    }

    private void refreshIfNeeded(List<IndexingCommand> cmds) {
        for (IndexingCommand cmd : cmds) {
            if (refreshIfNeeded(cmd)) {
                return;
            }
        }
    }

    private boolean refreshIfNeeded(IndexingCommand cmd) {
        if (cmd.isSync()) {
            refresh();
            return true;
        }
        return false;
    }

    @Override
    public void indexNonRecursive(IndexingCommand cmd, boolean isManualReindexing) {
        indexNonRecursive(List.of(cmd), isManualReindexing);
    }

    @Override
    public void indexNonRecursive(List<IndexingCommand> cmds, boolean isManualReindexing) {
        if (!indexInitDone) {
            stackCommands(cmds);
            return;
        }
        log.debug("Process indexing commands: {}", cmds);
        indexNonRecursive0(cmds, isManualReindexing);
    }

    private void indexNonRecursive0(List<IndexingCommand> cmds, boolean isManualReindexing) {
        int nbCommands = cmds.size();
        if (nbCommands == 1) {
            indexNonRecursive0(cmds.get(0), isManualReindexing);
            return;
        }
        processBulkDeleteCommands(cmds);
        try (Context ignored = bulkIndexTimer.time()) {
            processBulkIndexCommands(cmds, isManualReindexing);
        }
        totalCommandProcessed.addAndGet(nbCommands);
        refreshIfNeeded(cmds);
    }

    private void indexNonRecursive0(IndexingCommand cmd, boolean isManualReindexing) {
        Type type = cmd.getType();
        if (type == Type.UPDATE_DIRECT_CHILDREN) {
            // the parent don't need to be indexed
            return;
        }
        if (type == Type.DELETE) {
            try (Context ignored = deleteTimer.time()) {
                processDeleteCommand(cmd);
            }
        } else {
            try (Context ignored = indexTimer.time()) {
                // cross-check document existence
                String repositoryName = cmd.getRepositoryName();
                String targetDocumentId = cmd.getTargetDocumentId();
                CoreSession session = CoreInstance.getCoreSessionSystem(repositoryName);
                if (!session.exists(new IdRef(targetDocumentId))) {
                    throw new DocumentNotFoundException(
                            "Document " + targetDocumentId + " does not exist in repository " + repositoryName);
                }

                // check the document exists in OS
                GetRequest getRequest = GetRequest.of(b -> b.index(getWriteIndexForRepository(repositoryName)) //
                        .id(targetDocumentId));
                GetResponse<DocumentDTO> getResponse = getClient().get(getRequest);

                if (cmd.getType() == Type.UPDATE) {
                    if (getResponse.found() || session.exists(new IdRef(targetDocumentId))) {
                        processIndexCommand(cmd, isManualReindexing);
                    } else {
                        throw new DocumentNotFoundException(
                                "Document " + targetDocumentId + " not found in OpenSearch to update");
                    }
                } else {
                    // Type.INSERT
                    processIndexCommand(cmd, isManualReindexing);
                }
            }
        }
        refreshIfNeeded(cmd);
        totalCommandProcessed.incrementAndGet();
    }

    private void processIndexCommand(IndexingCommand cmd, boolean isCommandReindexing) {
        IndexRequest<DocumentDTO> request;
        DocumentModel doc = getTargetDocument(cmd.getRepositoryName(), cmd.getTargetDocumentId());
        try {
            request = buildEsIndexingRequest(cmd.getRepositoryName(), doc, cmd.getOrder());
        } catch (BlobNotFoundException | DocumentNotFoundException e) {
            request = null;
        } catch (IllegalStateException e) {
            log.error("Fail to create request for indexing command: {}", cmd, e);
            return;
        }
        if (request == null) {
            log.info("Cancel indexing command because target document does not exists anymore: {}", cmd);
            return;
        }
        try {
            var response = getClient().index(request);
            if (log.isDebugEnabled()) {
                log.debug("Process index command {} response {}", cmd.getId(), response.toJsonString());
            }
        } catch (DocumentNotFoundException e) {
            log.info("Ignore indexing command in single mode, doc does not exists anymore: {}", cmd);
            return;
        } catch (ConcurrentUpdateException e) {
            log.info("Ignore indexing of doc {} a more recent version has already been indexed: {}", cmd.getTargetDocumentId(),
                    e.getMessage());
        } catch (RepositoryException e) {
            if (e.getMessage() != null && e.getMessage().contains("Request failed: [illegal_argument_exception] Limit of total fields")) {
                // do something to rescue indexing process
                // increase the limit by 200 fields, max up to double of the default limit to avoid uncontrolled index growth
                int defaultTotalFieldsLimit = Framework.getIntProperty("opensearch.index.mapping.total_fields.limit", 1000);
                int currentTotalFieldsLimit = Integer.parseInt(
                        e.getMessage().replaceAll(".*Limit of total fields \\[(\\d+)\\] has been exceeded.*", "$1").strip());
                if (currentTotalFieldsLimit <= 2 * defaultTotalFieldsLimit) {
                    getClient().updateIndexDynamicSettings(
                            getWriteIndexForRepository(cmd.getRepositoryName()),
                            "{ \"index.mapping.total_fields.limit\": " + (currentTotalFieldsLimit + 200) + " }");
                    return; // it will be reprocessed later
                }
            }

            log.error("{} RequestId: {} .", isCommandReindexing ? OPENSEARCH_REINDEXING_COMMAND_FAILURE_MONITOR_TAG : OPENSEARCH_RUNTIME_INDEXING_FAILURE_MONITOR_TAG,
                    request.id(), e);
            throw e;
        }

        if (doc.isEmbeddings()) {
            embeddingsIndexer.process(cmd, isCommandReindexing, getMaxBulkSize());
        }
    }

    private void processDeleteCommand(IndexingCommand cmd) {
        if (cmd.isRecurse()) {
            processDeleteCommandRecursive(cmd);
        } else {
            processDeleteCommandNonRecursive(cmd);
        }
    }

    private void processDeleteCommandNonRecursive(IndexingCommand cmd) {
        String indexName = getWriteIndexForRepository(cmd.getRepositoryName());
        String docId = cmd.getTargetDocumentId();

        // Resolve the minimal embeddings deletion context and delete the embeddings if any.
        // Some repositories (e.g. vocabularies) have no embeddings index; in that case skip embeddings deletion.
        if (hasEmbeddingsIndex(cmd.getRepositoryName())) {
            String embeddingsIndex = getEmbeddingsWriteIndexForRepository(cmd.getRepositoryName());
            EmbeddingsIndexer.DeletionContext ctx = fetchEmbeddingsDeletionContext(indexName, docId);
            if (ctx != null) {
                embeddingsIndexer.deleteEmbeddingsByParent(embeddingsIndex, ctx);
            }
        }

        // Delete the document itself
        getClient().delete(indexName, docId);
    }


    private void processDeleteCommandRecursive(IndexingCommand cmd) {
        String indexName = getWriteIndexForRepository(cmd.getRepositoryName());
        // we don't want to rely on target document because the document can be
        // already removed
        String docPath = getPathOfDocFromEs(cmd.getRepositoryName(), cmd.getTargetDocumentId());
        if (docPath == null) {
            if (!Framework.isTestModeSet()) {
                log.warn("Trying to delete a non existing doc: {}", cmd);
            }
            // best effort to delete the doc by id
            getClient().delete(indexName, cmd.getTargetDocumentId());
            return;
        }

        // Run the scroll query
        Query filter = QueryBuilders.term()
                                    .field(SYS_PATH + ".children")
                                    .value(FieldValue.of(docPath))
                                    .build()
                                    .toQuery();
        Query query = ConstantScoreQuery.of(b -> b.filter(filter)).toQuery();
        Time keepAlive = Time.of(b -> b.time("1m"));
        SearchRequest request = SearchRequest.of(b -> b.index(indexName) //
                                                       .scroll(keepAlive)
                                                       .source(sb -> {
                                                           sb.fetch(true);
                                                           sb.filter(f -> f.includes(EMBEDDINGS_DELETION_CONTEXT_FIELDS));
                                                           return sb;
                                                       })
                                                       .size(100)
                                                       .query(query));

        // Refresh index before bulk delete
        getClient().refresh(indexName);

        // A cautious pre-check: if the search returns no hits due to (possibly) index not being updated yet
        // the deletion process will be skipped and the document will remain in OS index making it a ghost document.
        // Uses a lightweight non-scroll query to avoid opening a scroll context just for the existence check.
        // fetch(false) only fetches the hit metadata and no document body
        SearchRequest preCheckRequest = SearchRequest.of(
                b -> b.index(indexName).source(sb -> sb.fetch(false)).size(1).query(query));
        int maxRetry = 5;
        try {
            for (int i = 0; i < maxRetry; i++) {
                if (!getClient().search(preCheckRequest).hits().hits().isEmpty()) {
                    break;
                }
                log.debug(
                        "OpenSearchService: Initial search for docId: {} returned no hits for sys_path: {} in OpenSearch index: {}.  Retrying... (attempt {}/{})",
                        cmd.getTargetDocumentId(), docPath, indexName, i + 1, maxRetry);
                if (i < maxRetry - 1) {
                    Thread.sleep(2000L); // ideally few seconds
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                    "OpenSearchService: Initial search interrupted while waiting to retry for docId: {} sys_path: {} in OpenSearch index: {}. Aborting retries.",
                    cmd.getTargetDocumentId(), docPath, indexName);
        }

        boolean hasEmbeddings = hasEmbeddingsIndex(cmd.getRepositoryName());
        String embeddingsIndex = hasEmbeddings ? getEmbeddingsWriteIndexForRepository(cmd.getRepositoryName()) : null;
        List<String> deletedDocumentIds = new ArrayList<>();
        SearchResponse<DocumentDTO> response = getClient().search(request);
        try {
            while (!response.hits().hits().isEmpty()) {
                // Build bulk delete request
                List<BulkOperation> bulkOperations = new ArrayList<>();
                for (Hit<DocumentDTO> hit : response.hits().hits()) {
                    bulkOperations.add(BulkOperation.of(b -> b.delete(dob -> dob.index(hit.index()) //
                                                                                .id(hit.id()))));
                    if (hasEmbeddings) {
                        EmbeddingsIndexer.DeletionContext ctx = EmbeddingsIndexer.DeletionContext.fromDocument(hit.id(),
                                hit.source());
                        if (ctx != null) {
                            embeddingsIndexer.deleteEmbeddingsByParent(embeddingsIndex, ctx);
                        }
                    }
                }
                List<String> idsToDelete = bulkOperations.stream().map(op -> op.delete().id()).toList();
                deletedDocumentIds.addAll(idsToDelete);
                BulkRequest bulkRequest = BulkRequest.of(b -> b.operations(bulkOperations));
                log.debug("Bulk delete request on {} elements", bulkRequest.operations().size());
                // Run bulk delete request
                getClient().bulk(bulkRequest);
                response = runNextScroll(response, keepAlive);
            }
        } finally {
            safelyClearScroll(response.scrollId(), cmd.getTargetDocumentId());
        }

        postDeletionGhostDocumentCheck(indexName, cmd.getTargetDocumentId(), deletedDocumentIds);
    }

    void safelyClearScroll(String scrollId, String docId) {
        if (scrollId != null) {
            try {
                getClient().clearScroll(scrollId);
            } catch (RuntimeException e) {
                log.warn("Failed to clear scroll context for docId: {}", docId, e);
            }
        }
    }

    void postDeletionGhostDocumentCheck(String indexName, String docId, List<String> deletedDocumentIds) {
        // Refresh index post bulk delete
        getClient().refresh(indexName);

        Query postCheckFilter = QueryBuilders.term()
                .field(SYS_ANCESTOR_IDS)
                .value(FieldValue.of(docId))
                .build()
                .toQuery();
        Query postCheckQuery = ConstantScoreQuery.of(b -> b.filter(postCheckFilter)).toQuery();
        SearchRequest postCheckSearchRequest = SearchRequest.of(b -> b.index(indexName)
                .source(sb -> sb.fetch(false))
                .size(100)
                .query(postCheckQuery));

        SearchResponse<DocumentDTO> postCheckSearchResponse = getClient().search(postCheckSearchRequest);
        var responseHitsList = postCheckSearchResponse.hits().hits();
        if (!responseHitsList.isEmpty()) {
            responseHitsList.stream()
                    .map(Hit::id)
                    .filter(id -> !deletedDocumentIds.contains(id)) // avoid documents that were just deleted in the bulk request
                    .forEach(id -> cleanupGhostDocuments(indexName, id));
        }
    }

    private void cleanupGhostDocuments(String indexName, String docId) {
        // This is a safety net to clean up any potential ghost documents that might have been left due to unforeseen issues during the recursive deletion process.
        // It looks for any documents that have the deleted document id in their sys_ancestorIds and deletes them, as they are considered ghost documents without a valid ancestor.
        List<String> ancestorIds = getDocumentSysAncestorIds(indexName, docId);
        if (!ancestorIds.isEmpty() && ancestorIds.contains(docId)) { // confirm it's a ghost document
            log.warn("Cleaning up ghost document with id: {} in index: {}, since its ancestor document id: {} no longer exists",
                    docId, indexName, ancestorIds);
            getClient().delete(indexName, docId);
        }
    }

    private List<String> getDocumentSysAncestorIds(String indexName, String docId) {
        GetRequest request = GetRequest.of(b -> b.index(indexName)
                .id(docId)
                .sourceIncludes(List.of(SYS_ANCESTOR_IDS)));
        GetResponse<DocumentDTO> response = getClient().get(request);
        if (!response.found() || response.source() == null) {
            log.debug("Skip sys_ancestorIds fetching: document {} not found in index {}", docId, indexName);
            return Collections.emptyList();
        }
        Object ancestorIdsObj = response.source().getProperties().get(SYS_ANCESTOR_IDS);
        if (ancestorIdsObj instanceof List<?>) {
            return ((List<?>) ancestorIdsObj).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        } else {
            log.warn("Unexpected type for sys_ancestorIds of document {} in index {}: expected List<String> but got {}",
                    docId, indexName, ancestorIdsObj != null ? ancestorIdsObj.getClass() : "null");
            return Collections.emptyList();
        }
    }

    /**
     * Fetches the minimal set of fields required to delete embeddings for a {@code sys:embeddings} document.
     */
    private EmbeddingsIndexer.DeletionContext fetchEmbeddingsDeletionContext(String indexName, String docId) {
        GetRequest request = GetRequest.of(b -> b.index(indexName)
                                                 .id(docId)
                                                 // Avoid transferring the full document; we only need identifiers.
                                                 .sourceIncludes(EMBEDDINGS_DELETION_CONTEXT_FIELDS));
        GetResponse<DocumentDTO> response = getClient().get(request);
        if (!response.found() || response.source() == null) {
            log.debug("Skip embeddings deletion context fetching: document {} not found in index {}", docId, indexName);
            return null;
        }
        return EmbeddingsIndexer.DeletionContext.fromDocument(docId, response.source());
    }

    SearchResponse<DocumentDTO> runNextScroll(SearchResponse<DocumentDTO> response, Time keepAlive) {
        ScrollRequest request = ScrollRequest.of(b -> b.scrollId(response.scrollId()) //
                                                       .scroll(keepAlive));
        return getClient().searchScroll(request);
    }

    /**
     * Return the sys_path of an ES document or null if not found.
     */
    String getPathOfDocFromEs(String repository, String docId) {
        String indexName = getWriteIndexForRepository(repository);
        GetRequest request = GetRequest.of(b -> b.index(indexName) //
                                                 .id(docId)
                                                 .sourceIncludes(List.of(SYS_PATH)));
        GetResponse<DocumentDTO> ret = getClient().get(request);
        if (ret.source() == null) {
            return null;
        }
        return (String) ret.source().getProperties().get(SYS_PATH);
    }

    /**
     * Return indexing request or null if the doc does not exist anymore.
     *
     * @throws java.lang.IllegalStateException if the command is not attached to a session
     */
    private IndexRequest<DocumentDTO> buildEsIndexingRequest(String repository, DocumentModel doc, long order) {
        if (doc == null) {
            return null;
        }
        String index = getWriteIndexForRepository(repository);
        DocumentDTO dto = new DocumentDTO(doc);
        VersionType versionType;
        Long version;
        if (order > 0) {
            versionType = VersionType.External;
            version = order;
        } else {
            versionType = null;
            version = null;
        }
        return IndexRequest.of(b -> b.index(index) //
                                     .id(doc.getId())
                                     .document(dto)
                                     .versionType(versionType)
                                     .version(version));
    }

    protected DocumentModel getTargetDocument(String repositoryName, String targetDocumentId) {
        return getTargetDocument(repositoryName, new IdRef(targetDocumentId));
    }

    protected DocumentModel getTargetDocument(String repositoryName, DocumentRef targetDocumentRef) {
        CoreSession session = CoreInstance.getCoreSessionSystem(repositoryName);
        if (!session.exists(targetDocumentRef)) {
            // Doc was deleted : no way we can fetch it
            return null;
        }
        return session.getDocument(targetDocumentRef);
    }


    protected String getWriteIndexForRepository(String repository) {
        return getWriteIndexName(getIndexNameForRepository(repository));
    }

    protected String getEmbeddingsWriteIndexForRepository(String repository) {
        return getWriteIndexName(getEmbeddingsIndexNameForRepository(repository));
    }

    protected void stackCommands(List<IndexingCommand> cmds) {
        log.debug("Delaying indexing commands: Waiting for Index to be initialized. {}", cmds);
        stackedCommands.addAll(cmds);
    }

    @Override
    public void runIndexingWorker(List<IndexingCommand> cmds) {
        if (!indexInitDone) {
            stackCommands(cmds);
            return;
        }
        runIndexingWorkerCount.incrementAndGet();
        try {
            dispatchWork(cmds);
        } finally {
            runIndexingWorkerCount.decrementAndGet();
        }
    }

    /**
     * Dispatch jobs between sync and async worker
     */
    protected void dispatchWork(List<IndexingCommand> cmds) {
        Map<String, List<IndexingCommand>> syncCommands = new HashMap<>();
        Map<String, List<IndexingCommand>> asyncCommands = new HashMap<>();
        for (IndexingCommand cmd : cmds) {
            if (cmd.isSync()) {
                List<IndexingCommand> syncCmds = syncCommands.get(cmd.getRepositoryName());
                if (syncCmds == null) {
                    syncCmds = new ArrayList<>();
                }
                syncCmds.add(cmd);
                syncCommands.put(cmd.getRepositoryName(), syncCmds);
            } else {
                List<IndexingCommand> asyncCmds = asyncCommands.get(cmd.getRepositoryName());
                if (asyncCmds == null) {
                    asyncCmds = new ArrayList<>();
                }
                asyncCmds.add(cmd);
                asyncCommands.put(cmd.getRepositoryName(), asyncCmds);
            }
        }
        runIndexingSyncWorker(syncCommands);
        scheduleIndexingAsyncWorker(asyncCommands);
    }

    protected void scheduleIndexingAsyncWorker(Map<String, List<IndexingCommand>> asyncCommands) {
        if (asyncCommands.isEmpty()) {
            return;
        }
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        asyncCommands.forEach((k, v) -> BatchUtils.tryWithSplit(v,
                list -> asyncTasksService.submitTask(new IndexingTask(k, list), true))); // throwOnError=true
    }

    protected void runIndexingSyncWorker(Map<String, List<IndexingCommand>> syncCommands) {
        if (syncCommands.isEmpty()) {
            return;
        }
        TransactionHelper.runWithoutTransaction(
                () -> syncCommands.forEach((k, v) -> new IndexingTaskProcessor().process(new IndexingTask(k, v))));
    }

    @Override
    public void runReindexingWorker(String repositoryName, String nxql, boolean syncAlias) {
        if (nxql == null || nxql.isEmpty()) {
            throw new IllegalArgumentException("Expecting an NXQL query");
        }

        var properties = new HashMap<String, Serializable>();
        properties.put(PROPERTY_SYNC_ALIAS, syncAlias);
        var task = new ScrollerTask.Builder(INDEXING_SCROLLER_TASK_NAME, BATCH_INDEXING_TASK_NAME, nxql,
                repositoryName, SYSTEM_USERNAME).withProperties(properties) //
                                                .withBatchSize(getBatchSize())
                                                .build();
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        asyncTasksService.submitTask(task);
    }

    @Override
    public void reindexRepository(String repositoryName) {
        dropAndInitRepositoryIndex(repositoryName, false);
        runReindexingWorker(repositoryName, "SELECT sys_id FROM SysContent", true);
    }

    // Search ===============================================================

    @Override
    public DocumentModelList query(OpenSearchQueryBuilder queryBuilder) {
        return queryAndAggregate(queryBuilder).getDocuments();
    }

    @SuppressWarnings("resource") // IterableQueryResult closed by OpenSearchResult.getRows().close()
    @Override
    public OpenSearchResult queryAndAggregate(OpenSearchQueryBuilder queryBuilder) {
        SearchResponse<DocumentDTO> response = search(queryBuilder);
        if (queryBuilder.returnsDocuments()) {
            DocumentModelListImpl docs = getDocumentModels(queryBuilder, response);
            return new OpenSearchResult(docs, response);
        } else if (queryBuilder.returnsRows()) {
            IterableQueryResult rows = getRows(queryBuilder, response);
            return new OpenSearchResult(rows, response);
        }
        return new OpenSearchResult(response);
    }

    public OpenSearchResult queryWithTermsAggregation(OpenSearchQueryBuilder queryBuilder, String termsAggregationField,
            String searchTerm, int aggregationLimit) {
        SearchResponse<DocumentDTO> response = searchWithAggregations(queryBuilder, termsAggregationField, searchTerm, aggregationLimit);
        return new OpenSearchResult(response);
    }

    @Override
    public OpenSearchScrollResult scroll(OpenSearchQueryBuilder queryBuilder, long keepAlive) {
        return scroll(queryBuilder, SearchType.DfsQueryThenFetch, keepAlive);
    }

    protected OpenSearchScrollResult scroll(OpenSearchQueryBuilder queryBuilder, SearchType searchType, long keepAlive) {
        SearchResponse<DocumentDTO> response = searchScroll(queryBuilder, searchType, keepAlive);
        return getScrollResults(queryBuilder, response, response.scrollId(), keepAlive);
    }

    @Override
    public OpenSearchScrollResult scroll(OpenSearchScrollResult scrollResult) {
        SearchResponse<DocumentDTO> response = nextScroll(scrollResult.getScrollId(), scrollResult.getKeepAlive());
        return getScrollResults(scrollResult.getQueryBuilder(), response, response.scrollId(),
                scrollResult.getKeepAlive());
    }

    @Override
    public void clearScroll(OpenSearchScrollResult scrollResult) {
        getClient().clearScroll(scrollResult.getScrollId());
    }

    @SuppressWarnings("resource") // IterableQueryResult closed by EsResult.getRows().close()
    protected OpenSearchScrollResult getScrollResults(OpenSearchQueryBuilder queryBuilder, SearchResponse<DocumentDTO> response,
            String scrollId, long keepAlive) {
        if (queryBuilder.returnsDocuments()) {
            DocumentModelListImpl docs = getDocumentModels(queryBuilder, response);
            return new OpenSearchScrollResult(docs, response, queryBuilder, scrollId, keepAlive);
        } else if (queryBuilder.returnsRows()) {
            IterableQueryResult rows = getRows(queryBuilder, response);
            return new OpenSearchScrollResult(rows, response, queryBuilder, scrollId, keepAlive);
        }
        return new OpenSearchScrollResult(response, queryBuilder, scrollId, keepAlive);
    }

    protected DocumentModelListImpl getDocumentModels(OpenSearchQueryBuilder queryBuilder,
            SearchResponse<DocumentDTO> response) {
        DocumentModelListImpl ret;
        long totalSize = Optional.ofNullable(response.hits().total())
                                 .map(TotalHits::value)
                                 .orElse(0L);
        if (!queryBuilder.returnsDocuments() || response.hits().hits().isEmpty()) {
            ret = new DocumentModelListImpl(0);
            ret.setTotalSize(totalSize);
            return ret;
        }
        try (Context stopWatch = fetchTimer.time()) {
            Fetcher fetcher = queryBuilder.getFetcher(response, repoNames);
            ret = fetcher.fetchDocuments();
            logMinDurationFetch(stopWatch.stop(), totalSize);
        }
        ret.setTotalSize(totalSize);
        return ret;
    }

    private void logMinDurationFetch(long duration, long totalSize) {
        if (log.isDebugEnabled() && (duration > LOG_MIN_DURATION_FETCH_NS)) {
            String msg = String.format("Slow fetch duration_ms:\t%.2f\treturning:\t%d documents", duration / 1000000.0,
                    totalSize);
            if (log.isTraceEnabled()) {
                log.trace(msg, new Throwable("Slow fetch document stack trace"));
            } else {
                log.debug(msg);
            }
        }
    }

    private IterableQueryResult getRows(OpenSearchQueryBuilder queryBuilder, SearchResponse<DocumentDTO> response) {
        return new OpenSearchResultSetImpl(response, queryBuilder.getSelectFieldsAndTypes());
    }

    protected SearchResponse<DocumentDTO> search(OpenSearchQueryBuilder query) {
        try (Context ignored = searchTimer.time()) {
            // CIN-12609: hybrid (kNN + chunk FTS) queries MUST NOT use DFS. The hybrid score-ranker /
            // RRF phase-results processor is incompatible with the DFS pre-phase: with DFS the per-
            // sub-query results are not fused, so the same document id is returned once per sub-clause
            // with raw internal scores (e.g. -9.5E9) instead of one fused entry with a small positive
            // RRF score. Reproduced on the 5-shard staging cluster: identical hybrid query yields
            // distinct fused hits under QUERY_THEN_FETCH but un-fused duplicates under
            // DFS_QUERY_THEN_FETCH. (Invisible on a single shard, where DFS is a no-op.) DFS stays the
            // default for ordinary BM25 queries, which benefit from cross-shard term-frequency accuracy.
            SearchType searchType = query.isHybridQuery() ? SearchType.QueryThenFetch
                    : SearchType.DfsQueryThenFetch;
            SearchRequest request = buildSearchRequest(query, searchType);
            return getClient().search(request);
        }
    }

    protected SearchResponse<DocumentDTO> searchWithAggregations(OpenSearchQueryBuilder query, String termsAggregationField,
            String searchTerm, int aggregationLimit) {
        try (Context ignored = searchTimer.time()) {
            SearchRequest request = buildSearchRequestWithTopNAggregation(query, termsAggregationField, searchTerm, aggregationLimit);
            return getClient().search(request);
        }
    }

    protected SearchResponse<DocumentDTO> searchScroll(OpenSearchQueryBuilder query, SearchType searchType, long keepAlive) {
        try (Context ignored = searchTimer.time()) {
            SearchRequest request = buildSearchScrollRequest(query, searchType, keepAlive);
            return getClient().search(request);
        }
    }

    protected SearchResponse<DocumentDTO> nextScroll(String scrollId, long keepAlive) {
        try (Context ignored = scrollTimer.time()) {
            ScrollRequest request = ScrollRequest.of(b -> b.scrollId(scrollId) //
                                                           .scroll(tb -> tb.time(timeOf(keepAlive))));
            return getClient().searchScroll(request);
        }
    }

    protected SearchRequest buildSearchRequest(OpenSearchQueryBuilder query, SearchType searchType) {
        return buildSearchRequest(query, searchType, -1);
    }

    protected SearchRequest buildSearchScrollRequest(OpenSearchQueryBuilder query, SearchType searchType, long keepAlive) {
        SearchRequest request = buildSearchRequest(query, searchType, keepAlive);
        return request.toBuilder().scroll(b -> b.time(timeOf(keepAlive))).build();
    }

    protected SearchRequest buildSearchRequest(OpenSearchQueryBuilder query, SearchType searchType, long keepAlive) {
        // For embedding queries (vector/hybrid/embeddingId), use the embeddings index with denormalized data
        // For regular queries, use the main document index
        boolean isEmbeddingQuery = query.isEmbeddingQuery();
        List<String> indexes = isEmbeddingQuery
                ? getEmbeddingsSearchIndexes(query.getSearchRepositories())
                : getSearchIndexes(query.getSearchRepositories());
        // Log which index type is being used for verification - debug level to reduce production noise
        log.debug("Routing to {} index: indexes={}", isEmbeddingQuery ? "embeddings" : "document", indexes);

        SearchRequest.Builder builder = new SearchRequest.Builder().index(indexes) //
                                                                   .searchType(searchType)
                                                                   .timeout(timeOf(keepAlive));
        query.updateRequest(builder);
        return builder.build();
    }

    protected SearchRequest buildSearchRequestWithTopNAggregation(OpenSearchQueryBuilder nxqlQueryBuilder, String termsAggregationField,
            String searchTerm, int aggregationLimit) {
        List<String> indexes = getSearchIndexes(nxqlQueryBuilder.getSearchRepositories());

        String escapedWildcardPattern = (searchTerm == null ? "" : "*" + WildcardUtils.escapeForOpenSearchWildcard(searchTerm)) + "*";
        WildcardQuery wildcardQuery = QueryBuilders.wildcard()
                                                   .caseInsensitive(true)
                                                   .field(termsAggregationField)
                                                   .wildcard(escapedWildcardPattern)
                                                   .build();

        Query query = nxqlQueryBuilder.makeQuery();
        BoolQuery combinedQuery = QueryBuilders.bool()
                                               .must(query)
                                               .filter(wildcardQuery.toQuery())
                                               .build();

        // Build aggregation
        TermsAggregation.Builder termsAggBuilder = new TermsAggregation.Builder().field(termsAggregationField);

        if (aggregationLimit > 0) {
            termsAggBuilder.size(aggregationLimit);
        }

        TermsAggregation termsAggregation = termsAggBuilder.build();

        SamplerAggregation samplerAggregation = new SamplerAggregation.Builder().shardSize(SAMPLE_SHARD_SIZE).build();
        Aggregation topOccurrencesAggregation = new Aggregation(termsAggregation);
        Aggregation sampleAggregation = new Aggregation.Builder().sampler(samplerAggregation)
                                                                  .aggregations(TERMS_AGGREGATION_NAME, topOccurrencesAggregation)
                                                                  .build();
        var searchRequestBuilder = new SearchRequest.Builder().index(indexes)
                                       .query(combinedQuery.toQuery())
                                       .size(0)
                                       .aggregations(SAMPLE_AGGREGATION_NAME, sampleAggregation);
        return searchRequestBuilder.build();
    }

    protected String timeOf(long keepAlive) {
        return keepAlive > 0 ? keepAlive + "s" : null;
    }

    protected List<String> getSearchIndexes(List<String> searchRepositories) {
        if (searchRepositories.isEmpty()) {
            return new ArrayList<>(indexNames.values());
        } else {
            return searchRepositories.stream().map(this::getIndexNameForRepository).toList();
        }
    }

    /**
     * Returns the list of embeddings indexes to search for the specified repositories.
     * <p>
     * Embeddings indexes contain denormalized embeddings with parent document metadata,
     * allowing for faster queries without expensive parent-child joins. These indexes
     * follow the naming convention: {@code {repositoryName}_embeddings}
     * <p>
     * If no repositories are specified (empty list), returns all configured embeddings
     * indexes across all repositories.
     *
     * @param searchRepositories list of repository names to search, or empty list for all repositories
     * @return list of embeddings index names to query, or empty list if no embeddings indexes are configured
     */
    protected List<String> getEmbeddingsSearchIndexes(List<String> searchRepositories) {
        if (searchRepositories.isEmpty()) {
            return new ArrayList<>(embeddingsIndexNames.values());
        } else {
            return searchRepositories.stream().map(this::getEmbeddingsIndexNameForRepository).toList();
        }
    }

    // misc ====================================================================

    protected int getBatchSize() {
        String value = Framework.getProperty(REINDEX_BUCKET_READ_PROPERTY, DEFAULT_BUCKET_SIZE);
        return Integer.parseInt(value);
    }

    @Override
    public OpenSearchIndexStoreStats getIndexStoreStats() {
        return getClient().getIndexStoreStats(indexNames.values());
    }

    // package-private helper for same-package parser loading/indexing access to parser contributions
    Map<String, EmbeddingsParserDescriptor> getEmbeddingsParserDescriptors() { // NOSONAR
        return this.<MapRegistry> getExtensionPointRegistry(EP_EMBEDDINGS_PARSER).getContributions();
    }

}
