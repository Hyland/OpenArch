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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.query.sql.model.IntegerLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.primitives.LongType;
import com.hyland.openarch.ecm.core.storage.State.ListDiff;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.dbs.DBSTransactionState.ConditionalUpdates;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.mongodb.MongoDBConnectionService;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.result.UpdateResult;

public class TestMongoDBConnection {

    protected MongoDBRepository repository;

    protected MongoDBConnection connection;

    protected Bson filter;

    protected UpdateResult updateResult;

    protected static final String VERSION_FIELD = "sysemb_chunksVersion";

    protected static final long CURRENT_VERSION = 1L;

    @BeforeEach
    public void setUp() {
        repository = mock(MongoDBRepository.class);
        when(repository.getCollection()).thenReturn(mock(MongoCollection.class));
        connection = spy(new MongoDBConnection(repository, false));
        filter = new Document();

        // updateState needs a schema manager to turn the condition predicate into a MongoDB filter
        SchemaManager schemaManager = mock(SchemaManager.class);
        Field field = mock(Field.class);
        doReturn(VERSION_FIELD).when(field).getName();
        doReturn(LongType.INSTANCE).when(field).getType();
        doReturn(field).when(schemaManager).getField(VERSION_FIELD);
        doReturn(schemaManager).when(connection).getSchemaManager();
        updateResult = mock(UpdateResult.class);
        doReturn(updateResult).when(connection).updateMany(any(Bson.class), any(Bson.class));
    }

    @Test
    public void testSessionCommitLifecycle() {
        ClientSession session = mock(ClientSession.class);
        when(repository.supportsTransactions()).thenReturn(true);
        try (var _ = mockSession(session)) {
            connection = new MongoDBConnection(repository, false);

            connection.begin();
            assertTrue(connection.transactionStarted);
            assertSame(session, connection.clientSession);

            connection.commit();
            assertFalse(connection.transactionStarted);
            assertNull(connection.clientSession);

            connection.close();
            // the MongoDB commit and close of the shared session are left to the enclosing transaction
            verifyNoInteractions(session);
        }
    }

    @Test
    public void testSessionRollbackLifecycle() {
        ClientSession session = mock(ClientSession.class);
        when(repository.supportsTransactions()).thenReturn(true);
        try (var _ = mockSession(session)) {
            connection = new MongoDBConnection(repository, false);

            connection.begin();
            assertSame(session, connection.clientSession);

            connection.rollback();
            assertFalse(connection.transactionStarted);
            assertNull(connection.clientSession);

            connection.close();
            // the MongoDB abort and close of the shared session are left to the enclosing transaction
            verifyNoInteractions(session);
        }
    }

    @Test
    public void testNoSessionWhenNotSupported() {
        ClientSession session = mock(ClientSession.class);
        when(repository.supportsTransactions()).thenReturn(false);
        try (var _ = mockSession(session)) {
            connection = new MongoDBConnection(repository, false);

            connection.begin();
            assertFalse(connection.transactionStarted);
            assertNull(connection.clientSession);

            connection.commit();
            connection.close();
            verifyNoInteractions(session);
        }
    }

    @Test
    public void testNoSessionWhenTransactionsNotSupported() {
        ClientSession session = mock(ClientSession.class);
        // a deployment may support sessions without supporting transactions, in which case the undo log is used
        when(repository.supportsSessions()).thenReturn(true);
        when(repository.supportsTransactions()).thenReturn(false);
        try (var _ = mockSession(session)) {
            connection = new MongoDBConnection(repository, false);

            connection.begin();
            assertFalse(connection.transactionStarted);
            assertNull(connection.clientSession);

            connection.commit();
            connection.close();
            verifyNoInteractions(session);
        }
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

    @Test
    public void testUpdateStateSingleWriteAppliesConditionPredicate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);

        connection.updateState("id", newStateDiff("foo", "val"), conditionalUpdatesWithPredicate());

        assertEquals(List.of(true), updateFilterHasPredicate());
    }

    @Test
    public void testUpdateStateSplitWriteAppliesConditionPredicateToFirstWriteOnly() {
        when(updateResult.getModifiedCount()).thenReturn(1L, 1L);

        connection.updateState("id", splitDiff(), conditionalUpdatesWithPredicate());

        // the second write must not be guarded by the predicate, the first one already changed the document
        assertEquals(List.of(true, false), updateFilterHasPredicate());
    }

    @Test
    public void testUpdateStateSplitWriteLaterWriteFailureIsNotAConditionFailure() {
        when(updateResult.getModifiedCount()).thenReturn(1L, 0L);
        ConditionalUpdates conditionalUpdates = conditionalUpdatesWithPredicate();
        StateDiff stateDiff = splitDiff();

        assertThrows(ConcurrentUpdateException.class,
                () -> connection.updateState("id", stateDiff, conditionalUpdates));
    }

    @Test
    public void testUpdateStateSplitWriteWithoutConditionalUpdates() {
        when(updateResult.getModifiedCount()).thenReturn(1L, 1L);

        connection.updateState("id", splitDiff(), null);

        assertEquals(List.of(false, false), updateFilterHasPredicate());
    }

    /** For each {@code updateMany} call made so far, whether its filter contains the condition {@code version = CURRENT_VERSION}. */
    protected List<Boolean> updateFilterHasPredicate() {
        ArgumentCaptor<Bson> filters = ArgumentCaptor.forClass(Bson.class);
        verify(connection, atLeastOnce()).updateMany(filters.capture(), any(Bson.class));
        return filters.getAllValues().stream()
                      .map(f -> f.toBsonDocument().containsKey(VERSION_FIELD)
                              && f.toBsonDocument().get(VERSION_FIELD).asNumber().longValue() == CURRENT_VERSION)
                      .toList();
    }

    protected ConditionalUpdates conditionalUpdatesWithPredicate() {
        ConditionalUpdates conditionalUpdates = new ConditionalUpdates();
        conditionalUpdates.setConditionPredicate(
                new Predicate(new Reference(VERSION_FIELD), Operator.EQ, new IntegerLiteral(CURRENT_VERSION)));
        return conditionalUpdates;
    }

    /** A diff that MongoDB cannot apply in one write: it updates and pulls on the same array. */
    protected StateDiff splitDiff() {
        ListDiff listDiff = new ListDiff();
        listDiff.diff = List.of(newStateDiff("bar", "val"));
        listDiff.pull = List.of(newStateDiff("bar", "val2"));
        return newStateDiff("foo", listDiff);
    }

    protected StateDiff newStateDiff(String key, Object value) {
        StateDiff stateDiff = new StateDiff();
        stateDiff.put(key, value);
        return stateDiff;
    }

    protected void verifyNoCount(MongoDBConnection connection) {
        verify(connection, never()).countDocuments(any(Bson.class));
        verify(connection, never()).countDocuments(any(Bson.class), any(CountOptions.class));
    }

    /** Mocks the {@link MongoDBConnectionService} so that it hands out the given session. */
    protected MockedStatic<Framework> mockSession(ClientSession session) {
        MongoDBConnectionService mongoService = mock(MongoDBConnectionService.class);
        when(mongoService.getOrCreateSession()).thenReturn(session);
        MockedStatic<Framework> framework = mockStatic(Framework.class);
        framework.when(() -> Framework.getService(MongoDBConnectionService.class)).thenReturn(mongoService);
        return framework;
    }
}
