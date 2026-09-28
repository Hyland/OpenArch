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
package com.hyland.openarch.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;

public class TestMongoDBConnection {

    protected MongoDBConnection connection;

    protected Bson filter;

    @BeforeEach
    public void setUp() {
        MongoDBRepository repository = mock(MongoDBRepository.class);
        when(repository.getCollection()).thenReturn(mock(MongoCollection.class));
        connection = spy(new MongoDBConnection(repository, false));
        filter = new Document();
    }

    @Test
    public void testNoCountRequested() {
        assertEquals(-1, connection.getTotalSize(filter, 3, false, 1, 0, 0));
        verifyNoCount(connection);
    }

    @Test
    public void testFullCountFromEmptyUnlimitedQueryWithoutOffset() {
        assertEquals(0, connection.getTotalSize(filter, 0, false, 0, 0, -1));
        verifyNoCount(connection);
    }

    @Test
    public void testFullCountFromNonEmptyUnlimitedQueryWithOffset() {
        assertEquals(5, connection.getTotalSize(filter, 3, false, 0, 2, -1));
        verifyNoCount(connection);
    }

    @Test
    public void testFullCountFromEmptyUnlimitedQueryWithOffset() {
        doReturn(5L).when(connection).countDocuments(filter);

        assertEquals(5, connection.getTotalSize(filter, 0, false, 0, 1, -1));
        verify(connection).countDocuments(filter);
    }

    @Test
    public void testFullCountUnknownForLimitedManualProjection() {
        assertEquals(-1, connection.getTotalSize(filter, 3, true, 1, 0, -1));
        verifyNoCount(connection);
    }

    @Test
    public void testFullCountForLimitedQuery() {
        doReturn(5L).when(connection).countDocuments(filter);

        assertEquals(5, connection.getTotalSize(filter, 3, false, 1, 0, -1));
        verify(connection).countDocuments(filter);
    }

    @Test
    public void testBoundedCountFromUnlimitedQuery() {
        assertEquals(3, connection.getTotalSize(filter, 3, false, 0, 0, 5));
        assertEquals(-2, connection.getTotalSize(filter, 6, false, 0, 0, 5));
        verifyNoCount(connection);
    }

    @Test
    public void testBoundedCountUnknownForLimitedManualProjection() {
        assertEquals(-1, connection.getTotalSize(filter, 3, true, 1, 0, 5));
        verifyNoCount(connection);
    }

    @Test
    public void testBoundedCountForLimitedQuery() {
        doReturn(5L).when(connection).countDocuments(eq(filter), any(CountOptions.class));

        assertEquals(5, connection.getTotalSize(filter, 3, false, 1, 0, 5));
        verify(connection).countDocuments(eq(filter), argThat(options -> options.getLimit() == 6));
    }

    @Test
    public void testTruncatedBoundedCountForLimitedQuery() {
        doReturn(6L).when(connection).countDocuments(eq(filter), any(CountOptions.class));

        assertEquals(-2, connection.getTotalSize(filter, 3, false, 1, 0, 5));
        verify(connection).countDocuments(eq(filter), argThat(options -> options.getLimit() == 6));
    }

    protected void verifyNoCount(MongoDBConnection connection) {
        verify(connection, never()).countDocuments(any(Bson.class));
        verify(connection, never()).countDocuments(any(Bson.class), any(CountOptions.class));
    }
}
