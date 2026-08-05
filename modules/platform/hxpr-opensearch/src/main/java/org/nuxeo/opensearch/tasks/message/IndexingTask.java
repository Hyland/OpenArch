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
package org.nuxeo.opensearch.tasks.message;

import static org.nuxeo.opensearch.tasks.IndexingTaskProcessor.INDEXING_TASK_NAME;
import static org.nuxeo.runtime.api.login.LoginService.SYSTEM_USERNAME;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.commands.IndexingCommand.Type;

import com.hyland.hxpr.async.tasks.message.AbstractTask;

public class IndexingTask extends AbstractTask {

    private List<IndexingCommand> commands;

    public IndexingTask() {
        // Empty constructor for deserialization
    }

    public IndexingTask(String repositoryName, List<IndexingCommand> cmds) {
        super(INDEXING_TASK_NAME, repositoryName, SYSTEM_USERNAME);
        cmds.stream()
                .filter(c -> c.getType() == Type.INSERT || c.getType() == Type.DELETE)
                .findFirst()
                .ifPresent(c -> this.timestamp = null);
        this.commands = new ArrayList<>(cmds);
    }

    public IndexingTask(String repositoryName, List<IndexingCommand> cmds, Long timestamp) {
        super(INDEXING_TASK_NAME, repositoryName, SYSTEM_USERNAME);
        this.commands = new ArrayList<>(cmds);
        this.timestamp = timestamp;
    }

    public List<IndexingCommand> getCommands() {
        return commands;
    }
}
