package org.nuxeo.opensearch;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_HAS_EMBEDDINGS;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.SysEmbeddingsPolicies;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentConstants;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.model.EmbeddingModel;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.config.EmbeddingsParserDescriptor;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.io.EmbeddingOSDTO;
import org.nuxeo.opensearch.io.EmbeddingsParser;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;

/**
 * Encapsulates differential embeddings indexing (delete + upsert in one bulk).
 */
public class EmbeddingsIndexer {
    private static final Logger log = LogManager.getLogger(EmbeddingsIndexer.class);

    private static final String EMBEDDINGS_INDEXING_SCROLL_KEEP_ALIVE = Framework.getProperty(
            "hxpr.opensearch.embeddingsIndexingScrollKeepAlive", "1m");

    private final OpenSearchServiceImpl service;

    private final Timer embeddingsIndexTimer;

    /**
     * The resolved embeddings parser (never null). If no valid contribution is provided a default parser is used
     * and a warning is logged so callers don't need to null-check at each access.
     */
    private final EmbeddingsParser embeddingsParser;

    EmbeddingsIndexer(OpenSearchServiceImpl service) {
        this.service = service;
        // local timer registration (kept separate to avoid expanding service surface)
        this.embeddingsIndexTimer = SharedMetricRegistries.getOrCreate(MetricsService.class.getName())
                                                          .timer(MetricName.build("nuxeo.opensearch.service.timer")
                                                                           .tagged("service", "embeddingsIndex"));
        this.embeddingsParser = loadEmbeddingsParser();
    }

    void process(IndexingCommand cmd, boolean isReindexing, int maxBulkSize) {
        log.debug("EmbeddingsIndexer.process enter: cmdId={} type={} reindexing={}", cmd.getId(), cmd.getType(), isReindexing);
        try (Timer.Context ignored = embeddingsIndexTimer.time()) {
            ProcessingContext ctx = ProcessingContext.fromCommand(this, cmd);
            if (ctx == null) {
                log.debug("EmbeddingsIndexer.process skip: no context (doc missing?) for cmdId={}", cmd.getId());
                return;
            }
            Collection<EmbeddingModel> newEmbeddings = parse(cmd, ctx);
            log.debug("Parsed {} embeddings for command {} (type={}, parentId={}, existingIds={})", newEmbeddings.size(), cmd.getId(), ctx.embeddingsType(), ctx.parentDoc().getId(), ctx.existingIds().size());
            if (newEmbeddings.isEmpty() && ctx.existingIds().isEmpty()) {
                log.debug("No embeddings to index or delete for command {} (parentId={}), skipping", cmd.getId(), ctx.parentDoc().getId());
                return;
            }
            if (newEmbeddings.isEmpty()) {
                log.warn("No valid embeddings parsed for command {} (parentId={}). Deleting {} existing embeddings.", cmd.getId(),
                        ctx.parentDoc().getId(), ctx.existingIds().size());
            }
            Set<String> idsToDelete = computeIdsToDelete(ctx, newEmbeddings);

            log.debug("Embeddings differential for command {} -> deletes={}, upserts={}", cmd.getId(), idsToDelete.size(), newEmbeddings.size());

            // Build and execute with byte-size based batching - writes to embeddings index
            executeWithBatching(cmd.getRepositoryName(), ctx.parentDoc(), newEmbeddings, idsToDelete, isReindexing, maxBulkSize);
        }
    }

    /**
     * Deletes all child embeddings associated with the parent of the provided embeddings deletion context.
     * <p>
     * The context is resolved upstream <strong>before</strong> the embeddings document is deleted from OpenSearch so
     * we can still identify the parent document and embeddings type required for the {@code _delete_by_query} call.
     * </p>
     * <p>
     * Individual embedding vectors are deleted from the embeddings index using the denormalized sys_id field.
     * </p>
     *
     * @param embeddingsIndex the embeddings index name to delete from
     * @param context         minimal embeddings deletion context (document id, parent id, embeddings type)
     */
    void deleteEmbeddingsByParent(String embeddingsIndex, DeletionContext context) {
        if (context == null) {
            log.warn("Cannot delete embeddings: missing deletion context for embeddings index {}", embeddingsIndex);
            return;
        }

        deleteFromEmbeddingsIndex(embeddingsIndex, context);
    }

