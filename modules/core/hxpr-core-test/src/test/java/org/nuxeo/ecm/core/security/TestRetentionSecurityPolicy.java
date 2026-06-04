/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 *     Arindam Ghosh
 */
package org.nuxeo.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.DELETE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.security.config.RetentionSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-retention-security-policy.xml")
public class TestRetentionSecurityPolicy {

    private DocumentRef fileRefRegular;

    private DocumentRef fileWithRetention;

    private DocumentRef fileMakredAsRecord;

    private UserPrincipal operatorUser;

    private static final String TYPE_NAME = "GovernableFile";

    private static SecurityService securityService;

    private static final String REPOSITORY = "test";

    private static final String POLICY_NAME = "retention";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SecurityPolicyService securityPolicyService;

    private static RepositoryService repositoryService;


    @Inject
    protected CoreSession session;

    @BeforeAll
    public static void beforeClass() {
        // get the RetentionSecurityPolicyEngine
        securityService = Framework.getService(SecurityService.class);
        // set a custom RetentionSecurityPolicy as the only security policy
        setSinglePolicyWithPermissions();

        securityPolicyService = securityService.getSecurityPolicyService(REPOSITORY);
        repositoryService = Framework.getService(RepositoryService.class);
    }

    @BeforeEach
    public void before() {
        // create test documents
        fileRefRegular = createRegularFile("Regular File").getRef();
        fileWithRetention = createGovernableFile("File With Retention", session).getRef();
        session.setRecord(fileWithRetention, true);
        Instant twoSeconds = Instant.now().plusSeconds(2);
        session.setRetainUntil(fileWithRetention, twoSeconds, null);
        fileMakredAsRecord = createGovernableFile("Recorded File", session).getRef();
        session.setRecord(fileMakredAsRecord, true);
        // create test users
        operatorUser = DummyLoginFeature.getTestPrincipal("User", false);
    }

    private static void setSinglePolicyWithPermissions() {
        SecurityPolicyConfig config = new RetentionSecurityPolicyConfig();
        ((SecurityServiceImpl) securityService).setLocalConfiguration(REPOSITORY, Map.of(POLICY_NAME, asMap(config)));
    }

    private static Map<String, Object> asMap(Object config) {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(config), Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DocumentModel createRegularFile(String name) {
        DocumentModel file = session.createDocumentModel("/", name, SYS_FILE);
        file.create();
        session.save();
        return file;
    }

    private DocumentModel createGovernableFile(String name, CoreSession session) {
        DocumentModel file = session.createDocumentModel("/", name, TYPE_NAME);
        file.create();
        session.save(); // also implicitly sets sys_creator & co
        return file;
    }

    @Test
    public void testReadAdmin() {
        // all documents are accessible
        assertNotNull(session.getDocument(fileRefRegular));
        assertNotNull(session.getDocument(fileWithRetention));
        assertNotNull(session.getDocument(fileMakredAsRecord));
    }

    @Test
    public void testDeleteDocumentWithoutRetention() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), operatorUser);
        assertDoesNotThrow(() -> userSession.removeDocument(fileRefRegular));
    }

    @Test
    public void testCannotDeleteRetainedDocument() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), operatorUser);
        assertThrows(DocumentSecurityException.class, () -> userSession.removeDocument(fileWithRetention));
    }

    @Test
    public void testDeleteRetentionExpiredDocument() throws InterruptedException {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), operatorUser);
        Thread.sleep(3000);
        assertDoesNotThrow(() -> userSession.removeDocument(fileWithRetention));
    }

    @Test
    public void testDeleteDocumentMarkedAsRecord() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), operatorUser);
        assertDoesNotThrow(() -> userSession.removeDocument(fileMakredAsRecord));
    }

    @Test
    public void shouldAllowReadForDocumentUnderRetention() {
        InternalDocument doc = mock(InternalDocument.class);
        boolean hasPermission = securityPolicyService.hasPermission(doc, operatorUser, READ);
        assertTrue(hasPermission);
    }

    @Test
    public void shouldNotAllowDeleteForDocumentUnderRetention() {
        InternalDocument doc = getInternalDocument(fileWithRetention);
        boolean hasPermission = securityPolicyService.hasPermission(doc, operatorUser, DELETE);
        assertFalse(hasPermission);
    }

    @Test
    public void shouldNotAllowUpdateForDocumentUnderRetention() {
        InternalDocument doc = getInternalDocument(fileWithRetention);
        boolean hasPermission = securityPolicyService.hasPermission(doc, operatorUser, WRITE);
        assertFalse(hasPermission);
    }

    @Test
    public void shouldAllowDeleteForDocumentNotUnderRetention() {
        InternalDocument doc = getInternalDocument(fileRefRegular);
        boolean hasPermission = securityPolicyService.hasPermission(doc, operatorUser, DELETE);
        assertTrue(hasPermission);
    }

    @Test
    public void shouldAllowUpdateForDocumentNotUnderRetention() {
        InternalDocument doc = getInternalDocument(fileRefRegular);
        boolean hasPermission = securityPolicyService.hasPermission(doc, operatorUser, WRITE);
        assertTrue(hasPermission);
    }

    private InternalDocument getInternalDocument(DocumentRef ref) {
        String value = (String) ref.reference();
        return repositoryService.getSession(REPOSITORY).getDocumentByUUID(value);
    }

}
