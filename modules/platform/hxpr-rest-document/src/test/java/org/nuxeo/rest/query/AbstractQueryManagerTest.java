/*
 * (C) Copyright 2023 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.rest.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.spec.QuerySpecification;

@ExtendWith(MockitoExtension.class)
public class AbstractQueryManagerTest {

    @Mock
    AbstractQueryManager queryManager;

    @Mock
    QueryConfig config;

    @ParameterizedTest
    @ValueSource(ints = { 10, 0, -1, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testGetTotalCountUpTo(Integer countUpTo) {
        when(queryManager.getTrackTotalCountUpTo(any())).thenCallRealMethod();

        long expected = countUpTo;
        int actual = queryManager.getTrackTotalCountUpTo(countUpTo);

        assertEquals(expected, actual);
    }

    @Test
    public void testGetTotalCountUpToWhenPassedNull() {
        when(queryManager.getTrackTotalCountUpTo(any())).thenCallRealMethod();
        when(queryManager.getQueryConfig()).thenReturn(config);

        int expected = 1500;

        when(config.trackTotalCountUpTo()).thenReturn(1500);

        long actual = queryManager.getTrackTotalCountUpTo(null);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testGetTrackTotalCount(Boolean trackTotalCount) {
        when(queryManager.getTrackTotalCount(any())).thenCallRealMethod();

        boolean expected = trackTotalCount;
        boolean actual = queryManager.getTrackTotalCount(trackTotalCount);

        assertEquals(expected, actual);
    }

    @Test
    public void testGetTrackTotalCountWhenPassedNull() {
        when(queryManager.getTrackTotalCount(any())).thenCallRealMethod();
        when(queryManager.getQueryConfig()).thenReturn(config);

        boolean expected = false;

        when(config.trackTotalCount()).thenReturn(expected);

        assertFalse(queryManager.getTrackTotalCount(null));
    }

    @Test
    public void testInitQuerySpecification() {
        String repositoryId = "default";
        String query = "SELECT * FROM SysContent";
        Long limit = 10L;
        int offset = 1;
        long countUpTo = 10;
        Boolean trackTotalCount = true;
        Integer trackTotalCountUpTo = 20;
        List<String> sort = List.of();
        DocumentQueryDTO dto = new DocumentQueryDTO(repositoryId, query, limit, offset, trackTotalCount, trackTotalCountUpTo, sort);

        when(queryManager.initQuerySpecification(any(DocumentQueryDTO.class))).thenCallRealMethod();
        when(queryManager.getLimit(anyLong())).thenCallRealMethod();
        when(queryManager.getCountUpTo()).thenReturn(countUpTo);
        when(queryManager.getTrackTotalCountUpTo(any(Integer.class))).thenCallRealMethod();
        when(queryManager.getTrackTotalCount(any(Boolean.class))).thenCallRealMethod();
        when(queryManager.getQueryConfig()).thenReturn(config);

        QuerySpecification expected = new QuerySpecification(dto, query, limit, countUpTo, trackTotalCount, trackTotalCountUpTo, null);

        QuerySpecification actual = queryManager.initQuerySpecification(dto);

        assertEquals(expected, actual);

    }
}
