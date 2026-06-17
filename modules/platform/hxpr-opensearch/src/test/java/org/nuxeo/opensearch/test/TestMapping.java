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
 *     Delbosc Benoit
 */

package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.query.NxQueryBuilder;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
//@Deploy("org.nuxeo.opensearch.core:opensearch-test-contrib.xml")
@Deploy("org.nuxeo.opensearch.core:opensearch-test-mapping-contrib.xml")
public class TestMapping {

    @Inject
    protected CoreSession session;

    @Inject
    protected OpenSearchService oss;

    private int commandProcessed;

    private void nextTransactionWithWait() throws Exception {
        nextTransactionWithWaitAssertNumberOfCommandProcessed(-1);
    }

    private void nextTransactionWithWaitAssertNumberOfCommandProcessed(int n) throws Exception {
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        waitForIndexing();
        if (n >= 0) {
            assertNumberOfCommandProcessed(n);
        }
        startTransaction();
    }

    public void assertNumberOfCommandProcessed(int processed) {
        assertEquals(processed, oss.getTotalCommandProcessed() - commandProcessed); // NOSONAR
    }

    /**
     * Wait for sync and async job and refresh the index
     */
    public void waitForIndexing() throws Exception {
        oss.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        oss.refresh();
    }

    public void startTransaction() {
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
        commandProcessed = oss.getTotalCommandProcessed();
    }

    @BeforeEach
    public void setUpMapping() {
        oss.initIndexes(true);
    }

    @Test
    public void testIlikeSearch() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "testDoc1", SYS_FILE); // NOSONAR
        doc.setPropertyValue(SYS_TITLE, "upper case"); // NOSONAR
        doc.setPropertyValue(SYS_DESCRIPTION, "UPPER CASE DESC"); // NOSONAR
        doc.create();

        doc = session.createDocumentModel("/", "testDoc2", SYS_FILE); // NOSONAR
        doc.setPropertyValue(SYS_TITLE, "Mixed Case");
        doc.setPropertyValue(SYS_DESCRIPTION, "MiXeD cAsE dEsC");
        doc.create();

        doc = session.createDocumentModel("/", "testDoc3", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "lower case");
        doc.setPropertyValue(SYS_DESCRIPTION, "lower case desc");
        doc.create();

        nextTransactionWithWaitAssertNumberOfCommandProcessed(3);

        DocumentModelList ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_description ILIKE '%Case%'"));
        assertEquals(3, ret.totalSize());

        ret = oss.query(new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_description ILIKE 'Upper%'"));
        assertEquals(1, ret.totalSize());

        ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_description ILIKE 'mixED case desc'"));
        assertEquals(1, ret.totalSize());

        ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title LIKE 'case%'"));
        assertEquals(3, ret.totalSize());
        ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title LIKE 'Case%'"));
        assertEquals(3, ret.totalSize());

        // case sensitive for other operation
        ret = oss.query(new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_description LIKE '%Case%'"));
        assertEquals(0, ret.totalSize());
        try {
            oss.query(new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_description LIKE 'Upper%'"));
            fail("phrase prefix on keyword should raise error on Open 7.x");
        } catch (NuxeoException e) {
            assertEquals(
                    "Error performing OpenSearch query: failed to create query: Can only use phrase prefix queries on text fields - not on [sys_description] which is of type [keyword]",
                    e.getMessage());
        }
        ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_fulltext.sys_description LIKE '%Case%'"));
        assertEquals(3, ret.totalSize());
        ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_fulltext.sys_description LIKE 'Upper%'"));
        assertEquals(1, ret.totalSize());
        ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_fulltext.sys_description LIKE 'UPPER%'"));
        assertEquals(1, ret.totalSize());

        assertEquals(1, ret.totalSize());

    }

    @Test
    public void testFulltextAnalyzer() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "testDoc1", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "new-york.jpg");
        doc.create();

        doc = session.createDocumentModel("/", "testDoc2", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "York.jpg");
        doc.create();

        doc = session.createDocumentModel("/", "testDoc3", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foo_jpg");
        doc.create();

        nextTransactionWithWait();

        DocumentModelList ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'new-york.jpg'"));
        assertEquals(1, ret.totalSize());

        // The standard tokenizer first split new-york.jpg in "new" "york.jpg"
        // then the word delimiter gives: "new" york" "jpg" "york.jpg"
        ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'new york jpg'"));
        assertEquals(1, ret.totalSize());

        ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'new-york'"));
        assertEquals(1, ret.totalSize());

        ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'york new'"));
        assertEquals(1, ret.totalSize());

        ret = oss.query(new NxQueryBuilder(session).nxql(
                "SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'york -new-york'"));
        assertEquals(1, ret.totalSize());
        assertEquals("testDoc2", ret.get(0).getName());

        ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'NewYork'"));
        assertEquals(1, ret.totalSize());

        ret = oss.query(new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_fulltext.sys_title = 'jpg'"));
        assertEquals(3, ret.totalSize());

    }

    @Test
    public void testNgramSearch() throws Exception {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "testDoc1", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "FooBar12 test");
        doc.create();

        doc = session.createDocumentModel("/", "testDoc2", SYS_FILE);
        doc.setPropertyValue(SYS_TITLE, "foobar42");
        doc.create();

        nextTransactionWithWait();

        // Common left/right truncature with a ILIKE, translated into wilcard search *oba* with poor performance
        DocumentModelList ret = oss.query(
                new NxQueryBuilder(session).nxql("SELECT * FROM SysContent WHERE sys_title ILIKE '%Oba%'"));
        assertEquals(2, ret.totalSize());
    }
}
