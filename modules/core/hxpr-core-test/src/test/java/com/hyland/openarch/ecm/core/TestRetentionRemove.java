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

package com.hyland.openarch.ecm.core;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.RECORDS_CLEANER_GROUP;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;
import static com.hyland.openarch.ecm.core.model.InternalSession.PROP_RETENTION_COMPLIANCE_MODE_ENABLED;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import java.time.Instant;
import java.util.Collections;

import jakarta.inject.Inject;
import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.WithFrameworkProperty;

/**
 * @since 11.5
 */
@ExtendWith(FeaturesExtension.class)
@Features({ CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-retention-remove-types-contrib.xml")
public class TestRetentionRemove {

    public static final String JOHN = "John";

    private static final String GOVERNABLE_FILE = "GovernableFile";

    @Inject
    protected CoreSession session;

    protected DocumentModel folder;

    protected DocumentModel documentModelUnderLegalHold;

    protected DocumentModel documentModelUnderRetention;

    protected void addPermission(DocumentModel documentModel, String username, String... permissions) {
        ACL acl = documentModel.getLocalACL();
        for (String permission : permissions) {
            acl.add(ACE.builder().user(username).permission(permission).build());
        }
        documentModel.setACL(acl, false);
        session.save();
    }

    protected void checkRemoveBlob(DocumentModel doc, CoreSession session, boolean expectedToFail) {
        try {
            doc.setPropertyValue(SYSFILE_BLOB, null);
            if (expectedToFail) {
                fail();
            }
        } catch (DocumentSecurityException e) {
            if (expectedToFail) {
                assertEquals(genericErrorMessage(WRITE, session.getPrincipal().getId(), doc.getRef()), e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private String genericErrorMessage(String permission, String username, DocumentRef document) {
        return String.format(GENERIC_DOCUMENT_SECURITY_ERROR, permission, username, document);
    }

    protected void checkRemoveDocument(DocumentModel doc, CoreSession session, boolean expectedToFail, String permission) {
        try {
            session.removeDocument(doc.getRef());
            if (expectedToFail) {
                fail();
            }
        } catch (DocumentSecurityException e) {
            if (expectedToFail) {
                // subdocument?
                String pattern = String.format("Cannot remove %s, subdocument \\S+ is under retention / hold",
                        folder.getRef());
                if (!e.getMessage().matches(pattern)) {
                    // if not, check regular removal exception
                    assertEquals(genericErrorMessage(permission, session.getPrincipal().getId(), doc.getRef()), e.getMessage());
                }
            } else {
                throw e;
            }
        }
    }

    protected DocumentModel createFileDocument(CoreSession session, DocumentModel parent) {
        DocumentModel documentModel = session.createDocumentModel(parent.getPathAsString(), "myDocument", GOVERNABLE_FILE);
        Blob blob = Blobs.createBlob("Any Content");
        documentModel.setPropertyValue(SYSFILE_BLOB, blob);
        documentModel.create();
        return documentModel;
    }

    @Test
    @WithFrameworkProperty(name = PROP_RETENTION_COMPLIANCE_MODE_ENABLED, value = "true")
    public void iCannotRemoveFolderWithRetainedChildrenInCompliance() throws LoginException {
        try (var __ = Framework.loginUser(JOHN)) {
            CoreSession notAdminSession = CoreInstance.getCoreSession(session.getRepositoryName());
            folder = notAdminSession.getDocument(folder.getRef());
            checkRemoveDocument(folder, notAdminSession, true, DELETE_CHILD);

            // Add current user to records cleaner group.
            RepositoryPrincipal.getCurrent().setGroups(Collections.singletonList(RECORDS_CLEANER_GROUP));

            folder = notAdminSession.getDocument(folder.getRef());
            checkRemoveDocument(folder, notAdminSession, true, DELETE_CHILD);
        }
    }

    @Test
    @WithFrameworkProperty(name = PROP_RETENTION_COMPLIANCE_MODE_ENABLED, value = "true")
    public void iCannotRemoveRetainedDocInCompliance() throws LoginException {
        try (var __ = Framework.loginUser(JOHN)) {
            CoreSession notAdminSession = CoreInstance.getCoreSession(session.getRepositoryName());
            documentModelUnderLegalHold = notAdminSession.getDocument(documentModelUnderLegalHold.getRef());
            documentModelUnderRetention = notAdminSession.getDocument(documentModelUnderRetention.getRef());
            checkRemoveBlob(documentModelUnderLegalHold, notAdminSession, true);
            checkRemoveBlob(documentModelUnderRetention, notAdminSession, true);
            checkRemoveDocument(documentModelUnderLegalHold, notAdminSession, true, DELETE);
            checkRemoveDocument(documentModelUnderRetention, notAdminSession, true, DELETE);

            // Add current use to record cleaner group.
            RepositoryPrincipal.getCurrent().setGroups(Collections.singletonList(RECORDS_CLEANER_GROUP));

            documentModelUnderLegalHold = notAdminSession.getDocument(documentModelUnderLegalHold.getRef());
            documentModelUnderRetention = notAdminSession.getDocument(documentModelUnderRetention.getRef());
            checkRemoveBlob(documentModelUnderLegalHold, notAdminSession, true);
            checkRemoveBlob(documentModelUnderRetention, notAdminSession, true);
            checkRemoveDocument(documentModelUnderLegalHold, notAdminSession, true, DELETE);
            checkRemoveDocument(documentModelUnderRetention, notAdminSession, true, DELETE);
        }
    }

    @Test
    public void iCanRemoveFolderWithRetainedChildrenInGovernance() throws LoginException {
        try (var __ = Framework.loginUser(JOHN)) {
            CoreSession notAdminSession = CoreInstance.getCoreSession(session.getRepositoryName());
            folder = notAdminSession.getDocument(folder.getRef());
            checkRemoveDocument(folder, notAdminSession, true, DELETE_CHILD);

            // Add current user to records cleaner group.
            RepositoryPrincipal.getCurrent().setGroups(Collections.singletonList(RECORDS_CLEANER_GROUP));

            folder = notAdminSession.getDocument(folder.getRef());
            checkRemoveDocument(folder, notAdminSession, false, DELETE_CHILD);
        }
    }

    @Test
    public void iCanRemoveRetainedDocInGovernance() throws LoginException {
        try (var __ = Framework.loginUser(JOHN)) {
            CoreSession notAdminSession = CoreInstance.getCoreSession(session.getRepositoryName());
            documentModelUnderLegalHold = notAdminSession.getDocument(documentModelUnderLegalHold.getRef());
            documentModelUnderRetention = notAdminSession.getDocument(documentModelUnderRetention.getRef());
            checkRemoveBlob(documentModelUnderLegalHold, notAdminSession, true);
            checkRemoveBlob(documentModelUnderRetention, notAdminSession, true);
            checkRemoveDocument(documentModelUnderLegalHold, notAdminSession, true, DELETE);
            checkRemoveDocument(documentModelUnderRetention, notAdminSession, true, DELETE);

            // Add current user to records cleaner group.
            RepositoryPrincipal.getCurrent().setGroups(Collections.singletonList(RECORDS_CLEANER_GROUP));

            documentModelUnderLegalHold = notAdminSession.getDocument(documentModelUnderLegalHold.getRef());
            documentModelUnderRetention = notAdminSession.getDocument(documentModelUnderRetention.getRef());
            checkRemoveBlob(documentModelUnderLegalHold, notAdminSession, false);
            checkRemoveBlob(documentModelUnderRetention, notAdminSession, false);
            checkRemoveDocument(documentModelUnderLegalHold, notAdminSession, false, DELETE);
            checkRemoveDocument(documentModelUnderRetention, notAdminSession, false, DELETE);
        }
    }

    @Test
    @WithFrameworkProperty(name = PROP_RETENTION_COMPLIANCE_MODE_ENABLED, value = "true")
    public void iDoNotHaveRemovePermissionInCompliance() throws LoginException {
        assertFalse(session.hasPermission(documentModelUnderLegalHold.getRef(), DELETE));
        assertFalse(session.hasPermission(documentModelUnderRetention.getRef(), DELETE));
        try (var __ = Framework.loginUser(JOHN)) {
            CoreSession notAdminSession = CoreInstance.getCoreSession(session.getRepositoryName());
            assertFalse(notAdminSession.hasPermission(documentModelUnderLegalHold.getRef(), DELETE));
            assertFalse(notAdminSession.hasPermission(documentModelUnderRetention.getRef(), DELETE));
            RepositoryPrincipal.getCurrent().setGroups(Collections.singletonList(RECORDS_CLEANER_GROUP));
            assertFalse(notAdminSession.hasPermission(documentModelUnderLegalHold.getRef(), DELETE));
            assertFalse(notAdminSession.hasPermission(documentModelUnderRetention.getRef(), DELETE));
        }
    }

    @Test
    public void iHaveRemovePermissionInGovernance() throws LoginException {
        assertFalse(session.hasPermission(documentModelUnderLegalHold.getRef(), DELETE));
        assertFalse(session.hasPermission(documentModelUnderRetention.getRef(), DELETE));
        try (var __ = Framework.loginUser(JOHN)) {
            CoreSession notAdminSession = CoreInstance.getCoreSession(session.getRepositoryName());
            assertFalse(notAdminSession.hasPermission(documentModelUnderLegalHold.getRef(), DELETE));
            assertFalse(notAdminSession.hasPermission(documentModelUnderRetention.getRef(), DELETE));
            RepositoryPrincipal.getCurrent().setGroups(Collections.singletonList(RECORDS_CLEANER_GROUP));
            assertTrue(notAdminSession.hasPermission(documentModelUnderLegalHold.getRef(), DELETE));
            assertTrue(notAdminSession.hasPermission(documentModelUnderRetention.getRef(), DELETE));
        }
    }

    @BeforeEach
    public void setUp() {
        addPermission(session.getRootDocument(), JOHN, DELETE_CHILD);
        folder = session.createDocumentModel("/", "myFolder", SYS_FOLDER);
        folder.create();
        // Make a document under legal hold and another one under retention
        documentModelUnderLegalHold = createFileDocument(session, folder);
        documentModelUnderRetention = createFileDocument(session, folder);
        session.setRecord(documentModelUnderLegalHold.getRef(), true);
        session.setRecord(documentModelUnderRetention.getRef(), true);
        session.setLegalHold(documentModelUnderLegalHold.getRef(), true);
        Instant retainUntil = Instant.now().plus(10, DAYS);
        session.setRetainUntil(documentModelUnderRetention.getRef(), retainUntil);

        // Give the permission to another user
        addPermission(folder, JOHN, READ, WRITE, DELETE);
        addPermission(documentModelUnderLegalHold, JOHN, READ, WRITE, DELETE);
        addPermission(documentModelUnderRetention, JOHN, READ, WRITE, DELETE);
    }

}
