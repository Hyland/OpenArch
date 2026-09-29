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
package com.hyland.openarch.ecm.core.api;

import java.util.Map;

/**
 * Writes the chunk text of an {@code onPrem} {@code SysEmbeddings} child to the on-premises EV
 * Store, asynchronously.
 * <p>
 * The embeddings parquet drives cloud indexing for every document; the chunk parquet drives the EV
 * Store, but only for documents whose {@code sysemb_residency} is {@code onPrem}. A
 * residency-aware module contributes the implementation. When no implementation is registered (a
 * cloud-only deployment) the ingestion path resolves {@code null} and simply skips the write.
 * <p>
 * Implementations enqueue the write rather than performing it inline: each call fans the supplied
 * chunks out across one or more asynchronous EV Store bulk operations (at most 50 content values
 * per call) and returns without waiting for them to complete.
 */
public interface OnPremChunkTextWriter {

    /**
     * Enqueues the chunk text for a single {@code onPrem} {@code SysEmbeddings} child. The scope is
     * addressed exactly as ADR 0045 requires: the {@code contentId} is the child's {@code sys_id}
     * (identifying both document and embedding type), the version is the fixed literal
     * {@code current}, and each map key is the embedding id used as the EV Store content value id.
     * Entries with {@code null} text or a key that is not a valid UUID are skipped.
     *
     * @param repository          the OpenArch repository name owning the documents
     * @param systemIntegrationId the system integration id supplied by the client and used to scope EV Store calls
     *                            (currently sourced from the parent's {@code cin_sourceId})
     * @param contentId           the {@code SysEmbeddings} child {@code sys_id} (EV Store content id)
     * @param documentId          the parent document id (for diagnostics and logging)
     * @param embeddingType       the embedding type carried by the child (for diagnostics and logging)
     * @param chunkTextById       embedding id to chunk text; never {@code null}
     */
    void writeChunkText(String repository, String systemIntegrationId, String contentId, String documentId,
            String embeddingType, Map<String, String> chunkTextById);
}
