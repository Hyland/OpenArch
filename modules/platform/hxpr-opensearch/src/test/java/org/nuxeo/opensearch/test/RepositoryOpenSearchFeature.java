/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */

package org.nuxeo.opensearch.test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 11.1
 */
@Features(CoreFeature.class)
@Deploy("org.nuxeo.opensearch.core")
@Deploy("org.nuxeo.opensearch.core:opensearch-test-contrib.xml")
public class RepositoryOpenSearchFeature implements RunnerFeature {

    @Override
    public void afterMethodRun(Method method, Object test) {
        // make sure there is an active Tx to do the cleanup, so we don't hide previous assertion
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    @Override
    public void initialize(FeaturesLoader loader) {
        loader.getFeature(TransactionalFeature.class).addWaiter(RepositoryOpenSearchFeature::await);
    }

    public static boolean await(Duration duration) throws InterruptedException {
        // Wait for ES indexing
        OpenSearchService oss = Framework.getService(OpenSearchService.class);
        if (oss == null) {
            return true;
        }

        try {
            oss.prepareWaitForIndexing().get(duration.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException cause) {
            return false;
        }

        // Explicit refresh
        oss.refresh();

        return true;
    }
}
