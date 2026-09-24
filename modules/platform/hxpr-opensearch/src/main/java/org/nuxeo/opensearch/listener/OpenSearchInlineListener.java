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

package org.nuxeo.opensearch.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventListener;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.commands.IndexingCommands;
import org.nuxeo.opensearch.commands.IndexingCommandsStacker;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

/**
 * Synchronous Event listener used to record indexing command, submitted after commit completion.
 */
public class OpenSearchInlineListener extends IndexingCommandsStacker implements EventListener, Synchronization {

    private static final Logger log = LogManager.getLogger(OpenSearchInlineListener.class);

    protected static ThreadLocal<Map<String, IndexingCommands>> transactionCommands = ThreadLocal.withInitial(
            HashMap::new);

    public static final ThreadLocal<Boolean> useSyncIndexing = new ThreadLocal<>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }

        @Override
        public void set(Boolean value) {
            super.set(value);
            if (Boolean.TRUE.equals(value)) {
                // switch existing stack to sync
                for (IndexingCommands cmds : transactionCommands.get().values()) {
                    for (IndexingCommand cmd : cmds.getCommands()) {
                        cmd.makeSync();
                    }
                }
            }
        }
    };

    protected static ThreadLocal<Boolean> isEnlisted = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    protected Map<String, IndexingCommands> getAllCommands() {
        return transactionCommands.get();
    }

    @Override
    protected boolean isSyncIndexingByDefault() {
        Boolean ret = useSyncIndexing.get();
        if (ret == null) {
            ret = false;
        }
        return ret;
    }

    @Override
    public void handleEvent(Event event) {
        String eventId = event.getName();
        if (!isEnlisted.get()) { // NOSONAR (never null)
            if (event.isCommitEvent()) {
                // manual flush on save if TxManager is not hooked
                afterCompletion(Status.STATUS_COMMITTED);
                return;
            }
            // try to enlist our listener
            isEnlisted.set(registerSynchronization(this));
        }
        if (!(event.getContext() instanceof DocumentEventContext)) {
            // don't process Events that are not tied to Documents
            return;
        }
        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        stackCommand(docCtx, eventId);
    }

    @Override
    public void beforeCompletion() {
        // nothing
    }

    @Override
    public void afterCompletion(int status) {
        try {
            if (getAllCommands().isEmpty() || Status.STATUS_MARKED_ROLLBACK == status
                    || Status.STATUS_ROLLEDBACK == status) {
                return;
            }
            List<IndexingCommand> commandList = new ArrayList<>();
            for (IndexingCommands cmds : getAllCommands().values()) {
                for (IndexingCommand cmd : cmds.getCommands()) {
                    commandList.add(cmd);
                }
            }
            OpenSearchService oss = Framework.getService(OpenSearchService.class);
            oss.runIndexingWorker(commandList);
        } finally {
            isEnlisted.remove();
            transactionCommands.remove();
            useSyncIndexing.remove();
        }
    }

    protected boolean registerSynchronization(Synchronization sync) {
        try {
            TransactionManager tm = TransactionHelper.lookupTransactionManager();
            if (tm != null) {
                if (tm.getTransaction() != null) {
                    tm.getTransaction().registerSynchronization(sync);
                    return true;
                }
                if (!Framework.isTestModeSet()) {
                    log.error("Unable to register synchronization : no active transaction");
                }
                return false;
            } else {
                log.error("Unable to register synchronization : no TransactionManager");
                return false;
            }
        } catch (IllegalStateException | SystemException | RollbackException e) {
            log.error("Unable to register synchronization", e);
            return false;
        }
    }

}
