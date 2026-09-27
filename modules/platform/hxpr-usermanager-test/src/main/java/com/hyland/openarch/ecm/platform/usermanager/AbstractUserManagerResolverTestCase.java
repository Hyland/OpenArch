/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.platform.usermanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver.NAME_KEY;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.RepositoryGroup;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationService;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;

/**
 * Abstract base test for {@link UserManagerResolver} that exercises the resolver with a concrete {@link UserManager}
 * implementation.
 * <p>
 * Subclasses provide the feature and Spring configuration needed to boot a specific UserManager backend (enterprise IdP
 * or community file-based).
 * </p>
 */
@Features({ CoreFeature.class })
@Deploy("org.nuxeo.ecm.platform.usermanager.test:test-usermanager-resolver.xml")
@RepositoryConfig(cleanup = Granularity.METHOD)
@SuppressWarnings("java:S5960") // using Assert for production code...this is testcode
public abstract class AbstractUserManagerResolverTestCase extends RuntimeSpringBootTest {

    private static final String USER_MANAGER_RESOLVER = "userManagerResolver";

    /** UUID of the primary test user ({@code testuser}) defined in {@code hxpr-users.yml}. */
    public static final String ADMIN_USER = "ff7b0360-3594-416c-b587-2fa4dd0839ce";

    /** UUID of the {@code members} group defined in {@code hxpr-users.yml}. */
    public static final String MEMBERS_GROUP = "8d6542c9-50e9-4027-aa10-2cfb2179413f";

    private static final String USER_XPATH = "umr_user";

    private static final String GROUP_XPATH = "umr_group";

    private static final String USER_GROUP_XPATH = "umr_userOrGroup";

    private static final String PREFIXED_ADMIN = "user:" + ADMIN_USER;

    private static final String PREFIXED_MEMBERS_GROUP = "group:" + MEMBERS_GROUP;

    private static final String VALIDATION = "validation";

    @RuntimeInject
    protected CoreSession session;

    @RuntimeInject
    protected DocumentValidationService validator;

    @RuntimeInject
    protected UserManager userManager;

    protected DocumentModel doc;

    @BeforeEach
    public void setupBeforeEachTest() {
        doc = session.createDocumentModel("/", "doc1", "TestResolver");
    }

    @Test
    public void defaultSupportedClasses() {
        List<Class<?>> classes = new UserManagerResolver().getManagedClasses();
        assertEquals(2, classes.size());
        assertTrue(classes.contains(RepositoryPrincipal.class));
        assertTrue(classes.contains(RepositoryGroup.class));
    }

