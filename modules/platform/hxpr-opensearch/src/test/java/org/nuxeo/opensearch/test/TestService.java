/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     tiry
 */

package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.opensearch.api.OpenSearchIndexStoreStats;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class TestService {

    @Inject
    OpenSearchService searchService;

    @Test
    public void checkDeclaredServices() throws Exception {
        assertNotNull(searchService);

        @SuppressWarnings("resource") // not ours to close
        OpenSearchClient client = searchService.getClient();
        assertNotNull(client);

        assertEquals(0, searchService.getTotalCommandProcessed());
        assertFalse(searchService.isIndexingInProgress());

        assertEquals(3, searchService.getRepositoryNames().size());
    }

    @Test
    public void verifyNodeStartedWithConfig() throws Exception {
        assertTrue(searchService.getClient().waitForYellowStatus(null, 10));
    }

    @Test
    public void verifyPrepareWaitForIndexing() throws Exception {
        Future<Boolean> futureRet = searchService.prepareWaitForIndexing();
        assertFalse(futureRet.isCancelled());
        assertTrue(futureRet.get());
        assertTrue(futureRet.isDone());
        assertTrue(futureRet.get());
    }

    @Test
    public void verifyPrepareWaitForIndexingTimeout() throws Exception {
        // when a worker is created it is pending
        assertFalse(searchService.isIndexingInProgress());
        searchService.runReindexingWorker("test", "select * FROM SysContent");
        Future<Boolean> futureRet = searchService.prepareWaitForIndexing();
        try {
            futureRet.get(0, TimeUnit.MILLISECONDS);
            // sometime we don't timeout
            assertTrue(futureRet.isDone());
        } catch (TimeoutException e) {
            assertTrue(futureRet.get());
        } finally {
            assertFalse(searchService.isIndexingInProgress());
        }
    }

    @Test
    public void verifyIndexStoreStats() {
        OpenSearchIndexStoreStats stats = searchService.getIndexStoreStats();
        assertTrue(stats.getTotalSize() > 0);
        assertNotNull(stats.getIndexSizes());
    }

}
