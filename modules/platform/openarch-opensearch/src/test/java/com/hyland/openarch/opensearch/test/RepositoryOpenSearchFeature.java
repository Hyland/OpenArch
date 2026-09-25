/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.opensearch.test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

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
