/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.model.EmbeddingModel.VECTOR_SIZE;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_HAS_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.opensearch.OpenSearchServiceImpl.SAMPLE_AGGREGATION_NAME;
import static com.hyland.openarch.opensearch.OpenSearchServiceImpl.TERMS_AGGREGATION_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.SysEmbeddingsPolicies;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.ecm.core.model.LocationModel;
import com.hyland.openarch.ecm.core.schema.TypeConstants;
import com.hyland.openarch.opensearch.api.OpenSearchResult;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.io.DocumentDTO;
import com.hyland.openarch.opensearch.query.OpenSearchQueryBuilder;
import com.hyland.openarch.opensearch.test.util.ParquetBlobGenerator;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

import com.hyland.openarch.async.tasks.TaskService;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
@Deploy("com.hyland.openarch.opensearch.core:opensearch-test-nested-mapping-contrib.xml")
@Deploy("com.hyland.openarch.opensearch.core:schemas-test-contrib.xml")
public class TestOpenSearchQuery {

    public static final String ADVANCED_SEARCH_DOC_TYPE = "AdvancedSearch";

    public static final String SEARCH_TITLE_FIELD_PATH = "search_title";

    public static final String SEARCH_IS_CHECKED_IN_VERSION_FIELD_PATH = "search_isCheckedInVersion";

    public static final String SEARCH_NATURE_FIELD_PATH = "search_nature";

    public static final String SEARCH_CREATED_MIN_FIELD_PATH = "search_created_min";

    public static final String TEST_EMBEDDING_TYPE = "TEST_EMBEDDING_TYPE";

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected OpenSearchService oss;

    @Inject
    protected TaskService asyncTasksService;

    private int commandProcessed;

    // Number of processed command since the startTransaction
    public void assertNumberOfCommandProcessed(int processed) throws Exception {
        assertEquals(processed, oss.getTotalCommandProcessed() - commandProcessed);
    }

    /**
     * Extracts aggregation bucket map from OpenSearch result.
     * Verifies the aggregation structure and returns a map of bucket keys to document counts.
     */
    private Map<String, Long> extractBucketMap(OpenSearchResult result) {
        Map<String, Aggregate> aggregations = result.getOpenSearchResponse().aggregations();
        assertNotNull(aggregations);
        assertTrue(aggregations.containsKey(SAMPLE_AGGREGATION_NAME));

        Aggregate topNSample = aggregations.get(SAMPLE_AGGREGATION_NAME);
        Aggregate topOccurrences = topNSample.sampler().aggregations().get(TERMS_AGGREGATION_NAME);
        assertNotNull(topOccurrences);

        Map<String, Long> bucketMap = new HashMap<>();
        topOccurrences.sterms().buckets().array().forEach(bucket -> bucketMap.put(bucket.key(), bucket.docCount()));
        return bucketMap;
    }

    @BeforeEach
    public void setupIndex() throws Exception {
        oss.initIndexes(true);
    }