    private void deleteFromEmbeddingsIndex(String embeddingsIndex, DeletionContext context) {
        // Query by denormalized sys_id (parent document ID) AND embedding type if specified
        Query deleteEmbeddingsQuery;
        if (context.embeddingType() != null) {
            Query sysIdQuery = QueryBuilders.term()
                    .field(DocumentConstants.SYS_ID)
                    .value(FieldValue.of(context.parentDocId()))
                    .build()
                    .toQuery();
            TermQuery embeddingTypeQuery = QueryBuilders.term()
                    .field(EMBEDDING_PROPERTY_TYPE)
                    .value(FieldValue.of(context.embeddingType()))
                    .build();
            deleteEmbeddingsQuery = QueryBuilders.bool()
                    .filter(sysIdQuery)
                    .filter(embeddingTypeQuery.toQuery())
                    .build()
                    .toQuery();
        } else {
            deleteEmbeddingsQuery = QueryBuilders.term()
                    .field(DocumentConstants.SYS_ID)
                    .value(FieldValue.of(context.parentDocId()))
                    .build()
                    .toQuery();
        }

        DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                .index(embeddingsIndex)
                .query(deleteEmbeddingsQuery)
                .refresh(Refresh.True)
                .build();
        DeleteByQueryResponse response = service.getClient().deleteByQuery(request);

        if (response.deleted() > 0) {
            log.debug("Deleted {} embeddings from embeddings index '{}' for parent {} (type={})",
                    response.deleted(), embeddingsIndex, context.parentDocId(), context.embeddingType());
        }
        if (!response.failures().isEmpty()) {
            log.warn("Failed to delete embeddings from embeddings index '{}' for parent {} (type={}). Failures: {}",
                    embeddingsIndex, context.parentDocId(), context.embeddingType(), response.failures());
        }
    }

    /**
     * Fetches existing embedding IDs for a given parent document and embedding type.
     * Queries the embeddings index using denormalized sys_id field.
     *
     * @param repositoryName the repository name
     * @param parentId       the parent document ID
     * @param embeddingType  the embedding type to filter by
     * @return set of existing embedding IDs
     */
    Set<String> fetchExistingIds(String repositoryName, String parentId, Optional<String> embeddingType) {
        String embeddingsIndex = service.getEmbeddingsWriteIndexForRepository(repositoryName);
        Query sysIdQuery = QueryBuilders.term()
                                        .field(DocumentConstants.SYS_ID)
                                        .value(FieldValue.of(parentId))
                                        .build()
                                        .toQuery();
        Query query;
        if (embeddingType.isEmpty()) {
            query = sysIdQuery;
        } else {
            Query typeQ = QueryBuilders.term()
                                       .field(EMBEDDING_PROPERTY_TYPE)
                                       .value(FieldValue.of(embeddingType.get()))
                                       .build()
                                       .toQuery();
            query = Query.of(b -> b.bool(bb -> bb.filter(sysIdQuery).filter(typeQ)));
        }
        Time keepAlive = Time.of(b -> b.time(EMBEDDINGS_INDEXING_SCROLL_KEEP_ALIVE));
        SearchRequest request = SearchRequest.of(b -> b.index(embeddingsIndex)
                                                       .query(query)
                                                       .source(s -> s.fetch(false))
                                                       .scroll(keepAlive)
                                                       .size(1000));
        Set<String> ids = new HashSet<>();
        String scrollId = null;
        try {
            SearchResponse<DocumentDTO> response = service.getClient().search(request);
            scrollId = response.scrollId();
            response.hits().hits().stream().map(Hit::id).forEach(ids::add);
            while (!response.hits().hits().isEmpty()) {
                response = service.runNextScroll(response, keepAlive);
                response.hits().hits().stream().map(Hit::id).forEach(ids::add);
            }
            return ids;
        } catch (RuntimeException e) {
            throw new NuxeoException("Failed to fetch existing embedding IDs", e);
        } finally {
            if (scrollId != null) {
                try {
                    service.getClient().clearScroll(scrollId);
                } catch (RuntimeException ignore) {
                    // ignore cleanup failure
                }
            }
        }
    }

