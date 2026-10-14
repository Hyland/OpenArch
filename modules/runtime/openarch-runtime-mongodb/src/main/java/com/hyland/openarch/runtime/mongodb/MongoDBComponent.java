/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.model.ComponentStartOrders;
import com.hyland.openarch.runtime.model.DefaultComponent;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import jakarta.transaction.Synchronization;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * Component used to get a database connection to MongoDB. Don't expose {@link MongoClient} directly, because it's this
 * component which is responsible for creating and closing it.
 *
 * @since 9.1
 */
public class MongoDBComponent extends DefaultComponent implements MongoDBConnectionService {

    private static final Logger log = LogManager.getLogger(MongoDBComponent.class);

    private MongoClient client;

    private MongoDatabase database;

    /**
     * The MongoDB session shared by all the MongoDB-based stores taking part in the transaction of the current thread.
     */
    private final ThreadLocal<ClientSession> currentSession = new ThreadLocal<>();

    @Override
    public void start() {
        MongoDBConnectionConfig config = MongoDBConnectionHelper.getMongoClientConfig();
        client = MongoDBConnectionHelper.newMongoClient(config);
        database = client.getDatabase(config.dbname);
        log.debug("Initializing MongoClient for server {}", () -> maskSecrets(config.server));
    }

    @Override
    public void stop() throws InterruptedException {
        log.debug("Closing MongoClient");
        database = null;
        client.close();
        client = null;
    }

    @Override
    public int getApplicationStartedOrder() {
        // start before repository and directories
        return ComponentStartOrders.REPOSITORY - 10;
    }

    @Override
    public MongoClient getClient() {
        return client;
    }

    @Override
    public MongoDatabase getDatabase() {
        return database;
    }

    /**
     * Gets the MongoDB session in which the work of the current thread must take place, or {@code null} if there is
     * none.
     *
     * @return the session, or {@code null}
     */
    public ClientSession getCurrentSession() {
        return currentSession.get();
    }

    @Override
    public ClientSession getOrCreateSession() {
        ClientSession session = currentSession.get();
        if (session != null) {
            // the session may have been committed or aborted by the XA resource while still being published, if an
            // afterCompletion callback registered before ours runs and uses a transactional store
            if (!session.hasActiveTransaction()) {
                throw new IllegalStateException(
                        "The MongoDB transaction of the current thread is already completed");
            }
            return session;
        }
        // a transaction marked rollback-only is rejected, as no synchronization can be registered on it anymore
        if (!TransactionHelper.isTransactionActive()) {
            throw new IllegalStateException("No active transaction to create a MongoDB session for");
        }
        session = getClient().startSession();
        try {
            session.startTransaction();
            // the session is enlisted as an XA resource, so that the MongoDB commit happens after all the
            // beforeCompletion callbacks have validated the transaction, and before any afterCompletion callback
            TransactionHelper.enlistResource(new MongoDBSessionXAResource(session));
            // the synchronization only takes care of the cleanup, once the XA resource has committed or rolled back
            TransactionHelper.registerSynchronization(new MongoDBSessionSynchronization(session));
        } catch (RuntimeException e) {
            session.close();
            throw e;
        }
        currentSession.set(session);
        return session;
    }

    /**
     * Clears the current session if it is the given one.
     * <p>
     * Several sessions may be used in turn by the same thread, so only the session currently published may be cleared.
     *
     * @param session the session to clear
     */
    protected void clearCurrentSession(ClientSession session) {
        if (session != null && session.equals(currentSession.get())) {
            currentSession.remove();
        }
    }

    /**
     * Commits or rolls back the MongoDB transaction when the enclosing transaction completes.
     * <p>
     * A MongoDB transaction cannot take part in a two-phase commit, as it has no prepare phase. The resource
     * therefore behaves as a last resource: {@link #prepare} always votes to commit, and the actual MongoDB commit
     * happens in {@link #commit}. If another resource is enlisted in the same transaction and fails to commit after
     * this one has committed, the result is a heuristic outcome that cannot be undone.
     * <p>
     * Using an XA resource rather than a {@link Synchronization} is what gives the MongoDB commit the correct
     * position in the completion sequence: the transaction manager commits the enlisted resources after all the
     * {@code beforeCompletion} callbacks have had a chance to reject the transaction, and before any
     * {@code afterCompletion} callback makes the changes visible or returns connections to their pool. A commit
     * failure is also reported to the caller of {@code commit()} instead of being swallowed.
     */
    protected static class MongoDBSessionXAResource implements XAResource {

        protected final ClientSession session;

        protected int timeout;

        protected MongoDBSessionXAResource(ClientSession session) {
            this.session = session;
        }

        @Override
        public void start(Xid xid, int flags) {
            // the session is already associated with the thread, nothing to do
        }

        @Override
        public void end(Xid xid, int flags) {
            // the session is already associated with the thread, nothing to do
        }

        @Override
        public int prepare(Xid xid) {
            // MongoDB has no prepare phase, see the class javadoc
            return XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            if (!session.hasActiveTransaction()) {
                return;
            }
            try {
                session.commitTransaction();
            } catch (MongoException e) {
                log.error("Failed to commit MongoDB transaction", e);
                throw newXAException(XAException.XAER_RMERR, e);
            }
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            if (!session.hasActiveTransaction()) {
                return;
            }
            try {
                session.abortTransaction();
            } catch (MongoException e) {
                log.error("Failed to abort MongoDB transaction", e);
                throw newXAException(XAException.XAER_RMERR, e);
            }
        }

        @Override
        public void forget(Xid xid) {
            // no heuristic completion is ever remembered
        }

        @Override
        public Xid[] recover(int flag) {
            // MongoDB transactions are not recoverable, they are aborted by the server
            return new Xid[0];
        }

        @Override
        public boolean isSameRM(XAResource xares) {
            return this == xares;
        }

        @Override
        public int getTransactionTimeout() {
            return timeout;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            timeout = seconds;
            return true;
        }

        protected static XAException newXAException(int errorCode, Throwable cause) {
            XAException xae = new XAException(errorCode);
            xae.initCause(cause);
            return xae;
        }
    }

    /**
     * Clears and closes the MongoDB session once the enclosing transaction has completed.
     * <p>
     * The commit or rollback of the MongoDB transaction itself is done by {@link MongoDBSessionXAResource}, which the
     * transaction manager runs before this callback.
     */
    protected class MongoDBSessionSynchronization implements Synchronization {

        protected final ClientSession session;

        protected MongoDBSessionSynchronization(ClientSession session) {
            this.session = session;
        }

        @Override
        public void beforeCompletion() {
            // nothing to do, the session is committed by the XA resource
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (session.hasActiveTransaction()) {
                    // the transaction completed without the XA resource being asked to commit or roll back
                    session.abortTransaction();
                }
            } finally {
                clearCurrentSession(session);
                session.close();
            }
        }
    }

    /**
     * Masks credentials in MongoDB connection strings.
     * Handles both formats:
     * - mongodb://username:password@host:port
     * - mongodb+srv://username:password@host
     *
     * @param connectionString the MongoDB connection string
     * @return masked connection string with credentials replaced by asterisks
     */
    public static String maskSecrets(String connectionString) {
        if (connectionString == null || connectionString.isEmpty()) {
            return connectionString;
        }

        // Pattern matches: mongodb[+srv]://username:password@
        // Replaces the username:password part with ***:***
        return connectionString.replaceAll(
                "(mongodb(?:\\+srv)?://)[^:]+:(.*)(@[^@]+(?::\\d+)?(?:/|\\?|$))",
                "$1***:***$3"
        );
    }
}
