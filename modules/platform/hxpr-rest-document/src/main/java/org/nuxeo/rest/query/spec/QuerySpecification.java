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
package org.nuxeo.rest.query.spec;

import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.WhereClauseDTO;

/**
 * Internal representation of a query specification.
 * <p>
 * This eventually helps building a {@link DocumentAdvancedQueryDTO} from other kinds of queries.
 */
public record QuerySpecification( //
                                  String repositoryId, //
                                  String query, //
                                  long limit, //
                                  long offset, //
                                  String searchAfter, //
                                  long countUpTo, //
                                  boolean trackTotalCount, //
                                  int trackTotalCountUpTo, //
                                  WhereClauseDTO whereClauseDefinition, //
                                  Map<String, Object> parameters, //
                                  List<String> sort, //
                                  DocumentModel searchDocument) {

    public QuerySpecification {
        if (parameters == null) {
            parameters = Map.of();
        }
        if (sort == null) {
            sort = List.of();
        }
    }

    public QuerySpecification(DocumentQueryDTO query, String queryString, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo, DocumentModel searchDocument) {
        this(query.repositoryId(), queryString, limit, query.offset(), query.searchAfter(), countUpTo, trackTotalCount,
                trackTotalCountUpTo, null, null, query.sort(), searchDocument);
    }

    public QuerySpecification(DocumentAdvancedQueryDTO query, String queryString, long limit, long countUpTo,
            boolean trackTotalCount, int trackTotalCountUpTo, DocumentModel searchDocument) {
        this(query.repositoryId(), queryString, limit, query.offset(), query.searchAfter(), countUpTo, trackTotalCount,
                trackTotalCountUpTo, query.whereClauseDefinition(), query.parameters(), query.sort(), searchDocument);
    }

}
