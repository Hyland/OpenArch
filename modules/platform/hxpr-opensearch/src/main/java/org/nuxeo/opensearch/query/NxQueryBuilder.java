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
package org.nuxeo.opensearch.query;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_BLOB_REF;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_DOCUMENT_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_LOCATION;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TEXT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.HYBRID_PIPELINE;
import static org.nuxeo.opensearch.OpenSearchConstants.OPENSEARCH_SCORE_FIELD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.query.nxql.SortInfo;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.opensearch.OpenSearchConstants;
import org.nuxeo.opensearch.api.OpenSearchResult;
import org.nuxeo.opensearch.fetcher.Fetcher;
import org.nuxeo.opensearch.fetcher.VcsFetcher;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.mapping.FieldType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TrackHits;
import org.opensearch.client.opensearch.core.search.TrackHits.Builder;

/**
 * OpenSearch query builder for the Nuxeo ES api.
 *
 * @since 5.9.5
 */
public class NxQueryBuilder {

    private static final Logger log = LogManager.getLogger(NxQueryBuilder.class);

    private static final int DEFAULT_LIMIT = 10;

    private final CoreSession session;

    private final List<SortInfo> sortInfos = new ArrayList<>();

    private final List<String> repositories = new ArrayList<>();

    private final NxqlQueryConverter nxqlQueryConverter;

    private final SchemaManager schemaManager;

    private int limit = DEFAULT_LIMIT;

    private int offset = 0;

    private String nxql;

    private Query osQuery;

    private boolean searchOnAllRepo = false;

    private String[] selectFields = { OpenSearchConstants.ID_FIELD };

    private Map<String, Type> selectFieldsAndTypes;

    private boolean returnsDocuments = true;

    private boolean osOnly = false;

    private HitDocConsumer hitDocConsumer;

    protected boolean useUnrestrictedSession;

    private boolean trackTotalCount = true;

    private Integer trackTotalCountUpTo;

    private String embeddingType;

    private List<Double> embeddingVector;

    private String chunkFtsQuery;

    private String embeddingId;

    private List<Object> searchAfter;

    public NxQueryBuilder(CoreSession coreSession) {
        session = coreSession;
        repositories.add(session.getRepositoryName());
        nxqlQueryConverter = new NxqlQueryConverter(session.getSchemaManager());
        schemaManager = coreSession.getSchemaManager();
    }

    /** Used by tests. */
    protected NxQueryBuilder(CoreSession coreSession, String repositoryName, NxqlQueryConverter nxqlQueryConverter,
            SchemaManager schemaManager) {
        session = coreSession;
        repositories.add(repositoryName);
        this.nxqlQueryConverter = nxqlQueryConverter;
        this.schemaManager = schemaManager;
    }

    /**
     * No more than that many documents will be returned. Default to {DEFAULT_LIMIT}. Since Nuxeo 8.4 and ES 2.x, we can
     * not give -1 to this method as the default configuration on ES allows to have a search window of 10000 documents
     * at maximum. This settings could be changed on ES by changing {index.max_result_window}, but it is preferable to
     * use the scan &amp; scroll API.
     */
    public NxQueryBuilder limit(int limit) {
        // For compatibility only, deprecated since 8.4
        if (limit < 0) {
            limit = Integer.MAX_VALUE;
        }
        this.limit = limit;
        return this;
    }

    /**
     * Says to skip that many documents before beginning to return documents. If both offset and limit appear, then
     * offset documents are skipped before starting to count the limit documents that are returned.
     */
    public NxQueryBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    public NxQueryBuilder addSort(SortInfo sortInfo) {
        sortInfos.add(sortInfo);
        return this;
    }

    public NxQueryBuilder addSort(SortInfo[] sortInfos) {
        if (sortInfos != null && sortInfos.length > 0) {
            Collections.addAll(this.sortInfos, sortInfos);
        }
        return this;
    }

    /**
     * Build the query from a NXQL string. You should either use nxql, either esQuery, not both.
     */
    public NxQueryBuilder nxql(String nxql) {
        this.nxql = nxql;
        osQuery = null;
        return this;
    }

    /**
     * If search results are found, use this SearchHit and DocumentModel consumer on each hit.
     *
     * @since 10.2
     */
    public NxQueryBuilder hitDocConsumer(HitDocConsumer consumer) {
        hitDocConsumer = consumer;
        return this;
    }

    /**
     * @since 11.1
     */
    public NxQueryBuilder useUnrestrictedSession(boolean useUnrestrictedSession) {
        this.useUnrestrictedSession = useUnrestrictedSession;
        return this;
    }

    /**
     * If you need to track the total number of hits. You can improve query times by not setting this option (default
     * value is true)
     */
    public NxQueryBuilder trackTotalCount(boolean trackTotalCount) {
        this.trackTotalCount = trackTotalCount;
        return this;
    }

