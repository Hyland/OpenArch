/*
 * (C) Copyright 2025 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.opensearch.tasks.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.hyland.hxpr.async.tasks.AsyncTasksFeature;
import com.hyland.hxpr.async.tasks.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.runtime.cluster.BasicClusterFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({AsyncTasksFeature.class, BasicClusterFeature.class})
@Deploy("org.nuxeo.runtime.cluster.api.tests:OSGI-INF/test-nodeid.xml")
public class IndexingTaskTest {

    @Test
    public void jsonCanBeDeserializedAsTask() throws Exception {
        var json = """
               {
                    "taskName":"indexing",
                    "repository":"default",
                    "username":"system",
                    "retryCount":0,
                    "nodeId":"123",
                    "timestamp":1745952650139,
                    "commands":[
                        {
                            "id":"IxCd-91",
                            "type":"DELETE",
                            "sync":false,
                            "recurse":true,
                            "targetDocumentId":"cfbc33d1-29ba-4aa1-ba64-eede6b7a122b",
                            "path":"/Countries_f24520df-e72f-4330-b3e8-8605919f369a",
                            "repositoryName":"test",
                            "schemas":null,
                            "order":0}
                        ],
                    "taskId":"indexing:79e9ab66-a829-4807-8aa1-87bf28921219"
               }
                """;
        var task = TaskServiceImpl.MAPPER.readValue(json, IndexingTask.class);
        assertThat(task.getTaskName()).isEqualTo("indexing");
        assertThat(task.getRepository()).isEqualTo("default");
        assertThat(task.getUsername()).isEqualTo("system");
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getNodeId()).isEqualTo("123");
        assertThat(task.getTimestamp()).isEqualTo(1745952650139L);
        assertThat(task.getTaskId()).isEqualTo("indexing:79e9ab66-a829-4807-8aa1-87bf28921219");
    }

    @Test
    public void testIndexingTaskWithTypeInsertCommandReturnsNullTimeStamp() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        IndexingCommand cmd = new IndexingCommand(doc, IndexingCommand.Type.INSERT, false, false);
        IndexingTask task = new IndexingTask(doc.getRepositoryName(), List.of(cmd));
        assertThat(task.getTaskId()).asString().startsWith("indexing:");
        assertThat(task.getCommands()).elements(0);
        assertThat(task.getCommands().get(0).getType()).isEqualTo(IndexingCommand.Type.INSERT);
        assertThat(task.getTaskName()).isEqualTo("indexing");
        assertThat(task.getTimestamp()).isNull();
    }

    @Test
    public void testIndexingTaskWithTypeDeleteCommandReturnsNullTimeStamp() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        IndexingCommand cmd = new IndexingCommand(doc, IndexingCommand.Type.DELETE, false, true);
        IndexingTask task = new IndexingTask(doc.getRepositoryName(), List.of(cmd));
        assertThat(task.getTaskId()).asString().startsWith("indexing:");
        assertThat(task.getCommands()).elements(0);
        assertThat(task.getCommands().get(0).getType()).isEqualTo(IndexingCommand.Type.DELETE);
        assertThat(task.getTaskName()).isEqualTo("indexing");
        assertThat(task.getTimestamp()).isNull();
    }
}
