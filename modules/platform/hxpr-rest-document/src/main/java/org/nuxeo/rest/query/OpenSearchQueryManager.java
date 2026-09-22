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
package org.nuxeo.rest.query;

import static java.lang.Long.max;
import static org.nuxeo.ecm.core.api.DocumentConstants.CIN_SOURCE_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.FULLTEXT_EXTRACTOR_TASK_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY_ON_PREM;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_BLOB_REF;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_LOCATION;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TEXT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.opensearch.OpenSearchServiceImpl.SAMPLE_AGGREGATION_NAME;
import static org.nuxeo.opensearch.OpenSearchServiceImpl.TERMS_AGGREGATION_NAME;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.common.utils.ExceptionUtils;
import org.nuxeo.ecm.core.api.EmbeddingChunkTextFetcher;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.query.nxql.SortInfo;
import org.nuxeo.ecm.core.model.LocationModel;
import org.nuxeo.ecm.core.query.sql.HXQL;
import org.nuxeo.opensearch.api.OpenSearchResult;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.query.OpenSearchQueryBuilder;
import org.nuxeo.opensearch.query.WildcardUtils;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.embedding.EmbeddingRestDTO;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryResultDTO;
import org.nuxeo.rest.query.io.TermsAggregationQueryResultDTO;
import org.nuxeo.rest.query.io.SearchAfterToken;
import org.nuxeo.rest.query.spec.NamedQueryRegistry;
import org.nuxeo.rest.query.spec.QuerySpecification;
import com.hyland.openarch.runtime.api.Framework;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import com.hyland.openarch.runtime.metrics.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.hyland.hxpr.async.tasks.TaskService;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.SharedMetricRegistries;

/**
 * {@link QueryManager} implementation targeting the OpenSearch API.
 */
