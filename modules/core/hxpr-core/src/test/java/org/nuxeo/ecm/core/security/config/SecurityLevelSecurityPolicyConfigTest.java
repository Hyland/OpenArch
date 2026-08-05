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
package org.nuxeo.ecm.core.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.security.SecurityLevelSecurityPolicy.SECURITY_LEVEL_ENGINE_NAME;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class SecurityLevelSecurityPolicyConfigTest {

    private static final String PROJECT_ID_1 = null;

    private static final String PROJECT_ID_2 = "54321";

    private static final Set<String> CONTENT_TYPES_A = Set.of("ContentA");

    private static final Set<String> PERMISSIONS_A = Set.of("permissionsA");

    private static final String MIXIN_1 = "Clearance";

    private static final String MIXIN_2 = "SecurityClearance";

    private static final String USER_ATTRIBUTE_1 = "clearance";

    private static final String USER_ATTRIBUTE_2 = "level";

    private static final ACLSecurityPolicyConfig ACL = new ACLSecurityPolicyConfig();

    private static final SecurityLevelSecurityPolicyConfig POLICY_DEFAULT = new SecurityLevelSecurityPolicyConfig();

    private static final SecurityLevelSecurityPolicyConfig POLICY_1 = new SecurityLevelSecurityPolicyConfig(
            PROJECT_ID_1, CONTENT_TYPES_A, PERMISSIONS_A, MIXIN_1, USER_ATTRIBUTE_1);

    private static final SecurityLevelSecurityPolicyConfig POLICY_2 = POLICY_1.withProjectId(PROJECT_ID_2);

    private static final SecurityLevelSecurityPolicyConfig POLICY_MIXIN = new SecurityLevelSecurityPolicyConfig(
            PROJECT_ID_1, CONTENT_TYPES_A, PERMISSIONS_A, MIXIN_2, USER_ATTRIBUTE_1);

    private static final SecurityLevelSecurityPolicyConfig POLICY_USER = new SecurityLevelSecurityPolicyConfig(
            PROJECT_ID_1, CONTENT_TYPES_A, PERMISSIONS_A, MIXIN_1, USER_ATTRIBUTE_2);

    @Test
    public void testGets() {
        assertEquals(MIXIN_1, POLICY_1.getMixin());
        assertEquals(USER_ATTRIBUTE_1, POLICY_1.getUserAttribute());
        assertEquals(SECURITY_LEVEL_ENGINE_NAME, POLICY_1.getEngine());

        // superclass
        assertEquals(PROJECT_ID_1, POLICY_1.getProjectId());
        assertEquals(CONTENT_TYPES_A, POLICY_1.getContentTypes());
        assertEquals(PERMISSIONS_A, POLICY_1.getPermissions());

        assertNull(POLICY_DEFAULT.getMixin());
        assertNull(POLICY_DEFAULT.getUserAttribute());
        assertEquals(SECURITY_LEVEL_ENGINE_NAME, POLICY_DEFAULT.getEngine());

        assertNull(POLICY_DEFAULT.getProjectId());
        assertEquals(Set.of(), POLICY_DEFAULT.getContentTypes());
        assertEquals(Set.of(), POLICY_DEFAULT.getPermissions());
    }

    @Test
    public void testWithProjectId() {
        assertEquals(POLICY_1.getMixin(), POLICY_2.getMixin());
        assertEquals(POLICY_1.getUserAttribute(), POLICY_2.getUserAttribute());

        // superclass
        assertEquals(POLICY_1.getEngine(), POLICY_2.getEngine());
        assertEquals(POLICY_1.getContentTypes(), POLICY_2.getContentTypes());
        assertEquals(POLICY_1.getPermissions(), POLICY_2.getPermissions());

        assertNotEquals(POLICY_1.getProjectId(), POLICY_2.getProjectId());
        assertEquals(PROJECT_ID_2, POLICY_2.getProjectId());
    }

    @Test
    public void testEquals() {
        // self
        assertTrue(POLICY_1.equals(POLICY_1));      // NOSONAR really want to test the equals method

        // wrong class
        assertFalse(POLICY_1.equals(ACL));          // NOSONAR really want to test the equals method

        // super.equals
        assertFalse(POLICY_1.equals(POLICY_2));     // NOSONAR really want to test the equals method

        // mixin
        assertFalse(POLICY_1.equals(POLICY_MIXIN)); // NOSONAR really want to test the equals method

        // userAttribute
        assertFalse(POLICY_1.equals(POLICY_USER));  // NOSONAR really want to test the equals method
    }

    @Test
    public void testHashCode() {
        assertNotEquals(POLICY_1.hashCode(), POLICY_2.hashCode());
        assertEquals(POLICY_1.hashCode(), POLICY_2.withProjectId(PROJECT_ID_1).hashCode());
    }
}