    /**
     * Executes bulk operations with byte-size based batching - writes to embeddings index.
     * <p>
     * This method builds operations incrementally, tracking their serialized byte size.
     * When the cumulative size exceeds maxBulkSize, the current batch is sent and a new batch is started.
     * </p>
     * <p>
     * Individual embedding vectors are written to the embeddings index with denormalized parent data.
     * This avoids expensive parent-child joins at query time. The SysEmbeddings container documents
     * themselves remain in the main document index.
     * </p>
     *
     * @param repositoryName the repository name
     * @param parentDoc      the parent document (used for denormalization)
     * @param newEmbeddings  embeddings to upsert
     * @param idsToDelete    IDs of embeddings to delete
     * @param isReindexing   whether this is part of a reindexing operation
     * @param maxBulkSize    maximum byte size per bulk request
     */
    private void executeWithBatching(String repositoryName, DocumentModel parentDoc, Collection<EmbeddingModel> newEmbeddings,
            Set<String> idsToDelete, boolean isReindexing, int maxBulkSize) {
        var jsonProvider = service.getClient().getJsonProvider();
        List<BulkOperation> currentBatch = new ArrayList<>();
        int bulkSize = 0;

        String embeddingsIndex = service.getEmbeddingsWriteIndexForRepository(repositoryName);
        String parentId = parentDoc.getId();

        // Add delete operations for embeddings index
        for (String idToDelete : idsToDelete) {
            currentBatch.add(BulkOperation.of(b -> b.delete(d -> d.index(embeddingsIndex).id(idToDelete))));
        }

        // Add upsert operations with size tracking - write to embeddings index with denormalized parent data
        for (EmbeddingModel embedding : newEmbeddings) {
            EmbeddingOSDTO denormalizedDto = new EmbeddingOSDTO(embedding, parentDoc);
            int denormalizedDtoSize = denormalizedDto.size(jsonProvider);

            // Check if adding this operation would exceed the limit
            if (bulkSize + denormalizedDtoSize > maxBulkSize && !currentBatch.isEmpty()) {
                log.warn("Max bulk size reached {} bytes, sending bulk command", bulkSize);
                executeBulk(currentBatch, isReindexing, parentId);
                currentBatch.clear();
                bulkSize = 0;
            }


            currentBatch.add(BulkOperation.of(
                    b -> b.index(i -> i.index(embeddingsIndex).id(embedding.id()).document(denormalizedDto))));

            bulkSize += denormalizedDtoSize;
        }

        // Send remaining operations
        if (!currentBatch.isEmpty()) {
            executeBulk(currentBatch, isReindexing, parentId);
        }

        log.debug("Embeddings index write complete for parentId={}: deletes={}, upserts={}",
                  parentId, idsToDelete.size(), newEmbeddings.size());
    }

    private void executeBulk(List<BulkOperation> operations, boolean isReindexing, String parentId) {
        if (operations.isEmpty()) {
            return;
        }
        BulkRequest request = BulkRequest.of(b -> b.operations(new ArrayList<>(operations)));
        log.debug("Executing bulk for parentId={}: {} operations", parentId, operations.size());
        BulkResponse response = service.getClient().bulk(request);
        if (response.errors()) {
            service.logBulkFailure(response, isReindexing);
        }
        log.debug("Bulk done for parentId={} (took={})", parentId, response.took());
    }

    // --- Private methods, ordered by call sequence from process() ---

    private Collection<EmbeddingModel> parse(IndexingCommand cmd, ProcessingContext ctx) {
        // Validate context - parse() is only used for document-based embeddings (SysEmbeddings type)
        if (ctx.embeddingsDoc() == null) {
            throw new IllegalArgumentException(
                    "parse() requires non-null embeddingsDoc. Use processPropertyEmbeddings() for property-based embeddings.");
        }
        if (ctx.embeddingsType() == null) {
            throw new IllegalArgumentException(
                    "parse() requires non-null embeddingsType. Context may be improperly initialized.");
        }

        try {
            Blob blob = (Blob) ctx.embeddingsDoc().getPropertyValue(DocumentConstants.SYSEMB_EMBEDDINGS);
            if (blob == null) {
                log.debug("Null blob for command {}, will delete {} embeddings", cmd.getId(), ctx.existingIds().size());
                return List.of();
            }
            log.debug("Parsing embeddings blob (length={}) for command {} (type={} parentId={})", blob.getLength(), cmd.getId(), ctx.embeddingsType(), ctx.parentDoc().getId());
            Collection<EmbeddingModel> parsed = embeddingsParser
                          .parse(blob, cmd.getRepositoryName(), ctx.parentDoc().getId(), ctx.embeddingsType());
            log.debug("Parser returned {} embeddings for command {}", parsed.size(), cmd.getId());
            return parsed;
        } catch (RuntimeException e) {
            throw new NuxeoException("Failed to parse embeddings for command " + cmd, e);
        }
    }

    private Set<String> computeIdsToDelete(ProcessingContext ctx, Collection<EmbeddingModel> newEmbeddings) {
        Set<String> newIds = newEmbeddings.stream().map(EmbeddingModel::id).collect(Collectors.toSet());
        Set<String> idsToDelete = new HashSet<>(ctx.existingIds());
        idsToDelete.removeAll(newIds);
        return idsToDelete;
    }

    private EmbeddingsParser newParser(EmbeddingsParserDescriptor desc) {
        try {
            EmbeddingsParser parser = desc.newInstance();
            log.debug("Registered EmbeddingsParser: {}", desc.getParserClass().getName());
            return parser;
        } catch (RuntimeException e) {
            throw new NuxeoException("Failed to instantiate EmbeddingsParser: " + desc.getParserClass().getName(), e);
        }
    }

