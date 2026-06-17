/*
 * (C) Copyright 2025 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_BLOB_REF;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.opensearch.OpenSearchConstants.INDEX_BULK_MAX_SIZE_PROPERTY;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nuxeo.ecm.core.SysEmbeddingsPolicies;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentConstants;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.schema.MixinNames;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.opensearch.EmbeddingsIndexer;
import org.nuxeo.opensearch.OpenSearchServiceImpl;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.io.ParquetEmbeddingsParser;
import org.nuxeo.opensearch.tasks.IndexingTaskProcessor;
import org.nuxeo.opensearch.test.util.ParquetBlobGenerator;
import org.nuxeo.opensearch.test.util.ParquetBlobGenerator.EmbeddingVariant;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogFeature;
import org.nuxeo.runtime.test.runner.LoggerLevel;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.TaskService;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;
import jakarta.inject.Inject;

/**
 * Indexing coverage for SysEmbeddings doctype (child documents carrying a single sysemb_embeddings Blob) ensuring they
 * are indexed, re-indexed when relevant, and removed when the parent is deleted.
 * <p>
 * Legacy list-based embeddings model intentionally out of scope.
 * <p>
 */
@ExtendWith(FeaturesExtension.class)
@Features({ RepositoryOpenSearchFeature.class, LogFeature.class, LogCaptureFeature.class })
@LoggerLevel(klass = TestEmbeddingsIndexing.class, level = "WARN")
@LoggerLevel(klass = OpenSearchServiceImpl.class, level = "WARN")
@LoggerLevel(klass = EmbeddingsIndexer.class, level = "WARN")
@LoggerLevel(klass = IndexingTaskProcessor.class, level = "WARN")
@LoggerLevel(klass = ParquetEmbeddingsParser.class, level = "WARN")
class TestEmbeddingsIndexing {

    private static final Logger log = LoggerFactory.getLogger(TestEmbeddingsIndexing.class);

    static final String IDX_NAME = "nxutest";

    static final String EMBEDDINGS_IDX_NAME = "nxuembtest";

    static final String PARENT_PATH = "/parentDoc";

    static final String PARENT_CHILD_PREFIX = PARENT_PATH + "/";

    private static final int EMBEDDINGS_PER_BLOB = 50;

    // MIME type constants for embeddings parsers
    private static final String MIME_TYPE_TEXT_PLAIN = "text/plain";

    private static final String MIME_TYPE_EMBEDDINGS = "application/vnd.nuxeo.embeddings";

    private static final String MIME_TYPE_EMBEDDINGS_NEW = MIME_TYPE_EMBEDDINGS + "; variant=new";

    private static final String MIME_TYPE_EMBEDDINGS_COMBINED = MIME_TYPE_EMBEDDINGS + "; variant=combined";

    private static final String MIME_TYPE_EMBEDDINGS_SAME = MIME_TYPE_EMBEDDINGS + "; variant=same";

    private static final List<String> EMBEDDINGS_DELETION_CONTEXT_FIELDS = List.of(DocumentConstants.SYS_PRIMARY_TYPE,
            DocumentConstants.SYS_PARENT_ID, DocumentConstants.SYS_NAME);

    @Inject
    TaskService asyncTasksService;

    @Inject
    CoreSession session;

    @Inject
    OpenSearchService oss;

    @Inject
    LogCaptureFeature.Result logCaptureResult;

    static Stream<Arguments> embeddingsBlobUpdateParameters() {
        return Stream.of(Arguments.of(MIME_TYPE_TEXT_PLAIN, 10, "default.txt"),
                Arguments.of(MIME_TYPE_EMBEDDINGS_NEW, 50, "new.txt"),
                Arguments.of(MIME_TYPE_EMBEDDINGS_COMBINED, 50, "combined.txt"),
                Arguments.of(MIME_TYPE_EMBEDDINGS_SAME, 50, "same.txt"));
    }

    /* ---------------- Refactored Plain Tests ---------------- */

    /**
     * Extracts the EmbeddingVariant from the MIME type string.
     */
    static EmbeddingVariant extractVariant(String mimeType) {
        if (mimeType == null)
            return EmbeddingVariant.NEW;
        int idx = mimeType.indexOf("variant=");
        if (idx >= 0) {
            String v = mimeType.substring(idx + "variant=".length()).trim().toUpperCase();
            switch (v) {
            case "SAME":
                return EmbeddingVariant.SAME;
            case "COMBINED":
                return EmbeddingVariant.COMBINED;
            default:
                return EmbeddingVariant.NEW;
            }
        }
        return EmbeddingVariant.NEW;
    }

    @BeforeEach
    public void initIndex() throws InterruptedException {
        oss.initIndexes(true);
        // Wait for any async indexing from previous tests to complete
        asyncTasksService.awaitCompletion(Duration.ofSeconds(5));
        startTx();
    }

    @Test
    void testGetEmbeddingsIndexNameForRepository() {
        // Test that the service correctly returns the configured embeddings index name
        String embeddingsIndexName = oss.getEmbeddingsIndexNameForRepository("test");
        assertEquals(EMBEDDINGS_IDX_NAME, embeddingsIndexName,
                "Service should return the configured embeddings index name");
    }

