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
package org.nuxeo.opensearch.commands;

import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_CHECKIN;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_EXTEND_RETENTION;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_MAKE_RECORD;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_REMOVE_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_SET_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.AFTER_SET_RETENTION;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.BINARYTEXT_UPDATED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDIN;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDOUT;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHILDREN_ORDER_CHANGED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED_BY_COPY;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_MOVED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_RESTORED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_SECURITY_UPDATED;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static com.hyland.openarch.ecm.core.api.trash.TrashService.DOCUMENT_TRASHED;
import static com.hyland.openarch.ecm.core.api.trash.TrashService.DOCUMENT_UNTRASHED;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.opensearch.OpenSearchConstants;
import org.nuxeo.opensearch.commands.IndexingCommand.Type;

/**
 * Contains logic to stack OpenSearch commands depending on Document events This class is mainly here to make testing
 * easier
 */
public abstract class IndexingCommandsStacker {

    protected static final Logger log = LogManager.getLogger(IndexingCommandsStacker.class);

    protected abstract Map<String, IndexingCommands> getAllCommands();

    protected abstract boolean isSyncIndexingByDefault();

    protected IndexingCommands getCommands(DocumentModel doc) {
        return getAllCommands().get(getDocKey(doc));
    }

    public void stackCommand(DocumentEventContext docCtx, String eventId) {
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc == null) {
            return;
        }
        if ("/".equals(doc.getPathAsString())) {
            if (log.isDebugEnabled()) {
                log.debug("Skip indexing command for root document");
            }
            if (eventId.equals(DOCUMENT_SECURITY_UPDATED)) {
                if (log.isDebugEnabled()) {
                    log.debug("Indexing root document children to update their permissions");
                }
                CoreSession systemSession = CoreInstance.getCoreSessionSystem(doc.getRepositoryName());
                DocumentModelList children = systemSession.getChildren(doc.getRef());
                children.forEach(child -> stackCommand(child, docCtx, eventId));
            }
        } else {
            stackCommand(doc, docCtx, eventId);
        }
    }

    protected void stackCommand(DocumentModel doc, DocumentEventContext docCtx, String eventId) {
        Boolean block = (Boolean) docCtx.getProperty(OpenSearchConstants.DISABLE_AUTO_INDEXING);
        if (block != null && block) {
            if (log.isDebugEnabled()) {
                log.debug("Indexing is disable, skip indexing command for doc {}", doc);
            }
            return;
        }
        boolean sync = isSynchronous(docCtx, doc);
        stackCommand(doc, eventId, sync);
    }

    protected boolean isSynchronous(DocumentEventContext docCtx, DocumentModel doc) {
        // 1. look at event context
        Boolean sync = (Boolean) docCtx.getProperty(OpenSearchConstants.OPENSEARCH_SYNC_INDEXING_FLAG);
        if (sync != null) {
            return sync;
        }
        // 2. look at document context
        sync = (Boolean) doc.getContextData(OpenSearchConstants.OPENSEARCH_SYNC_INDEXING_FLAG);
        if (sync != null) {
            return sync;
        }
        // 3. get the default
        sync = isSyncIndexingByDefault();
        return sync;
    }

    protected void stackCommand(DocumentModel doc, String eventId, boolean sync) {
        IndexingCommands cmds = getOrCreateCommands(doc);
        Type type;
        boolean recurse = false;
        switch (eventId) {
        case DOCUMENT_CREATED:
            type = Type.INSERT;
            break;
        case DOCUMENT_CREATED_BY_COPY:
            type = Type.INSERT;
            recurse = isFolderishOrHasEmbeddings(doc);
            break;
        case DOCUMENT_UPDATED, DOCUMENT_CHECKEDIN, DOCUMENT_CHECKEDOUT, BINARYTEXT_UPDATED,
             DOCUMENT_TRASHED, DOCUMENT_UNTRASHED, DOCUMENT_RESTORED, AFTER_MAKE_RECORD, AFTER_SET_RETENTION,
             AFTER_EXTEND_RETENTION, AFTER_SET_LEGAL_HOLD, AFTER_REMOVE_LEGAL_HOLD:
            type = Type.UPDATE;
            recurse = doc.hasEmbeddings();
            break;
        case ABOUT_TO_CHECKIN:
            type = Type.UPDATE;
            break;
        case DOCUMENT_MOVED:
            type = Type.UPDATE;
            recurse = isFolderishOrHasEmbeddings(doc);
            break;
        case DOCUMENT_REMOVED:
            type = Type.DELETE;
            recurse = true;
            break;
        case DOCUMENT_SECURITY_UPDATED:
            type = Type.UPDATE_SECURITY;
            recurse = isFolderishOrHasEmbeddings(doc);
            break;
        case DOCUMENT_CHILDREN_ORDER_CHANGED:
            type = Type.UPDATE_DIRECT_CHILDREN;
            recurse = true;
            break;
        default:
            return;
        }
        if (sync && recurse) {
            // split into 2 commands one sync and an async recurse
            cmds.add(type, true, false);
            cmds.add(type, false, true);
        } else {
            cmds.add(type, sync, recurse);
        }
    }

    private boolean isFolderishOrHasEmbeddings(DocumentModel doc) {
        return (doc.hasEmbeddings() || doc.isFolder()) && !doc.isVersion();
    }

    protected IndexingCommands getOrCreateCommands(DocumentModel doc) {
        IndexingCommands cmds = getCommands(doc);
        if (cmds == null) {
            cmds = new IndexingCommands(doc);
            getAllCommands().put(getDocKey(doc), cmds);
        }
        return cmds;
    }

    protected String getDocKey(DocumentModel doc) {
        return doc.getId();
    }

}
