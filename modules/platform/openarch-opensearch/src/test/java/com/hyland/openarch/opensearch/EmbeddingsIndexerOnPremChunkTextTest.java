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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.DocumentConstants;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.OnPremChunkTextWriter;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.opensearch.EmbeddingsIndexer.ProcessingContext;
import com.hyland.openarch.opensearch.io.EmbeddingsParser;
import com.hyland.openarch.runtime.api.Framework;

class EmbeddingsIndexerOnPremChunkTextTest {

    private static final String REPOSITORY = "default";
    private static final String EMBEDDINGS_DOC_ID = "emb-1";
    private static final String PARENT_DOC_ID = "parent-1";
    private static final String EMBEDDING_TYPE = "type-a";
    private static final String TENANT = "tenant-1";

    private final EmbeddingsParser parser = Mockito.mock(EmbeddingsParser.class);

    // The method only inspects isEmpty(), never dereferences elements.
    private static final Collection<EmbeddingModel> SOME_EMBEDDINGS = Collections.singletonList(null);

    @Test
    void writesChunkTextForOnPremIngest() {
        DocumentModel embeddingsDoc = Mockito.mock(DocumentModel.class);
        DocumentModel parentDoc = Mockito.mock(DocumentModel.class);
        Blob chunksBlob = Mockito.mock(Blob.class);
        when(embeddingsDoc.getId()).thenReturn(EMBEDDINGS_DOC_ID);
        when(embeddingsDoc.getPropertyValue(DocumentConstants.SYSEMB_CHUNKS)).thenReturn(chunksBlob);
        when(parentDoc.getId()).thenReturn(PARENT_DOC_ID);
        when(parentDoc.getPropertyValue(DocumentConstants.CIN_SOURCE_ID)).thenReturn(TENANT);
        Map<String, String> chunks = Map.of("id-1", "text-1");
        when(parser.parseChunkTexts(chunksBlob, EMBEDDING_TYPE)).thenReturn(chunks);

        ProcessingContext ctx = context(embeddingsDoc, parentDoc, DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OnPremChunkTextWriter.class)).thenReturn(writer);
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, false);
        }

        verify(writer).writeChunkText(REPOSITORY, TENANT, EMBEDDINGS_DOC_ID, PARENT_DOC_ID, EMBEDDING_TYPE, chunks);
    }

    @Test
    void skipsWhenReindexing() {
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);
        ProcessingContext ctx = context(Mockito.mock(DocumentModel.class), Mockito.mock(DocumentModel.class),
                DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, true);
            framework.verifyNoInteractions();
        }

        verifyNoInteractions(writer);
    }

    @Test
    void skipsWhenNoEmbeddings() {
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);
        ProcessingContext ctx = context(Mockito.mock(DocumentModel.class), Mockito.mock(DocumentModel.class),
                DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, Collections.emptyList(), false);
            framework.verifyNoInteractions();
        }

        verifyNoInteractions(writer);
    }

    @Test
    void skipsForCloudResidency() {
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);
        ProcessingContext ctx = context(Mockito.mock(DocumentModel.class), Mockito.mock(DocumentModel.class),
                DocumentConstants.SYSEMB_RESIDENCY_CLOUD);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, false);
            framework.verifyNoInteractions();
        }

        verifyNoInteractions(writer);
    }

    @Test
    void skipsWhenNoWriterRegistered() {
        DocumentModel embeddingsDoc = Mockito.mock(DocumentModel.class);
        when(embeddingsDoc.getId()).thenReturn(EMBEDDINGS_DOC_ID);
        ProcessingContext ctx = context(embeddingsDoc, Mockito.mock(DocumentModel.class),
                DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OnPremChunkTextWriter.class)).thenReturn(null);
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, false);
        }

        verify(parser, never()).parseChunkTexts(any(), any());
    }

    @Test
    void skipsWhenNoChunksBlob() {
        DocumentModel embeddingsDoc = Mockito.mock(DocumentModel.class);
        when(embeddingsDoc.getId()).thenReturn(EMBEDDINGS_DOC_ID);
        when(embeddingsDoc.getPropertyValue(DocumentConstants.SYSEMB_CHUNKS)).thenReturn(null);
        ProcessingContext ctx = context(embeddingsDoc, Mockito.mock(DocumentModel.class),
                DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OnPremChunkTextWriter.class)).thenReturn(writer);
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, false);
        }

        verify(parser, never()).parseChunkTexts(any(), any());
        verifyNoInteractions(writer);
    }

    @Test
    void skipsWhenNoChunkTextsParsed() {
        DocumentModel embeddingsDoc = Mockito.mock(DocumentModel.class);
        Blob chunksBlob = Mockito.mock(Blob.class);
        when(embeddingsDoc.getId()).thenReturn(EMBEDDINGS_DOC_ID);
        when(embeddingsDoc.getPropertyValue(DocumentConstants.SYSEMB_CHUNKS)).thenReturn(chunksBlob);
        when(parser.parseChunkTexts(chunksBlob, EMBEDDING_TYPE)).thenReturn(Map.of());
        ProcessingContext ctx = context(embeddingsDoc, Mockito.mock(DocumentModel.class),
                DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OnPremChunkTextWriter.class)).thenReturn(writer);
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, false);
        }

        verifyNoInteractions(writer);
    }

    @Test
    void skipsWhenSystemIntegrationIdMissing() {
        DocumentModel embeddingsDoc = Mockito.mock(DocumentModel.class);
        DocumentModel parentDoc = Mockito.mock(DocumentModel.class);
        Blob chunksBlob = Mockito.mock(Blob.class);
        when(embeddingsDoc.getId()).thenReturn(EMBEDDINGS_DOC_ID);
        when(embeddingsDoc.getPropertyValue(DocumentConstants.SYSEMB_CHUNKS)).thenReturn(chunksBlob);
        when(parentDoc.getId()).thenReturn(PARENT_DOC_ID);
        when(parentDoc.getPropertyValue(DocumentConstants.CIN_SOURCE_ID)).thenReturn("  ");
        when(parser.parseChunkTexts(chunksBlob, EMBEDDING_TYPE)).thenReturn(Map.of("id-1", "text-1"));
        ProcessingContext ctx = context(embeddingsDoc, parentDoc, DocumentConstants.SYSEMB_RESIDENCY_ON_PREM);
        OnPremChunkTextWriter writer = Mockito.mock(OnPremChunkTextWriter.class);

        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OnPremChunkTextWriter.class)).thenReturn(writer);
            EmbeddingsIndexer.writeOnPremChunkText(parser, REPOSITORY, ctx, SOME_EMBEDDINGS, false);
        }

        verify(writer, never()).writeChunkText(any(), any(), any(), any(), any(), any());
    }

    private static ProcessingContext context(DocumentModel embeddingsDoc, DocumentModel parentDoc, String residency) {
        return new ProcessingContext(embeddingsDoc, parentDoc, EMBEDDING_TYPE, residency, Set.of());
    }
}
