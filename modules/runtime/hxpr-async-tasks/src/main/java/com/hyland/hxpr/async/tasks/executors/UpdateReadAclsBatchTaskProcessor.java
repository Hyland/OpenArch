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
package com.hyland.hxpr.async.tasks.executors;

import org.nuxeo.ecm.core.api.CoreSession;

import com.hyland.hxpr.async.tasks.message.BatchTask;

import java.util.List;

public class UpdateReadAclsBatchTaskProcessor extends AbstractBatchTaskProcessor {

    public static final String BATCH_UPDATE_READ_ACLS_TASK_NAME = "updateReadAcls";

    @Override
    public String getName() {
        return BATCH_UPDATE_READ_ACLS_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        session.updateDenormalizedPropertiesNoCache(documentIds);
        session.save();
    }
}
