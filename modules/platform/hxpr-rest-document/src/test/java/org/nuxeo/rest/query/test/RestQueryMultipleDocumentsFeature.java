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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.hyland.hxpr.async.tasks.TaskService;

@Features(RepositoryOpenSearchFeature.class)
@RepositoryConfig(cleanup = Granularity.CLASS)
@Deploy("org.nuxeo.ecm.platform.usermanager")
public class RestQueryMultipleDocumentsFeature implements RunnerFeature {

    public static final ZonedDateTime NOW = ZonedDateTime.now()
                                                         .withZoneSameInstant(ZoneOffset.UTC)
                                                         .truncatedTo(ChronoUnit.DAYS);

    FeaturesLoader loader;

    CoreFeature core;

    List<DocumentModel> docs;

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void start() {
        core = loader.getFeature(CoreFeature.class);
    }

    @Override
    public void beforeRun() {
        OpenSearchService oss = Framework.getService(OpenSearchService.class);
        oss.initIndexes(true);
        startTransaction();
        docs = createDocuments(core.getCoreSession());
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
    }

    @Override
    public void afterRun() {
        if (docs != null) {
            TransactionHelper.startTransaction();
            cleanupDocuments(core.getCoreSession(), docs);
            docs = null;
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    protected List<DocumentModel> createDocuments(CoreSession session) {
        List<DocumentModel> docs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String name = "doc" + i;
            DocumentModel doc = session.createDocumentModel("/", name, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, "File" + i);
            doc.setPropertyValue("mydc_source", "Source " + i);
            doc.setPropertyValue("mydc_nature", "Nature" + i % 2);
            doc.setPropertyValue("mydc_coverage", "Coverage" + i % 3);
            Blob blob = new StringBlob("x".repeat(1024 * i));
            blob.setFilename("file" + i + ".txt");
            doc.setPropertyValue(SYSFILE_BLOB, blob);
            doc.create();
            try {
                Thread.sleep(10); // NOSONAR sleep a bit to have different sys_modified
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NuxeoException("Interrupted", e);
            }
            docs.add(doc);
        }
        session.save();
        return docs;
    }

    protected void cleanupDocuments(CoreSession session, List<DocumentModel> docs) {
        if (docs != null) {
            docs.stream().map(DocumentModel::getId).forEach(id -> session.removeDocument(new IdRef(id)));
            session.save();
        }
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() {
        try {
            Framework.getService(TaskService.class).awaitCompletion(Duration.ofSeconds(20));
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

}