    /**
     * Set the max value for totalCount
     */
    public NxQueryBuilder trackTotalCountUpTo(Integer trackTotalCountUpTo) {
        this.trackTotalCountUpTo = trackTotalCountUpTo;
        return this;
    }

    /**
     * Set the search_after values for efficient pagination. When using search_after, the offset parameter is ignored.
     * The values should be the sort values from the last document of the previous page.
     *
     * @param searchAfter list of sort values from the last document
     * @return this builder
     */
    public NxQueryBuilder searchAfter(List<Object> searchAfter) {
        this.searchAfter = searchAfter;
        return this;
    }

    /**
     * Don't return document model list or rows, only the original OpenSearch response is accessible from
     * {@link OpenSearchResult#getOpenSearchResponse()}
     *
     * @since 7.3
     */
    public NxQueryBuilder onlyOpenSearchResponse() {
        osOnly = true;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public List<SortInfo> getSortInfos() {
        return sortInfos;
    }

    public String getNxql() {
        return nxql;
    }

    public CoreSession getSession() {
        return session;
    }

    /**
     * Get the OpenSearch queryBuilder. Note that it returns only the query part without order or limits, use the
     * udpateRequest to get the full request.
     */
    public Query makeQuery() {
        if (osQuery == null && nxql != null) {
            osQuery = nxqlQueryConverter.toESQueryBuilder(nxql, session).build().toQuery();
            // handle the built-in order by clause
            if (nxql.toLowerCase().contains("order by")) {
                List<SortInfo> builtInSortInfos = nxqlQueryConverter.getSortInfo(nxql);
                sortInfos.addAll(builtInSortInfos);
            }
            if (nxqlHasSelectClause(nxql)) {
                selectFieldsAndTypes = nxqlQueryConverter.getSelectClauseFields(nxql);
                Set<String> keySet = selectFieldsAndTypes.keySet();
                selectFields = keySet.toArray(new String[0]);
                returnsDocuments = false;
            }
            if (isEmbeddingQuery()) {
                log.debug("Embedding Query - Using denormalized embeddings index (NO has_parent join). " +
                                "Details: vector={}, embeddingId={}, embeddingType={}, chunkFtsQuery={}",
                        embeddingVector != null ? "present(" + embeddingVector.size() + " dims)" : "null",
                        embeddingId,
                        embeddingType,
                        chunkFtsQuery);

                Query docFilterQuery = osQuery;
                if (embeddingVector != null) {
                    if (embeddingType != null) {
                        Query embeddingTypeQuery = QueryBuilders.wildcard()
                                                                .field(EMBEDDING_PROPERTY_TYPE)
                                                                .wildcard(embeddingType)
                                                                .build()
                                                                .toQuery();
                        docFilterQuery = QueryBuilders.bool().filter(embeddingTypeQuery, docFilterQuery).build().toQuery();
                    }

                    int k = getOffset() + getLimit();
                    if (trackTotalCount && trackTotalCountUpTo != null && trackTotalCountUpTo > 0) {
                        k = Math.max(k, trackTotalCountUpTo);
                    }
                    Query knnQuery = QueryBuilders.knn()
                                                  .field(EMBEDDING_PROPERTY_VECTOR)
                                                  .k(k)
                                                  .filter(docFilterQuery)
                                                  .vector(embeddingVector.stream().map(Double::floatValue).toList())
                                                  .build()
                                                  .toQuery();

                    if (isHybridQuery()) {
                        log.debug("Building hybrid query (knn + full-text) with k={}", k);
                        Query ftsQuery = QueryBuilders.bool()
                                                      .must(QueryBuilders.match()
                                                                         .field(EMBEDDING_PROPERTY_TEXT)
                                                                         .query(FieldValue.of(chunkFtsQuery))
                                                                         .build()
                                                                         .toQuery())
                                                      .filter(docFilterQuery)
                                                      .build()
                                                      .toQuery();
                        osQuery = QueryBuilders.hybrid()
                                               .paginationDepth(k)
                                               .queries(knnQuery, ftsQuery)
                                               .build()
                                               .toQuery();
                    } else {
                        log.debug("Building vector-only query (knn) with k={}", k);
                        osQuery = knnQuery;
                    }
                } else {
                    log.debug("Building embedding-id query for embeddingId={}", embeddingId);
                    Query embeddingIdQuery = QueryBuilders.match()
                                                          .field(EMBEDDING_PROPERTY_ID)
                                                          .query(FieldValue.of(embeddingId))
                                                          .build()
                                                          .toQuery();
                    osQuery = QueryBuilders.bool().filter(embeddingIdQuery, docFilterQuery).build().toQuery();
                }

                selectFields = ArrayUtils.addAll(selectFields, EMBEDDING_PROPERTY_ID, EMBEDDING_PROPERTY_DOCUMENT_ID,
                        EMBEDDING_PROPERTY_TYPE, EMBEDDING_PROPERTY_VECTOR, EMBEDDING_PROPERTY_TEXT,
                        EMBEDDING_PROPERTY_LOCATION, EMBEDDING_PROPERTY_BLOB_REF, SYS_ID);
            }
        }
        return osQuery;
    }

    protected boolean nxqlHasSelectClause(String nxql) {
        String lowerNxql = nxql.toLowerCase();
        return lowerNxql.startsWith("select") && !lowerNxql.startsWith("select * from");
    }

    public List<SortOptions> getSortBuilders() {
        if (sortInfos.isEmpty()) {
            return List.of();
        }
        List<SortOptions> ret = new ArrayList<>();
        for (SortInfo sortInfo : sortInfos) {
            String name = sortInfo.sortColumn();
            SortOrder order = sortInfo.sortAscending() ? SortOrder.Asc : SortOrder.Desc;
            FieldType fieldType = guessFieldType(name);
            SortOptions sortOptions = SortOptions.of(
                    s -> s.field(f -> f.field(name).order(order).unmappedType(fieldType)));
            ret.add(sortOptions);
        }
        return ret;
    }

    protected FieldType guessFieldType(String field) {
        String fieldType;
        if (OPENSEARCH_SCORE_FIELD.equals(field)) {
            // this special field should not have an unmappedType
            return null;
        }
        try {
            fieldType = schemaManager.getField(field).getType().getName();
        } catch (NullPointerException e) {
            // probably an internal field without schema
            fieldType = "keyword";
        }
        switch (fieldType) {
            case "integer":
            case "long":
            case "boolean":
            case "date":
                break;
            default:
                fieldType = "keyword";
        }
        return FieldType._DESERIALIZER.parse(fieldType);
    }

    /**
     * Ensures that sortBuilders has a unique tiebreaker (_id) for search_after pagination. If the list is empty, _id
     * is used as the sole sort field. Otherwise, if _id or sys_id is not already in the sort list, _id is appended as
     * the last sort field.
     *
     * @param sorts the original list of sort options
     * @return a new list with _id tiebreaker if needed
     */
    protected List<SortOptions> ensureTiebreakerSort(List<SortOptions> sorts) {
        // _id is an OpenSearch meta-field, always present, no unmappedType needed
        SortOptions idSort = SortOptions.of(
                s -> s.field(f -> f.field(OpenSearchConstants.ID_FIELD).order(SortOrder.Asc)));

        if (sorts.isEmpty()) {
            return List.of(idSort);
        }

        boolean hasIdField = sorts.stream().anyMatch(sort -> {
            if (sort.isField()) {
                String fieldName = sort.field().field();
                return SYS_ID.equals(fieldName) || OpenSearchConstants.ID_FIELD.equals(fieldName);
            }
            return false;
        });

        if (hasIdField) {
            return sorts;
        }

        List<SortOptions> newSorts = new ArrayList<>(sorts);
        newSorts.add(idSort);
        return newSorts;
    }

    /*
     * Add the default sort field _id (either default or tiebreaker) to the list.
     * The field was added from {@link NxQueryBuilder#ensureTiebreakerSort}
     */
    public static void addDefaultSortField(List<String> sortFields) {
        sortFields.add(OpenSearchConstants.ID_FIELD);
    }

    public void updateRequest(SearchRequest.Builder request) {
        // Set limits: use search_after for efficient pagination when provided
        if (searchAfter != null && !searchAfter.isEmpty()) {
            // when using search_after, offset is ignored
            request.size(getLimit());
            request.searchAfter(searchAfter.stream().map(FieldValueConverter::toFieldValue).toList());
        } else {
            // offset pagination
            request.from(getOffset()).size(getLimit());
        }
        // build query with security checks
        Query query = makeQuery();
        request.query(query);
        if (query.isHybrid()) {
            request.pipeline(HYBRID_PIPELINE);
        }
        // add sort (with tiebreaker to enable search_after, but not for embedding queries which rely on KNN scoring)
        List<SortOptions> sorts = isEmbeddingQuery() ? getSortBuilders() : ensureTiebreakerSort(getSortBuilders());
        for (SortOptions sortBuilder : sorts) {
            request.sort(sortBuilder);
        }
        // Ask for total hits
        setTotalHits(request);

        // Fields selection
        String[] nxqlFields = getSelectFields();
        List<String> includes = Arrays.stream(nxqlFields) //
                                      .map(nxqlName -> nxqlQueryConverter.getFieldName(nxqlName, null))
                                      .toList();
        request.source(b -> b.filter(fb -> fb.includes(includes)));
    }

    /**
     * Sets the trackTotalHits and TrackTotalHitsUpTo parameters on the supplied SearchRequest.Builder.
     * trackTotalCount values set to < 0 will not be applied and not limit the total count.
     */
    protected void setTotalHits(SearchRequest.Builder request) {
        Builder builder = new TrackHits.Builder();
        builder.enabled(trackTotalCount);
        if (trackTotalCount && trackTotalCountUpTo != null && trackTotalCountUpTo > -1) {
            builder.count(trackTotalCountUpTo);
        }
        request.trackTotalHits(builder.build());
    }

    /**
     * Add a specific repository to search. Default search is done on the session repository only.
     *
     * @since 6.0
     */
    public NxQueryBuilder addSearchRepository(String repositoryName) {
        repositories.add(repositoryName);
        return this;
    }

    /**
     * Search on all available repositories.
     *
     * @since 6.0
     */
    public NxQueryBuilder searchOnAllRepositories() {
        searchOnAllRepo = true;
        return this;
    }

    /**
     * Return the list of repositories to search, or an empty list to search on all available repositories;
     *
     * @since 6.0
     */
    public List<String> getSearchRepositories() {
        if (searchOnAllRepo) {
            return Collections.emptyList();
        }
        return repositories;
    }

    /**
     * @since 6.0
     */
    public Fetcher getFetcher(SearchResponse<DocumentDTO> response, Map<String, String> repoNames) {
        if (useUnrestrictedSession) {
            return CoreInstance.doPrivileged(session, s -> {
                return getFetcher(s, response, repoNames);
            });
        }
        return getFetcher(session, response, repoNames);
    }

    protected Fetcher getFetcher(CoreSession session, SearchResponse<DocumentDTO> response, Map<String, String> repoNames) {
        return new VcsFetcher(session, response, repoNames, hitDocConsumer);
    }

    /**
     * @since 7.2
     */
    public String[] getSelectFields() {
        return selectFields;
    }

    /**
     * @since 7.2
     */
    public Map<String, Type> getSelectFieldsAndTypes() {
        return selectFieldsAndTypes;
    }

    public void setSelectFieldsAndTypes(Map<String, Type> selectFieldsAndTypes) {
        this.selectFieldsAndTypes = selectFieldsAndTypes;
    }

    /**
     * @since 7.2
     */
    public boolean returnsDocuments() {
        if (osOnly) {
            return false;
        }
        return returnsDocuments;
    }

    public boolean returnsRows() {
        if (osOnly) {
            return false;
        }
        return !returnsDocuments;
    }

    public NxQueryBuilder embeddingQuery(String embeddingType, List<Double> vector, String chunkFtsQuery, String nxql) {
        nxql(nxql);
        this.embeddingId = null;
        this.embeddingType = embeddingType;
        this.embeddingVector = vector;
        this.chunkFtsQuery = chunkFtsQuery;
        // Enable OpenSearch-only response mode only for actual embedding queries.
        // Keeping osOnly=false for null vector preserves no-op/reset behavior.
        this.osOnly = vector != null;
        return this;
    }

    public NxQueryBuilder embeddingIdQuery(String embeddingId, String nxql) {
        nxql(nxql);
        this.embeddingVector = null;
        this.chunkFtsQuery = null;
        this.embeddingId = embeddingId;
        // Enable OpenSearch-only response mode only for actual embedding-id queries.
        // Keeping osOnly=false for null embeddingId preserves no-op/reset behavior.
        this.osOnly = embeddingId != null;
        return this;
    }

    /**
     * Consumes both a SearchHit and DocumentModel.
     *
     * @since 10.2
     */
    @FunctionalInterface
    public interface HitDocConsumer extends BiConsumer<Hit<DocumentDTO>, DocumentModel> {

    }

    public boolean isHybridQuery() {
        return embeddingVector != null && chunkFtsQuery != null;
    }

    /**
     * Returns true if this query is an embedding query (vector, hybrid, or embedding ID query).
     * <p>
     * Embedding queries target the embeddings index with denormalized data instead of the main
     * document index. This includes:
     * <ul>
     *   <li>Vector similarity queries (with optional type filter)</li>
     *   <li>Hybrid queries (vector + full-text search)</li>
     *   <li>Embedding ID lookup queries</li>
     * </ul>
     * When this returns true, the query will be routed to the {@code {repository}_embeddings} index
     * and will not use expensive parent-child joins.
     *
     * @return true if this is an embedding query (has vector or embedding ID), false otherwise
     */
    public boolean isEmbeddingQuery() {
        return embeddingVector != null || embeddingId != null;
    }
}
