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
package com.hyland.openarch.async.tasks.executors;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.PropertyException;

import com.hyland.openarch.async.tasks.message.BatchTask;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SetSystemPropertiesBatchTaskProcessor extends AbstractBatchTaskProcessor {

    public static final String BATCH_SET_SYSTEM_PROPERTIES_TASK_NAME = "setSystemProperties";

    private static final Logger log = LogManager.getLogger(SetSystemPropertiesBatchTaskProcessor.class);

    @Override
    public String getName() {
        return BATCH_SET_SYSTEM_PROPERTIES_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        Map<String, Serializable> properties = task.getProperties();
        List<IdRef> refs = documentIds.stream().map(IdRef::new).toList();
        for (DocumentRef ref : refs) {
            for (Map.Entry<String, Serializable> entry : properties.entrySet()) {
                try {
                    CoreInstance.doPrivileged(session.getRepositoryName(), systemSession -> {
                        systemSession.getDocument(ref).setPropertyValue(entry.getKey(), entry.getValue());
                    });
                } catch (RepositoryException e) {
                    log.error("Cannot set system property: {} on: {}", entry.getKey(), ref, e);
                }
            }
        }
        try {
            session.save();
        } catch (PropertyException e) {
            log.error("Cannot save session", e);
        }
    }
}
