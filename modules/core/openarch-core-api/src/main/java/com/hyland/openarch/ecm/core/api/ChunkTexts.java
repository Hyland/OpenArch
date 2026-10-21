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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Chunk texts keyed by embedding id, plus the number of chunk rows whose raw id was empty or missing.
 *
 * @param texts      embedding id to chunk text
 * @param invalidIds number of chunk rows with an empty or missing raw id
 */
public record ChunkTexts(Map<String, String> texts, int invalidIds) {

    private static final Logger log = LogManager.getLogger(ChunkTexts.class);

    public static final ChunkTexts EMPTY = new ChunkTexts(Map.of(), 0);

    public ChunkTexts {
        texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    /**
     * Logs one error when some chunk rows had an empty or missing id.
     *
     * @param documentId  the parent document the embeddings belong to
     * @param embeddingId the SysEmbeddings document the chunks came from
     */
    public void logIfInvalid(String documentId, String embeddingId) {
        if (invalidIds > 0) {
            log.error("{} chunk rows have an empty or missing id for embedding {} (document {}); duplicate ids can "
                    + "overwrite chunk text. Fix the data and repeat the write", invalidIds, embeddingId, documentId);
        }
    }
}
