/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.fetcher;

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.query.sql.HXQL;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.opensearch.query.OpenSearchQueryBuilder.HitDocConsumer;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

/**
 * @since 6.0
 */
public class VcsFetcher extends Fetcher {

    private static final int CHUNK_SIZE = 100;

    protected final HitDocConsumer hitDocConsumer;

    public VcsFetcher(CoreSession session, SearchResponse<DocumentDTO> response, Map<String, String> repoNames,
            HitDocConsumer hitDocConsumer) {
        super(session, response, repoNames);
        this.hitDocConsumer = hitDocConsumer;
    }

    @Override
    public DocumentModelListImpl fetchDocuments() {
        Map<String, List<Hit<DocumentDTO>>> repoHits = getHitsPerRepository();
        List<DocumentModel> docs = fetchFromVcs(repoHits);
        sortResults(docs);
        DocumentModelListImpl ret = new DocumentModelListImpl(docs.size());
        if (!docs.isEmpty()) {
            ret.addAll(docs);
        }
        return ret;
    }

    @SuppressWarnings("resource") // session closed only if we opened it
    protected List<DocumentModel> fetchFromVcs(Map<String, List<Hit<DocumentDTO>>> repoHits) {
        List<DocumentModel> docs = new ArrayList<>();
        String openSessionRepository = getSession().getRepositoryName();
        repoHits.forEach((repo, hits) -> {
            CoreSession session;
            if (openSessionRepository.equals(repo)) {
                session = getSession();
            } else {
                session = CoreInstance.getCoreSession(repo);
            }
            docs.addAll(fetchFromVcs(hits, session));
        });
        return docs;
    }

    private Map<String, List<Hit<DocumentDTO>>> getHitsPerRepository() {
        Map<String, List<Hit<DocumentDTO>>> ret = new HashMap<>();
        for (Hit<DocumentDTO> hit : getResponse().hits().hits()) {
            String repoName = getRepoForIndex(hit.index());
            List<Hit<DocumentDTO>> hits = ret.computeIfAbsent(repoName, k -> new ArrayList<>());
            hits.add(hit);
        }
        return ret;
    }

    private List<DocumentModel> fetchFromVcs(List<Hit<DocumentDTO>> hits, CoreSession session) {
        List<DocumentModel> ret = null;
        int size = hits.size();
        int start = 0;
        int end = Math.min(CHUNK_SIZE, size);
        boolean done = false;
        while (!done) {
            List<Hit<DocumentDTO>> subHits = hits.subList(start, end);
            Map<String, Hit<DocumentDTO>> idToHit = subHits.stream().collect(toMap(Hit::id, h -> h));
            List<DocumentModel> docs = fetchFromVcsChunk(subHits, session);
            if (hitDocConsumer != null) {
                docs.forEach(doc -> hitDocConsumer.accept(idToHit.get(doc.getId()), doc));
            }
            if (ret == null) {
                ret = docs;
            } else {
                ret.addAll(docs);
            }
            if (end >= size) {
                done = true;
            } else {
                start = end;
                end = Math.min(start + CHUNK_SIZE, size);
            }
        }
        return ret;
    }

    private List<DocumentModel> fetchFromVcsChunk(List<Hit<DocumentDTO>> hits, CoreSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM SysContent, SysRelation WHERE sys_id IN (");
        int size = hits.size();
        for (int i = 0; i < size; i++) {
            sb.append(HXQL.escapeString(hits.get(i).id()));
            if (i < size - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return session.query(sb.toString());
    }

    private void sortResults(List<DocumentModel> docs) {
        final List<String> ids = new ArrayList<>();
        for (Hit<DocumentDTO> hit : getResponse().hits().hits()) {
            ids.add(getRepoForIndex(hit.index()) + hit.id());
        }

        docs.sort(new Comparator<DocumentModel>() {
            @Override
            public int compare(DocumentModel a, DocumentModel b) {
                return ids.indexOf(a.getRepositoryName() + a.getId()) - ids.indexOf(b.getRepositoryName() + b.getId());
            }
        });

    }

}
