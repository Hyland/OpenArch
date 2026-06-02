/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package org.nuxeo.ecm.core.async.tasks;

import java.time.Instant;
import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.executors.AbstractBatchTaskProcessor;
import com.hyland.hxpr.async.tasks.message.BatchTask;

public class RetentionExpiredBatchTaskProcessor extends AbstractBatchTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(RetentionExpiredBatchTaskProcessor.class);

    public static final String BATCH_RETENTION_EXPIRED_TASK_NAME = "retentionExpired";

    @Override
    public String getName() {
        return BATCH_RETENTION_EXPIRED_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        List<IdRef> refs = documentIds.stream().map(IdRef::new).toList();
        for (DocumentRef ref : refs) {
            // sanity checks
            if (!session.exists(ref)) {
                // document might have been deleted
                log.debug("Document: {} does not exist", ref);
                continue;
            }
            if (!session.isRecord(ref)) {
                log.debug("Document: {} is not a record", ref);
                continue;
            }
            Instant retainUntil = session.getRetainUntil(ref);
            if (retainUntil == null) {
                log.debug("Document: {} was not under retention", ref);
                continue;
            }
            if (isUnderRetention(retainUntil)) {
                log.debug("Document: {} is still under retention: {}", ref, retainUntil);
                continue;
            }

            try {
                // reset retention to null
                session.setRetainUntil(ref, null, null);
                // send event about retention expired
                sendEvent(session, ref, retainUntil);
            } catch (NuxeoException e) {
                // TODO send to error stream
                log.warn("Cannot reset retention on: {}", ref, e);
            }

        }
        try {
            session.save();
        } catch (PropertyException e) {
            // TODO send to error stream
            log.warn("Cannot save session", e);
        }
    }

    private boolean isUnderRetention(Instant retainUntil) {
        return retainUntil != null && Instant.now().isBefore(retainUntil);
    }

    private void sendEvent(CoreSession session, DocumentRef ref, Instant retainUntil) {
        DocumentModel doc = session.getDocument(ref);
        DocumentEventContext ctx = new DocumentEventContext(session, null, doc);
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, session.getRepositoryName());
        ctx.setProperty(DocumentEventContext.CATEGORY_PROPERTY_KEY, DocumentEventCategories.EVENT_DOCUMENT_CATEGORY);
        ctx.setProperty(CoreEventConstants.RETAIN_UNTIL, retainUntil);
        ctx.setProperty(DocumentEventContext.COMMENT_PROPERTY_KEY, retainUntil.toString());
        Event event = ctx.newEvent(DocumentEventTypes.RETENTION_EXPIRED);
        Framework.getService(EventService.class).fireEvent(event);
    }
}
