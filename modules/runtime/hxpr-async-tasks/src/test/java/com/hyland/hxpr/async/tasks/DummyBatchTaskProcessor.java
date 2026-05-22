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
package com.hyland.hxpr.async.tasks;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;

import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;

import com.hyland.hxpr.async.tasks.executors.AbstractBatchTaskProcessor;
import com.hyland.hxpr.async.tasks.message.BatchTask;

/**
 * Dummy async tasks just updating the description metadata of the documents specified in input.
 */
public class DummyBatchTaskProcessor extends AbstractBatchTaskProcessor {

    private static final String SELECT_DOCUMENTS_IN = "SELECT * FROM SysContent WHERE sys_id IN ('%s')";

    public static String DUMMY_TASK_NAME = "dummyTask";

    @Override
    public String getName() {
        return DUMMY_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        var docs = session.query(String.format(SELECT_DOCUMENTS_IN, String.join("', '", documentIds)));
        docs.forEach(doc -> {
            doc.setPropertyValue(SYS_DESCRIPTION, "Updated description by dummy taskName");
        });
        session.save();
    }
}
