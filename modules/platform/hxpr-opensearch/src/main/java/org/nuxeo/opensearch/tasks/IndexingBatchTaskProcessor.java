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
package org.nuxeo.opensearch.tasks;

import static org.nuxeo.opensearch.OpenSearchServiceImpl.PROPERTY_REINDEXING_COMMAND;
import static org.nuxeo.opensearch.tasks.IndexingScrollerTaskProcessor.PROPERTY_SYNC_ALIAS;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.opensearch.Timestamp;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.executors.AbstractBatchTaskProcessor;
import com.hyland.hxpr.async.tasks.message.BatchTask;

public class IndexingBatchTaskProcessor extends AbstractBatchTaskProcessor {

    public static final String BATCH_INDEXING_TASK_NAME = "bucketIndexing";

    private static final Logger log = LoggerFactory.getLogger(IndexingBatchTaskProcessor.class);

    @Override
    public String getName() {
        return BATCH_INDEXING_TASK_NAME;
    }

    @Override
    public void process(BatchTask task, List<String> documentIds, CoreSession session) {
        var oss = Framework.getService(OpenSearchService.class);
        log.debug("Start index non recursive for task {}", task.getTaskId());
        var commandReindexing = (Boolean) task.getProperties().getOrDefault(PROPERTY_REINDEXING_COMMAND, false);
        oss.indexNonRecursive(getIndexingCommands(session, documentIds), commandReindexing);
    }

    private List<IndexingCommand> getIndexingCommands(CoreSession session, List<String> ids) {
        List<IndexingCommand> ret = new ArrayList<>(ids.size());
        long now = Timestamp.currentTimeMicros();
        for (DocumentModel doc : loadDocuments(session, ids)) {
            IndexingCommand cmd = new IndexingCommand(doc, IndexingCommand.Type.INSERT, false, false);
            cmd.setOrder(now);
            ret.add(cmd);
        }
        return ret;
    }

    @Override
    public void handlePostProcessing(BatchTask task) {
        var oss = Framework.getService(OpenSearchService.class);
        var syncAlias = (Boolean) task.getProperties().getOrDefault(PROPERTY_SYNC_ALIAS, false);
        if (syncAlias) {
            log.debug("Sync alias for task {}", task.getTaskId());
            oss.syncSearchAndWriteAlias(oss.getIndexNameForRepository(task.getRepository()));
        }
    }

}
