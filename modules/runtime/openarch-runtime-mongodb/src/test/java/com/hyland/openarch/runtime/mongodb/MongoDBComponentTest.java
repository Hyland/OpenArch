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
package com.hyland.openarch.runtime.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.hyland.openarch.runtime.transaction.TransactionHelper;
import com.hyland.openarch.runtime.transaction.TransactionRuntimeException;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;

/**
 * Tests for {@link MongoDBComponent}, covering the MongoDB session shared by the stores taking part in a transaction,
 * and the masking of credentials in log messages.
 */
public class MongoDBComponentTest {

    @Test
    public void testSessionCommit() throws XAException {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            assertThat(component.getCurrentSession()).isNull();

            assertThat(component.getOrCreateSession()).isSameAs(session);
            verify(session).startTransaction();
            assertThat(component.getCurrentSession()).isSameAs(session);

            // whoever comes next in the same transaction joins the session instead of creating a new one
            when(session.hasActiveTransaction()).thenReturn(true);
            assertThat(component.getOrCreateSession()).isSameAs(session);
            verify(component.getClient(), times(1)).startSession();

            // the transaction manager commits the enlisted resources once the transaction has been validated by all
            // the beforeCompletion callbacks, and before any afterCompletion callback
            XAResource xaResource = captureXAResource(transactionHelper);
            xaResource.commit(mock(Xid.class), true);
            verify(session).commitTransaction();
            verify(session, never()).close();

            // the session is closed at the end of the enclosing transaction
            when(session.hasActiveTransaction()).thenReturn(false);
            captureSynchronization(transactionHelper).afterCompletion(Status.STATUS_COMMITTED);
            verify(session, never()).abortTransaction();
            verify(session).close();
            assertThat(component.getCurrentSession()).isNull();
        }
    }

    @Test
    public void testSessionRollback() throws XAException {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            component.getOrCreateSession();

            when(session.hasActiveTransaction()).thenReturn(true);
            captureXAResource(transactionHelper).rollback(mock(Xid.class));
            verify(session, never()).commitTransaction();
            verify(session).abortTransaction();
            verify(session, never()).close();

            when(session.hasActiveTransaction()).thenReturn(false);
            captureSynchronization(transactionHelper).afterCompletion(Status.STATUS_ROLLEDBACK);
            verify(session).close();
            assertThat(component.getCurrentSession()).isNull();
        }
    }

    @Test
    public void testSessionCommitFailureIsNotSwallowed() {
        ClientSession session = mock(ClientSession.class);
        when(session.hasActiveTransaction()).thenReturn(true);
        doThrow(new MongoException("commit failed")).when(session).commitTransaction();
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            component.getOrCreateSession();

            // the failure is reported to the transaction manager, which reports it to the caller of commit()
            XAResource xaResource = captureXAResource(transactionHelper);
            Xid xid = mock(Xid.class);
            assertThatThrownBy(() -> xaResource.commit(xid, true)).isInstanceOf(XAException.class)
                                                                  .hasFieldOrPropertyWithValue("errorCode",
                                                                          XAException.XAER_RMERR)
                                                                  .hasRootCauseInstanceOf(MongoException.class);

            // the session is still aborted and closed at the end of the enclosing transaction
            captureSynchronization(transactionHelper).afterCompletion(Status.STATUS_ROLLEDBACK);
            verify(session).abortTransaction();
            verify(session).close();
            assertThat(component.getCurrentSession()).isNull();
        }
    }

    @Test
    public void testSessionAbortedWhenTransactionManagerDidNotCompleteIt() {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            component.getOrCreateSession();

            // the XA resource was never asked to commit or roll back, the session must not be left open
            when(session.hasActiveTransaction()).thenReturn(true);
            captureSynchronization(transactionHelper).afterCompletion(Status.STATUS_UNKNOWN);
            verify(session, never()).commitTransaction();
            verify(session).abortTransaction();
            verify(session).close();
            assertThat(component.getCurrentSession()).isNull();
        }
    }

    @Test
    public void testCompletedSessionIsNotHandedOutAgain() throws XAException {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            component.getOrCreateSession();
            when(session.hasActiveTransaction()).thenReturn(true);
            captureXAResource(transactionHelper).commit(mock(Xid.class), true);

            // the session is still published until the cleanup synchronization runs, but an afterCompletion callback
            // registered before it must not be able to write outside of the completed MongoDB transaction
            when(session.hasActiveTransaction()).thenReturn(false);
            assertThat(component.getCurrentSession()).isSameAs(session);
            assertThatThrownBy(component::getOrCreateSession).isInstanceOf(IllegalStateException.class)
                                                            .hasMessageContaining("already completed");
        }
    }

    @Test
    public void testPrepareAlwaysVotesToCommit() throws XAException {        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            component.getOrCreateSession();

            // MongoDB has no prepare phase, the session behaves as a last resource
            assertThat(captureXAResource(transactionHelper).prepare(mock(Xid.class))).isEqualTo(XAResource.XA_OK);
            verify(session, never()).commitTransaction();
        }
    }

    @Test
    public void testSessionRequiresATransaction() {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);

        assertThatThrownBy(component::getOrCreateSession).isInstanceOf(IllegalStateException.class)
                                                         .hasMessageContaining("No active transaction");
        assertThat(component.getCurrentSession()).isNull();
    }

    @Test
    public void testSessionClosedWhenResourceCannotBeEnlisted() {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (MockedStatic<TransactionHelper> transactionHelper = mockTransaction()) {
            // for instance if the transaction became marked rollback-only in the meantime
            transactionHelper.when(() -> TransactionHelper.enlistResource(any()))
                             .thenThrow(new TransactionRuntimeException("Cannot enlist XA resource"));

            assertThatThrownBy(component::getOrCreateSession).isInstanceOf(TransactionRuntimeException.class);
            // the session is not published, so it must have been closed instead of leaked
            verify(session).close();
            assertThat(component.getCurrentSession()).isNull();
        }
    }

    @Test
    public void testClearCurrentSessionIgnoresAnotherSession() {
        ClientSession session = mock(ClientSession.class);
        MongoDBComponent component = newComponent(session);
        try (var _ = mockTransaction()) {
            component.getOrCreateSession();

            component.clearCurrentSession(mock(ClientSession.class));
            assertThat(component.getCurrentSession()).isSameAs(session);

            component.clearCurrentSession(session);
            assertThat(component.getCurrentSession()).isNull();
        }
    }

    /** Returns a component whose client hands out the given session. */
    protected MongoDBComponent newComponent(ClientSession session) {
        MongoClient client = mock(MongoClient.class);
        when(client.startSession()).thenReturn(session);
        MongoDBComponent component = spy(new MongoDBComponent());
        doReturn(client).when(component).getClient();
        return component;
    }

    /** Mocks {@link TransactionHelper} to simulate an active transaction, so a session can be lazily created. */
    protected MockedStatic<TransactionHelper> mockTransaction() {
        MockedStatic<TransactionHelper> transactionHelper = mockStatic(TransactionHelper.class);
        transactionHelper.when(TransactionHelper::isTransactionActive).thenReturn(true);
        return transactionHelper;
    }

    /** Returns the XA resource enlisted in the current transaction. */
    protected XAResource captureXAResource(MockedStatic<TransactionHelper> transactionHelper) {
        ArgumentCaptor<XAResource> captor = ArgumentCaptor.forClass(XAResource.class);
        transactionHelper.verify(() -> TransactionHelper.enlistResource(captor.capture()));
        return captor.getValue();
    }

    /** Returns the synchronization registered on the current transaction. */
    protected Synchronization captureSynchronization(MockedStatic<TransactionHelper> transactionHelper) {
        ArgumentCaptor<Synchronization> captor = ArgumentCaptor.forClass(Synchronization.class);
        transactionHelper.verify(() -> TransactionHelper.registerSynchronization(captor.capture()));
        return captor.getValue();
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("maskingCases")
    @SuppressWarnings("unused") // caseName is used only in the @ParameterizedTest display name
    public void testMaskSecrets(String caseName, String connectionString, String expectedMaskedConnectionString) {
        // when
        String actualMasked = MongoDBComponent.maskSecrets(connectionString);

        // then
        assertThat(actualMasked).isEqualTo(expectedMaskedConnectionString);
    }

    private static Stream<Arguments> maskingCases() {
        return Stream.of(
                Arguments.of(
                        "standard mongodb with credentials",
                        "mongodb://admin:password123@localhost:27017",
                        "mongodb://***:***@localhost:27017"),
                Arguments.of(
                        "mongodb+srv with credentials",
                        "mongodb+srv://dbUser:secret@cluster0.mongodb.net",
                        "mongodb+srv://***:***@cluster0.mongodb.net"),
                Arguments.of(
                        "complex password with special characters",
                        "mongodb://user:p@ssw0rd!#$%@host.example.com:27017",
                        "mongodb://***:***@host.example.com:27017"),
                Arguments.of(
                        "complex username with @ symbol",
                        "mongodb://user@domain.com:password@host.example.com:27017",
                        "mongodb://***:***@host.example.com:27017"),
                Arguments.of(
                        "url-encoded credentials",
                        "mongodb://user%40domain:pass%3Aword@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "long connection string with replica set",
                        "mongodb://myUser:myPassword@host1:27017,host2:27017,host3:27017/mydb?replicaSet=myReplicaSet&ssl=true",
                        "mongodb://***:***@host1:27017,host2:27017,host3:27017/mydb?replicaSet=myReplicaSet&ssl=true"),
                Arguments.of(
                        "mongodb+srv with query parameters",
                        "mongodb+srv://admin:secretPass@cluster0.example.mongodb.net/myDatabase?retryWrites=true&w=majority",
                        "mongodb+srv://***:***@cluster0.example.mongodb.net/myDatabase?retryWrites=true&w=majority"),
                Arguments.of(
                        "no credentials - unchanged",
                        "mongodb://localhost:27017",
                        "mongodb://localhost:27017"),
                Arguments.of(
                        "no credentials srv - unchanged",
                        "mongodb+srv://cluster0.mongodb.net",
                        "mongodb+srv://cluster0.mongodb.net"),
                Arguments.of(
                        "very long password with special characters",
                        "mongodb://user:aVeryLongPasswordWith1234567890AndSpecialChars!@#$%^&*()@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "minimal credentials",
                        "mongodb://u:p@host",
                        "mongodb://***:***@host"),
                Arguments.of(
                        "numeric credentials",
                        "mongodb://12345:67890@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "unicode credentials",
                        "mongodb://用户:密码@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "non-mongodb URL - unchanged",
                        "https://user:pass@example.com",
                        "https://user:pass@example.com"),
                Arguments.of(
                        "real-world DocumentDB connection string",
                        "mongodb://masteruser:MySecretPassword123!@docdb-cluster.cluster-abc123.us-east-1.docdb.amazonaws.com:27017/?tls=true&tlsCAFile=rds-combined-ca-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false",
                        "mongodb://***:***@docdb-cluster.cluster-abc123.us-east-1.docdb.amazonaws.com:27017/?tls=true&tlsCAFile=rds-combined-ca-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false")
        );
    }
}
