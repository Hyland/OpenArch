/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.async.tasks;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.SCHEDULED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import com.google.inject.Inject;
import com.hyland.hxpr.async.tasks.AsyncTasksFeature;
import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

@ExtendWith(FeaturesExtension.class)
@Features({CoreFeature.class, AsyncTasksFeature.class})
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestCleanupOrphansTaskProcessor {

    CleanupOrphansTaskProcessor sut = new CleanupOrphansTaskProcessor();

    @Inject
    CoreSession session;

    @Inject
    TaskService taskService;

    @BeforeEach
    public void setUp() {
        DocumentModel doc = session.createDocumentModel("/", "sourceFolder", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "Source Folder");
        doc.create();

        doc = session.createDocumentModel("/", "targetFolder", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "Target Folder");
        doc.create();

        doc = session.createDocumentModel("/sourceFolder", "doc1", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "Document 1");
        doc.create();

        doc = session.createDocumentModel("/sourceFolder", "doc2", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "Document 2");
        doc.create();

        doc = session.createDocumentModel("/targetFolder", "doc3", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "Document 3");
        doc.create();
        session.save();
    }

    @Test
    public void taskProcessorCanRemovedDocumentsReturnedByQuery() {
        // Create a status manually since the task is not submitted through the TaskService
        var taskCommand = new CleanupOrphansTask(session.getRepositoryName(),
                session.getPrincipal().getId());
        var status = new TaskStatus(taskCommand.getTaskId());
        status.setUsername(taskCommand.getUsername());
        status.setState(SCHEDULED);
        status.setTask(taskCommand.getTaskId());
        status.setSubmitTime(Instant.now());
        taskService.setStatus(status);

        var results = session.queryAndFetch("SELECT sys_id, sys_parentId FROM SysContent WHERE sys_path STARTSWITH '/sourceFolder'");
        // update the parent ids to simulate orphans
        var updatedResults = new ArrayList<Map<String, Object>>();
        results.forEach(map -> {
            map.put("sys_parentId", "99999999999999999999"); // non existing parent id
            updatedResults.add(map);
        });
        sut.batchRemoveDocuments(session, updatedResults, taskCommand);

        // Check that the documents under /sourceFolder have been removed
        var docs = session.query("SELECT * FROM SysFile");
        assertThat(docs).size().isEqualTo(1); // Only doc3 under /targetFolder should remain
        assertThat(docs.get(0).getPathAsString()).isEqualTo("/targetFolder/doc3");
    }
}
