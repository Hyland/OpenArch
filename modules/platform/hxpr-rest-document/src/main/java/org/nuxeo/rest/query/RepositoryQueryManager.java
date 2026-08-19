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

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryResultDTO;
import org.nuxeo.rest.query.io.TermsAggregationQueryResultDTO;
import org.nuxeo.rest.query.spec.NamedQueryRegistry;
import org.nuxeo.rest.query.spec.QuerySpecification;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * {@link QueryManager} implementation targeting the repository API.
 */
@Component
@EnableConfigurationProperties({ QueryConfig.class })
public class RepositoryQueryManager extends AbstractQueryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryQueryManager.class);

    protected static final String NXQL_VERSIONS = "SELECT * FROM SysContent WHERE sys_parentId = '%s' AND sysver_isVersion = 1";

    protected static final String NXQL_VERSION_BY_VERSION = "SELECT * FROM SysContent WHERE sys_parentId = '%s' AND sysver_version = %d";

    protected RepositoryQueryManager(QueryConfig queryConfig, NamedQueryRegistry namedQueryRegistry,
            DocumentMapper translator) {
        super(queryConfig, namedQueryRegistry, translator);
    }

    @Override
    protected DocumentQueryResultDTO executeAdvancedQuery(DocumentAdvancedQueryDTO query) {
        QuerySpecification spec = initQuerySpecification(query);
        log.debug("Perform repository query '{}' with limit={}, offset={}", spec.query(), spec.limit(), spec.offset());

        // execute the core query
        DocumentQueryResultDTO result;
        String repositoryId = query.repositoryId();
        if (StringUtils.isBlank(repositoryId)) {
            repositoryId = Framework.getService(RepositoryManager.class).getDefaultRepositoryName();
        }
        CoreSession session = CoreInstance.getCoreSession(repositoryId);
        DocumentModelList docs = session.query(spec.query(), spec.limit(), spec.offset(), spec.countUpTo());
        long totalSize = 0;
        if (docs != null) {
            totalSize = docs.totalSize();
        }
        result = new DocumentQueryResultDTO(spec, translate(docs), totalSize);
        log.debug("Performed repository query '{}' with limit={}, offset={}: {} hits ({} total hits)",
                spec.query(), spec.limit(), spec.offset(), result.count(), result.totalCount());

        // Fire audit event for document query
        if (docs != null) {
            String queryId = UUID.randomUUID().toString();
            docs.forEach(doc -> fireDocumentQueriedEvent(session, doc, queryId));
        }
        return result;
    }

    public DocumentQueryResultDTO getDocumentVersions(String repoId, String docId, long limit, long offset) {
        String NXQLQuery = NXQL_VERSIONS.formatted(docId);
        DocumentQueryDTO query = new DocumentQueryDTO(repoId, NXQLQuery, limit, offset, null);
        return getResults(query);
    }

    public DocumentDTO getDocumentVersion(String repoId, String docId, long version) {
        String NXQLQuery = NXQL_VERSION_BY_VERSION.formatted(docId, version);
        DocumentQueryDTO query = new DocumentQueryDTO(repoId, NXQLQuery, 1L, 0, null);
        List<DocumentDTO> versions = getResults(query).documents();
        if (versions.isEmpty()) {
            throw new NuxeoException("Could not find version for document", HttpStatus.NOT_FOUND.value());
        }
        return versions.get(0);
    }

    @Override
    protected EmbeddingQueryResultDTO executeEmbeddingQuery(String repoId, String docId, String embeddingId) {
        throw new NotImplementedException("Embedding queries are not supported against the repository.");
    }

    @Override
    protected EmbeddingQueryResultDTO executeEmbeddingQuery(EmbeddingQueryDTO query) {
        throw new NotImplementedException("Embedding queries are not supported against the repository.");
    }

    @Override
    protected TermsAggregationQueryResultDTO executeTermsAggregationQuery(DocumentQueryDTO query,
            String termsAggregationField, String searchTerm, int aggregationLimit) {
        throw new NotImplementedException("Terms aggregation queries are not supported against the repository.");
    }
}
