/*
 * (C) Copyright 2026 Hyland (https://hyland.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicy.CONTEXT_CONTENT;
import static org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicy.CONTEXT_USER;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.SessionImpl;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.security.expr.ExprEvaluator;
import org.nuxeo.ecm.core.security.expr.ExpressionVisitor;
import org.nuxeo.ecm.core.security.expr.ast.Expr;
import org.nuxeo.ecm.core.storage.dbs.DBSDocument;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public class TestExprEvaluatorOnInternalDocument {

    @Inject
    protected CoreSession session;

    private Object evaluateValue(String string) {
        InternalSession internalSession = ((SessionImpl) session).getSession();
        InternalDocument root = internalSession.getRootDocument();
        DBSDocument idoc = DBSDocument.newUnCreatedDocument(root, "doc", SYS_FILE, internalSession);
        // system schema
        idoc.setPropertyValue(SYS_TITLE, "my title");
        // sysfile schema
        Blob blob = Blobs.createBlob("blob content");
        blob.setFilename("file.txt");
        idoc.setValue(SYSFILE_BLOB, blob);
        // mydublincore inside test's SysFile
        idoc.setValue("mydc_source", "internal");

        NuxeoPrincipal principal = new UserPrincipal("", null, false);
        Map<String, Object> context = Map.of( //
                CONTEXT_CONTENT, idoc, //
                CONTEXT_USER, principal);
        Expr expr = new ExpressionVisitor().parse(string);
        return new ExprEvaluator(context).evaluateValue(expr);
    }

    private void check(String expression, Object expectedValue) {
        assertEquals(expectedValue, evaluateValue(expression));
    }

    @Test
    public void testSysSchema() {
        check("content.sys_name == \"doc\"", true);
        check("content.sys_title == \"my title\"", true);
    }

    @Test
    public void testRegularSchema() {
        check("content.mydc_source == \"internal\"", true);
    }

    @Test
    public void testBlob() {
        check("content.sysfile_blob.key == \"7b24cf3d897fd680e0258c1c7c23db50a5428581ed1785c08de505c381b4c4b5\"", true);
        check("content.sysfile_blob.digest == \"7b24cf3d897fd680e0258c1c7c23db50a5428581ed1785c08de505c381b4c4b5\"",
                true);
        check("content.sysfile_blob.encoding == \"UTF-8\"", true);
        check("content.sysfile_blob.filename == \"file.txt\"", true);
        check("content.sysfile_blob.length == 12", true);
        check("content.sysfile_blob.mimeType == \"text/plain\"", true);
    }

}
