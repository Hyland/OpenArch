/*
 * (C) Copyright 2014-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Benoit Delbosc
 *     Florent Guillaume
 */
package org.nuxeo.opensearch.core;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.TotalHits;

/**
 * Iterable query result of the results of an OpenSearch query.
 * <p>
 * Loads all results in memory.
 *
 * @since 7.2
 */
public class OpenSearchResultSetImpl implements IterableQueryResult, Iterator<Map<String, Object>> {

    private final SearchResponse<DocumentDTO> response;

    private final Map<String, Type> selectFieldsAndTypes;

    protected List<Map<String, Object>> maps;

    protected long size;

    boolean closed;

    private long pos;

    public OpenSearchResultSetImpl(SearchResponse<DocumentDTO> response, Map<String, Type> selectFieldsAndTypes) {
        this.response = response;
        this.selectFieldsAndTypes = selectFieldsAndTypes;
        maps = buildMaps();
        size = maps.size();
    }

    protected List<Map<String, Object>> buildMaps() {
        return new OpenSearchHitConverter(selectFieldsAndTypes).convert(response.hits().hits());
    }

    @Override
    public void close() {
        closed = true;
        pos = -1;
    }

    @Override
    public boolean mustBeClosed() {
        return false; // holds no resources
    }

    /**
     * Returns the number of results available in the iterator. Note that before 9.1 this method was returning the
     * totalSize.
     *
     * @since 9.1
     */
    @Override
    public long size() {
        return size;
    }

    /**
     * Returns the total number of result that match the query.
     *
     * @since 9.1
     */
    public long totalSize() {
        return Optional.ofNullable(response.hits().total())
                       .map(TotalHits::value)
                       .orElse(0L);
    }

    @Override
    public long pos() {
        return pos;
    }

    @Override
    public void skipTo(long pos) {
        if (pos < 0) {
            pos = 0;
        } else if (pos > size) {
            pos = size;
        }
        this.pos = pos;
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        return this; // NOSONAR this iterable does not support multiple traversals
    }

    @Override
    public boolean hasNext() {
        return pos < size;
    }

    @Override
    public Map<String, Object> next() {
        if (closed || pos == size) {
            throw new NoSuchElementException();
        }
        Map<String, Object> map = maps.get((int) pos);
        pos++;
        return map;
    }

}
