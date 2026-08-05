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
package org.nuxeo.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.SessionImpl;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.ExternalUserRelationsSecurityPolicyConfig;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-external-user-relations-security-policy.xml")
class TestExternalUserRelationsSecurityPolicy {

    private static final String REPOSITORY = "test";

    private static final String POLICY_NAME = "wdsecurity";

    private static final String POLICY_MIXIN = "WDEmployee";

    private static final String POLICY_EMPLOYEE_PROPERTY = "wdemployee_empId";

    private static final String RELATION_ADMIN = "HRAdmin";

    private static final String RELATION_SUPERVISOR = "SupervisoryReports";

    private static final List<String> POLICY_RELATIONS = List.of(RELATION_ADMIN, RELATION_SUPERVISOR);

    private static final String POLICY_USER_ATTRIBUTE = "wdId";

    private static final String QUERY = "SELECT * FROM SysContent";

    private static final String TYPE_NAME = "Resume";

    private static final Set<String> POLICY_CONTRACT_TYPES = Set.of(TYPE_NAME);

    private static final Set<String> POLICY_PERMISSIONS = Set.of(READ, WRITE);

    private static final String POLICY_CONFIG_JSON_WITH_PERMISSIONS = """
            {
              "engine": "externaluserrelations",
              "contentTypes": ["Resume"],
              "permissions": %s,
              "mixin": "WDEmployee",
              "relations": ["HRAdmin", "SupervisoryReports"],
              "userAttribute": "wdId"
            }
            """;

