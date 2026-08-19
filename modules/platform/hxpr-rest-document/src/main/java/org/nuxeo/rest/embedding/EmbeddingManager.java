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
package org.nuxeo.rest.embedding;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.io.EmbeddingQueryResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingManager {

    @Autowired
    OpenSearchQueryManager osqm;

    public EmbeddingRestDTO getEmbedding(String repoId, String docId, String embeddingId) {
        EmbeddingQueryResultDTO results = osqm.getResults(repoId, docId, embeddingId);
        List<EmbeddingRestDTO> embeddings = results.embeddings();
        if (embeddings.isEmpty()) {
            throw new NuxeoException("Could not find embedding with id " + embeddingId + " for document " + docId,
                    NOT_FOUND.value());
        }
        return embeddings.getFirst();
    }

}
