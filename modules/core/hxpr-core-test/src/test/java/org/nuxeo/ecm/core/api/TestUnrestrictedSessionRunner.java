/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

/**
 * Test unrestricted session runner.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestUnrestrictedSessionRunner {

    private static final String USERNAME = "bob";

    private static final String DOC_NAME = "doc";

    private static final String NEW_TITLE = "new title";

    @Inject
    protected CoreFeature coreFeature;

    @Test
    public void testUnrestrictedPropertySetter() throws Exception {
        CoreSession session = coreFeature.getCoreSession(USERNAME);
        seeDocCreatedByUnrestricted(session);
    }

    @Test
    public void testUnrestrictedSessionSeesDocCreatedBefore() throws Exception {
        CoreSession session = coreFeature.getCoreSession();
        unrestrictedSeesDocCreatedBefore(session);
    }

    /*
     * ----- Actual tests. Also run in JCA mode. -----
     */

    /**
     * An unrestricted session creates a doc then the calling session tries to access it.
     */
    public static void seeDocCreatedByUnrestricted(CoreSession session) throws Exception {
        UnrestrictedPropertySetter setter = new UnrestrictedPropertySetter(session);
        setter.runUnrestricted();
        DocumentModel doc = session.getDocument(setter.docRef);
        assertEquals(NEW_TITLE, doc.getPropertyValue(SYS_TITLE));
    }

    /**
     * A session creates a doc, then calls an unrestricted session that modifies it.
     */
    public static void unrestrictedSeesDocCreatedBefore(CoreSession session) throws Exception {
        DocumentModel doc = session.createDocumentModel("/", DOC_NAME, SYS_FILE);
        doc.create();
        UnrestrictedDocumentReader reader = new UnrestrictedDocumentReader(session, doc.getRef());
        reader.runUnrestricted();
        assertEquals(DOC_NAME, reader.getName());
    }

    protected static class UnrestrictedPropertySetter extends UnrestrictedSessionRunner {

        public DocumentRef docRef;

        public UnrestrictedPropertySetter(CoreSession session) {
            super(session);
        }

        @Override
        public void run() {
            DocumentModel root = session.getRootDocument();
            ACL acl = root.getLocalACL();
            acl.add(ACE.builder().user(USERNAME).permission(SecurityConstants.READ_WRITE).build());
            session.setACL(root.getRef(), acl, true);
            DocumentModel doc = session.createDocumentModel("/", DOC_NAME, SYS_FILE);
            doc.setPropertyValue(SYS_TITLE, NEW_TITLE);
            doc.create();
            docRef = doc.getRef();
        }
    }

    protected static class UnrestrictedDocumentReader extends UnrestrictedSessionRunner {
        private DocumentRef ref;

        private String name;

        public UnrestrictedDocumentReader(CoreSession session, DocumentRef ref) {
            super(session);
            this.ref = ref;
        }

        @Override
        public void run() {
            DocumentModel doc = session.getDocument(ref);
            name = doc.getName();
        }

        public String getName() {
            return name;
        }
    }

}
