/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.ChunkTexts;
import com.hyland.openarch.ecm.core.api.DocumentConstants;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.OnPremChunkTextWriter;
import com.hyland.openarch.opensearch.api.OpenSearchClient;
import com.hyland.openarch.opensearch.commands.IndexingCommand;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import org.opensearch.client.opensearch.core.BulkRequest;

/**
 * Runtime-backed test of {@link EmbeddingsIndexer#process} for an on-prem child with no embeddings and no existing ids.
 * <p>
 * Needs the runtime because {@link OpenSearchServiceImpl} cannot be mocked until its static initializer can read
 * Framework properties.
 * </p>
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
class EmbeddingsIndexerProcessTest {

    private static final String REPOSITORY = "default";
    private static final String EMBEDDINGS_DOC_ID = "emb-1";
    private static final String PARENT_DOC_ID = "parent-1";
    private static final String EMBEDDING_TYPE = "type-a";
    private static final String TENANT = "tenant-1";

    @Test
    void forwardsEmptyChunkTextWithoutBulkWhenNoEmbeddingsAndNoExistingIds() {
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);
        OpenSearchClient client = Mockito.mock(OpenSearchClient.class);
        DocumentModel embeddingsDoc = Mockito.mock(DocumentModel.class);
        DocumentModel parentDoc = Mockito.mock(DocumentModel.class);
        IdRef parentRef = new IdRef(PARENT_DOC_ID);
        when(embeddingsDoc.getRepositoryName()).thenReturn(REPOSITORY);
        when(embeddingsDoc.getId()).thenReturn(EMBEDDINGS_DOC_ID);
        when(embeddingsDoc.getName()).thenReturn("_e_" + EMBEDDING_TYPE);
        when(embeddingsDoc.getParentRef()).thenReturn(parentRef);
        when(embeddingsDoc.getPropertyValue(DocumentConstants.SYSEMB_RESIDENCY)).thenReturn(DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);
        when(embeddingsDoc.getPropertyValue(DocumentConstants.SYSEMB_CHUNKS)).thenReturn(Mockito.mock(Blob.class));
        when(parentDoc.getId()).thenReturn(PARENT_DOC_ID);
        when(parentDoc.getPropertyValue(DocumentConstants.CIN_SOURCE_ID)).thenReturn(TENANT);
        IndexingCommand cmd = new IndexingCommand(embeddingsDoc, IndexingCommand.Type.INSERT, false, false);

        OpenSearchServiceImpl service = Mockito.mock(OpenSearchServiceImpl.class);
        when(service.getClient()).thenReturn(client);
        doReturn(embeddingsDoc).when(service).getTargetDocument(REPOSITORY, EMBEDDINGS_DOC_ID);
        doReturn(parentDoc).when(service).getTargetDocument(REPOSITORY, parentRef);
        EmbeddingsIndexer indexer = Mockito.spy(new EmbeddingsIndexer(service));
        doReturn(Set.of()).when(indexer).fetchExistingIds(anyString(), anyString(), any());

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OnPremChunkTextWriter.class)).thenReturn(writer);
            indexer.process(cmd, false, Integer.MAX_VALUE);
        }

        verify(writer).writeChunkText(REPOSITORY, TENANT, EMBEDDINGS_DOC_ID, PARENT_DOC_ID, EMBEDDING_TYPE, ChunkTexts.EMPTY);
        verify(client, never()).bulk(any(BulkRequest.class));
    }
}
