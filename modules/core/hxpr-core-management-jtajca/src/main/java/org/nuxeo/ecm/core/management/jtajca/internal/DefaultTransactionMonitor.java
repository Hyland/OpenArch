/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.management.jtajca.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.geronimo.transaction.manager.TransactionImpl;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.apache.geronimo.transaction.manager.TransactionManagerMonitor;
import org.apache.geronimo.transaction.manager.XidImpl;
import org.apache.logging.log4j.ThreadContext;
import org.nuxeo.ecm.core.management.jtajca.TransactionMonitor;
import org.nuxeo.ecm.core.management.jtajca.TransactionStatistics;
import org.nuxeo.ecm.core.management.jtajca.internal.DefaultMonitorComponent.ServerInstance;
import com.hyland.openarch.runtime.jtajca.RepositoryContainer;

/**
 * @author matic
 */
public class DefaultTransactionMonitor implements TransactionManagerMonitor, TransactionMonitor, Synchronization {

    protected static final Logger log = LogManager.getLogger(DefaultTransactionMonitor.class);

    protected TransactionManagerImpl tm;

    protected boolean enabled;

    @Override
    public void install() {
        tm = lookup();
        if (tm == null) {
            log.warn("Cannot monitor transactions, not a geronimo tx manager");
            return;
        }
        bindManagementInterface();
        toggle();
    }

    @Override
    public void uninstall() {
        if (tm == null) {
            return;
        }
        unbindManagementInterface();
        if (enabled) {
            toggle();
        }
    }

    protected ServerInstance self;

    protected void bindManagementInterface() {
        self = DefaultMonitorComponent.bind(TransactionMonitor.class, this);
    }

    protected void unbindManagementInterface() {
        DefaultMonitorComponent.unbind(self);
        self = null;
    }

    protected TransactionManagerImpl lookup() {
        TransactionManager localtm = RepositoryContainer.getTransactionManager();
        if (!(localtm instanceof TransactionManagerImpl tmi)) {
            return null;
        }
        return tmi;
    }

    protected TransactionStatistics lastCommittedStatistics;

    protected TransactionStatistics lastRollbackedStatistics;

    protected final Map<Object, DefaultTransactionStatistics> activeStatistics = new HashMap<>();

    public static String id(Object key) {
        if (key instanceof XidImpl) {
            byte[] globalId = ((XidImpl) key).getGlobalTransactionId();
            StringBuilder sb = new StringBuilder();
            for (byte aGlobalId : globalId) {
                sb.append(String.format("%02X", aGlobalId));
            }
            return StringUtils.stripEnd(sb.toString(), "0");
        }
        return key.toString();
    }

    public static String id(Transaction tx) {
        return Integer.toHexString(tx.hashCode());
    }

    @Override
    public void threadAssociated(Transaction tx) {
        long now = System.currentTimeMillis();
        Object key = tm.getTransactionKey();
        ThreadContext.put("tx", id(key));
        final Thread thread = Thread.currentThread();
        DefaultTransactionStatistics info = new DefaultTransactionStatistics(key);
        info.threadName = thread.getName();
        info.status = TransactionStatistics.Status.fromTx(tx);

        info.startTimestamp = now;
        info.startCapturedContext = new Throwable("** start invoke context **");
        synchronized (this) {
            activeStatistics.put(key, info);
        }
        if (TransactionStatistics.Status.ACTIVE == info.status) {
            tm.registerInterposedSynchronization(this); // register end status
        }
        if (log.isTraceEnabled()) {
            log.trace(info.toString());
        }
    }

    @Override
    public void threadUnassociated(Transaction tx) {
        try {
            Object key = ((TransactionImpl) tx).getTransactionKey();
            DefaultTransactionStatistics stats;
            synchronized (DefaultTransactionMonitor.class) {
                stats = activeStatistics.remove(key);
            }
            if (stats == null) {
                log.debug("{} not found in active statistics map", key);
                return;
            }
            if (log.isTraceEnabled()) {
                log.trace(stats);
            }
            if (TransactionStatistics.Status.COMMITTED.equals(stats.status)) {
                lastCommittedStatistics = stats;
            } else if (TransactionStatistics.Status.ROLLEDBACK.equals(stats.status)) {
                lastRollbackedStatistics = stats;
            }
        } finally {
            ThreadContext.remove("tx");
        }
    }

    @Override
    public List<TransactionStatistics> getActiveStatistics() {
        List<TransactionStatistics> l = new ArrayList<>(activeStatistics.values());
        l.sort((o1, o2) -> o1.getStartDate().compareTo(o2.getEndDate()));
        return l;
    }

    @Override
    public long getActiveCount() {
        return tm.getActiveCount();
    }

    @Override
    public long getTotalCommits() {
        return tm.getTotalCommits();
    }

    @Override
    public long getTotalRollbacks() {
        return tm.getTotalRollbacks();
    }

    @Override
    public TransactionStatistics getLastCommittedStatistics() {
        return lastCommittedStatistics;
    }

    @Override
    public TransactionStatistics getLastRollbackedStatistics() {
        return lastRollbackedStatistics;
    }

    protected DefaultTransactionStatistics thisStatistics() {
        Object key = tm.getTransactionKey();
        DefaultTransactionStatistics stats;
        synchronized (this) {
            stats = activeStatistics.get(key);
        }
        if (stats == null) {
            log.debug("{} not found in active statistics map", key);
        }
        return stats;
    }

    @Override
    public void beforeCompletion() {
        DefaultTransactionStatistics stats = thisStatistics();
        if (stats == null) {
            return;
        }
        stats.endCapturedContext = new Throwable("** end invoke context **");
    }

    @Override
    public void afterCompletion(int code) {
        DefaultTransactionStatistics stats = thisStatistics();
        if (stats == null) {
            return;
        }
        stats.endTimestamp = System.currentTimeMillis();
        stats.status = TransactionStatistics.Status.fromCode(code);
        if (code == Status.STATUS_COMMITTED) {
            lastCommittedStatistics = stats;
        } else if (code == Status.STATUS_ROLLEDBACK) {
            lastRollbackedStatistics = stats;
            stats.endCapturedContext = new Throwable("** rollback context **");
        }
    }

    @Override
    public boolean toggle() {
        if (enabled) {
            tm.removeTransactionAssociationListener(this);
            activeStatistics.clear();
            enabled = false;
        } else {
            tm.addTransactionAssociationListener(this);
            enabled = true;
        }
        return enabled;
    }

    @Override
    public boolean getEnabled() {
        return enabled;
    }

}
