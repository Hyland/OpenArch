/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;

import com.hyland.hxpr.async.tasks.TaskService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.opensearch.OpenSearchConstants;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.core.IncrementalIndexNameGenerator;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

/**
 * @since 9.3
 */
@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestManageAliasAndWriteAlias {

    @Inject
    protected OpenSearchService oss;

    protected String getRepositoryName() {
        String repositoryName = "test";
        List<String> repositoryNames = oss.getRepositoryNames();
        assertTrue(repositoryNames.contains(repositoryName), repositoryNames.toString());
        return repositoryName;
    }

    @Test
    public void testClientAliasMethods() {
        @SuppressWarnings("resource") // not ours to close
        OpenSearchClient client = oss.getClient();
        assertFalse(client.aliasExists("unknown-name"), "Expecting alias does not exist");
        assertFalse(client.indexExists("unknown-name"), "Expecting alias does not exist");

        String index = "a-test-index";
        assertFalse(client.indexExists(index));
        client.createIndex(index, "{\"index\" : {\"number_of_shards\" : 1, \"number_of_replicas\" : 0}}");
        assertTrue(client.indexExists(index));
        assertFalse(client.aliasExists(index));

        String alias = "a-test-alias";
        client.updateAlias(alias, index);
        assertTrue(client.aliasExists(alias));
        // an alias is seen as an existing index as well
        assertTrue(client.indexExists(alias));

        assertEquals(index, client.getFirstIndexForAlias(alias));
        try {
            client.deleteIndex(alias, 10);
            fail("Deleting an alias is not possible in 6.0 you must delete the index");
        } catch (IllegalArgumentException e) {
            // expected
        }
        client.deleteIndex(index, 10);
        assertFalse(client.indexExists(alias));
        assertFalse(client.aliasExists(alias));
        assertFalse(client.indexExists(index));
    }

    @Test
    public void testDefaultIndex() {
        String repo = getRepositoryName();

        String index = oss.getIndexNameForRepository(repo);
        assertTrue(oss.getClient().indexExists(index));
        assertEquals(index, oss.getWriteIndexName(index));
        assertFalse(oss.getClient().aliasExists(index));
    }

    @Test
    @Deploy("org.nuxeo.opensearch.core:opensearch-test-alias-contrib.xml")
    public void testIndexWithManageAlias() {
        String repo = getRepositoryName();

        String alias = oss.getIndexNameForRepository(repo);
        assertEquals("nxutestalias", alias);

        assertTrue(oss.getClient().aliasExists(alias), "Expecting an alias");

        String writeAlias = oss.getWriteIndexName(alias);
        assertEquals("nxutestalias-write", writeAlias);
        assertTrue(oss.getClient().aliasExists(writeAlias), "Expecting an alias");

        String searchIndex = oss.getClient().getFirstIndexForAlias(alias);
        String writeIndex = oss.getClient().getFirstIndexForAlias(writeAlias);
        assertEquals(searchIndex, writeIndex);
        assertTrue(oss.getClient().indexExists(searchIndex), "Expecting an index");
        assertTrue(writeIndex.startsWith("nxutestalias-0"), writeIndex);
        assertTrue(oss.getClient().mappingExists(alias, OpenSearchConstants.DOC_TYPE));
        assertEquals(repo, oss.getRepositoryForIndex(searchIndex));
        // recreate repo the alias are in sync
        oss.dropAndInitRepositoryIndex(repo);

        // new index in sync
        String newIndex = new IncrementalIndexNameGenerator().getNextIndexName(alias, writeIndex);
        assertEquals(newIndex, oss.getClient().getFirstIndexForAlias(alias));
        assertEquals(newIndex, oss.getClient().getFirstIndexForAlias(writeAlias));
        // same alias
        assertEquals("nxutestalias-write", writeAlias);
    }

    @Test
    @Deploy("org.nuxeo.opensearch.core:opensearch-test-alias-contrib.xml")
    public void testIndexWithManageAliasReindex() throws Exception {
        String repo = getRepositoryName();

        String searchAlias = oss.getIndexNameForRepository(repo);
        String writeAlias = oss.getWriteIndexName(searchAlias);

        // Split the reindex in two to ensure the search index name is fetched before all the async tasks are executed
        oss.dropAndInitRepositoryIndex(repo, false);

        String searchIndex = oss.getClient().getFirstIndexForAlias(searchAlias);
        String writeIndex = oss.getClient().getFirstIndexForAlias(writeAlias);
        assertNotEquals(searchIndex, writeIndex);
        assertEquals(repo, oss.getRepositoryForIndex(searchIndex));

        // Reindex repo here search and write index are different until reindexing is done
        oss.runReindexingWorker(repo, "SELECT sys_id FROM SysContent", true);
        Thread.sleep(500); // Ensure all tasks are started
        waitEndAsyncTasks();
        String searchIndexUpdated = oss.getClient().getFirstIndexForAlias(searchAlias);
        writeIndex = oss.getClient().getFirstIndexForAlias(writeAlias);
        assertNotEquals(searchIndex, searchIndexUpdated);
        assertEquals(searchIndexUpdated, writeIndex);
        assertEquals(repo, oss.getRepositoryForIndex(searchIndexUpdated));
    }

    @Test
    @Deploy("org.nuxeo.opensearch.core:opensearch-test-write-alias-contrib.xml")
    public void testIndexWithWriteAlias() {
        String repo = getRepositoryName();

        String searchIndex = oss.getIndexNameForRepository(repo);
        assertEquals("nxutestalias", searchIndex);

        String writeIndex = oss.getWriteIndexName(searchIndex);
        assertEquals("i-manage-my-alias-alone", writeIndex);
    }

    private void waitEndAsyncTasks() throws InterruptedException {
        Framework.getService(TaskService.class).awaitCompletion(Duration.ofSeconds(20));
    }
}