    @Test
    public void searchWithCorrelation() throws Exception {
        createDocumentWithFiles();

        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent"));
        assertEquals(1, ret.totalSize());
        // search without correlation works
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                "SELECT * FROM SysContent WHERE sysfiles_blobs/*/blob/filename = 'testfile1.txt' AND sysfiles_blobs/*/blob/length=3"));
        assertEquals(1, ret.totalSize());
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                "SELECT * FROM SysContent WHERE sysfiles_blobs/blob/filename = 'testfile1.txt' AND sysfiles_blobs/blob/length=3"));
        assertEquals(1, ret.totalSize());
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                "SELECT * FROM SysContent WHERE sysfiles_blobs/blob/filename = 'testfile4.txt' AND sysfiles_blobs/blob/length=3"));
        assertEquals(1, ret.totalSize());
        // search with correlation is not supported in NXQL
        ret = oss.query(new OpenSearchQueryBuilder(session).hxql(
                "SELECT * FROM SysContent WHERE sysfiles_blobs/*1/blob/filename = 'testfile3.txt' AND sysfiles_blobs/*1/blob/length=3"));
        assertEquals(0, ret.totalSize()); // TODO should be 1 when implemented
    }

    @Test
    public void testSecurity() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "aFile", SYS_FILE);
        doc.create();
        session.save();
        txFeature.nextTransaction();

        // match the document with system user
        DocumentModelList ret = oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent"));
        assertEquals(1, ret.totalSize());

        // no match for unknown user
        CoreSession restrictedSession = CoreInstance.getCoreSession(session.getRepositoryName(),
                DummyLoginFeature.getTestPrincipal("bob", false));
        ret = oss.query(new OpenSearchQueryBuilder(restrictedSession).hxql("SELECT * FROM SysContent"));
        assertEquals(0, ret.totalSize());
    }

    @Test
    public void testEmbeddingQuery() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "aFile", SYS_FILE);
        doc.addMixin(SYS_HAS_EMBEDDINGS);
        doc.create();
        // Add vectors like (0, 0, 0, ...), (0.1, 0.1, 0.1, ...), (0.2, 0.2, 0.2, ...), etc.
        String embeddingId = UUID.randomUUID().toString();
        String embeddingIdPrefix = embeddingId.substring(0, embeddingId.length() - 1);
        List<EmbeddingModel> rawEmbeddings = IntStream.range(0, 5).mapToObj(i -> {
            double d = (double) i / 10;
            List<Double> vector = makeVectorFilledWith(d);
            return new EmbeddingModel(embeddingIdPrefix + i, session.getRepositoryName(), doc.getId(),
                    TEST_EMBEDDING_TYPE, vector, "This is the chunk for vector #" + i, new LocationModel(1, i), null);
        }).toList();
        Blob embeddingsBlob = new ParquetBlobGenerator().generateFromEmbeddingModels(rawEmbeddings);
        // Create a SysEmbeddings document with the generated blob
        String name = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix(TEST_EMBEDDING_TYPE);
        DocumentModel embeddingDoc = session.createDocumentModel(
                doc.getPathAsString(),
                name,
                TypeConstants.SYS_EMBEDDINGS
        );
        embeddingDoc.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        embeddingDoc.create();

        session.save();
        txFeature.nextTransaction();

        // Run a knn query for the embedding. 0.11 is close to 0.1, so we expect "embedding_id_0.1" to be returned.
        List<Double> targetVector = makeVectorFilledWith(0.11);
        OpenSearchResult result = oss.queryAndAggregate(
                new OpenSearchQueryBuilder(session).embeddingQuery(TEST_EMBEDDING_TYPE, targetVector, null, "SELECT * FROM SysContent"));
        HitsMetadata<DocumentDTO> hits = result.getOpenSearchResponse().hits();
        assertEquals(5, hits.total().value());
        assertEquals(embeddingIdPrefix + "1", hits.hits().get(0).id(), "Unexpected closest embedding returned");
    }

    private List<Double> makeVectorFilledWith(double d) {
        return IntStream.range(0, VECTOR_SIZE).mapToObj(x -> d).toList();
    }

    @Test
    public void testExceptionReturnedByOpenSearch() {
        var exception = assertThrows(RepositoryException.class, () -> {
            oss.query(new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent WHERE sys_name LIKE 'File%'"));
        });
        assertEquals(
                "Error performing OpenSearch query: failed to create query: Can only use phrase prefix queries on text fields - not on [sys_name] which is of type [keyword]",
                exception.getMessage());
    }

    protected void createDocumentWithFiles() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "myFile", SYS_FILE);
        // create doc with a list of blob attachment (textfile1.txt length=1,textfile2.txt length=2, ...)
        var blobs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Blob blob = Blobs.createBlob(new String(new char[i]).replace('\0', 'a'));
            blob.setFilename(String.format("testfile%d.txt", i));
            blobs.add(Map.of("blob", blob));
        }
        doc.setPropertyValue("sysfiles_blobs", blobs);
        doc.create();
        session.save();
        txFeature.nextTransaction();
    }

    @Test
    public void testSearchWithAggregationsOnStringField() throws Exception {
        // Create documents with title field (single string)
        DocumentModel doc1 = session.createDocumentModel("/", "file1", ADVANCED_SEARCH_DOC_TYPE);
        doc1.setPropertyValue(SEARCH_TITLE_FIELD_PATH, "Important Report Document");
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/", "file2", ADVANCED_SEARCH_DOC_TYPE);
        doc2.setPropertyValue(SEARCH_TITLE_FIELD_PATH, "Technical Report Analysis");
        doc2.create();

        DocumentModel doc3 = session.createDocumentModel("/", "file3", ADVANCED_SEARCH_DOC_TYPE);
        doc3.setPropertyValue(SEARCH_TITLE_FIELD_PATH, "Technical Manual Guide");
        doc3.create();

        session.save();
        txFeature.nextTransaction();

        // Search with aggregations on title field
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent");
        OpenSearchResult result = oss.queryWithTermsAggregation(queryBuilder, SEARCH_TITLE_FIELD_PATH,
                "tEchNiCaL", 5);

        // Verify aggregation results
        Map<String, Long> bucketMap = extractBucketMap(result);

        // Verify counts - should match titles starting with "tEchNiCaL"
        Map<String, Long> expected = Map.of(
                        "Technical Report Analysis", 1L,
                        "Technical Manual Guide", 1L);
        assertEquals(expected, bucketMap);
    }

    @Test
    public void testSearchWithAggregationsOnStringListField() throws Exception {
        // Create documents with nature field (array of strings)
        DocumentModel doc1 = session.createDocumentModel("/", "file1", ADVANCED_SEARCH_DOC_TYPE);
        doc1.setPropertyValue(SEARCH_NATURE_FIELD_PATH, List.of("article", "report", "documentation"));
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/", "file2", ADVANCED_SEARCH_DOC_TYPE);
        doc2.setPropertyValue(SEARCH_NATURE_FIELD_PATH, List.of("article", "whitepaper"));
        doc2.create();

        DocumentModel doc3 = session.createDocumentModel("/", "file3", ADVANCED_SEARCH_DOC_TYPE);
        doc3.setPropertyValue(SEARCH_NATURE_FIELD_PATH, List.of("report", "presentation"));
        doc3.create();

        session.save();
        txFeature.nextTransaction();

        // Search with aggregations on nature field
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent");
        OpenSearchResult result = oss.queryWithTermsAggregation(queryBuilder, SEARCH_NATURE_FIELD_PATH, "report", 5);

        // Verify aggregation results
        Map<String, Long> bucketMap = extractBucketMap(result);

        // Verify counts : no post processing of the aggregation result is expected, so the counts should match the
        // number of occurrences of "report" in the nature field values
        Map<String, Long> expected = Map.of("report", 2L, "presentation", 1L, "article", 1L, "documentation", 1L);
        assertEquals(expected, bucketMap);
    }


    @Test
    public void testSearchWithAggregationsOnBooleanField() throws Exception {
        // Create documents with isCheckedInVersion field (boolean)
        DocumentModel doc1 = session.createDocumentModel("/", "file1", ADVANCED_SEARCH_DOC_TYPE);
        doc1.setPropertyValue(SEARCH_IS_CHECKED_IN_VERSION_FIELD_PATH, true);
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/", "file2", ADVANCED_SEARCH_DOC_TYPE);
        doc2.setPropertyValue(SEARCH_IS_CHECKED_IN_VERSION_FIELD_PATH, true);
        doc2.create();

        DocumentModel doc3 = session.createDocumentModel("/", "file3", ADVANCED_SEARCH_DOC_TYPE);
        doc3.setPropertyValue(SEARCH_IS_CHECKED_IN_VERSION_FIELD_PATH, false);
        doc3.create();

        session.save();
        txFeature.nextTransaction();

        // Search with aggregations on boolean field
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent");

        assertThrows(RepositoryException.class, () ->  oss.queryWithTermsAggregation(queryBuilder, SEARCH_IS_CHECKED_IN_VERSION_FIELD_PATH, "true", 5));
    }
    @Test
    public void testSearchWithAggregationsOnDateField() throws Exception {
        // Create documents with created_min field (date)
        DocumentModel doc1 = session.createDocumentModel("/", "file1", ADVANCED_SEARCH_DOC_TYPE);
        doc1.setPropertyValue(SEARCH_CREATED_MIN_FIELD_PATH, "2023-01-15");
        doc1.create();

        DocumentModel doc2 = session.createDocumentModel("/", "file2", ADVANCED_SEARCH_DOC_TYPE);
        doc2.setPropertyValue(SEARCH_CREATED_MIN_FIELD_PATH, "2023-01-20");
        doc2.create();

        DocumentModel doc3 = session.createDocumentModel("/", "file3", ADVANCED_SEARCH_DOC_TYPE);
        doc3.setPropertyValue(SEARCH_CREATED_MIN_FIELD_PATH, "2024-01-15");
        doc3.create();

        session.save();
        txFeature.nextTransaction();

        // Search with aggregations on date field (matching 2023 dates)
        OpenSearchQueryBuilder queryBuilder = new OpenSearchQueryBuilder(session).hxql("SELECT * FROM SysContent");
        OpenSearchResult result = oss.queryWithTermsAggregation(queryBuilder, SEARCH_CREATED_MIN_FIELD_PATH,
                "2023", 5);

        // Verify aggregation results
        Map<String, Long> bucketMap = extractBucketMap(result);

        // Verify counts - should match dates containing "2023"
        Map<String, Long> expected = Map.of(
                "2023-01-15T00:00:00Z", 1L,
                "2023-01-20T00:00:00Z", 1L);
        assertEquals(expected, bucketMap);
    }
}
