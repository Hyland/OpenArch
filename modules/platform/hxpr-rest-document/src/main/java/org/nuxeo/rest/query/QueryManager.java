/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.query;

import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentNamedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryResultDTO;
import org.nuxeo.rest.query.io.TermsAggregationQueryResultDTO;

/**
 * Interface for queries handling.
 */
public interface QueryManager {

    DocumentQueryResultDTO getResults(DocumentQueryDTO query);

    DocumentQueryResultDTO getResults(DocumentAdvancedQueryDTO query);

    DocumentQueryResultDTO getResults(DocumentNamedQueryDTO query);

    EmbeddingQueryResultDTO getResults(String repoId, String docId, String embeddingId);

    EmbeddingQueryResultDTO getResults(EmbeddingQueryDTO query);

    TermsAggregationQueryResultDTO getResults(DocumentQueryDTO query, String termsAggregationField, String searchTerm,
            int aggregationLimit);
}