    @Test
    public void userAndGroupSupportedClasses() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        umr.configure(parameters);
        List<Class<?>> classes = umr.getManagedClasses();
        assertEquals(2, classes.size());
        assertTrue(classes.contains(RepositoryPrincipal.class));
        assertTrue(classes.contains(RepositoryGroup.class));
    }

    @Test
    public void userSupportedClasses() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_USER);
        umr.configure(parameters);
        List<Class<?>> classes = umr.getManagedClasses();
        assertEquals(1, classes.size());
        assertTrue(classes.contains(RepositoryPrincipal.class));
    }

    @Test
    public void groupSupportedClasses() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_GROUP);
        umr.configure(parameters);
        List<Class<?>> classes = umr.getManagedClasses();
        assertEquals(1, classes.size());
        assertTrue(classes.contains(RepositoryGroup.class));
    }

    @Test
    public void testLifecycleNoConfigurationFetch() {
        assertThrows(IllegalStateException.class, () -> new UserManagerResolver().fetch("usr:Administrator"));
    }

    @Test
    public void testLifecycleNoConfigurationFetchCast() {
        assertThrows(IllegalStateException.class,
                () -> new UserManagerResolver().fetch(RepositoryPrincipal.class, "usr:Administrator"));
    }

    @Test
    public void testLifecycleNoConfigurationGetReference() {
        assertThrows(IllegalStateException.class,
                () -> new UserManagerResolver().getReference(userManager.getPrincipal(ADMIN_USER)));
    }

    @Test
    public void testLifecycleNoConfigurationGetParameters() {
        assertThrows(IllegalStateException.class, () -> new UserManagerResolver().getParameters());
    }

    @Test
    public void testLifecycleNoConfigurationGetConstraintErrorMessage() {
        assertThrows(IllegalStateException.class,
                () -> new UserManagerResolver().getConstraintErrorMessage(null, Locale.ENGLISH));
    }

    @Test
    public void testLifecycleConfigurationTwice() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        umr.configure(parameters);
        assertThrows(IllegalStateException.class, () -> umr.configure(parameters));
    }

    @Test
    public void testConfigurationDefaultUserOrGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(Map.of(NAME_KEY, USER_MANAGER_RESOLVER));
        assertTrue(umr.isIncludingGroups());
        assertTrue(umr.isIncludingUsers());
        assertEquals(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, VALIDATION, true), umr.getParameters());
    }

    @Test
    public void testConfigurationUser() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, UserManagerResolver.INPUT_PARAM_FILTER,
                UserManagerResolver.FILTER_USER));
        assertFalse(umr.isIncludingGroups());
        assertTrue(umr.isIncludingUsers());
        assertEquals(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, "type", "user", VALIDATION, true), umr.getParameters());
    }

    @Test
    public void testConfigurationGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, UserManagerResolver.INPUT_PARAM_FILTER,
                UserManagerResolver.FILTER_GROUP));
        assertTrue(umr.isIncludingGroups());
        assertFalse(umr.isIncludingUsers());
        assertEquals(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, "type", "group", VALIDATION, true), umr.getParameters());
    }

    @Test
    public void testValidateGoodUserWithDefaultConf() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertTrue(umr.validate(PREFIXED_ADMIN));
    }

    @Test
    public void testValidateGoodUserWithFilterUser() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_USER);
        umr.configure(parameters);
        assertTrue(umr.validate(PREFIXED_ADMIN));
    }

    @Test
    public void testValidateUserFailedWithBadValue() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertFalse(umr.validate("toto"));
    }

    @Test
    public void testValidateUserSucceedWithoutPrefix() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertTrue(umr.validate(ADMIN_USER));
    }

    @Test
    public void testValidateUserFailedWithFilterGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_GROUP);
        umr.configure(parameters);
        assertFalse(umr.validate(PREFIXED_ADMIN));
    }

    @Test
    public void testValidateGoodGroupWithDefaultConf() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertTrue(umr.validate(PREFIXED_MEMBERS_GROUP));
    }

    @Test
    public void testValidateGoodGroupWithFilterGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_GROUP);
        umr.configure(parameters);
        assertTrue(umr.validate(PREFIXED_MEMBERS_GROUP));
    }

    @Test
    public void testValidateGroupSucceedWithoutPrefix() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertTrue(umr.validate(MEMBERS_GROUP));
    }

    @Test
    public void testValidateGroupFailedWithFilterGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_USER);
        umr.configure(parameters);
        assertFalse(umr.validate(PREFIXED_MEMBERS_GROUP));
    }

    @Test
    public void testFetchGoodUserWithDefaultConf() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        Object entity = umr.fetch(PREFIXED_ADMIN);
        assertTrue(entity instanceof RepositoryPrincipal);
        assertEquals(ADMIN_USER, ((RepositoryPrincipal) entity).getId());
    }

    @Test
    public void testFetchGoodUserWithFilterUser() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_USER);
        umr.configure(parameters);
        Object entity = umr.fetch(PREFIXED_ADMIN);
        assertTrue(entity instanceof RepositoryPrincipal);
        assertEquals(ADMIN_USER, ((RepositoryPrincipal) entity).getId());
    }

    @Test
    public void testFetchUserFailedWithBadValue() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNull(umr.fetch("user:toto"));
    }

    @Test
    public void testFetchUserSucceedWithoutPrefix() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNotNull(umr.fetch(ADMIN_USER));
    }

    @Test
    public void testFetchUserFailedWithFilterGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_GROUP);
        umr.configure(parameters);
        assertNull(umr.fetch(PREFIXED_ADMIN));
    }

    @Test
    public void testFetchGoodGroupWithDefaultConf() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        Object entity = umr.fetch(PREFIXED_MEMBERS_GROUP);
        assertTrue(entity instanceof RepositoryGroup);
        assertEquals(MEMBERS_GROUP, ((RepositoryGroup) entity).getId());
    }

    @Test
    public void testFetchGoodGroupWithFilterGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_GROUP);
        umr.configure(parameters);
        Object entity = umr.fetch(PREFIXED_MEMBERS_GROUP);
        assertTrue(entity instanceof RepositoryGroup);
        assertEquals(MEMBERS_GROUP, ((RepositoryGroup) entity).getId());
    }

    @Test
    public void testFetchGroupFailedWithBadValue() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNull(umr.fetch("group:toto"));
    }

    @Test
    public void testFetchGroupSucceedWithoutPrefix() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNotNull(umr.fetch(MEMBERS_GROUP));
    }

    @Test
    public void testFetchGroupFailedWithFilterUser() {
        UserManagerResolver umr = new UserManagerResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(UserManagerResolver.INPUT_PARAM_FILTER, UserManagerResolver.FILTER_USER);
        umr.configure(parameters);
        assertNull(umr.fetch(PREFIXED_MEMBERS_GROUP));
    }

    @Test
    public void testFetchCastNuxeoPrincipal() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        RepositoryPrincipal principal = umr.fetch(RepositoryPrincipal.class, PREFIXED_ADMIN);
        assertNotNull(principal);
        assertEquals(ADMIN_USER, principal.getId());
        assertNotNull(umr.fetch(RepositoryPrincipal.class, ADMIN_USER));
        assertNull(umr.fetch(RepositoryPrincipal.class, "user:toto"));
        assertNull(umr.fetch(RepositoryPrincipal.class, PREFIXED_MEMBERS_GROUP));
    }

    @Test
    public void testFetchCastNuxeoGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        RepositoryGroup group = umr.fetch(RepositoryGroup.class, PREFIXED_MEMBERS_GROUP);
        assertNotNull(group);
        assertEquals(MEMBERS_GROUP, group.getId());
        assertNotNull(umr.fetch(RepositoryGroup.class, MEMBERS_GROUP));
        assertNull(umr.fetch(RepositoryGroup.class, "group:toto"));
        assertNull(umr.fetch(RepositoryGroup.class, PREFIXED_ADMIN));
    }

    @Test
    public void testFetchCastDoesntSupportDocumentModel() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNull(umr.fetch(DocumentModel.class, PREFIXED_ADMIN));
    }

    @Test
    public void testFetchCastDoesntSupportStupidTypes() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNull(umr.fetch(List.class, PREFIXED_ADMIN));
    }

    @Test
    public void testGetReferenceUser() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        RepositoryPrincipal principal = userManager.getPrincipal(ADMIN_USER);
        assertEquals(PREFIXED_ADMIN, umr.getReference(principal));
    }

    @Test
    public void testGetReferenceGroup() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        RepositoryGroup group = userManager.getGroup(MEMBERS_GROUP);
        assertEquals(PREFIXED_MEMBERS_GROUP, umr.getReference(group));
    }

    @Test
    public void testGetReferenceWithNonExistingUserWorks() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        RepositoryPrincipal principal = new RepositoryPrincipalImpl("chaps");
        assertEquals("user:chaps", umr.getReference(principal));
    }

    @Test
    public void testGetReferenceWithNonExistingGroupWorks() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        RepositoryGroup group = new RepositoryGroupImpl("chapsId", "chaps");
        assertEquals("group:chapsId", umr.getReference(group));
    }

    @Test
    public void testGetReferenceInvalid() {
        UserManagerResolver umr = new UserManagerResolver();
        umr.configure(new HashMap<>());
        assertNull(umr.getReference("nothing"));
    }

    @Test
    public void testConfigurationIsLoaded() {
        UserManagerResolver userResolver = (UserManagerResolver) session.getSchemaManager()
                                                                        .getField(USER_XPATH)
                                                                        .getType()
                                                                        .getObjectResolver();
        assertTrue(userResolver.isIncludingUsers());
        assertFalse(userResolver.isIncludingGroups());
        UserManagerResolver groupResolver = (UserManagerResolver) session.getSchemaManager()
                                                                         .getField(GROUP_XPATH)
                                                                         .getType()
                                                                         .getObjectResolver();
        assertFalse(groupResolver.isIncludingUsers());
        assertTrue(groupResolver.isIncludingGroups());
        UserManagerResolver anyResolver = (UserManagerResolver) session.getSchemaManager()
                                                                       .getField(USER_GROUP_XPATH)
                                                                       .getType()
                                                                       .getObjectResolver();
        assertTrue(anyResolver.isIncludingUsers());
        assertTrue(anyResolver.isIncludingGroups());
    }

    @Test
    public void testTypeHasResolver() {
        ObjectResolver resolver;
        resolver = session.getSchemaManager().getField(USER_XPATH).getType().getObjectResolver();
        assertNotNull(resolver);
        assertTrue(resolver instanceof UserManagerResolver);
        resolver = session.getSchemaManager().getField(GROUP_XPATH).getType().getObjectResolver();
        assertNotNull(resolver);
        assertTrue(resolver instanceof UserManagerResolver);
        resolver = session.getSchemaManager().getField(USER_GROUP_XPATH).getType().getObjectResolver();
        assertNotNull(resolver);
        assertTrue(resolver instanceof UserManagerResolver);
    }

    @Test
    public void testNullValueReturnNull() {
        assertNull(doc.getObjectResolver(USER_XPATH).fetch());
        assertNull(doc.getObjectResolver(USER_XPATH).fetch(DocumentModel.class));
        assertNull(doc.getObjectResolver(GROUP_XPATH).fetch());
        assertNull(doc.getObjectResolver(GROUP_XPATH).fetch(DocumentModel.class));
        assertNull(doc.getObjectResolver(USER_GROUP_XPATH).fetch());
        assertNull(doc.getObjectResolver(USER_GROUP_XPATH).fetch(DocumentModel.class));
    }

    @Test
    public void testBadValuesValidationFailed() {
        doc.setPropertyValue(USER_XPATH, "totoDoesntExists");
        assertNull(doc.getObjectResolver(USER_XPATH).fetch());
        assertFalse(doc.getObjectResolver(USER_XPATH).validate());
        doc.setPropertyValue(GROUP_XPATH, "totoDoesntExists");
        assertNull(doc.getObjectResolver(GROUP_XPATH).fetch());
        assertFalse(doc.getObjectResolver(GROUP_XPATH).validate());
        doc.setPropertyValue(USER_GROUP_XPATH, "totoDoesntExists");
        assertNull(doc.getObjectResolver(USER_GROUP_XPATH).fetch());
        assertFalse(doc.getObjectResolver(USER_GROUP_XPATH).validate());
        assertEquals(3, validator.validate(doc).numberOfViolations());
    }

    @Test
    public void testUserCorrectValues() {
        doc.setPropertyValue(USER_XPATH, PREFIXED_ADMIN);
        RepositoryPrincipal principal = (RepositoryPrincipal) doc.getObjectResolver(USER_XPATH).fetch();
        assertNotNull(principal);
        assertEquals(ADMIN_USER, principal.getId());
        principal = doc.getObjectResolver(USER_XPATH).fetch(RepositoryPrincipal.class);
        assertNotNull(principal);
        assertEquals(ADMIN_USER, principal.getId());
    }

    @Test
    public void testUserDoesntSupportGroup() {
        doc.setPropertyValue(USER_XPATH, PREFIXED_MEMBERS_GROUP);
        assertNull(doc.getObjectResolver(USER_XPATH).fetch());
    }

    @Test
    public void testUserNoPrefixWorks() {
        doc.setPropertyValue(USER_XPATH, ADMIN_USER);
        assertNotNull(doc.getObjectResolver(USER_XPATH).fetch());
    }

    @Test
    public void testUserOrGroupSupportsUser() {
        doc.setPropertyValue(USER_GROUP_XPATH, PREFIXED_ADMIN);
        assertNotNull(doc.getObjectResolver(USER_GROUP_XPATH).fetch());
    }

    @Test
    public void testGroupCorrectValues() {
        doc.setPropertyValue(GROUP_XPATH, PREFIXED_MEMBERS_GROUP);
        RepositoryGroup group = (RepositoryGroup) doc.getObjectResolver(GROUP_XPATH).fetch();
        assertNotNull(group);
        assertEquals(MEMBERS_GROUP, group.getId());
        group = doc.getObjectResolver(GROUP_XPATH).fetch(RepositoryGroup.class);
        assertNotNull(group);
        assertEquals(MEMBERS_GROUP, group.getId());
    }

    @Test
    public void testGroupFieldDoesntSupportUser() {
        doc.setPropertyValue(GROUP_XPATH, PREFIXED_ADMIN);
        assertNull(doc.getObjectResolver(GROUP_XPATH).fetch());
    }

    @Test
    public void testGroupNoPrefixWorks() {
        doc.setPropertyValue(GROUP_XPATH, MEMBERS_GROUP);
        assertNotNull(doc.getObjectResolver(GROUP_XPATH).fetch());
    }

    @Test
    public void testUserOrGroupSupportsGroup() {
        doc.setPropertyValue(USER_GROUP_XPATH, PREFIXED_MEMBERS_GROUP);
        assertNotNull(doc.getObjectResolver(USER_GROUP_XPATH).fetch());
    }

    @Test
    public void testTranslation() {
        UserManagerResolver allUMR = new UserManagerResolver();
        allUMR.configure(Map.of(NAME_KEY, USER_MANAGER_RESOLVER));
        checkMessage("\"abc123\" is not a valid user name or a valid group name.", allUMR);

        UserManagerResolver userUMR = new UserManagerResolver();
        userUMR.configure(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, UserManagerResolver.INPUT_PARAM_FILTER,
                UserManagerResolver.FILTER_USER));
        checkMessage("\"abc123\" is not a valid user name.", userUMR);

        UserManagerResolver groupUMR = new UserManagerResolver();
        groupUMR.configure(Map.of(NAME_KEY, USER_MANAGER_RESOLVER, UserManagerResolver.INPUT_PARAM_FILTER,
                UserManagerResolver.FILTER_GROUP));
        checkMessage("\"abc123\" is not a valid group name.", groupUMR);

        UserManagerResolver groupUMR2 = new UserManagerResolver();
        groupUMR2.configure(Map.of(NAME_KEY, "myResolver", UserManagerResolver.INPUT_PARAM_FILTER,
                UserManagerResolver.FILTER_GROUP));
        checkMessage("myResolver cannot resolve reference abc123", groupUMR2);
    }

    private void checkMessage(String expected, UserManagerResolver umr) {
        assertEquals(expected, umr.getConstraintErrorMessage("abc123", Locale.ENGLISH));
    }
}
