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
package com.hyland.openarch.ecm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_WRITE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.SessionImpl;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.impl.UserPrincipal;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.ecm.core.query.sql.model.FromClause;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-security-level-security-policy.xml")
public class TestSecurityLevelSecurityPolicy {

    private static final String REPOSITORY = "test";

    private static final String POLICY_NAME = "ClearanceSecurityLevel";

    private static final String POLICY_MIXIN = "Clearance";

    private static final String POLICY_BUSINESS_PROPERTY = "clearance_level";

    private static final String POLICY_USER_ATTRIBUTE = "clearance";

    private static final String QUERY = "SELECT * FROM SysContent";

    private static final String TYPE_NAME = "Contract";

    private static final Set<String> POLICY_CONTRACT_TYPES = Set.of(TYPE_NAME);

    private static final Set<String> POLICY_PERMISSIONS = Set.of(READ, WRITE);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SecurityService securityService;

    private DocumentRef fileRefRegular;

    private DocumentRef fileRefLevel1;

    private DocumentRef fileRefLevel2;

    private DocumentRef fileRefLevel3;

    private UserPrincipal userLevel2;

    private UserPrincipal userWithoutClearance;

    @Inject
    protected CoreSession session;

    @BeforeAll
    public static void beforeClass() {
        securityService = Framework.getService(SecurityService.class);
        // set a custom SecurityLevelSecurityPolicy as the only security policy
        setSinglePolicyWithPermissions(Set.of(READ, MANAGE_SECURITY));
    }

    private static void setSinglePolicyWithPermissions(Set<String> permissions) {
        SecurityPolicyConfig config = new SecurityLevelSecurityPolicyConfig(null, Set.of(TYPE_NAME), permissions,
                POLICY_MIXIN, POLICY_USER_ATTRIBUTE);
        ((SecurityServiceImpl) securityService).setLocalFromConfigMap(REPOSITORY, Map.of(POLICY_NAME, asMap(config)));
    }

    private static Map<String, Object> asMap(Object config) {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(config), Map.class);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void before() {
        // create test documents
        fileRefRegular = createRegularFile("Regular File").getRef();
        fileRefLevel1 = createFileWithClearanceLevel("File Level 1", 1L, session).getRef();
        fileRefLevel2 = createFileWithClearanceLevel("File Level 2", 2L, session).getRef();
        fileRefLevel3 = createFileWithClearanceLevel("File Level 3", 3L, session).getRef();
        // create test users
        userLevel2 = DummyLoginFeature.getTestPrincipal("userLevel2", false);
        userLevel2.setProperties(Collections.singletonMap(POLICY_USER_ATTRIBUTE, 2));
        userWithoutClearance = DummyLoginFeature.getTestPrincipal("userWithoutClearance", false);
    }

    private static void assertConstructorViolation(String expected, Set<String> contentTypes, Set<String> permissions,
            String mixin, String userAttribute) {
        SecurityLevelSecurityPolicyConfig config = new SecurityLevelSecurityPolicyConfig(null, contentTypes,
                permissions, mixin, userAttribute);
        List<ValidationViolation> violations = new ArrayList<>();
        config.newSecurityPolicy(REPOSITORY, POLICY_NAME, violations);
        assertEquals(1, violations.size());
        assertEquals(expected, violations.get(0).getMessage(null));
    }

    @Test
    public void testReadAdmin() {
        // all documents are accessible
        assertNotNull(session.getDocument(fileRefRegular));
        assertNotNull(session.getDocument(fileRefLevel1));
        assertNotNull(session.getDocument(fileRefLevel2));
        assertNotNull(session.getDocument(fileRefLevel3));
    }