@Component
@EnableConfigurationProperties({ QueryConfig.class })
public class OpenSearchQueryManager extends AbstractQueryManager {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchQueryManager.class);

    private final ObjectProvider<EmbeddingChunkTextFetcher> chunkTextFetcher;

    private final Counter omittedOnPremChunksCounter = SharedMetricRegistries.getOrCreate(MetricsService.class.getName())
                                                                              .counter(MetricName.build("nuxeo.opensearch.embedding.omitted-onprem-chunks"));

    public OpenSearchQueryManager(QueryConfig queryConfig, NamedQueryRegistry namedQueryRegistry,
            DocumentMapper translator, ObjectProvider<EmbeddingChunkTextFetcher> chunkTextFetcher) {
        super(queryConfig, namedQueryRegistry, translator);
        this.chunkTextFetcher = chunkTextFetcher;
    }

    @Override
    protected DocumentQueryResultDTO executeAdvancedQuery(DocumentAdvancedQueryDTO query) {
        QuerySpecification spec = initQuerySpecification(query);
        boolean usedSearchAfter = StringUtils.isNotBlank(spec.searchAfter());
        log.debug("Perform OpenSearch query '{}' with limit={}, offset={}, searchAfter={}", spec.query(), spec.limit(), spec.offset(), usedSearchAfter);

        // build and execute the ES query
        DocumentQueryResultDTO result;
        String repositoryId = findRepositoryId(query.repositoryId());
        CoreSession session = CoreInstance.getCoreSession(repositoryId);
        OpenSearchQueryBuilder nxQuery = new OpenSearchQueryBuilder(session).hxql(spec.query())
                                                                       .parameters(spec.parameters())
                                                            .offset((int) spec.offset())
                                                            .trackTotalCountUpTo(spec.trackTotalCountUpTo())
                                                            .trackTotalCount(spec.trackTotalCount());

        // handle search_after for efficient pagination
        if (usedSearchAfter) {
            try {
                SearchAfterToken token = SearchAfterToken.decode(spec.searchAfter());
                nxQuery.searchAfter(token.sortValues());
                log.debug("Using search_after pagination with sort values {} (fields: {})", token.sortValues(), token.sortFields());
            } catch (NuxeoException e) {
                log.warn("Invalid search_after token, falling back to offset pagination: {}", e.getMessage());
            }
        }

        int limit = (int) spec.limit();
        //including limit = 0 and above
        if (limit >= 0) {
            nxQuery.limit(limit);
        }
        if (repositoryId == null || "*".equals(repositoryId)) {
            nxQuery.searchOnAllRepositories();
        }
        OpenSearchResult ret = Framework.getService(OpenSearchService.class).queryAndAggregate(nxQuery);
        DocumentModelList docs = ret.getDocuments();
        long totalSize = 0;
        long docCount = 0;
        if (docs != null) {
            totalSize = docs.totalSize();
            docCount = docs.size();
        }
        boolean totalCountTruncated = ret.isTotalCountTruncated();

        // generate nextSearchAfter token if results are not empty and if it is not the last page
        // (docCount == actual limit used by OpenSearch query)
        String nextSearchAfter = null;
        if (docs != null && !docs.isEmpty() && ret.getOpenSearchResponse() != null && docCount == nxQuery.getLimit()) {
            nextSearchAfter = extractNextSearchAfter(ret, spec);
        }

        result = new DocumentQueryResultDTO(spec, translate(docs), totalSize, totalCountTruncated, nextSearchAfter);
        log.debug("Performed OpenSearch query '{}' with limit={}, offset={}: {} hits ({} total hits, doc ids {})",
                spec.query(), spec.limit(), spec.offset(), result.count(), result.totalCount(),
                result.documents().stream().map(DocumentDTO::getId).toList());
        // using the number of documents instead of count verify if there is an inconsistency
        if (ret.isInconsistentResult()) {
            log.warn("Inconsistent OpenSearch result for query '{}' with limit={}, offset={}: {} document(s) (count={}) out of {} total document(s)",
                    spec.query(), nxQuery.getLimit(), spec.offset(), result.documents().size(), result.count(), result.totalCount());
        }

        // Fire audit event for document query
        if (docs != null) {
            String queryId = UUID.randomUUID().toString();
            docs.forEach(doc -> fireDocumentQueriedEvent(session, doc, queryId));
        }
        return result;
    }

    @Override
    protected EmbeddingQueryResultDTO executeEmbeddingQuery(String repoId, String docId, String embeddingId) {
        log.debug("Look up embedding with id '{}' for document '{}' in OpenSearch", embeddingId, docId);

        String nxql = "SELECT * FROM %s WHERE %s = %s".formatted(SYS_CONTENT, SYS_ID, HXQL.escapeString(docId));

        DocumentQueryDTO documentQuery = new DocumentQueryDTO(repoId, nxql, 1L, 0, List.of());
        QuerySpecification spec = initQuerySpecification(documentQuery);

        String repositoryId = findRepositoryId(repoId);
        CoreSession session = CoreInstance.getCoreSession(repositoryId);
        OpenSearchQueryBuilder nxQuery = new OpenSearchQueryBuilder(session).embeddingIdQuery(embeddingId, nxql).offset(0).limit(1);

        return getEmbeddingQueryResultDTO(session, repositoryId, spec, nxQuery, null);
    }

    @Override
    protected EmbeddingQueryResultDTO executeEmbeddingQuery(EmbeddingQueryDTO query) {
        log.debug("Perform OpenSearch embedding query '{}' with limit={}, offset={}", query.query(), query.limit(),
                query.offset());

        DocumentQueryDTO documentQuery = new DocumentQueryDTO(query.repositoryId(), query.query(), query.syntax(),
                query.limit(), query.offset(), null, Boolean.TRUE, null, List.of());
        QuerySpecification spec = initQuerySpecification(documentQuery);

        String repositoryId = findRepositoryId(query.repositoryId());
        CoreSession session = CoreInstance.getCoreSession(repositoryId);
        // the assembled query, not the request's own, as the request may have expressed it as an expression
        OpenSearchQueryBuilder nxQuery = new OpenSearchQueryBuilder(session)
                                                            .embeddingQuery(query.embeddingType(), query.vector(),
                                                                    query.chunkFTS(), spec.query())
                                                            .offset((int) query.offset())
                                                            .trackTotalCountUpTo(query.trackTotalCountUpTo())
                                                            .trackTotalCount(query.trackTotalCount());
        int limit = (int) spec.limit();
        // including limit = 0 and above
        if (limit >= 0) {
            nxQuery.limit(limit);
        }
        return getEmbeddingQueryResultDTO(session, repositoryId, spec, nxQuery, query.vector());
    }

    private EmbeddingQueryResultDTO getEmbeddingQueryResultDTO(CoreSession session, String repositoryId,
            QuerySpecification spec, OpenSearchQueryBuilder nxQuery, List<Double> vector) {
        if (repositoryId == null || "*".equals(repositoryId)) {
            nxQuery.searchOnAllRepositories();
        }
        OpenSearchResult ret = Framework.getService(OpenSearchService.class).queryAndAggregate(nxQuery);
        HitsMetadata<org.nuxeo.opensearch.io.DocumentDTO> hits = ret.getOpenSearchResponse().hits();
        Long totalSize = Optional.ofNullable(hits).map(HitsMetadata::total).map(TotalHits::value).orElse(null);
        boolean totalCountTruncated = ret.isTotalCountTruncated();

        List<EmbeddingRestDTO> embeddings = getEmbeddingRestDTOs(hits, session);

        EmbeddingQueryResultDTO result = new EmbeddingQueryResultDTO(spec, vector, embeddings, totalSize,
                totalCountTruncated);
        log.debug("Performed OpenSearch query '{}' with limit={}, offset={}: {} hits ({} total hits)", spec.query(),
                spec.limit(), spec.offset(), result.count(), result.totalCount());

        return result;
    }

    List<EmbeddingRestDTO> getEmbeddingRestDTOs(HitsMetadata<org.nuxeo.opensearch.io.DocumentDTO> hits,
            CoreSession session) {
        Set<String> onPremIds = new HashSet<>();
        Map<String, List<String>> onPremIdsBySource = new HashMap<>();
        List<EmbeddingRestDTO> pending = new ArrayList<>(hits.hits().size());
        for (var hit : hits.hits()) {
            EmbeddingRestDTO dto = embeddingFromHit(hit);
            pending.add(dto);
            Map<String, Object> props = hit.source().getProperties();
            if (SYSEMB_RESIDENCY_ON_PREM.equals(props.get(SYSEMB_RESIDENCY))) {
                onPremIds.add(dto.id());
                String sourceId = (String) props.get(CIN_SOURCE_ID);
                if (sourceId != null) {
                    onPremIdsBySource.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(dto.id());
                }
            }
        }
        Map<String, String> onPremText = fetchOnPremChunkText(onPremIdsBySource);

        Set<String> docIds = new HashSet<>();
        List<String> omittedIds = new ArrayList<>();
        List<EmbeddingRestDTO> embeddings = new ArrayList<>(pending.size());
        for (EmbeddingRestDTO dto : pending) {
            if (onPremIds.contains(dto.id())) {
                String text = onPremText.get(dto.id());
                if (text == null) {
                    omittedIds.add(dto.id());
                    continue;
                }
                dto = new EmbeddingRestDTO(dto.id(), dto.docId(), dto.type(), dto.vector(), text, dto.blobref(),
                        dto.location(), dto.score());
            }
            docIds.add(dto.docId());
            embeddings.add(dto);
        }
        if (!omittedIds.isEmpty()) {
            omittedOnPremChunksCounter.inc(omittedIds.size());
            log.warn("Missing onPrem chunk text: {} of {} onPrem id(s) omitted",
                    omittedIds.size(), onPremIds.size());
            log.debug("Omitted onPrem chunk ids: {}", omittedIds);
        }

        if (!docIds.isEmpty()) {
            String queryId = UUID.randomUUID().toString();
            DocumentModelList docs = session.getDocuments(docIds.stream().map(IdRef::new).toArray(IdRef[]::new));
            docs.forEach(doc -> fireDocumentQueriedEvent(session, doc, queryId));
        }
        return embeddings;
    }

    private Map<String, String> fetchOnPremChunkText(Map<String, List<String>> onPremIdsBySource) {
        if (onPremIdsBySource.isEmpty()) {
            return Map.of();
        }
        EmbeddingChunkTextFetcher fetcher = chunkTextFetcher == null ? null : chunkTextFetcher.getIfAvailable();
        if (fetcher == null) {
            return Map.of();
        }
        Map<String, String> merged = new HashMap<>();
        for (var entry : onPremIdsBySource.entrySet()) {
            merged.putAll(fetcher.fetchTexts(entry.getKey(), entry.getValue()));
        }
        return merged;
    }

    @Override
    public TermsAggregationQueryResultDTO executeTermsAggregationQuery(DocumentQueryDTO query, String termsAggregationField,
            String searchTerm, int aggregationLimit) {
        QuerySpecification spec = initQuerySpecification(query);
        log.debug("Perform OpenSearch terms aggregation query '{}' with field={}, pattern={}, topN={}",
                spec.query(), termsAggregationField, searchTerm, aggregationLimit);

        // build and execute the ES query
        String repositoryId = findRepositoryId(query.repositoryId());
        CoreSession session = CoreInstance.getCoreSession(repositoryId);
        OpenSearchQueryBuilder nxQuery = new OpenSearchQueryBuilder(session).hxql(spec.query())
                                                                       .parameters(spec.parameters())
                                                            .offset((int) spec.offset())
                                                            .onlyOpenSearchResponse();

        int limit = (int) spec.limit();
        if (limit != 0) {
            nxQuery.limit(limit);
        }

        if (repositoryId == null || "*".equals(repositoryId)) {
            nxQuery.searchOnAllRepositories();
        }

        aggregationLimit = (int) getLimit(aggregationLimit);

        OpenSearchResult ret = Framework.getService(OpenSearchService.class)
                                        .queryWithTermsAggregation(nxQuery, termsAggregationField, searchTerm, aggregationLimit);

        DocumentModelList docs = ret.getDocuments();
        long totalSize = 0;
        if (docs != null) {
            totalSize = docs.totalSize();
        }

        boolean totalCountTruncated = ret.isTotalCountTruncated();
        List<StringTermsBucket> buckets = new ArrayList<>();
        if (ret.getOpenSearchResponse().aggregations() != null
                && ret.getOpenSearchResponse().aggregations().containsKey(SAMPLE_AGGREGATION_NAME)) {
            Aggregate aggregate = ret.getOpenSearchResponse().aggregations().get(SAMPLE_AGGREGATION_NAME);
            buckets = aggregate.sampler()
                               .aggregations()
                               .get(TERMS_AGGREGATION_NAME)
                               .sterms()
                               .buckets()
                               .array()
                               .stream()
                               .filter(bucket -> {
                                   // we post-filter buckets with regex pattern because case-insensitive
                                   // wildcard matching is not supported by OpenSearch for aggregations
                                   if(bucket.key() == null || searchTerm == null) {
                                       return true;
                                   }
                                   String escapedSearchTerm = "*" + WildcardUtils.escapeForOpenSearchWildcard(searchTerm) + "*";
                                   Pattern wilcardPattern = WildcardUtils.toRegex(escapedSearchTerm);
                                   return wilcardPattern.matcher(bucket.key()).matches();
                               })
                               .toList();
        }

        TermsAggregationQueryResultDTO result = new TermsAggregationQueryResultDTO(spec, translate(docs), totalSize, totalCountTruncated,
                buckets);

        if (log.isDebugEnabled()) {
            log.debug("Performed OpenSearch terms aggregation query with field={}, pattern={}: {} hits, {} aggregations buckets",
                    termsAggregationField, searchTerm, docs != null ? docs.size() : 0,
                    result.aggregationsBuckets() != null ? result.aggregationsBuckets().size() : 0);
        }

        return result;
    }

    /**
     * Extracts sort values from the OpenSearch result and creates a search_after token.
     *
     * @param result the OpenSearch query result
     * @param spec the query specification
     * @return encoded search_after token, or null if not applicable
     */
    protected String extractNextSearchAfter(OpenSearchResult result, QuerySpecification spec) {
        try {
            // get sort values from the last hit
            List<Object> sortValues = result.getLastSortValues();
            if (sortValues == null || sortValues.isEmpty()) {
                return null;
            }

            // extract sort field names from the query specification
            List<SortInfo> sortInfos = DocumentQueryDTO.getSortInfos(spec.sort());
            List<String> sortFields = new ArrayList<>();
            for (SortInfo sortInfo : sortInfos) {
                sortFields.add(sortInfo.sortColumn());
            }

            // add the missing default sort field
            if (sortValues.size() > sortFields.size()) {
                OpenSearchQueryBuilder.addDefaultSortField(sortFields);
            }

            if (sortValues.size() != sortFields.size()) {
                log.warn("Mismatch between sort values ({}) and sort fields ({}), cannot generate search_after token",
                        sortValues, sortFields);
                return null;
            }

            SearchAfterToken token = SearchAfterToken.of(sortValues, sortFields);
            return token.encode();
        } catch (NuxeoException e) {
            log.warn("Failed to extract search_after token: {}", e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    EmbeddingRestDTO embeddingFromHit(Hit<org.nuxeo.opensearch.io.DocumentDTO> hit) {
        Map<String, Object> properties = hit.source().getProperties();
        return new EmbeddingRestDTO(hit.id(),
                (String) properties.get(SYS_ID),
                (String) properties.get(EMBEDDING_PROPERTY_TYPE),
                (List<Double>) properties.get(EMBEDDING_PROPERTY_VECTOR),
                (String) properties.get(EMBEDDING_PROPERTY_TEXT),
                (String) properties.get(EMBEDDING_PROPERTY_BLOB_REF),
                LocationModel.from(properties.get(EMBEDDING_PROPERTY_LOCATION)),
                hit.score());
    }

    private static String findRepositoryId(String repositoryId) {
        if (StringUtils.isBlank(repositoryId)) {
            repositoryId = Framework.getService(RepositoryManager.class).getDefaultRepositoryName();
        }
        return repositoryId;
    }

    public boolean waitForIndexing(String repositoryId, int timeout, boolean refresh) {
        long start = System.currentTimeMillis();
        OpenSearchService oss = Framework.getService(OpenSearchService.class);
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        try {
            if (!asyncTasksService.awaitCompletion(Duration.ofSeconds(timeout),
                    FULLTEXT_EXTRACTOR_TASK_NAME)) {
                throw new TimeoutException("Timeout on wait for fulltext extractor");
            }
            oss.prepareWaitForIndexing().get(computeRemainingTime(start, timeout), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        } catch (TimeoutException | ExecutionException e) {
            if (ExceptionUtils.hasInterruptedCause(e)) {
                // reset interrupted status
                Thread.currentThread().interrupt();
            }
            return false;
        }
        if (refresh) {
            oss.refreshRepositoryIndex(repositoryId);
            oss.refreshRepositoryEmbeddingsIndex(repositoryId);
        }
        return true;
    }

    protected long computeRemainingTime(long start, int timeout) {
        long elapsed = System.currentTimeMillis() - start;
        // at least one second
        return max(timeout - TimeUnit.MILLISECONDS.toSeconds(elapsed), 1);
    }

}