    private static final String EXTERNAL_USER_RELATIONS_JSON = """
            {
              "alice": {
                "HRAdmin": ["bob"],
                "SupervisoryReports": ["carol"]
              },
              "dan": {
                "HRAdmin": ["bob"],
                "SupervisoryReports": ["bob"]
              }
            }
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SecurityService securityService;

    private DocumentRef fileRefRegular;

    private DocumentRef fileRefAlice;

    private DocumentRef fileRefBob;

    private DocumentRef fileRefCarol;

    private DocumentRef fileRefDan;

    private UserPrincipal regularUser;

    private UserPrincipal userBob;

    @Inject
    protected CoreSession session;

    @BeforeAll
    static void beforeClass() {
        securityService = Framework.getService(SecurityService.class);
        // set a custom ExternalUserRelationsSecurityPolicy as the only security policy
        ExternalUserRelationsSecurityPolicy policy = setSinglePolicyWithPermissions(Set.of(READ, MANAGE_SECURITY));

        // set external user relations
        Map<String, Map<String, List<String>>> config = MAPPER.readValue(EXTERNAL_USER_RELATIONS_JSON, Map.class);
        policy.setExternalUserRelations(config);
    }

    private static ExternalUserRelationsSecurityPolicy setSinglePolicyWithPermissions(Set<String> permissions) {
        String permissionsJson = permissions == null ? null : MAPPER.writeValueAsString(permissions);
        String configJson = POLICY_CONFIG_JSON_WITH_PERMISSIONS.formatted(permissionsJson);
        Map<String, Object> configMap = MAPPER.readValue(configJson, Map.class);
        ((SecurityServiceImpl) securityService).setLocalConfiguration(REPOSITORY, Map.of(POLICY_NAME, configMap));
        // fetch the created policy
        List<SecurityPolicy> policies = securityService.getSecurityPolicyService(REPOSITORY).getSecurityPolicies();
        assertEquals(1, policies.size());
        return (ExternalUserRelationsSecurityPolicy) policies.iterator().next();
    }

    @BeforeEach
    void before() {
        // create test documents
        fileRefRegular = createRegularFile("Regular File").getRef();
        fileRefAlice = createFileWithEmployee("alice", session).getRef();
        fileRefBob = createFileWithEmployee("bob", session).getRef();
        fileRefCarol = createFileWithEmployee("carol", session).getRef();
        fileRefDan = createFileWithEmployee("dan", session).getRef();
        // create test users
        regularUser = DummyLoginFeature.getTestPrincipal("hxuserregular", false);
        userBob = DummyLoginFeature.getTestPrincipal("hxbob", false);
        userBob.setProperties(Map.of(POLICY_USER_ATTRIBUTE, "bob"));
    }

    private static void assertConstructorViolation(String expected, Set<String> contentTypes, Set<String> permissions,
            String mixin, List<String> relations, String userAttribute) {
        ExternalUserRelationsSecurityPolicyConfig config = new ExternalUserRelationsSecurityPolicyConfig(null,
                contentTypes, permissions, mixin, relations, userAttribute);
        List<ValidationViolation> violations = new ArrayList<>();
        config.newSecurityPolicy(REPOSITORY, POLICY_NAME, violations);
        assertEquals(1, violations.size());
        assertEquals(expected, violations.get(0).getMessage(null));
    }

    @Test
    void testConfigPersistence() {
        // re-create a policy from scratch with same name, it will re-read the persisted config
        ExternalUserRelationsSecurityPolicy policy = setSinglePolicyWithPermissions(Set.of(READ, MANAGE_SECURITY));
        // check config is as expected
        assertNotNull(policy.getExternalUserRelationsLastModified());
        Map<String, Object> publicConfig = policy.getExternalUserRelations();
        assertEquals(Set.of("alice", "dan"), publicConfig.keySet());
    }

    @Test
    void testReadAdmin() {
        // all documents are accessible
        assertNotNull(session.getDocument(fileRefRegular));
        assertNotNull(session.getDocument(fileRefAlice));
        assertNotNull(session.getDocument(fileRefBob));
        assertNotNull(session.getDocument(fileRefCarol));
        assertNotNull(session.getDocument(fileRefDan));
    }

    @Test
    void testReadBob() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userBob);
        // regular document is accessible
        assertNotNull(userSession.getDocument(fileRefRegular));
        // bob can read his docs and also those from alice and dan
        assertNotNull(userSession.getDocument(fileRefBob));
        assertNotNull(userSession.getDocument(fileRefAlice));
        assertNotNull(userSession.getDocument(fileRefDan));
        // but not carol's
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefCarol));
    }

    @Test
    void testReadRegularUser() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), regularUser);
        // regular document is accessible
        assertNotNull(userSession.getDocument(fileRefRegular));
        // no document under security policy is accessible
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefAlice));
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefBob));
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefCarol));
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefDan));
    }

    @Test
    void testSearchAdmin() {
        // all documents are accessible
        DocumentModelList docs = session.query(QUERY);
        List<DocumentRef> docRefs = docs.stream().map(DocumentModel::getRef).toList();
        assertEquals(5, docs.size());
        assertTrue(docRefs.contains(fileRefRegular));
        assertTrue(docRefs.contains(fileRefAlice));
        assertTrue(docRefs.contains(fileRefBob));
        assertTrue(docRefs.contains(fileRefCarol));
        assertTrue(docRefs.contains(fileRefDan));
    }

    @Test
    void testSearchUserBob() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userBob);
        DocumentModelList docs = userSession.query(QUERY);
        List<DocumentRef> docRefs = docs.stream().map(DocumentModel::getRef).toList();
        assertEquals(4, docs.size());
        // regular document is accessible
        assertTrue(docRefs.contains(fileRefRegular));
        // bob can read his docs and also those from alice and dan
        assertTrue(docRefs.contains(fileRefBob));
        assertTrue(docRefs.contains(fileRefAlice));
        assertTrue(docRefs.contains(fileRefDan));
        // but not carol's
        assertFalse(docRefs.contains(fileRefCarol));
    }

    @Test
    void testSearchUserRegular() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), regularUser);
        DocumentModelList docs = userSession.query(QUERY);
        List<DocumentRef> docRefs = docs.stream().map(DocumentModel::getRef).toList();
        assertEquals(1, docs.size());
        // regular document is accessible
        assertTrue(docRefs.contains(fileRefRegular));
        // no document under security policy is accessible
        assertFalse(docRefs.contains(fileRefAlice));
        assertFalse(docRefs.contains(fileRefBob));
        assertFalse(docRefs.contains(fileRefCarol));
        assertFalse(docRefs.contains(fileRefDan));
    }

    @Test
    void testAllowedUpdates() {
        DocumentModel documentModel = session.getDocument(fileRefBob);
        InternalSession internalSession = ((SessionImpl) session).getSession();
        InternalDocument internalDocument = internalSession.getDocumentByUUID(documentModel.getId());
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(REPOSITORY);
        // bob is allowed to update the business mixin
        securityPolicyService.checkAllowedMixinUpdate(internalDocument, userBob, POLICY_MIXIN);
        // userRegular is not allowed to update the business mixin
        assertThrows(DocumentSecurityException.class,
                () -> securityPolicyService.checkAllowedMixinUpdate(internalDocument, //
                        regularUser, POLICY_MIXIN));
        // bob is allowed to update the security property
        securityPolicyService.checkAllowedPropertyUpdate(internalDocument, userBob, POLICY_EMPLOYEE_PROPERTY);
        // userRegular is not allowed to update the security property
        assertThrows(DocumentSecurityException.class,
                () -> securityPolicyService.checkAllowedPropertyUpdate(internalDocument, //
                        regularUser, POLICY_EMPLOYEE_PROPERTY));
        // userRegular would be allowed to update other mixins (if they had permission)
        securityPolicyService.checkAllowedMixinUpdate(internalDocument, regularUser, "otherMixin");
        // userRegular would be allowed to update other properties (if they had permission)
        securityPolicyService.checkAllowedPropertyUpdate(internalDocument, regularUser, "otherProperty");
    }

    private DocumentModel createRegularFile(String name) {
        DocumentModel file = session.createDocumentModel("/", name, SYS_FILE);
        file.create();
        session.save();
        return file;
    }

    private DocumentModel createFileWithEmployee(String empId, CoreSession session) {
        DocumentModel file = session.createDocumentModel("/", "File for " + empId, TYPE_NAME);
        file.create();
        // security policy (and mixin) was automatically associated at creation time
        file.setPropertyValue(POLICY_EMPLOYEE_PROPERTY, empId);
        session.save();
        return file;
    }

    @Test
    void testEmptyRelations() {
        assertConstructorViolation("The relations must be set as a list on security policy \"wdsecurity\"",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, POLICY_MIXIN, null, POLICY_USER_ATTRIBUTE);
        assertConstructorViolation("The relations must be set as a list on security policy \"wdsecurity\"",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, POLICY_MIXIN, List.of(), POLICY_USER_ATTRIBUTE);
    }

    @Test
    void testBlankUserAttribute() {
        assertConstructorViolation("The userAttribute must be set on security policy \"wdsecurity\"",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, POLICY_MIXIN, POLICY_RELATIONS, " ");
    }

}