    private EmbeddingsParser loadEmbeddingsParser() {
        Map<String, EmbeddingsParserDescriptor> descriptors = service.getEmbeddingsParserDescriptors();

        if (descriptors.size() > 1) {
            // should never occur: tests should ensure only one contribution is active
            String classes = descriptors.values().stream().map(d -> d.getParserClass().getName()).collect(Collectors.joining(", "));
            throw new NuxeoException("Multiple EmbeddingsParser contributions found (" + classes + "). Exactly one parser is supported at this stage.");
        }

        if (descriptors.size() == 1) {
            // should only occur from test contributions
            EmbeddingsParserDescriptor desc = descriptors.values().iterator().next();
            EmbeddingsParser parser = newParser(desc);
            log.info("Using EmbeddingsParser implementation: {}", parser.getClass().getName());

            return parser;
        }

        return EmbeddingsParser.newParser();
    }

    /**
     * Processing context for an embeddings indexing command.
     *
     * @param embeddingsDoc  the embeddings document model
     * @param parentDoc      the parent document model
     * @param embeddingsType the resolved embeddings type
     * @param existingIds    the existing embeddings ids already indexed for this parent/type
     */
    record ProcessingContext(DocumentModel embeddingsDoc, DocumentModel parentDoc,
            String embeddingsType,
            Set<String> existingIds) {

        static ProcessingContext fromCommand(EmbeddingsIndexer indexer, IndexingCommand cmd) {
            OpenSearchServiceImpl service = indexer.service;
            DocumentModel embeddingsDoc = service.getTargetDocument(cmd.getRepositoryName(), cmd.getTargetDocumentId());
            if (embeddingsDoc == null) {
                log.info("Cancel embeddings indexing command; embeddings doc missing: {}", cmd);
                return null;
            }
            DocumentModel parentDoc = service.getTargetDocument(cmd.getRepositoryName(), embeddingsDoc.getParentRef());
            if (parentDoc == null) {
                log.info("Cancel embeddings indexing command; parent doc missing for parentRef={}: {}", embeddingsDoc.getParentRef(), cmd);
                return null;
            }
            String embeddingsType = SysEmbeddingsPolicies.getEmbeddingsTypeFromName(embeddingsDoc.getName());
            Set<String> existingIds = indexer.fetchExistingIds(cmd.getRepositoryName(), parentDoc.getId(), Optional.of(embeddingsType));
            return new ProcessingContext(embeddingsDoc, parentDoc, embeddingsType, existingIds);
        }
    }

    /**
     * Minimal fields snapshot for a {@code sys:embeddings} document used when deleting its child vectors.
     *
     * @param embeddingsDocId the concrete embeddings child document id (used for diagnostics only)
     * @param parentDocId     the parent document id
     * @param embeddingType   the embeddings type resolved from the child name
     */
    public record DeletionContext(String embeddingsDocId, String parentDocId, String embeddingType) {

        /**
         * Resolves the minimal deletion context required for downstream {@code _delete_by_query} calls.
         * <p>
         * Exposed for integration tests that need to assert how OpenSearch {@code _source} filtering behaves.
         * </p>
         */
        public static DeletionContext fromDocument(String docId, DocumentDTO documentDTO) {
            if (documentDTO == null) {
                return null;
            }
            String type = getPrimaryType(documentDTO);
            List<String> mixinTypes = getMixinTypes(documentDTO);
            if (SYS_EMBEDDINGS.equals(type)) {
                String parentId = getParentId(documentDTO);
                String embeddingType = getEmbeddingType(documentDTO);
                return new DeletionContext(docId, parentId, embeddingType);
            } else if (mixinTypes.contains(SYS_HAS_EMBEDDINGS)) {
                // Document has embeddings as children - delete all embeddings for this parent
                return new DeletionContext(null, docId, null);
            }
            return null;
        }

        private static String getStringPropertyValue(DocumentDTO documentDTO, String propertyName) {
            Object value = documentDTO.getProperties().get(propertyName);
            return Objects.toString(value, null);
        }
        private static List<String> getListPropertyValue(DocumentDTO documentDTO, String propertyName) {
            Object value = documentDTO.getProperties().get(propertyName);
            if (!(value instanceof List<?> listValue)) {
                return List.of();
            }
            return listValue.stream().map(Object::toString).toList();
        }

        private static String getPrimaryType(DocumentDTO documentDTO) {
            return getStringPropertyValue(documentDTO, SYS_PRIMARY_TYPE);
        }

        private static List<String> getMixinTypes(DocumentDTO documentDTO) {
            return getListPropertyValue(documentDTO, SYS_MIXIN_TYPES);
        }

        private static String getParentId(DocumentDTO documentDTO) {
            return getStringPropertyValue(documentDTO, SYS_PARENT_ID);
        }

        private static String getEmbeddingType(DocumentDTO documentDTO) {
            String docName = getStringPropertyValue(documentDTO, SYS_NAME);
            return SysEmbeddingsPolicies.getEmbeddingsTypeFromName(docName);
        }
    }
}
