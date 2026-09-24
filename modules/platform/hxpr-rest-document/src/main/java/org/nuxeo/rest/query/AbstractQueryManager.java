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
package org.nuxeo.rest.query;

import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_QUERIED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Objects;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.event.CoreEventConstants;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.ecm.core.event.impl.EventContextImpl;
import com.hyland.openarch.ecm.core.event.impl.query.QueryEvent;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentNamedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryResultDTO;
import org.nuxeo.rest.query.io.TermsAggregationQueryResultDTO;
import org.nuxeo.rest.query.spec.NamedQueryDefinition;
import org.nuxeo.rest.query.spec.NamedQueryRegistry;
import org.nuxeo.rest.query.spec.QueryAssembler;
import org.nuxeo.rest.query.spec.QueryAssembler.AssembledQuery;
import org.nuxeo.rest.query.spec.QuerySpecification;
import com.hyland.openarch.runtime.api.Framework;


public abstract class AbstractQueryManager implements QueryManager {

    private final QueryConfig queryConfig;

    private final NamedQueryRegistry namedQueryRegistry;

    private final DocumentMapper translator;

    protected AbstractQueryManager(QueryConfig queryConfig, NamedQueryRegistry namedQueryRegistry,
            DocumentMapper translator) {
        this.queryConfig = queryConfig;
        this.namedQueryRegistry = namedQueryRegistry;
        this.translator = translator;
    }

    protected QueryConfig getQueryConfig() {
        return queryConfig;
    }

    protected long getLimit(long requestedLimit) {
        long confLimit = getQueryConfig().limit();
        if (confLimit > 0 && requestedLimit > confLimit) {
            return confLimit;
        }
        return requestedLimit;
    }

    protected long getCountUpTo() {
        return queryConfig.countUpTo();
    }

    protected int getTrackTotalCountUpTo(Integer trackTotalCountUpTo) {
        return trackTotalCountUpTo != null ? trackTotalCountUpTo : getQueryConfig().trackTotalCountUpTo();
    }

    protected boolean getTrackTotalCount(Boolean trackTotalCount) {
        return trackTotalCount != null ? trackTotalCount : getQueryConfig().trackTotalCount();
    }

    protected void checkResultWindow(long limit, long offset) {
        long max = getQueryConfig().maxResultWindow();
        if (max > 0 && limit + offset > max) {
            throw new RepositoryException(
                    String.format("Error performing query: limit (%s) + offset (%s) should be <= %s (%s)", limit,
                            offset, max, limit + offset),
                    BAD_REQUEST.value());
        }
    }

    protected QuerySpecification initQuerySpecification(DocumentQueryDTO query) {
        return initQuerySpecification(new DocumentAdvancedQueryDTO(query));
    }

    protected QuerySpecification initQuerySpecification(DocumentAdvancedQueryDTO query) {
        long limit = getLimit(query.limit());
        // only check result window when using offset pagination (not search_after)
        if (StringUtils.isBlank(query.searchAfter())) {
            checkResultWindow(limit, query.offset());
        }
        AssembledQuery assembled = QueryAssembler.assemble(query, DocumentQueryDTO.getSortInfos(query.sort()));
        long countUpTo = getCountUpTo();
        int trackTotalCountUpTo = getTrackTotalCountUpTo(query.trackTotalCountUpTo());
        boolean trackTotalCount = getTrackTotalCount(query.trackTotalCount());
        return new QuerySpecification(query, assembled, limit, countUpTo, trackTotalCount, trackTotalCountUpTo);
    }

    protected NamedQueryDefinition getNamedQuery(String name) {
        NamedQueryDefinition def = namedQueryRegistry.getNamedQuery(name);
        if (def == null) {
            throw new RepositoryException("No named query with name " + name, NOT_FOUND.value());
        }
        return def;
    }

    @Override
    public DocumentQueryResultDTO getResults(DocumentQueryDTO query) {
        return getResults(new DocumentAdvancedQueryDTO(query));
    }

    @Override
    public DocumentQueryResultDTO getResults(DocumentNamedQueryDTO namedQuery) {
        StopWatch watch = new StopWatch();
        watch.start();
        DocumentQueryResultDTO result = executeAdvancedQuery(new DocumentAdvancedQueryDTO(namedQuery, getNamedQuery(namedQuery.queryName())));
        watch.stop();
        fireQueryUsageEvent(new QueryEvent(namedQuery.queryName(), result.count(), watch.getTime()));
        return result;
    }

    @Override
    public DocumentQueryResultDTO getResults(DocumentAdvancedQueryDTO query) {
        StopWatch watch = new StopWatch();
        watch.start();
        DocumentQueryResultDTO result = executeAdvancedQuery(query);
        watch.stop();
        fireQueryUsageEvent(new QueryEvent(result.count(), watch.getTime()));
        return result;
    }

    @Override
    public TermsAggregationQueryResultDTO getResults(DocumentQueryDTO query, String termsAggregationsField,
            String searchTerm, int aggregationLimit) {
        StopWatch watch = new StopWatch();
        watch.start();
        TermsAggregationQueryResultDTO result = executeTermsAggregationQuery(query, termsAggregationsField, searchTerm,
                aggregationLimit);
        watch.stop();
        fireQueryUsageEvent(new QueryEvent(result.count(), watch.getTime()));
        return result;
    }

    @Override
    public EmbeddingQueryResultDTO getResults(String repoId, String docId, String embeddingId) {
        StopWatch watch = new StopWatch();
        watch.start();
        EmbeddingQueryResultDTO result = executeEmbeddingQuery(repoId, docId, embeddingId);
        watch.stop();
        fireQueryUsageEvent(new QueryEvent(result.count(), watch.getTime()));
        return result;
    }

    @Override
    public EmbeddingQueryResultDTO getResults(EmbeddingQueryDTO query) {
        StopWatch watch = new StopWatch();
        watch.start();
        EmbeddingQueryResultDTO result = executeEmbeddingQuery(query);
        watch.stop();
        fireQueryUsageEvent(new QueryEvent(result.count(), watch.getTime()));
        return result;
    }

    protected abstract DocumentQueryResultDTO executeAdvancedQuery(DocumentAdvancedQueryDTO query);

    protected abstract EmbeddingQueryResultDTO executeEmbeddingQuery(String repoId, String docId, String embeddingId);

    protected abstract EmbeddingQueryResultDTO executeEmbeddingQuery(EmbeddingQueryDTO query);

    protected abstract TermsAggregationQueryResultDTO executeTermsAggregationQuery(DocumentQueryDTO query,
            String termsAggregationField, String searchTerm, int aggregationLimit);

    protected List<DocumentDTO> translate(List<DocumentModel> docs) {
        if (docs == null) {
            return List.of();
        }
        return docs.stream().map(translator::documentModelToDocumentDTO).toList();
    }

    protected void fireQueryUsageEvent(QueryEvent event) {
        event.fire();
    }

    protected void fireDocumentQueriedEvent(CoreSession session, DocumentModel documentModel, String queryId) {
        Objects.requireNonNull(queryId);
        RepositoryPrincipal principal = session.getPrincipal();
        EventContextImpl ctx = new DocumentEventContext(session, principal, documentModel);
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, session.getRepositoryName());
        ctx.setProperty(CoreEventConstants.RELATED_OBJECT, queryId);
        Event event = ctx.newEvent(DOCUMENT_QUERIED);
        EventService eventService = Framework.getService(EventService.class);
        eventService.fireEvent(event);
    }
}
