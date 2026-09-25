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
package org.nuxeo.rest.query.test;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.ecm.platform.test.UserManagerMockFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockTestConfiguration;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.test.RepositoryOpenSearchFeature;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.springframework.context.annotation.Import;

import com.hyland.openarch.async.tasks.TaskService;

@Features({ UserManagerMockFeature.class, RepositoryOpenSearchFeature.class })
@RepositoryConfig(cleanup = Granularity.CLASS)
@Import(UserManagerMockTestConfiguration.class)
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-write-types-contrib.xml")
public class RestQueryAggregationsFeature implements RunnerFeature {

    public static final String ANY_PROPERTY_FIELD = "any_obj";

    public static final String STRING_LIST_FIELD = "myStringList";

    public static final String STRING_LIST_PROP_PATH = ANY_PROPERTY_FIELD + "." + STRING_LIST_FIELD;

    public static final String BOOLEAN_FIELD = "myBoolean";

    public static final String BOOLEAN_PROP_PATH = ANY_PROPERTY_FIELD + "." + BOOLEAN_FIELD;

    public static final String ALT_USER_WITH_RESTRICTED_ACCESS = "altUserRestricted";

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
        List<DocumentModel> documents = new ArrayList<>();

        List<List<String>> stringListPropValues = List.of(
                List.of("objectId", "sourceId", "createdAt", "modifiedAt", "createdBy"),
                List.of("objectId", "sourceId", "sourceTimestamp", "cm:description"),
                List.of("sourceId", "modifiedBy", "modifiedAt"),
                List.of("objectId", "createdAt", "createdBy", "ALLOW_ACCESS"),
                List.of("sourceTimestamp", "cm:description", "modifiedBy", "ALLOW_ACCESS"),
                List.of("objectId", "sourceId", "createdAt"), List.of("modifiedAt", "modifiedBy", "ALLOW_ACCESS"),
                List.of("objectId", "sourceTimestamp", "cm:description"),
                List.of("sourceId", "createdBy", "ALLOW_ACCESS"), List.of("objectId", "modifiedAt", "modifiedBy"));

        for (int i = 0; i < stringListPropValues.size(); i++) {
            String name = "aggdoc" + i;

            DocumentModel doc = session.createDocumentModel("/", name, "MyFile");
            doc.setPropertyValue(SYS_TITLE, "AggFile" + i);
            doc.setPropertyValue(ANY_PROPERTY_FIELD, Map.of(STRING_LIST_FIELD, stringListPropValues.get(i),
                    BOOLEAN_FIELD, i % 2 == 0 ? Boolean.TRUE : Boolean.FALSE));
            doc.setPropertyValue(SYS_DESCRIPTION, "source-" + (i % 3));

            Blob blob = new StringBlob("aggregation test content " + i);
            blob.setFilename("aggfile" + i + ".txt");

            doc.setPropertyValue(SYSFILE_BLOB, blob);

            // Set ACL to allow alt user to read document with index 2
            if (i == 2) {
                ACL customAcl = doc.getLocalACL();
                customAcl.add(ACE.builder().user("ced9ee59-c4d5-4b58-866a-b84cd6cc6fae").permission(SecurityConstants.READ).build());
                doc.setACL(customAcl, true);
            }
            doc.create();

            documents.add(doc);
        }
        session.save();
        return documents;
    }

    protected void cleanupDocuments(CoreSession session, List<DocumentModel> docs) {
        if (docs != null) {
            docs.stream().map(DocumentModel::getId).forEach(id -> session.removeDocument(new IdRef(id)));
            session.save();
        }
    }

    public void waitForCompletion() {
        try {
            Framework.getService(TaskService.class).awaitCompletion(Duration.ofSeconds(20));
            OpenSearchService oss = Framework.getService(OpenSearchService.class);
            oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
            oss.refresh();
        } catch (InterruptedException e) {
            throw new RepositoryException("Interrupted while waiting for completion", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RepositoryException("Error waiting for completion", e);
        }
    }

    protected void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

}
