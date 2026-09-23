/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.query.test;

import static java.util.stream.Collectors.toList;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.model.EmbeddingModel.VECTOR_SIZE;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_HAS_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.rest.query.test.EmbeddingQueryAPITest.ALT_USER_WITH_RESTRICTED_ACCESS;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.nuxeo.ecm.core.SysEmbeddingsPolicies;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.model.EmbeddingModel;
import org.nuxeo.ecm.core.model.LocationModel;
import com.hyland.openarch.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.opensearch.test.util.ParquetBlobGenerator;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.hyland.hxpr.async.tasks.TaskService;

@Features(RepositoryOpenSearchFeature.class)
@RepositoryConfig(cleanup = Granularity.CLASS)
public class RestQueryMultipleEmbeddingsFeature implements RunnerFeature {

    static final String EMBEDDING_TYPE = "some_embedding_type_123";

    static final List<String> englishWords = List.of("anteater", "bear", "cheetah", "dingo", "elephant", "frog",
            "giraffe", "hog", "iguana", "jaguar");

    FeaturesLoader loader;

    CoreFeature core;

    TransactionalFeature tx;

    List<DocumentModel> docs;

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void start() {
        core = loader.getFeature(CoreFeature.class);
        tx = loader.getFeature(TransactionalFeature.class);
    }

    @Override
    public void beforeRun() {
        OpenSearchService oss = Framework.getService(OpenSearchService.class);
        oss.initIndexes(true);
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        docs = createDocuments(core.getCoreSession());
        // this will wait for indexing completion through the waiter added by RepositoryOpenSearchFeature
        tx.nextTransaction();
        TransactionHelper.commitOrRollbackTransaction();
    }

    @Override
    public void afterRun() {
        if (docs != null) {
            if (!TransactionHelper.isTransactionActive()) {
                TransactionHelper.startTransaction();
            }
            cleanupDocuments(core.getCoreSession(), docs);
            docs = null;
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    protected List<DocumentModel> createDocuments(CoreSession session) {
        List<DocumentModel> docs = new ArrayList<>();
        for (int docIndex = 0; docIndex < 10; docIndex++) {
            String name = "doc" + docIndex;
            DocumentModel doc = session.createDocumentModel("/", name, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "File" + docIndex);
            doc.setPropertyValue("mydc_source", "Source " + docIndex);
            doc.setPropertyValue("mydc_nature", "Nature" + docIndex % 2);
            doc.setPropertyValue("mydc_coverage", "Coverage" + docIndex % 3);
            Blob blob = new StringBlob("x".repeat(1024 * docIndex));
            blob.setFilename("file" + docIndex + ".txt");
            doc.setPropertyValue(SYSFILE_BLOB, blob);

            // Set ACL to allow alt user to read document with index 2
            if (docIndex == 2) {
                ACL customAcl = doc.getLocalACL();
                customAcl.add(
                        ACE.builder().user(ALT_USER_WITH_RESTRICTED_ACCESS).permission(SecurityConstants.READ).build());
                doc.setACL(customAcl, true);
            }

            // Create embeddings via Parquet files
            doc.addMixin(SYS_HAS_EMBEDDINGS);
            doc.create();

            try {
                addParquetEmbeddings(session, doc, docIndex);
            } catch (IOException e) {
                throw new NuxeoException("Failed to create Parquet embeddings for doc " + docIndex, e);
            }

            try {
                Thread.sleep(10); // NOSONAR sleep a bit to have different sys_modified
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NuxeoException("Interrupted", e);
            }
            docs.add(doc);
        }
        session.save();
        return docs;
    }

    private void addParquetEmbeddings(CoreSession session, DocumentModel doc, int docIndex) throws IOException {
        ParquetBlobGenerator parquetBlobGenerator = new ParquetBlobGenerator();

        // Generate 10 embeddings for the document with a mix of text, vector, and location data
        List<EmbeddingModel> embeddings = IntStream.range(0, 10)
                                                   .mapToObj(embeddingIndex -> createEmbedding(doc.getId(), docIndex,
                                                           embeddingIndex))
                                                   .toList();

        // Generate the Parquet blob with all embeddings
        Blob embeddingsBlob = parquetBlobGenerator.generateFromEmbeddingModels(embeddings);

        // Create a SysEmbeddings document with the generated blob
        String name = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix(EMBEDDING_TYPE);
        DocumentModel embeddingDoc = session.createDocumentModel(doc.getPathAsString(), name,
                TypeConstants.SYS_EMBEDDINGS);
        embeddingDoc.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        embeddingDoc.create();
    }

    private EmbeddingModel createEmbedding(String docId, int docIndex, int embeddingIndex) {
        List<Double> vector = createVector(docIndex, embeddingIndex);
        String phrase = createPhrase(docIndex, embeddingIndex);
        String text = "This is the text found in doc %d and page %d and a phrase %s".formatted(docIndex, embeddingIndex,
                phrase);
        LocationModel location = new LocationModel(docIndex, 0);
        // Generate deterministic UUID based on docIndex and embeddingIndex for test reproducibility
        String embeddingId = UUID.nameUUIDFromBytes(("doc" + docIndex + "-emb" + embeddingIndex).getBytes()).toString();
        return new EmbeddingModel(embeddingId, "default", // repository name
                docId, EMBEDDING_TYPE, vector, text, location, null // blobref not used in this test
        );
    }

    private List<Double> createVector(int docIndex, int embeddingIndex) {
        // Generate a reproducible, but random looking vector for the given document and embedding.
        return IntStream.range(0, VECTOR_SIZE)
                        .map(x -> (137 * x + 173 * docIndex + 317 * embeddingIndex) % 1000)
                        .mapToObj(x -> Double.valueOf(x) / 1000)
                        .collect(toList());
    }

    private String createPhrase(int docIndex, int embeddingIndex) {
        return IntStream.range(0, 3)
                        .map(x -> (137 * x + 173 * docIndex + 317 * embeddingIndex) % englishWords.size())
                        .mapToObj(englishWords::get)
                        .collect(Collectors.joining(" "));
    }

    private Map<String, Object> locationToMap(LocationModel location) {
        Map<String, Object> map = new HashMap<>();
        if (location.text() != null) {
            map.put("text", Map.of("page", location.text().page(), "paragraph", location.text().paragraph()));
        }
        if (location.position() != null) {
            map.put("position", Map.of("left", location.position().left(), "top", location.position().top(), "right",
                    location.position().right(), "bottom", location.position().bottom()));
        }
        if (location.timestamp() != null) {
            map.put("timestamp", Map.of("start", location.timestamp().start(), "end", location.timestamp().end()));
        }
        if (location.spreadsheet() != null) {
            map.put("spreadsheet", Map.of("sheet", location.spreadsheet().sheet(), "row", location.spreadsheet().row(),
                    "column", location.spreadsheet().column()));
        }
        return map;
    }

    protected void cleanupDocuments(CoreSession session, List<DocumentModel> docs) {
        if (docs != null) {
            docs.stream().map(DocumentModel::getId).forEach(id -> session.removeDocument(new IdRef(id)));
            session.save();
        }
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() {
        try {
            Framework.getService(TaskService.class).awaitCompletion(Duration.ofSeconds(20));
            OpenSearchService oss = Framework.getService(OpenSearchService.class);
            oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
            oss.refresh();
        } catch (InterruptedException e) {
            throw new NuxeoException("Interrupted while waiting for completion", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new NuxeoException("Error waiting for completion", e);
        }
    }
}
