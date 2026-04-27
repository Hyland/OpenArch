/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 *     Julien Carsique
 */
package org.nuxeo.runtime.jtajca;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.apache.geronimo.transaction.manager.NamedXAResourceFactory;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;
import org.apache.geronimo.transaction.manager.TransactionImpl;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.apache.geronimo.transaction.manager.XidImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.transaction.TransactionHelper;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.BlankSpan;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

/**
 * Internal helper for the Nuxeo-defined transaction manager and connection manager.
 * <p>
 * This code is called by the factories registered through JNDI, or by unit tests mimicking JNDI bindings.
 */
public class NuxeoContainer {

    protected static final Logger log = LogManager.getLogger(NuxeoContainer.class);

    public static final String TX_TIMEOUT_PROPERTY = "nuxeo.db.transactiontimeout";

    public static final String TX_TIMEOUT_PROPERTY_DEFAULT = "300";

    protected static RecoverableTransactionManager tmRecoverable;

    protected static TransactionManager tm;

    protected static TransactionSynchronizationRegistry tmSynchRegistry;

    protected static UserTransaction ut;

    // @since 5.7
    protected static final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected static final Counter rollbackCount = registry.counter(
            MetricRegistry.name("nuxeo", "transactions", "rollbacks"));

    protected static final Counter concurrentCount = registry.counter(
            MetricRegistry.name("nuxeo", "transactions", "concurrency"));

    protected static final Counter concurrentMaxCount = registry.counter(
            MetricRegistry.name("nuxeo", "transactions", "concurrency", "max"));

    protected static final Timer transactionTimer = registry.timer(
            MetricRegistry.name("nuxeo", "transactions", "timer"));

    protected static final ConcurrentHashMap<Transaction, Timer.Context> timers = new ConcurrentHashMap<>();

    private NuxeoContainer() {
    }

    public static class InstallContext extends Throwable {
        private static final long serialVersionUID = 1L;

        public final String threadName;

        InstallContext() {
            super("Container installation context (" + Thread.currentThread().getName() + ")");
            threadName = Thread.currentThread().getName();
        }
    }

    /**
     * Install naming and bind transaction and connection management factories "by hand".
     */
    protected static synchronized void install() {
        installTransactionManager();
    }

    protected static void uninstall() {
        log.trace("Uninstalling nuxeo container");
        tm = null;
        tmRecoverable = null;
        tmSynchRegistry = null;
        ut = null;
    }

    /**
     * Gets the transaction manager used by the container.
     *
     * @return the transaction manager
     */
    public static TransactionManager getTransactionManager() {
        return tm;
    }

    /**
     * Gets the user transaction used by the container.
     *
     * @return the user transaction
     */
    public static UserTransaction getUserTransaction() {
        return ut;
    }

    protected static synchronized TransactionManager initTransactionManager() {
        TransactionManagerImpl impl = createTransactionManager();
        tm = impl;
        tmRecoverable = impl;
        tmSynchRegistry = impl;
        ut = new UserTransactionImpl(tm);
        return tm;
    }

    protected static TransactionManagerWrapper wrapTransactionManager(TransactionManager tm) {
        if (tm == null) {
            return null;
        }
        if (tm instanceof TransactionManagerWrapper tMW) {
            return tMW;
        }
        return new TransactionManagerWrapper(tm);
    }

    protected static void installTransactionManager() {
        if (tm != null) {
            return;
        }
        tm = createTransactionManager();
        tmRecoverable = wrapTransactionManager(tm);
        ut = new UserTransactionImpl(tm);
        tmSynchRegistry = (TransactionSynchronizationRegistry) tm;
    }

    protected static TransactionManagerImpl createTransactionManager() {
        String prop = Framework.getProperty(TX_TIMEOUT_PROPERTY, TX_TIMEOUT_PROPERTY_DEFAULT);
        try {
            int transactionTimeoutSeconds = Integer.parseInt(prop);
            return new TransactionManagerImpl(transactionTimeoutSeconds);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid transaction timeout", e);
        } catch (XAException e) {
            // failed in recovery somewhere
            throw new RuntimeException(e.toString(), e);
        }
    }

    /**
     * User transaction that uses this container's transaction manager.
     *
     * @since 5.6
     */
    public static class UserTransactionImpl implements UserTransaction {

        protected final TransactionManager transactionManager;

        public UserTransactionImpl(TransactionManager manager) {
            transactionManager = manager;
        }

        @Override
        public int getStatus() throws SystemException {
            return transactionManager.getStatus();
        }

        @Override
        public void setRollbackOnly() throws IllegalStateException, SystemException {
            transactionManager.setRollbackOnly();
        }

        @Override
        public void setTransactionTimeout(int seconds) throws SystemException {
            transactionManager.setTransactionTimeout(seconds);
        }

        @Override
        public void begin() throws NotSupportedException, SystemException {
            transactionManager.begin();
            Tracer tracer = Tracing.getTracer();
            Span span = tracer.getCurrentSpan();
            if (!(span instanceof BlankSpan)) {
                HashMap<String, AttributeValue> map = new HashMap<>();
                map.put("tx.thread", AttributeValue.stringAttributeValue(Thread.currentThread().getName()));
                map.put("tx.id", AttributeValue.stringAttributeValue(getTransactionId()));
                span.addAnnotation("tx.begin", map);
            }
            timers.put(transactionManager.getTransaction(), transactionTimer.time());
            concurrentCount.inc();
            if (concurrentCount.getCount() > concurrentMaxCount.getCount()) {
                concurrentMaxCount.inc();
            }
        }

        protected String getTransactionId() {
            return transactionKeyAsString(((TransactionManagerImpl) transactionManager).getTransactionKey());
        }

