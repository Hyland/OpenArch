/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.security.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicy.EXPRESSION_ENGINE_NAME;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.security.config.ACLSecurityPolicyConfig;

public class ExpressionSecurityPolicyConfigTest {

    private static final String PROJECT_ID_1 = null;

    private static final String PROJECT_ID_2 = "54321";

    private static final Set<String> CONTENT_TYPES_A = Set.of("Contract");

    private static final Set<String> PERMISSIONS_A = Set.of("permissionsA");

    private static final String MIXIN_1 = "Clearance";

    private static final String EXPRESSION_1 = "1 < 2";

    private static final String EXPRESSION_2 = "2 < 3";

    private static final ACLSecurityPolicyConfig ACL = new ACLSecurityPolicyConfig();

    private static final ExpressionSecurityPolicyConfig POLICY_DEFAULT = new ExpressionSecurityPolicyConfig();

    private static final ExpressionSecurityPolicyConfig POLICY_1 = new ExpressionSecurityPolicyConfig(PROJECT_ID_1,
            CONTENT_TYPES_A, PERMISSIONS_A, MIXIN_1, EXPRESSION_1);

    private static final ExpressionSecurityPolicyConfig POLICY_2 = POLICY_1.withProjectId(PROJECT_ID_2);

    private static final ExpressionSecurityPolicyConfig POLICY_EXPRESSION = new ExpressionSecurityPolicyConfig(
            PROJECT_ID_1, CONTENT_TYPES_A, PERMISSIONS_A, MIXIN_1, EXPRESSION_2);

    @Test
    public void testGets() {
        assertEquals(EXPRESSION_1, POLICY_1.getExpression());
        assertEquals(EXPRESSION_ENGINE_NAME, POLICY_1.getEngine());

        // superclass
        assertEquals(PROJECT_ID_1, POLICY_1.getProjectId());
        assertEquals(CONTENT_TYPES_A, POLICY_1.getContentTypes());
        assertEquals(PERMISSIONS_A, POLICY_1.getPermissions());
        assertEquals(MIXIN_1, POLICY_1.getMixin());

        assertNull(POLICY_DEFAULT.getExpression());
        assertEquals(EXPRESSION_ENGINE_NAME, POLICY_DEFAULT.getEngine());
        assertNull(POLICY_DEFAULT.getProjectId());
    }

    @Test
    public void testWithProjectId() {
        assertEquals(POLICY_1.getExpression(), POLICY_2.getExpression());
        assertEquals(POLICY_1.getEngine(), POLICY_2.getEngine());
        assertEquals(POLICY_1.getContentTypes(), POLICY_2.getContentTypes());
        assertEquals(POLICY_1.getMixin(), POLICY_2.getMixin());

        assertNotEquals(POLICY_1.getProjectId(), POLICY_2.getProjectId());
        assertEquals(PROJECT_ID_2, POLICY_2.getProjectId());
    }

    @Test
    public void testEquals() {
        // self
        assertTrue(POLICY_1.equals(POLICY_1));           // NOSONAR really want to test the equals method

        // wrong class
        assertFalse(POLICY_1.equals(ACL));               // NOSONAR really want to test the equals method

        // super.equals (projectId)
        assertFalse(POLICY_1.equals(POLICY_2));          // NOSONAR really want to test the equals method

        // expression
        assertFalse(POLICY_1.equals(POLICY_EXPRESSION)); // NOSONAR really want to test the equals method
    }

    @Test
    public void testHashCode() {
        assertNotEquals(POLICY_1.hashCode(), POLICY_2.hashCode());
        assertEquals(POLICY_1.hashCode(), POLICY_2.withProjectId(PROJECT_ID_1).hashCode());
    }
}
