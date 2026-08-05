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
package org.nuxeo.rest.query.test;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.transaction.TransactionHelper;

@Features(RepositoryOpenSearchFeature.class)
public class WildcardQueryFeature extends RestQuerySingleDocumentFeature {

    @Override
    protected DocumentModel createDocument(CoreSession session) {
        startTransaction();
        DocumentModel doc = session.createDocumentModel("/", "doc", "TestDocument");
        // title
        doc.setPropertyValue(SYS_TITLE, "assignments-js-doc.pdf");
        doc.create();
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        waitForCompletion();
        return doc;
    }
}
