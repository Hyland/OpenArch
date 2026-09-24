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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.PropertyException;

import com.hyland.openarch.async.tasks.message.BatchTask;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SetPropertiesBatchTaskProcessor extends AbstractBatchTaskProcessor {

    private static final Logger log = LogManager.getLogger(SetPropertiesBatchTaskProcessor.class);

    public static final String BATCH_SET_PROPERTIES_TASK_NAME = "setProperties";

    public static final String PARAM_CHECK_IN = "checkIn";

    boolean checkIn;

    @Override
    public String getName() {
        return BATCH_SET_PROPERTIES_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        Map<String, Serializable> properties = task.getProperties();
        initParams(properties);
        for (DocumentModel doc : loadDocuments(session, documentIds)) {
            // update properties
            for (Map.Entry<String, Serializable> es : properties.entrySet()) {
                if (!PARAM_CHECK_IN.equals(es.getKey())) {
                    try {
                        doc.setPropertyValue(es.getKey(), es.getValue());
                    } catch (PropertyException e) {
                        // TODO send to error stream
                        log.warn("Cannot write property: {} of document: {}", es.getKey(), doc.getId(), e);
                    }
                }
            }
            try {
                if (checkIn) {
                    doc.checkIn();
                }
            } catch (PropertyException e) {
                // TODO send to error stream
                log.warn("Cannot save document: {}", doc.getId(), e);
            }
        }
        session.save();
    }

    protected void initParams(Map<String, Serializable> properties) {
        Serializable checkInParam = properties.get(PARAM_CHECK_IN);
        checkIn = Boolean.parseBoolean(String.valueOf(checkInParam));
    }
}
