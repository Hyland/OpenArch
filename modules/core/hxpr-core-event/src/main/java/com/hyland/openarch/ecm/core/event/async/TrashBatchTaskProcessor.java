/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.event.async;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.REPOSITORY_NAME;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventCategories.EVENT_DOCUMENT_CATEGORY;
import static com.hyland.openarch.ecm.core.api.trash.TrashService.DOCUMENT_TRASHED;
import static com.hyland.openarch.ecm.core.api.trash.TrashService.DOCUMENT_UNTRASHED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.trash.TrashService;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.executors.AbstractBatchTaskProcessor;
import com.hyland.hxpr.async.tasks.message.BatchTask;

public class TrashBatchTaskProcessor extends AbstractBatchTaskProcessor {

    public static final String BATCH_TRASH_TASK_NAME = "trash";

    public static final String PARAM_NAME = "value";

    public static final String SYSPROP_QUERY_TEMPLATE = "SELECT sys_id, sys_name, sys_parentId FROM SysContent WHERE sys_isTrashed=%s AND sys_id IN ('%s')";

    private static final Logger log = LoggerFactory.getLogger(TrashBatchTaskProcessor.class);

    @Override
    public String getName() {
        return BATCH_TRASH_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        Boolean trashValue = (Boolean) task.getProperties().get(PARAM_NAME);
        setSystemProperty(session, documentIds, trashValue);
    }

    public void setSystemProperty(CoreSession session, List<String> ids, boolean value) {
        List<DocumentRef> updatedRefs = new ArrayList<>(ids.size());
        String query = String.format(SYSPROP_QUERY_TEMPLATE, value ? "0" : "1", String.join("', '", ids));
        try (IterableQueryResult result = session.queryAndFetch(query)) {
            TrashService trashService = Framework.getService(TrashService.class);
            for (Map<String, Object> map : result) {
                DocumentRef ref = new IdRef((String) map.get(SYS_ID));
                try {
                    CoreInstance.doPrivileged(session.getRepositoryName(), systemSession -> {
                        systemSession.getDocument(ref).setPropertyValue(SYS_IS_TRASHED, value);
                    });
                    String docName = (String) map.get(SYS_NAME);
                    if (!value && trashService.isMangledName(docName)) {
                        DocumentRef parentRef = new IdRef((String) map.get(SYS_PARENT_ID));
                        session.move(ref, parentRef, trashService.unmangleName(session, parentRef, docName));
                    }
                    updatedRefs.add(ref);
                } catch (RepositoryException e) {
                    // TODO send to error stream
                    log.warn("Cannot set system property: isTrashed on: {}", ref, e);
                }
            }
        }
        try {
            session.save();
            if (!updatedRefs.isEmpty()) {
                fireEvent(session, value ? DOCUMENT_TRASHED : DOCUMENT_UNTRASHED, updatedRefs);
            }
        } catch (PropertyException e) {
            // TODO send to error stream
            log.warn("Cannot save session", e);
        }
    }

    protected void fireEvent(CoreSession session, String eventId, Collection<DocumentRef> refs) {
        EventService eventService = Framework.getService(EventService.class);
        DocumentModelList docs = session.getDocuments(refs.toArray(new DocumentRef[0]));
        docs.forEach(d -> {
            DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), d);
            ctx.setProperty(REPOSITORY_NAME, session.getRepositoryName());
            ctx.setCategory(EVENT_DOCUMENT_CATEGORY);
            Event event = ctx.newEvent(eventId);
            event.setImmediate(false);
            event.setInline(false);
            eventService.fireEvent(event);
        });
    }
}