        protected static String transactionKeyAsString(Object key) {
            if (key instanceof XidImpl xid) {
                byte[] globalId = xid.getGlobalTransactionId();
                StringBuilder buffer = new StringBuilder();
                for (byte aGlobalId : globalId) {
                    buffer.append(String.format("%02X", aGlobalId));
                }
                String stringKey = buffer.toString();
                // remove trailing 0
                for (int index = stringKey.length() - 1; index >= 0; index--) {
                    if (stringKey.charAt(index) != '0') {
                        return stringKey.substring(0, index + 1);
                    }
                }
                return stringKey;
            }
            return key.toString();
        }

        @Override
        public void commit() throws HeuristicMixedException, HeuristicRollbackException, IllegalStateException,
                RollbackException, SecurityException, SystemException {
            Span span = Tracing.getTracer().getCurrentSpan();
            span.addAnnotation("tx.committing");
            Transaction transaction = transactionManager.getTransaction();
            if (transaction == null) {
                throw new IllegalStateException("No transaction associated with current thread");
            }
            @SuppressWarnings("resource")
            Timer.Context timerContext = timers.remove(transaction);
            transactionManager.commit();
            if (timerContext != null) {
                long elapsed = timerContext.stop();

                HashMap<String, AttributeValue> map = new HashMap<>();
                map.put("tx.duration_ms", AttributeValue.longAttributeValue(elapsed / 1000_000));
                span.addAnnotation("tx.commited", map);
            }
            concurrentCount.dec();
            span.setStatus(Status.OK);
        }

        @Override
        public void rollback() throws IllegalStateException, SecurityException, SystemException {
            Span span = Tracing.getTracer().getCurrentSpan();
            span.addAnnotation("tx.rollbacking");
            Transaction transaction = transactionManager.getTransaction();
            if (transaction == null) {
                throw new IllegalStateException("No transaction associated with current thread");
            }
            @SuppressWarnings("resource")
            Timer.Context timerContext = timers.remove(transaction);
            transactionManager.rollback();
            concurrentCount.dec();
            rollbackCount.inc();
            if (timerContext != null) {
                long elapsed = timerContext.stop();
                span.addAnnotation("tx.rollbacked " + elapsed / 1000000 + "ms");
            } else {
                span.addAnnotation("tx.rollbacked");
            }
            span.setStatus(Status.UNKNOWN);
        }
    }

    /**
     * Wraps a transaction manager for providing a dummy recoverable interface.
     *
     * @author matic
     */
    public static class TransactionManagerWrapper implements RecoverableTransactionManager {

        protected TransactionManager tm;

        public TransactionManagerWrapper(TransactionManager tm) {
            this.tm = tm;
        }

        @Override
        public Transaction suspend() throws SystemException {
            return tm.suspend();
        }

        @Override
        public void setTransactionTimeout(int seconds) throws SystemException {
            tm.setTransactionTimeout(seconds);
        }

        @Override
        public void setRollbackOnly() throws IllegalStateException, SystemException {
            tm.setRollbackOnly();
        }

        @Override
        public void rollback() throws IllegalStateException, SecurityException, SystemException {
            tm.rollback();
        }

        @Override
        public void resume(Transaction tobj)
                throws IllegalStateException, InvalidTransactionException, SystemException {
            tm.resume(tobj);
        }

        @Override
        public int getStatus() throws SystemException {
            return tm.getStatus();
        }

        @Override
        public void commit() throws HeuristicMixedException, HeuristicRollbackException, IllegalStateException,
                RollbackException, SecurityException, SystemException {
            tm.commit();
        }

        @Override
        public void begin() throws SystemException {
            try {
                tm.begin();
            } catch (jakarta.transaction.NotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void recoveryError(Exception e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerNamedXAResourceFactory(NamedXAResourceFactory factory) {
            if (!RecoverableTransactionManager.class.isAssignableFrom(tm.getClass())) {
                throw new UnsupportedOperationException();
            }
            ((RecoverableTransactionManager) tm).registerNamedXAResourceFactory(factory);
        }

        @Override
        public void unregisterNamedXAResourceFactory(String factory) {
            if (!RecoverableTransactionManager.class.isAssignableFrom(tm.getClass())) {
                throw new UnsupportedOperationException();
            }
            ((RecoverableTransactionManager) tm).unregisterNamedXAResourceFactory(factory);
        }

        @Override
        public Transaction getTransaction() throws SystemException {
            final Transaction tx = tm.getTransaction();
            if (tx instanceof TransactionImpl) {
                return tx;
            }
            return new TransactionImpl(null, null) {
                @Override
                public void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
                        SecurityException, SystemException {
                    tx.commit();
                }

                @Override
                public void rollback() throws IllegalStateException, SystemException {
                    tx.rollback();
                }

                @Override
                public synchronized boolean enlistResource(XAResource xaRes)
                        throws IllegalStateException, RollbackException, SystemException {
                    return tx.enlistResource(xaRes);
                }

                @Override
                public synchronized boolean delistResource(XAResource xaRes, int flag)
                        throws IllegalStateException, SystemException {
                    return super.delistResource(xaRes, flag);
                }

                @Override
                public synchronized void setRollbackOnly() throws IllegalStateException {
                    try {
                        tx.setRollbackOnly();
                    } catch (SystemException e) {
                        throw new IllegalStateException(e);
                    }
                }

                @Override
                public void registerInterposedSynchronization(jakarta.transaction.Synchronization synchronization) {
                    TransactionHelper.lookupSynchronizationRegistry()
                                     .registerInterposedSynchronization(synchronization);
                }
            };
        }
    }

    public static TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
        return tmSynchRegistry;
    }

}
