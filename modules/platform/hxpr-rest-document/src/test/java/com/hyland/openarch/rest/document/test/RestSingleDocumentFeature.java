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
package com.hyland.openarch.rest.document.test;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.ecm.platform.test.UserManagerMockFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockTestConfiguration;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.springframework.context.annotation.Import;

import com.google.inject.Binder;

@Features({CoreFeature.class, UserManagerMockFeature.class})
@RepositoryConfig(cleanup = Granularity.CLASS)
@Import(UserManagerMockTestConfiguration.class)
public class RestSingleDocumentFeature implements RunnerFeature {

    FeaturesLoader loader;

    CoreFeature core;

    DocumentModel doc;

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
        TransactionHelper.startTransaction();
        doc = createDocument(core.getCoreSession());
        TransactionHelper.commitOrRollbackTransaction();
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(DocumentModel.class).toProvider(() -> doc);
    }

    @Override
    public void afterRun() {
        if (doc != null) {
            TransactionHelper.startTransaction();
            cleanupDocument(core.getCoreSession(), doc.getId());
            doc = null;
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    protected static Object getBlob(String name, String content) {
        Blob blob = Blobs.createBlob(content);
        blob.setFilename(name);
        return blob;
    }

    protected DocumentModel createDocument(CoreSession session) {
        DocumentModel doc = session.createDocumentModel("/", "doc", "TestDocument");
        // title
        doc.setPropertyValue(SYS_TITLE, "title");
        // misc
        doc.setPropertyValue("tp_decimal", new BigDecimal("1234567890.12345678901234567890"));
        // misc lists
        doc.setPropertyValue("tp_stringArray", new String[] { "foo", "bar" });
        Instant cal = Instant.parse("2013-02-28T13:24:56Z");
        doc.setPropertyValue("tp_dateArray", new Object[] { cal });
        doc.setPropertyValue("tp_intArray", new Long[] { 1L, 2L, 3L });
        // misc maps
        Map<String, Object> mapItem = Map.of("stringItem", "test1", "dateItem", cal, "intItem", 3L);
        doc.setPropertyValue("tp_complex", mapItem);
        Map<String, Object> complexChain = Map.of("chainStringItem", "test chain", "complexItem", mapItem);
        doc.setPropertyValue("tp_complexChain", complexChain);
        Map<String, Object> lmapItem = Map.of("stringItem", "test2", "dateItem", cal, "intItem", 5L);
        doc.setPropertyValue("tp_complexList", List.of(mapItem, lmapItem));
        // lists of lists
        doc.setPropertyValue("tp_listOfLists",
                List.of(Map.of("stringItem", "foo", "stringListItem", List.of("a", "b"))));
        // blobs
        doc.setPropertyValue(SYSFILE_BLOB, getBlob("hello", "hello world!"));
        doc.setPropertyValue("tp_fileList", List.of(getBlob("foo", "hello multiple world1!"),
                getBlob("bar", "hello multiple world2!")));
        doc.setPropertyValue("tp_fileComplexList", List.of(
                Map.of("filenameItem", "bar", "blobItem", getBlob("complex", "hello complex world!"))));

        doc.create();
        session.save();
        return doc;
    }

    protected void cleanupDocument(CoreSession session, String docId) {
        if (docId != null) {
            session.removeDocument(new IdRef(docId));
        }
    }

}
