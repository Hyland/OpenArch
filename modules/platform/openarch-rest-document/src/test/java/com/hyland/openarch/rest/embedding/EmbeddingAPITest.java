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
package com.hyland.openarch.rest.embedding;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.rest.embedding.EmbeddingAPI.PATH;
import static org.springframework.http.HttpMethod.GET;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.SysEmbeddingsPolicies;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.ecm.core.schema.MixinNames;
import com.hyland.openarch.ecm.core.schema.TypeConstants;
import com.hyland.openarch.opensearch.test.RepositoryOpenSearchFeature;
import com.hyland.openarch.opensearch.test.util.ParquetBlobGenerator;
import com.hyland.openarch.rest.query.test.AbstractQueryAPITest;
import com.hyland.openarch.rest.query.test.QueryAPITestConfiguration;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = { EmbeddingAPI.class, EmbeddingManager.class })
@Tag("useOpenSearch")
@Features({ RepositoryOpenSearchFeature.class })
@Import({ QueryAPITestConfiguration.class })
public class EmbeddingAPITest extends AbstractQueryAPITest {

    @RuntimeInject
    CoreSession session;

    @RuntimeInject
    TransactionalFeature txFeature;

    private DocumentModel document;

    private String embeddingId;

    @BeforeEach
    public void setup() throws IOException {
        document = createDocument();
        embeddingId = createEmbedding(document.getId());
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testGetEmbeddingByIdUnauthorized() throws Exception {
        checkResponse(UNAUTHORIZED, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), embeddingId));
    }

    @Test
    public void testGetEmbeddingById() throws Exception {
        checkResponse(OK, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), embeddingId));
    }

    @Test
    @WithUser(ALT_USER)
    public void testGetEmbeddingByIdWithAltUser() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), embeddingId));
    }

    @Test
    public void testGetEmbeddingWhenDocumentNotFound() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted("made-up-doc-id", embeddingId));
    }

    @Test
    public void testEmbeddingNotFound() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), "made-up-id"));
    }

    @Test
    public void testCannotGetEmbeddingWithInvalidId() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), ""));
        checkResponse(NOT_FOUND, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), " "));
        checkResponse(NOT_FOUND, GET, REPO_ID, PATH + "/%s/embedding/%s".formatted(document.getId(), "made-up-id"));
        checkResponse(BAD_REQUEST, GET, REPO_ID,
                PATH + "/%s/embedding/%s".formatted(document.getId(), "../some/made/up/path"));
    }

    private DocumentModel createDocument() {
        DocumentModel document = session.createDocumentModel("/", "document", SYS_FILE);
        document.create();
        session.save();
        txFeature.nextTransaction();
        return document;
    }

    private String createEmbedding(String docId) throws IOException {
        DocumentModel document = session.getDocument(new IdRef(docId));
        String embeddingId = UUID.randomUUID().toString();
        document.addMixin(MixinNames.SYS_HAS_EMBEDDINGS);

        // Generate test parquet blob with 1 embedding
        ParquetBlobGenerator parquetBlobGenerator = new ParquetBlobGenerator();
        Blob embeddingsBlob = parquetBlobGenerator.generateOneEmbedding(embeddingId, "test_type", true);

        // Create a single embeddings document with the generated blob
        String name = SysEmbeddingsPolicies.ensureEmbeddingsNamePrefix("child_1");
        DocumentModel embeddingDoc = session.createDocumentModel(document.getPathAsString(), name,
                TypeConstants.SYS_EMBEDDINGS);
        embeddingDoc.setPropertyValue(SYSEMB_EMBEDDINGS, embeddingsBlob);
        embeddingDoc.create();

        session.save();

        txFeature.nextTransaction();
        return embeddingId;
    }
}
