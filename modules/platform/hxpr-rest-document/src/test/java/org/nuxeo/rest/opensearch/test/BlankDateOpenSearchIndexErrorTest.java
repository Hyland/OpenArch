/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.opensearch.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.UserManagerMockFeature;
import org.nuxeo.ecm.platform.test.UserManagerMockTestConfiguration;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.acl.ACLMapper;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.query.OpenSearchQueryManager;
import org.nuxeo.rest.query.conf.DefaultNamedQueryRegistry;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.upload.handler.DefaultUploadHandler;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.hyland.hxpr.async.tasks.TaskService;

/*
    This test checks the specific default behavior of OpenSearch functionality: date_detection.
    If in not yet indexed by OpenSearch field, properly formatted date data is sent to OpenSearch, OpenSearch will by default index it as date.
    Then, if you try to send not a date data in the same field of the document, opensearch indexing will fail and document won't be searchable.
    For now, date detection should be disabled by default in hxpr app.
 */
@Features({RepositoryOpenSearchFeature.class, UserManagerMockFeature.class})
@Deploy("org.nuxeo.opensearch.core:opensearch-test-nested-mapping-contrib.xml")
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/open-search-blank-date-error-contrib.xml")
@Tag("useOpenSearch")
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest( classes = { OpenSearchQueryManager.class, DefaultNamedQueryRegistry.class, DocumentMapper.class,
        DefaultUploadHandler.class, ACLMapper.class, ACEMapper.class })
public class BlankDateOpenSearchIndexErrorTest extends RuntimeSpringBootTest {

    private static final String TYPE_NAME = "NewType";
    private static final String BLOB_UPLOAD_DATA = "test content";
    private static final String NEW_TYPE_QUERY = "SELECT * FROM " + TYPE_NAME;

    private static final String NEW_DATE_FIELD = "NewDateField";
    private static final String OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX = "osbd_";
    private static final String REPO_ID = "test";
    private static final int OPENSEARCH_QUERY_MANAGER_TIMEOUT = 20;
    public static final String NEW_DATE_FIELD_VALUE = "2022-01-01";

    @RuntimeInject
    protected CoreSession coreSession;

    @RuntimeInject
    protected TaskService asyncTasksService;

    @RuntimeInject
    OpenSearchService oss;

    @Autowired
    OpenSearchQueryManager openSearchQueryManager;

    @Test
    public void shouldNotFieldBeConvertedToDateWhenDateDetectionIsSetToFalse() throws Exception {
        // Creation of the first Document. The first document contains properly formatted date.
        // It causes opensearch to index the field as date if "date_detection" is true
        // It should index field as a string if "date_detection" is false
        Blob firstBlob = Blobs.createBlob(BLOB_UPLOAD_DATA, TEXT_PLAIN, UTF_8, "test.txt");
        DocumentModel firstDocument = coreSession.createDocumentModel("/", "FirstDocument", TYPE_NAME);
        firstDocument.setPropertyValue(OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX + NEW_DATE_FIELD, NEW_DATE_FIELD_VALUE);
        firstDocument.setPropertyValue(SYSFILE_BLOB, firstBlob);
        firstDocument.create();
        String firstDocumentSysId = firstDocument.getId();
        coreSession.save();
        waitForCompletion();
        openSearchQueryManager.waitForIndexing(REPO_ID, OPENSEARCH_QUERY_MANAGER_TIMEOUT, true);

        DocumentQueryResultDTO resultAfterFirstDoc = openSearchQueryManager.getResults(new DocumentQueryDTO(REPO_ID, NEW_TYPE_QUERY, 100L, 0L, List.of()));
        assertEquals(1L, resultAfterFirstDoc.count());
        assertEquals(firstDocumentSysId, resultAfterFirstDoc.documents().getFirst().getId());

        // Creation of the second Document. The second document contains blank field in date field.
        // It causes opensearch to fail to index the document if "date_detection" is true
        // OpenSearch should index the document successfully if "date_detection" is false
        Blob secondBlob = Blobs.createBlob(BLOB_UPLOAD_DATA, TEXT_PLAIN, UTF_8, "test.txt");
        DocumentModel secondDocument = coreSession.createDocumentModel("/", "SecondDocument", TYPE_NAME);
        secondDocument.setPropertyValue(OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX + NEW_DATE_FIELD, "");
        secondDocument.setPropertyValue(SYSFILE_BLOB, secondBlob);
        secondDocument.create();
        String secondDocumentSysId = secondDocument.getId();
        coreSession.save();
        waitForCompletion();
        openSearchQueryManager.waitForIndexing(REPO_ID, OPENSEARCH_QUERY_MANAGER_TIMEOUT, true);

        assertEquals(NEW_DATE_FIELD_VALUE,
        firstDocument.getPropertyValue(OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX + NEW_DATE_FIELD));
        assertTrue(secondDocument.getPropertyValue(OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX + NEW_DATE_FIELD).toString().isBlank());

        // as date_detection is set to false, both documents should be indexed by OpenSearch successfully
        DocumentQueryResultDTO resultAfterSecondDoc = openSearchQueryManager.getResults(new DocumentQueryDTO(REPO_ID, NEW_TYPE_QUERY, 100L, 0L, List.of()));
        assertThat(resultAfterSecondDoc.documents()).hasSize(2).extracting(DocumentDTO::getId)
                .containsExactlyInAnyOrder(firstDocumentSysId, secondDocumentSysId);
        resultAfterSecondDoc.documents().forEach(doc -> {
            if (doc.getId().equals(firstDocumentSysId)) {
                assertThat(doc.getProperties()).containsEntry(OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX + NEW_DATE_FIELD,
                        NEW_DATE_FIELD_VALUE);
            } else if (doc.getId().equals(secondDocumentSysId)) {
                assertThat(doc.getProperties()).containsEntry(OPEN_SEARCH_BLANK_DATE_SCHEMA_PREFIX + NEW_DATE_FIELD,
                        "");
            } else {
                throw new IllegalStateException("Unexpected document id: " + doc.getId());
            }
        });
    }

    private void waitForCompletion() throws Exception {
        TransactionHelper.commitOrRollbackTransaction();
        asyncTasksService.awaitCompletion(Duration.ofSeconds(OPENSEARCH_QUERY_MANAGER_TIMEOUT));
        oss.prepareWaitForIndexing().get(OPENSEARCH_QUERY_MANAGER_TIMEOUT, TimeUnit.SECONDS);
        oss.refresh();
        TransactionHelper.startTransaction();
    }

}
