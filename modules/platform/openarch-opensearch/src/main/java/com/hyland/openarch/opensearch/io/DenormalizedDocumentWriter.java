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
package com.hyland.openarch.opensearch.io;


import com.hyland.openarch.ecm.core.api.DocumentModel;
import org.opensearch.client.json.JsonpMapper;

import jakarta.json.stream.JsonGenerator;

/**
 * Specialized writer for denormalized document data in embeddings index.
 * <p>
 * This writer extends {@link JsonOpenSearchDocumentWriter} but excludes certain fields that are not needed in
 * denormalized embedding documents:
 * <ul>
 * <li>Fulltext binary content - too expensive to denormalize</li>
 * </ul>
 */
public class DenormalizedDocumentWriter extends JsonOpenSearchDocumentWriter {

    public DenormalizedDocumentWriter(JsonGenerator jg, JsonpMapper mapper) {
        super(jg, mapper);
    }

    @Override
    protected void writeFulltextBinary(DocumentModel doc) {
        // Do nothing - exclude fulltext from denormalized data
    }
}
