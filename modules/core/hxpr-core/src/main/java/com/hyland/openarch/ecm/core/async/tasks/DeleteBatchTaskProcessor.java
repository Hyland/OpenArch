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
package com.hyland.openarch.ecm.core.async.tasks;

import java.util.List;

import com.hyland.openarch.ecm.core.SessionImpl;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentExistsException;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.model.InternalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.executors.AbstractBatchTaskProcessor;
import com.hyland.hxpr.async.tasks.message.BatchTask;

public class DeleteBatchTaskProcessor extends AbstractBatchTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeleteBatchTaskProcessor.class);

    public static final String BATCH_DELETE_TASK_NAME = "deletion";

    @Override
    public String getName() {
        return BATCH_DELETE_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        InternalSession internalSession = ((SessionImpl) session).getSession();
        for (String id : documentIds) {
            try {
                InternalDocument doc = internalSession.getDocumentByUUID(id);
                doc.removeSingleton(session.getPrincipal());
            } catch (DocumentNotFoundException e) {
                // Document is already deleted
            } catch (DocumentExistsException | DocumentSecurityException e) {
                log.debug("Cannot delete %s: %s".formatted(id, e.getMessage()));
            }
        }
        session.save();
    }
}