    @Test
    public void testReadUserWithClearance() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userLevel2);
        // regular document is accessible
        assertNotNull(userSession.getDocument(fileRefRegular));
        // documents up to level 2 are accessible, 3 and above are not
        assertNotNull(userSession.getDocument(fileRefLevel1));
        assertNotNull(userSession.getDocument(fileRefLevel2));
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefLevel3));
    }

    @Test
    public void testUserReadWithoutClearance() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userWithoutClearance);
        // regular document is accessible
        assertNotNull(userSession.getDocument(fileRefRegular));
        // no document under security policy is accessible
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefLevel1));
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefLevel2));
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefLevel3));
    }

    @Test
    public void testUserReadAsCreator() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userLevel2);
        DocumentModel doc = createFileWithClearanceLevel("File Level 4", 4L, userSession);
        // document has a higher clearance level but is still accessible by the creator
        assertNotNull(userSession.getDocument(doc.getRef()));
    }

    @Test
    public void testSearchAdmin() {
        // all documents are accessible
        DocumentModelList docs = session.query(QUERY);
        List<DocumentRef> docRefs = docs.stream().map(DocumentModel::getRef).toList();
        assertEquals(4, docs.size());
        assertTrue(docRefs.contains(fileRefRegular));
        assertTrue(docRefs.contains(fileRefLevel1));
        assertTrue(docRefs.contains(fileRefLevel2));
        assertTrue(docRefs.contains(fileRefLevel3));
    }

    @Test
    public void testSearchUserWithClearance() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userLevel2);
        DocumentModelList docs = userSession.query(QUERY);
        List<DocumentRef> docRefs = docs.stream().map(DocumentModel::getRef).toList();
        assertEquals(3, docs.size());
        // regular document is accessible
        assertTrue(docRefs.contains(fileRefRegular));
        // documents up to level 2 are accessible, 3 and above are not
        assertTrue(docRefs.contains(fileRefLevel1));
        assertTrue(docRefs.contains(fileRefLevel2));
        assertFalse(docRefs.contains(fileRefLevel3));
    }

    @Test
    public void testSearchUserWithoutClearance() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userWithoutClearance);
        DocumentModelList docs = userSession.query(QUERY);
        List<DocumentRef> docRefs = docs.stream().map(DocumentModel::getRef).toList();
        assertEquals(1, docs.size());
        // regular document is accessible
        assertTrue(docRefs.contains(fileRefRegular));
        // no document under security policy is accessible
        assertFalse(docRefs.contains(fileRefLevel1));
        assertFalse(docRefs.contains(fileRefLevel2));
        assertFalse(docRefs.contains(fileRefLevel3));
    }

    @Test
    public void testSearchUserAsCreator() {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userLevel2);
        DocumentModel doc = createFileWithClearanceLevel("File Level 4", 4L, userSession);
        DocumentModelList docs = userSession.query(QUERY);
        // document has a higher clearance level but is still accessible by the creator
        assertTrue(docs.contains(doc));
    }

    @Test
    public void testAllowedUpdates() {
        DocumentModel documentModel = session.getDocument(fileRefLevel2);
        InternalSession internalSession = ((SessionImpl) session).getSession();
        InternalDocument internalDocument = internalSession.getDocumentByUUID(documentModel.getId());
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(REPOSITORY);
        // userLevel2 is allowed to update the business mixin
        securityPolicyService.checkAllowedMixinUpdate(internalDocument, userLevel2, POLICY_MIXIN);
        // userWithoutClearance is not allowed to update the business mixin
        assertThrows(DocumentSecurityException.class,
                () -> securityPolicyService.checkAllowedMixinUpdate(internalDocument, //
                        userWithoutClearance, POLICY_MIXIN));
        // userLevel2 is allowed to update the security property
        securityPolicyService.checkAllowedPropertyUpdate(internalDocument, userLevel2, POLICY_BUSINESS_PROPERTY);
        // userWithoutClearance is not allowed to update the security property
        assertThrows(DocumentSecurityException.class,
                () -> securityPolicyService.checkAllowedPropertyUpdate(internalDocument, //
                        userWithoutClearance, POLICY_BUSINESS_PROPERTY));
        // userWithoutClearance would be allowed to update other mixins (if they had permission)
        securityPolicyService.checkAllowedMixinUpdate(internalDocument, userWithoutClearance, "otherMixin");
        // userWithoutClearance would be allowed to update other properties (if they had permission)
        securityPolicyService.checkAllowedPropertyUpdate(internalDocument, userWithoutClearance, "otherProperty");
    }

    private DocumentModel createRegularFile(String name) {
        DocumentModel file = session.createDocumentModel("/", name, SYS_FILE);
        file.create();
        session.save();
        return file;
    }

    private DocumentModel createFileWithClearanceLevel(String name, long clearanceLevel, CoreSession session) {
        DocumentModel file = session.createDocumentModel("/", name, TYPE_NAME);
        file.create();
        // security policy (and mixin) was automatically associated at creation time
        file.setPropertyValue(POLICY_BUSINESS_PROPERTY, clearanceLevel);
        session.save(); // also implicitly sets sys_creator & co
        return file;
    }

    @Test
    public void testBlankMixin() {
        assertConstructorViolation("The mixin must be set on security policy \"ClearanceSecurityLevel\"",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, " ", POLICY_USER_ATTRIBUTE);
    }

    @Disabled("HXCS-6020 Cannot be checked at config time, the content model may not be ready yet")
    @Test
    public void testInvalidMixin() {
        assertConstructorViolation("Unknown mixin \"invalid\" on security policy \"ClearanceSecurityLevel\"",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, "invalid", POLICY_USER_ATTRIBUTE);
    }

    @Disabled("HXCS-6020 Cannot be checked at config time, the content model may not be ready yet")
    @Test
    public void testInvalidMixinTooManyProperties() {
        assertConstructorViolation("The mixin \"tooManyBusinessProperties\" on security policy "
                        + "\"ClearanceSecurityLevel\" should only bring in one security property",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, "tooManyBusinessProperties", POLICY_USER_ATTRIBUTE);
    }

    @Test
    public void testInvalidPermission() {
        assertConstructorViolation(
                "An element of permissions \"invalid\" on security policy \"ClearanceSecurityLevel\" is unknown",
                POLICY_CONTRACT_TYPES, Set.of("invalid"), POLICY_MIXIN, POLICY_USER_ATTRIBUTE);
    }

    @Test
    public void testBlankUserAttribute() {
        assertConstructorViolation("The userAttribute must be set on security policy \"ClearanceSecurityLevel\"",
                POLICY_CONTRACT_TYPES, POLICY_PERMISSIONS, POLICY_MIXIN, " ");
    }

    @Test
    public void testPermissionsNull() {
        // sets a single policy managing null permissions (all permissions apply)
        setSinglePolicyWithPermissions(null);
        // READ, WRITE and EVERYTHING are tested
        assertHasPermission(false, false, false);
        // query clause is included
        assertIncludesPolicyQueryClause(true);
        beforeClass(); // reset proper policy for other tests
    }

    @Test
    public void testPermissionsEmpty() {
        // sets a single policy managing an empty list of permissions (all permissions apply)
        setSinglePolicyWithPermissions(Set.of());
        // READ, WRITE and EVERYTHING are tested
        assertHasPermission(false, false, false);
        // query clause is included
        assertIncludesPolicyQueryClause(true);
        beforeClass(); // reset proper policy for other tests
    }

    @Test
    public void testPermissionsRead() {
        // sets a single policy managing only the READ permission
        setSinglePolicyWithPermissions(Set.of(READ));
        // READ is tested, WRITE and EVERYTHING are granted by default
        assertHasPermission(false, true, true);
        // query clause is included
        assertIncludesPolicyQueryClause(true);
        beforeClass(); // reset proper policy for other tests
    }

    @Test
    public void testPermissionsWrite() {
        // sets a single policy managing only the WRITE permission
        setSinglePolicyWithPermissions(Set.of(WRITE));
        // WRITE is tested, READ and EVERYTHING are granted by default
        assertHasPermission(true, false, true);
        // query clause is not included
        assertIncludesPolicyQueryClause(false);
        beforeClass(); // reset proper policy for other tests
    }

    @Test
    public void testPermissionsReadWrite() {
        // sets a single policy managing only the READ_WRITE permission
        setSinglePolicyWithPermissions(Set.of(READ_WRITE));
        // READ and WRITE are tested, EVERYTHING is granted by default
        assertHasPermission(false, false, true);
        // query clause is included
        assertIncludesPolicyQueryClause(true);
        beforeClass(); // reset proper policy for other tests
    }

    @Test
    public void testPermissionsEverything() {
        // sets a single policy managing the EVERYTHING permission (all permissions apply)
        setSinglePolicyWithPermissions(Set.of(EVERYTHING));
        // READ, WRITE and EVERYTHING are tested
        assertHasPermission(false, false, false);
        // query clause is included
        assertIncludesPolicyQueryClause(true);
        beforeClass(); // reset proper policy for other tests
    }

    @Test
    public void testReadUserWithClearanceStringValue() {
        UserPrincipal user = DummyLoginFeature.getTestPrincipal("userWithStringAttributeValue", false);
        user.setProperties(Collections.singletonMap(POLICY_USER_ATTRIBUTE, "2"));
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), user);
        // document with level 2 is accessible
        assertNotNull(userSession.getDocument(fileRefLevel2));
        // document with level 3 is not
        assertThrows(DocumentSecurityException.class, () -> userSession.getDocument(fileRefLevel3));
    }

    /**
     * Asserts whether a user has READ, WRITE and EVERYTHING permissions on a document with a higher clearance level.
     * If a single SecurityLevelSecurityPolicy is configured and is evaluated, it should deny the access.
     * If it is not evaluated, the default behaviour of the service level prevails, granting the access.
     * @param canRead whether the user has the READ permission
     * @param canWrite whether the user has the WRITE permission
     * @param canDoAll whether the user has the EVERYTHING permission
     */
    private void assertHasPermission(boolean canRead, boolean canWrite, boolean canDoAll) {
        CoreSession userSession = CoreInstance.getCoreSession(session.getRepositoryName(), userLevel2);
        Assertions.assertEquals(canRead, userSession.hasPermission(fileRefLevel3, READ));
        Assertions.assertEquals(canWrite, userSession.hasPermission(fileRefLevel3, WRITE));
        Assertions.assertEquals(canDoAll, userSession.hasPermission(fileRefLevel3, EVERYTHING));
    }

    /**
     * Asserts whether the policy clause is included in a user search query.
     * With a single SecurityLevelSecurityPolicy configured, the clause should be included if the policy manages the
     * READ permission.
     * @param includesClause whether the policy clause is included in the query
     */
    private void assertIncludesPolicyQueryClause(boolean includesClause) {
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(REPOSITORY);
        Statement query = new Statement(new SelectClause(), new FromClause(List.of(SYS_CONTENT)));
        QueryParameters queryParameters = new QueryParameters(userLevel2, 0, 0);
        query = securityPolicyService.addSecurityPolicies(query, queryParameters);
        if (includesClause) {
            Assertions.assertTrue(query.getWhereClause().toString().contains(POLICY_BUSINESS_PROPERTY));
        } else {
            Assertions.assertNull(query.getWhereClause());
        }
    }

}
