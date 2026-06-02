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

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ComputeDigestHelper;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.TaskService;

public class ComputeDigestTaskProcessor implements TaskProcessor<ComputeDigestTask> {

    public static final String COMPUTE_DIGEST_TASK_NAME = "computeDigest";

    private static final Logger log = LoggerFactory.getLogger(ComputeDigestTaskProcessor.class);

    @Override
    public String getName() {
        return COMPUTE_DIGEST_TASK_NAME;
    }

    @Override
    public Class<ComputeDigestTask> getTaskClass() {
        return ComputeDigestTask.class;
    }

    @Override
    public void process(ComputeDigestTask task) {
        log.debug("Start compute digest task");
        try {
            new ComputeDigestHelper(task.getBlobProviderId(), task.getKey()).computeAndReplaceDigest();
        } catch (NuxeoException e) {
            e.addInfo("For blob provider: " + task.getBlobProviderId() + " and key: " + task.getKey());
            throw e;
        }
        // Update the status of the indexing task
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());
        status.addProcessed(1);
        status.setState(COMPLETED);
        asyncTasksService.setStatus(status);
    }
}