    @Test
    void testIndexingOnCreation() throws IOException, InterruptedException, TimeoutException, ExecutionException {
        // given a parent document with 3 embeddings children
        final int EMB_CHILD_COUNT = 3;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        // when the documents are created and indexed
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        // then the parent and all children are indexed
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);
    }

    @Test
    void testParquetEmbeddingsHandleOptionalBlobRefField() throws IOException, InterruptedException, TimeoutException, ExecutionException {
        // given a parent with two embeddings children, one carrying blobref, one without
        DocumentModel parent = createParent("parentBlobRef");

        String withRefName = "withref";
        String withoutRefName = "noref";

        createParquetEmbeddingsChild(parent, withRefName, withRefName, true);
        createParquetEmbeddingsChild(parent, withoutRefName, withoutRefName, false);

        // when
        saveAndAssertForEmbeddingsIndexCommands(2);

        // then: both children indexed and blobref behaves as expected
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, 2);
        assertEmbeddingsCount(parent, 2);

        String withRefType = SysEmbeddingsPolicies.getEmbeddingsTypeFromName(childNameOfSuffix(withRefName));
        String withoutRefType = SysEmbeddingsPolicies.getEmbeddingsTypeFromName(childNameOfSuffix(withoutRefName));

        assertBlobRefPresence(parent, withRefType, true, "blob:" + withRefType + ":embedding-");
        assertBlobRefPresence(parent, withoutRefType, false, null);
    }

    @Test
    void testDeleteParentRemovesEmbeddings() throws Exception {
        // given a parent document with 2 embeddings children indexed
        final int EMB_CHILD_COUNT = 2;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when the parent is deleted and the index updated
        deleteParent();
        saveAndAssertForEmbeddingsIndexCommands(0);

        // then all children are deleted from index
        assertHasEmbeddingsDocCount(0);
        assertEmbeddingsDocCount(parent, 0);
        assertEmbeddingsCount(parent, 0);
    }

    @Test
    void testRecursiveDeleteRemovesDeeplyNestedEmbeddings() throws Exception {
        DocumentModel rootFolder = session.createDocumentModel("/", "nestedRoot", TypeConstants.SYS_FOLDER);
        rootFolder.create();

        DocumentModel levelTwo = session.createDocumentModel(rootFolder.getPathAsString(), "nestedLevel2",
                TypeConstants.SYS_FOLDER);
        levelTwo.create();

        DocumentModel parent = session.createDocumentModel(levelTwo.getPathAsString(), "nestedParentDoc", "SysFile");
        parent.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);
        parent.create();

        Blob embeddingsBlob = new ParquetBlobGenerator().generate(EMBEDDINGS_PER_BLOB, "test_type");
        String childName = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("nested_child_0");
        DocumentModel emb = session.createDocumentModel(parent.getPathAsString(), childName,
                TypeConstants.SYS_EMBEDDINGS);
        emb.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        emb.create();

        session.save();

        saveAndAssertForEmbeddingsIndexCommands(1);
        assertEmbeddingsDocCount(parent, 1);
        assertEmbeddingsCount(parent, 1);

        session.removeDocument(rootFolder.getRef());
        saveAndAssertForEmbeddingsIndexCommands(0);

        assertEmbeddingsCount(parent, 0);
        assertHasEmbeddingsDocCount(0);
        assertEquals(0, oss.getClient().getOpenScrollContexts());
    }

    @Test
    void testDeleteChildRemovesEmbeddings() throws Exception {
        // Baseline guardrail: deleting a single embeddings child must only remove that child’s vectors
        // given a parent document with 2 embeddings children indexed
        final int EMB_CHILD_COUNT = 2;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when the child is deleted and the index updated
        deleteChild(0);
        saveAndAssertForEmbeddingsIndexCommands(0);

        // then all children are deleted from index
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsCount(parent, 1);
        assertEmbeddingsDocCount(parent, 1);
    }

    @Test
    void testDeleteSpecificChildDoesNotAffectSiblings() throws Exception {
        // Regression coverage: ensure delete-by-query filters on the child’s embeddings type (not just the first child)
        // given a parent document with 3 embeddings children indexed
        final int EMB_CHILD_COUNT = 3;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when a non-default child is deleted and the index updated
        deleteChild(1);
        saveAndAssertForEmbeddingsIndexCommands(0);

        // then only that child's embeddings are removed
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT - 1);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT - 1);
        assertEquals(EMBEDDINGS_PER_BLOB, getEmbeddingIdsForChild(parent, 0).size(),
                "Deleting child_1 should keep child_0 embeddings intact");
    }

    @Test
    void testDeletionContextSkipsMissingSource() throws Exception {
        // given an embeddings child indexed for a parent
        final int EMB_CHILD_COUNT = 1;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        DocumentModel child = session.getDocument(new PathRef(PARENT_CHILD_PREFIX + childNameOf(0)));

        // when deletion metadata resolution receives a missing source
        EmbeddingsIndexer.DeletionContext ctx = EmbeddingsIndexer.DeletionContext.fromDocument(child.getId(),
                null);

        // then the context is skipped and no side effects occur
        assertNull(ctx, "Missing _source should skip embeddings deletion context resolution");
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);
    }

    @Test
    void testDeletionContextResolvesFromFilteredSource() throws Exception {
        // given an embeddings child indexed for a parent
        final int EMB_CHILD_COUNT = 1;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        DocumentModel child = session.getDocument(new PathRef(PARENT_CHILD_PREFIX + childNameOf(0)));

        // when we fetch only the minimal deletion metadata fields
        DocumentDTO dto = fetchDeletionContextFields(child);
        EmbeddingsIndexer.DeletionContext ctx = EmbeddingsIndexer.DeletionContext.fromDocument(child.getId(),
                dto);

        // then the deletion context still resolves correctly
        assertNotNull(ctx, "Filtered _source should still resolve deletion context");
        assertEquals(child.getId(), ctx.embeddingsDocId());
        assertEquals(parent.getId(), ctx.parentDocId());
        assertEquals(SysEmbeddingsPolicies.getEmbeddingsTypeFromName(child.getName()), ctx.embeddingType());
    }

    DocumentDTO fetchDeletionContextFields(DocumentModel child) {
        OpenSearchServiceImpl service = (OpenSearchServiceImpl) oss;
        GetRequest request = GetRequest.of(
                b -> b.index(IDX_NAME).id(child.getId()).sourceIncludes(EMBEDDINGS_DELETION_CONTEXT_FIELDS));
        DocumentDTO dto = service.getClient().get(request).source();
        assertNotNull(dto, "Embeddings child metadata missing from OpenSearch");
        return dto;
    }

    @Test
    void testDeleteNonEmbeddingsDocumentDoesNotAffectEmbeddings()
            throws InterruptedException, IOException, TimeoutException, ExecutionException {
        // given a parent with embeddings and a sibling regular document
        final int EMB_CHILD_COUNT = 2;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        DocumentModel sibling = session.createDocumentModel("/", "siblingDoc", TypeConstants.SYS_FILE);
        sibling.create();
        session.save();
        saveAndWaitForIndexing();

        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when the sibling (non-embeddings) document is deleted
        session.removeDocument(sibling.getRef());
        saveAndWaitForIndexing();

        // then embeddings are unaffected
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);
    }

    /**
     * Tests embeddings blob updates with different parser variants. Verifies that embeddings are correctly replaced
     * when a blob is updated, preventing race conditions where old embeddings might remain visible during the
     * delete-then-index operation. Run with high repetitions to catch race conditions.
     */
    @ParameterizedTest(name = "[{index}] {0} with {1} children")
    @MethodSource("embeddingsBlobUpdateParameters")
    void testReindexOnEmbeddingsBlobUpdate(String mimeType, int embChildCount, String filename) throws Exception {
        // given a parent document with embeddings children
        DocumentModel parent = createParentWithEmbeddings(embChildCount);
        saveAndAssertForEmbeddingsIndexCommands(embChildCount);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, embChildCount);
        assertEmbeddingsCount(parent, embChildCount);

        // Get initial embedding IDs for CHILD0
        List<String> initialIds = getEmbeddingIdsForChild(parent, 0);

        // when the embeddings blob is updated with the specified parser variant
        updateEmbeddingsBlobWithMimeType(0, filename, mimeType);
        saveAndAssertForEmbeddingsIndexCommands(1);

        // then the parent and all children are still correctly indexed
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, embChildCount);
        assertEmbeddingsCount(parent, embChildCount);
        assertEmbeddingsBlobFilename(0, filename);

        // Additional variant-specific assertions on ID reuse
        EmbeddingVariant variant = extractVariant(mimeType);
        oss.refresh(); // Force refresh to ensure documents are searchable
        List<String> updatedIds = getEmbeddingIdsForChild(parent, 0);
        long reused = updatedIds.stream().filter(initialIds::contains).count();
        if (variant == EmbeddingVariant.SAME || variant == EmbeddingVariant.COMBINED) {
            log.debug("[DIAG] Variant={} initialIds={} updatedIds={}", variant, initialIds, updatedIds);
        }
        switch (variant) {
        case SAME:
            assertEquals(EMBEDDINGS_PER_BLOB, reused, "Expected all IDs reused for 'same' variant");
            break;
        case COMBINED:
            assertEquals(EMBEDDINGS_PER_BLOB / 2, reused, "Expected half IDs reused for 'combined' variant");
            break;
        case NEW:
        default:
            assertEquals(0, reused, "Expected no IDs reused for 'new' variant");
            break;
        }
    }

    @Test
    void testReindexOnEmbeddingsWithNullBlob() throws Exception {
        // given a parent document with 1 embeddings child
        final int EMB_CHILD_COUNT = 1;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when the embeddings blob is updated with a null blob
        updateEmbeddingsWithNullBlob(0);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);

        // then the child is re-indexed with updated null blob
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        // and no embeddings remain
        assertEmbeddingsCount(parent, 0);
    }

    @Test
    void testReindexOnEmbeddingsWithEmptyBlob() throws Exception {
        // given a parent document with 1 embeddings child
        final int EMB_CHILD_COUNT = 1;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when the embeddings blob is updated with an empty blob
        updateEmbeddingsBlob(0, "", "empty.txt");
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);

        // then the child is re-indexed with updated empty blob
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        // and no embeddings remain
        assertEmbeddingsCount(parent, 0);
    }

    @Test
    void testReindexOnParentMetadataUpdate() throws Exception {
        // given a parent document with 2 embeddings children indexed
        final int EMB_CHILD_COUNT = 2;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // when the parent title is updated and indexing committed
        updateParentTitle("Embeddings Parent Updated");
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);

        // then the parent is updated and no duplicate children are created
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);
        assertParentTitle("Embeddings Parent Updated");
    }

    @Test
    void testParentMetadataUpdateReflectedInEmbeddingsIndex() throws Exception {
        // given a parent document with initial metadata
        String initialTitle = "Initial Title";
        String initialDescription = "Initial Description";
        DocumentModel parent = createParentWithMetadata(initialTitle, initialDescription);
        createEmbeddingsChildForParent(parent, "child_0");
        saveAndAssertForEmbeddingsIndexCommands(1);

        // Verify initial denormalized metadata in embeddings index
        String parentId = parent.getId();
        SearchResponse<DocumentDTO> initialResponse = searchEmbeddingsForParentInEmbeddingsIndex(parent);
        assertEquals(EMBEDDINGS_PER_BLOB, initialResponse.hits().total().value(),
                "Embeddings index should contain embeddings");
        initialResponse.hits().hits().forEach(hit -> {
            var props = hit.source().getProperties();
            assertEquals(initialTitle, props.get(DocumentConstants.SYS_TITLE),
                    "Initial title should be denormalized in embeddings index");
            assertEquals(initialDescription, props.get(DocumentConstants.SYS_DESCRIPTION),
                    "Initial description should be denormalized in embeddings index");
            // Verify fulltext is NOT denormalized (excluded by DenormalizedDocumentWriter)
            assertNull(props.get(DocumentConstants.SYS_FULLTEXT_BINARY),
                    "Fulltext binary should NOT be denormalized in embeddings");
        });

        // when the parent metadata is updated
        String updatedTitle = "Updated Title After Change";
        String updatedDescription = "Updated Description After Change";
        parent = session.getDocument(parent.getRef());
        parent.setPropertyValue(DocumentConstants.SYS_TITLE, updatedTitle);
        parent.setPropertyValue(DocumentConstants.SYS_DESCRIPTION, updatedDescription);
        session.save();
        saveAndAssertForEmbeddingsIndexCommands(1);

        // then the embeddings index should contain the UPDATED denormalized metadata
        SearchResponse<DocumentDTO> updatedResponse = searchEmbeddingsForParentInEmbeddingsIndex(parent);
        assertEquals(EMBEDDINGS_PER_BLOB, updatedResponse.hits().total().value(),
                "Embeddings count should remain the same after parent update");

        updatedResponse.hits().hits().forEach(hit -> {
            var props = hit.source().getProperties();
            assertEquals(updatedTitle, props.get(DocumentConstants.SYS_TITLE),
                    "Updated title should be reflected in embeddings index denormalized data");
            assertEquals(updatedDescription, props.get(DocumentConstants.SYS_DESCRIPTION),
                    "Updated description should be reflected in embeddings index denormalized data");
            assertEquals(parentId, props.get(DocumentConstants.SYS_ID),
                    "Parent ID should remain unchanged");
            // Verify fulltext is NOT denormalized (excluded by DenormalizedDocumentWriter)
            assertNull(props.get(DocumentConstants.SYS_FULLTEXT_BINARY),
                    "Fulltext binary should NOT be denormalized in embeddings");
        });
    }

    @Test
    void testReindexOnParentMove() throws Exception {
        final int EMB_CHILD_COUNT = 50;
        DocumentModel parent = createParentWithEmbeddings(EMB_CHILD_COUNT);
        saveAndAssertForEmbeddingsIndexCommands(EMB_CHILD_COUNT);
        assertEmbeddingsDocCount(parent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(parent, EMB_CHILD_COUNT);

        // create destination folder and move parent
        DocumentModel dest = session.createDocumentModel("/", "destFolder", "SysFolder");
        dest.create();
        String newPath = "/destFolder/" + parent.getName();
        session.move(parent.getRef(), dest.getRef(), parent.getName());

        // we don't assert for embeddings index commands count as it depends on some optimization regarding the number
        // of embeddings per blob, but the final count of indexed embeddings should be the same
        saveAndWaitForIndexing();

        DocumentModel movedParent = session.getDocument(new PathRef(newPath));
        assertHasEmbeddingsDocCount(1); // still exactly one parent
        assertEmbeddingsDocCount(movedParent, EMB_CHILD_COUNT);
        assertEmbeddingsCount(movedParent, EMB_CHILD_COUNT);
        assertParentPathInIndex(movedParent.getId(), newPath);
    }

    /* ---------------- Helpers ---------------- */

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "WARN", loggerClass = EmbeddingsIndexer.class)
    void testParquetEmbeddingsBatchingWhenExceedingMaxSize() throws Exception {
        // Set max bulk size to ~35KB to force splitting into multiple batches
        // Each 1024-dim embedding is ~4KB when serialized
        System.setProperty(INDEX_BULK_MAX_SIZE_PROPERTY, "35000");

        try {
            // Create parent with one SysEmbeddings child containing many embeddings in Parquet format
            var parent = session.createDocumentModel("/", "parentWithLargeParquet", "SysFile");
            parent.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);
            parent.create();

            // Generate a Parquet blob with 30 embeddings (30 * ~4KB = ~120KB)
            // This should trigger at least 3 batches with 35KB limit
            Blob embeddingsBlob = new ParquetBlobGenerator().generate(30, "test_type");

            String name = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("large_embeddings");
            DocumentModel emb = session.createDocumentModel(parent.getPathAsString(), name,
                    TypeConstants.SYS_EMBEDDINGS);
            emb.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
            emb.create();

            saveAndWaitForIndexing();

            // Verify all 30 embeddings are indexed
            assertHasEmbeddingsDocCount(1);
            assertEmbeddingsDocCount(parent, 1);
            assertEquals(30, searchEmbeddingsForParentInEmbeddingsIndex(parent).hits().total().value(),
                    "Expected 30 embeddings to be indexed");

            // Verify batching occurred by checking log messages
            List<String> events = logCaptureResult.getCaughtEventMessages();
            long batchCount = events.stream().filter(s -> s.contains("Max bulk size reached")).count();
            assertTrue(batchCount >= 2,
                    "Expected at least 2 batch log messages due to byte size batching, got " + batchCount);
        } finally {
            System.clearProperty(INDEX_BULK_MAX_SIZE_PROPERTY);
        }
    }

    void startTx() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    DocumentModel createParentWithMetadata(String title, String description) {
        DocumentModel parent = session.createDocumentModel("/", "parentDoc", TypeConstants.SYS_FILE);
        parent.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);
        parent.setPropertyValue(DocumentConstants.SYS_TITLE, title);
        parent.setPropertyValue(DocumentConstants.SYS_DESCRIPTION, description);
        // Add a blob to verify it's omitted in denormalization (DenormalizedDocumentWriter excludes fulltext)
        Blob textBlob = new StringBlob("This is test content that should NOT appear in embeddings", "text/plain");
        parent.setPropertyValue(DocumentConstants.SYSFILE_BLOB, textBlob);
        parent.create();
        session.save();
        return parent;
    }

    DocumentModel createParent(String name) {
        DocumentModel parent = session.createDocumentModel("/", name, TypeConstants.SYS_FILE);
        parent.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);
        parent.create();
        session.save();
        return parent;
    }

    void createEmbeddingsChildForParent(DocumentModel parent, String childName) throws IOException {
        Blob embeddingsBlob = new ParquetBlobGenerator().generate(EMBEDDINGS_PER_BLOB, "test_type");
        String embName = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix(childName);
        DocumentModel embDoc = session.createDocumentModel(
                parent.getPathAsString(), embName, TypeConstants.SYS_EMBEDDINGS);
        embDoc.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        embDoc.create();
        session.save();
    }

    DocumentModel createParquetEmbeddingsChild(DocumentModel parent, String childNameSuffix, String embeddingsType,
            boolean withBlobRef) throws IOException {
        Blob embeddingsBlob = new ParquetBlobGenerator().generate(EMBEDDINGS_PER_BLOB, embeddingsType, withBlobRef);
        String embName = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix(childNameSuffix);
        DocumentModel embDoc = session.createDocumentModel(parent.getPathAsString(), embName, TypeConstants.SYS_EMBEDDINGS);
        embDoc.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        embDoc.create();
        session.save();
        return embDoc;
    }

    DocumentModel createParentWithEmbeddings(int children) throws IOException {
        var parent = session.createDocumentModel("/", "parentDoc", TypeConstants.SYS_FILE);
        parent.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);
        parent.create();

        for (int i = 0; i < children; i++) {
            // Generate test embeddings with the specified children
            Blob embeddingsBlob = new ParquetBlobGenerator().generate(EMBEDDINGS_PER_BLOB, "test_type");

            // Create a single embeddings document with the generated blob
            String name = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("child_" + i);
            DocumentModel emb = session.createDocumentModel(parent.getPathAsString(), name,
                    TypeConstants.SYS_EMBEDDINGS);
            emb.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
            emb.create();
        }

        session.save();
        return parent;
    }

    void saveAndWaitForIndexing() throws InterruptedException, TimeoutException, ExecutionException {
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        asyncTasksService.awaitCompletion(Duration.ofSeconds(20));
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
        startTx();
    }

    void saveAndAssertForEmbeddingsIndexCommands(int expectedEmbeddingsDelta)
            throws IOException, InterruptedException, TimeoutException, ExecutionException {
        Timer timer = getEmbeddingsIndexTimer();
        long before = timer.getCount();
        saveAndWaitForIndexing();
        long after = timer.getCount();
        assertEquals(expectedEmbeddingsDelta, after - before,
                "Unexpected embeddings indexing timer count delta (before=" + before + ", after=" + after + ")");
    }

    void deleteParent() {
        session.removeDocument(new PathRef(PARENT_PATH));
    }

    String childNameOf(int index) {
        return SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("child_" + index);
    }

    String childNameOfSuffix(String suffix) {
        return SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix(suffix);
    }

    void deleteChild(int index) {
        session.removeDocument(new PathRef(PARENT_CHILD_PREFIX + childNameOf(index)));
    }

    void updateParentTitle(String newTitle) {
        DocumentModel parent = session.getDocument(new PathRef(PARENT_PATH));
        parent.setPropertyValue(DocumentConstants.SYS_TITLE, newTitle);
        session.save();
    }

    void updateEmbeddingsBlob(int childIndex, String content, String filename) {
        DocumentModel emb = session.getDocument(new PathRef(PARENT_CHILD_PREFIX + childNameOf(childIndex)));
        emb.setPropertyValue(SYSEMB_EMBEDDINGS, new StringBlob(content, MIME_TYPE_TEXT_PLAIN, "UTF-8", filename));
        session.save();
    }

    void updateEmbeddingsBlobWithMimeType(int childIndex, String filename, String mimeType) {
        // Adapted: now generating a Parquet blob based on variant semantics instead of textual protocol.
        DocumentModel parent = session.getDocument(new PathRef(PARENT_PATH));
        String childName = childNameOf(childIndex);
        DocumentModel emb = session.getDocument(new PathRef(PARENT_CHILD_PREFIX + childName));
        EmbeddingVariant variant = extractVariant(mimeType);
        List<String> previousIds = getEmbeddingIdsForChild(parent, childIndex);
        try {
            String embeddingsType = SysEmbeddingsPolicies.getEmbeddingsTypeFromName(childName);
            Blob blob = new ParquetBlobGenerator().generateVariantUpdate(variant, EMBEDDINGS_PER_BLOB, embeddingsType);
            blob.setFilename(filename);
            blob.setMimeType(mimeType);
            if (log.isDebugEnabled()) {
                log.debug("[GEN] variant={} prevSize={} blobLength={}", variant, previousIds.size(), blob.getLength());
            }
            // Pre-parse diagnostic
            var preParser = org.nuxeo.opensearch.io.EmbeddingsParser.newParser();
            var parsed = preParser.parse(blob, "test", parent.getId(), embeddingsType);
            if (log.isDebugEnabled()) {
                log.debug("[PARSE-PRE] variant={} parsedCount={}", variant, parsed.size());
            }
            if (variant != EmbeddingVariant.NEW && log.isTraceEnabled()) {
                log.trace("[PARSE-IDS] variant={} parsedIds={}", variant,
                        parsed.stream().map(org.nuxeo.ecm.core.model.EmbeddingModel::id).toList());
            }
            emb.setPropertyValue(SYSEMB_EMBEDDINGS, blob);
        } catch (IOException e) {
            throw new NuxeoException("Failed generating variant update parquet blob", e);
        }
        session.save();
    }

    List<String> getEmbeddingIdsForChild(DocumentModel parent, int childIndex) {
        String childName = childNameOf(childIndex);
        // Query embeddings index using denormalized sys_id field
        String embeddingsType = SysEmbeddingsPolicies.getEmbeddingsTypeFromName(childName);

        // Create a query that filters by denormalized parent sys_id and embedding type
        Query sysIdQuery = QueryBuilders.term()
                                        .field(DocumentConstants.SYS_ID)
                                        .value(FieldValue.of(parent.getId()))
                                        .build()
                                        .toQuery();
        Query typeQuery = QueryBuilders.term()
                                       .field(EMBEDDING_PROPERTY_TYPE)
                                       .value(FieldValue.of(embeddingsType))
                                       .build()
                                       .toQuery();
        Query combinedQuery = QueryBuilders.bool().must(sysIdQuery, typeQuery).build().toQuery();

        SearchRequest request = SearchRequest.of(b -> b.index(EMBEDDINGS_IDX_NAME)
                                                       .searchType(SearchType.DfsQueryThenFetch)
                                                       .query(combinedQuery)
                                                       .from(0)
                                                       .size(2000));
        SearchResponse<DocumentDTO> response = oss.getClient().search(request);

        log.debug("[SEARCH-DEBUG] Searching for child: '{}' -> type: '{}'", childName, embeddingsType);
        if (log.isDebugEnabled()) {
            log.debug("[SEARCH-DEBUG] Total hits: {} for parent: {}", response.hits().total().value(), parent.getId());
        }

        List<String> ids = new ArrayList<>();
        final int[] debugCount = { 0 }; // Use array for mutable counter in lambda
        response.hits().hits().forEach(hit -> {
            DocumentDTO dto = hit.source();
            if (dto != null && dto.getProperties() != null) {
                Object type = dto.getProperties().get(EMBEDDING_PROPERTY_TYPE);
                if (debugCount[0] < 5) { // Only show first 5 for brevity
                    if (log.isTraceEnabled()) {
                        log.trace("[SEARCH-DEBUG] Hit ID: {} type: '{}' expected: '{}'", hit.id(), type,
                                embeddingsType);
                    }
                    debugCount[0]++;
                }
                ids.add(hit.id()); // All hits should match the type now due to the filtered query
            }
        });
        if (log.isDebugEnabled()) {
            log.debug("[SEARCH-DEBUG] Found {} matching embeddings for child: {}", ids.size(), childName);
        }
        return ids;
    }

    void updateEmbeddingsWithNullBlob(int childIndex) {
        String childName = childNameOf(childIndex);
        DocumentModel emb = session.getDocument(new PathRef(PARENT_CHILD_PREFIX + childName));
        emb.setPropertyValue(SYSEMB_EMBEDDINGS, null);
        session.save();
    }

    SearchResponse<DocumentDTO> searchHasEmbeddingsDocs() {
        Query q = new Query.Builder().term(t -> t.field(DocumentConstants.SYS_MIXIN_TYPES)
                                                 .value(v -> v.stringValue(MixinNames.SYS_HAS_EMBEDDINGS)))
                                     .build();
        SearchRequest request = SearchRequest.of(
                b -> b.index(IDX_NAME).searchType(SearchType.DfsQueryThenFetch).query(q).from(0).size(100));
        return oss.getClient().search(request);
    }

    SearchResponse<DocumentDTO> searchEmbeddingsDoc(DocumentModel parent) {
        Query q = QueryBuilders.bool()
                               .must(m -> m.term(t -> t.field(DocumentConstants.SYS_PRIMARY_TYPE)
                                                       .value(v -> v.stringValue(TypeConstants.SYS_EMBEDDINGS))))
                               .must(m -> m.term(t -> t.field(DocumentConstants.SYS_PARENT_ID)
                                                       .value(v -> v.stringValue(parent.getId()))))
                               .build()
                               .toQuery();
        SearchRequest request = SearchRequest.of(
                b -> b.index(IDX_NAME).searchType(SearchType.DfsQueryThenFetch).query(q).from(0).size(100));
        return oss.getClient().search(request);
    }


    void assertBlobRefPresence(DocumentModel parent, String embeddingsType, boolean expectedBlobRefPresent,
            String expectedBlobRefPrefix) {
        SearchResponse<DocumentDTO> response = searchEmbeddingsForParentByTypeInEmbeddingsIndex(parent, embeddingsType);
        assertEquals(EMBEDDINGS_PER_BLOB, response.hits().total().value());
        response.hits().hits().forEach(hit -> {
            DocumentDTO dto = hit.source();
            assertNotNull(dto, "Embedding source should not be null");
            Map<String, Object> props = dto.getProperties();
            assertNotNull(props, "Embedding properties should not be null");
            Object blobRef = props.get(EMBEDDING_PROPERTY_BLOB_REF);
            if (expectedBlobRefPresent){
                assertNotNull(blobRef, "Expected blob reference to be present");
                assertTrue(blobRef.toString().startsWith(expectedBlobRefPrefix),
                        "Unexpected blob reference value: " + blobRef);
            }
        });
    }

    SearchResponse<DocumentDTO> searchEmbeddingsForParentInEmbeddingsIndex(DocumentModel parent) {
        // Query embeddings index using denormalized sys_id field (no parent-child join, no routing)
        // This verifies that denormalized parent document data is properly indexed
        Query q = QueryBuilders.term()
                               .field(DocumentConstants.SYS_ID)
                               .value(FieldValue.of(parent.getId()))
                               .build()
                               .toQuery();
        SearchRequest request = SearchRequest.of(b -> b.index(EMBEDDINGS_IDX_NAME)
                                                       .searchType(SearchType.DfsQueryThenFetch)
                                                       .query(q)
                                                       .from(0)
                                                       .size(2000));
        return oss.getClient().search(request);
    }

    SearchResponse<DocumentDTO> searchEmbeddingsForParentByTypeInEmbeddingsIndex(DocumentModel parent,
            String embeddingsType) {
        Query q = QueryBuilders.bool()
                               .must(m -> m.term(t -> t.field(DocumentConstants.SYS_ID)
                                                       .value(FieldValue.of(parent.getId()))))
                               .must(m -> m.term(t -> t.field(EMBEDDING_PROPERTY_TYPE)
                                                       .value(FieldValue.of(embeddingsType))))
                               .build()
                               .toQuery();
        SearchRequest request = SearchRequest.of(b -> b.index(EMBEDDINGS_IDX_NAME)
                                                       .searchType(SearchType.DfsQueryThenFetch)
                                                       .query(q)
                                                       .from(0)
                                                       .size(2000));
        return oss.getClient().search(request);
    }

    void assertHasEmbeddingsDocCount(int expected) {
        assertEquals(expected, searchHasEmbeddingsDocs().hits().total().value());
    }

    /**
     * Asserts that the given parent document has the expected number of embeddings children indexed.
     *
     * @param parent the parent document
     * @param expected the expected number of embeddings children
     */
    void assertEmbeddingsDocCount(DocumentModel parent, int expected) {
        assertEquals(expected, searchEmbeddingsDoc(parent).hits().total().value());
    }

    void assertEmbeddingsCount(DocumentModel parent, int expected) {
        // Count all embeddings per test child embeddings doc in embeddings index
        long expectedCount = expected * EMBEDDINGS_PER_BLOB;
        long embeddingsIndexCount = searchEmbeddingsForParentInEmbeddingsIndex(parent).hits().total().value();

        assertEquals(expectedCount, embeddingsIndexCount,
                    "Embeddings index should contain " + expectedCount + " embeddings");
    }

    void assertEmbeddingsBlobFilename(int childIndex, String expected) {
        String childName = childNameOf(childIndex);
        DocumentModel emb = session.getDocument(new PathRef(PARENT_CHILD_PREFIX + childName));
        var blob = (Blob) emb.getPropertyValue(SYSEMB_EMBEDDINGS);
        assertNotNull(blob);
        assertEquals(expected, blob.getFilename());
    }

    Timer getEmbeddingsIndexTimer() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        return registry.getTimers()
                       .get(MetricName.build("nuxeo.opensearch.service.timer").tagged("service", "embeddingsIndex"));
    }

    void assertParentTitle(String expected) {
        DocumentModel parent = session.getDocument(new PathRef(PARENT_PATH));
        assertEquals(expected, parent.getPropertyValue(DocumentConstants.SYS_TITLE));
    }

    SearchResponse<DocumentDTO> searchParentDocById(String parentId) {
        Query q = QueryBuilders.ids().values(parentId).build().toQuery();
        SearchRequest request = SearchRequest.of(
                b -> b.index(IDX_NAME).searchType(SearchType.DfsQueryThenFetch).query(q).size(1));
        return oss.getClient().search(request);
    }

    void assertParentPathInIndex(String parentId, String expectedPath) {
        SearchResponse<DocumentDTO> resp = searchParentDocById(parentId);
        assertEquals(1, resp.hits().hits().size());
        DocumentDTO dto = resp.hits().hits().get(0).source();
        assertNotNull(dto, "Parent document source missing");
        Map<String, Object> properties = dto.getProperties();
        assertNotNull(properties, "Parent document properties missing");
        Object path = properties.get(DocumentConstants.SYS_PATH);
        assertEquals(expectedPath, path, "Parent path not updated in index");
    }

    /**
     * Test that embedding documents contain denormalized parent document metadata.
     * This verifies that embeddings are indexed with parent document fields
     * to avoid expensive parent-child joins at query time.
     */
    @Test
    void testEmbeddingsContainDenormalizedParentMetadata() throws Exception {
        // given a parent document with known metadata
        String expectedTitle = "Test Document Title";
        String expectedDescription = "Test document description";
        DocumentModel parent = session.createDocumentModel("/", "parentDoc", TypeConstants.SYS_FILE);
        parent.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);
        parent.setPropertyValue(DocumentConstants.SYS_TITLE, expectedTitle);
        parent.setPropertyValue(DocumentConstants.SYS_DESCRIPTION, expectedDescription);
        parent.create();

        // Create one embeddings child
        Blob embeddingsBlob = new ParquetBlobGenerator().generate(EMBEDDINGS_PER_BLOB, "test_type");
        String embName = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("child_0");
        DocumentModel embDoc = session.createDocumentModel(
                parent.getPathAsString(), embName, TypeConstants.SYS_EMBEDDINGS);
        embDoc.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        embDoc.create();

        // when we index the embeddings
        saveAndAssertForEmbeddingsIndexCommands(1);
        assertHasEmbeddingsDocCount(1);
        assertEmbeddingsCount(parent, 1);

        // Capture expected values before querying OpenSearch (avoids transaction issues)
        String expectedPath = PARENT_PATH; // We create the doc at root
        String expectedType = parent.getType();
        String expectedRepository = parent.getRepositoryName();
        String expectedId = parent.getId();

        // then all embeddings should contain denormalized parent metadata
        SearchResponse<DocumentDTO> response = searchEmbeddingsForParentInEmbeddingsIndex(parent);
        assertEquals(EMBEDDINGS_PER_BLOB, response.hits().total().value(),
                "Embeddings index should contain " + EMBEDDINGS_PER_BLOB + " embeddings");

        // Verify each embedding contains denormalized parent document metadata
        Map<String, Object> expectedProperties = Map.of(
                DocumentConstants.SYS_ID, expectedId,
                DocumentConstants.SYS_TITLE, expectedTitle,
                DocumentConstants.SYS_DESCRIPTION, expectedDescription,
                DocumentConstants.SYS_PATH, expectedPath,
                DocumentConstants.SYS_PRIMARY_TYPE, expectedType,
                DocumentConstants.SYS_REPOSITORY, expectedRepository
        );

        response.hits().hits().forEach(hit -> {
            DocumentDTO dto = hit.source();
            assertNotNull(dto, "Embedding source should not be null");

            var actualProperties = dto.getProperties();
            assertNotNull(actualProperties, "Embedding properties should not be null");

            // Verify key denormalized fields from parent document using expectedProperties map
            expectedProperties.forEach((key, expectedValue) ->
                assertEquals(expectedValue, actualProperties.get(key),
                    "Embedding should contain parent " + key));

            // Verify ACLs are denormalized (critical for security filtering)
            assertNotNull(actualProperties.get(DocumentConstants.SYS_READ_ACL),
                         "Embedding should contain parent sys_acl (Access Control List)");
        });
    }

    @Test
    void testHasEmbeddingsIndexForConfiguredRepository() {
        // Verify that a repository with embeddings configured can resolve its embeddings index name
        assertDoesNotThrow(() -> oss.getEmbeddingsIndexNameForRepository("test"),
                "Expected embeddings index to be resolvable for repository 'test'");
        assertEquals(EMBEDDINGS_IDX_NAME, oss.getEmbeddingsIndexNameForRepository("test"),
                "Expected embeddings index name to match configured value");
    }

    @Test
    void testHasEmbeddingsIndexForRepositoryWithoutEmbeddings() {
        // Repositories without embeddings configuration should throw NoSuchElementException
        // This documents the contract that callers must guard before resolving the index name
        assertThrows(NoSuchElementException.class,
                () -> oss.getEmbeddingsIndexNameForRepository("test_vocabularies"),
                "Repositories without embeddings configuration should throw NoSuchElementException");
    }

    @Test
    void testGetEmbeddingsIndexNameThrowsForUnknownRepository() {
        assertThrows(NoSuchElementException.class,
                () -> oss.getEmbeddingsIndexNameForRepository("no_such_repo"),
                "Expected to also throw for missing index");
    }

    @Test
    void testDeleteFromRepositoryWithoutEmbeddingsDoesNotFail() throws Exception {
        // repository has no embeddings index configured
        assertThrows(NoSuchElementException.class,
                () -> oss.getEmbeddingsIndexNameForRepository("test_vocabularies"));

        CoreSession vocabSession = CoreInstance.getCoreSessionSystem("test_vocabularies");

        // given a document in a repository without embeddings support
        DocumentModel doc = vocabSession.createDocumentModel("/",
                "testVocabulary",
                TypeConstants.SYS_FILE);

        doc.create();
        vocabSession.save();

        saveAndWaitForIndexing();

        // when deleting the document
        final var docRef = doc.getRef();

        // then delete processing skips embeddings cleanup and succeeds
        assertDoesNotThrow(() -> {
            vocabSession.removeDocument(docRef);
            vocabSession.save();
            saveAndWaitForIndexing();
        });
    }
}
