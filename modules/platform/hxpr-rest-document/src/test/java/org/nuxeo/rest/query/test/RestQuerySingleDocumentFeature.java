/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.query.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.rest.document.test.RestSingleDocumentFeature;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@Features(RepositoryOpenSearchFeature.class)
public class RestQuerySingleDocumentFeature extends RestSingleDocumentFeature {

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() {
        try {
            OpenSearchService oss = Framework.getService(OpenSearchService.class);
            oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
            oss.refresh();
        } catch (InterruptedException e) {
            throw new NuxeoException("Interrupted while waiting for completion", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new NuxeoException("Error waiting for completion", e);
        }
    }

    protected void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    @Override
    protected DocumentModel createDocument(CoreSession session) {
        startTransaction();
        DocumentModel doc = super.createDocument(session);
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        return doc;
    }

}
