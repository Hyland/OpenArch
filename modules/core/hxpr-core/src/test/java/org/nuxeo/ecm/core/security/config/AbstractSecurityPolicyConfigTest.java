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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.SecurityPolicy;

public class AbstractSecurityPolicyConfigTest {

    private static class SubClass extends AbstractSecurityPolicyConfig {
        public SubClass(String engine, String projectId, Set<String> contentTypes, Set<String> permissions,
                String mixin) {
            super(engine, projectId, contentTypes, permissions, mixin);
        }

        @Override
        public AbstractSecurityPolicyConfig withProjectId(String projectId) {
            return null;
        }

        @Override
        public SecurityPolicy newSecurityPolicy(String repositoryName, String name,
                List<ValidationViolation> violations) {
            return null;
        }
    }

    private static final String ENGINE_1 = "engine1";

    private static final String ENGINE_2 = "engine2";

    private static final String PROJECT_1 = "one";

    private static final String PROJECT_2 = "two";

    private static final Set<String> CONTENT_TYPES_A = Set.of("A");

    private static final Set<String> CONTENT_TYPES_B = Set.of("ContentB1", "ContentB2");

    private static final Set<String> PERMISSIONS_REVIEW = Set.of("read", "highlight", "comment");

    private static final Set<String> PERMISSIONS_EMPTY = Set.of();

    private static final String MIXIN_FOO = "FooMixin";

    private static final AbstractSecurityPolicyConfig POLICY_1A = new SubClass(ENGINE_1, PROJECT_1,
            CONTENT_TYPES_A, PERMISSIONS_REVIEW, MIXIN_FOO);

    private static final AbstractSecurityPolicyConfig POLICY_COPY = new SubClass(ENGINE_1, PROJECT_1,
            CONTENT_TYPES_A, PERMISSIONS_REVIEW, MIXIN_FOO);

    private static final AbstractSecurityPolicyConfig POLICY_ENGINE = new SubClass(ENGINE_2, PROJECT_1,
            CONTENT_TYPES_A, PERMISSIONS_REVIEW, MIXIN_FOO);

    private static final AbstractSecurityPolicyConfig POLICY_2A = new SubClass(ENGINE_1, PROJECT_2,
            CONTENT_TYPES_A, PERMISSIONS_REVIEW, MIXIN_FOO);

    private static final AbstractSecurityPolicyConfig POLICY_1B = new SubClass(ENGINE_1, PROJECT_1,
            CONTENT_TYPES_B, PERMISSIONS_REVIEW, MIXIN_FOO);

    private static final AbstractSecurityPolicyConfig POLICY_1A_EMPTY = new SubClass(ENGINE_1, PROJECT_1,
            CONTENT_TYPES_B, PERMISSIONS_EMPTY, MIXIN_FOO);

    @Test
    public void testGets() {
        assertEquals(PROJECT_1, POLICY_1A.getProjectId());
        assertEquals(CONTENT_TYPES_A, POLICY_1A.getContentTypes());
        assertEquals(PERMISSIONS_REVIEW, POLICY_1A.getPermissions());
        assertEquals(PERMISSIONS_EMPTY, POLICY_1A_EMPTY.getPermissions());
        assertEquals(MIXIN_FOO, POLICY_1A.getMixin());
    }

    @Test
    public void testSets_notNull_emptyOrSorted() {
        // Having the order of collections mutate is a pain in tests and less friendly to clients
        assertTrue(new SubClass(ENGINE_1, PROJECT_1, null, null, null).getContentTypes().isEmpty());
        assertTrue(new SubClass(ENGINE_1, PROJECT_1, Set.of("A"), null, null).getContentTypes() instanceof SortedSet);

        assertTrue(new SubClass(ENGINE_1, PROJECT_1, null, null, null).getPermissions().isEmpty());
        assertTrue(new SubClass(ENGINE_1, PROJECT_1, null, Set.of("A"), null).getPermissions() instanceof SortedSet);
    }

    @Test
    public void testEquals() {
        // self
        assertTrue(POLICY_1A.equals(POLICY_1A));         // NOSONAR really want to test the equals method

        // wrong class
        assertFalse(POLICY_1A.equals(""));               // NOSONAR really want to test the equals method

        // super.equals
        assertFalse(POLICY_1A.equals(POLICY_ENGINE));    // NOSONAR really want to test the equals method

        // projectId
        assertFalse(POLICY_1A.equals(POLICY_2A));        // NOSONAR really want to test the equals method

        // contentTypes
        assertFalse(POLICY_1A.equals(POLICY_1B));        // NOSONAR really want to test the equals method

        // permissions
        assertFalse(POLICY_1A.equals(POLICY_1A_EMPTY)); // NOSONAR really want to test the equals method
    }

    @Test
    public void testHashCode() {
        assertEquals(POLICY_1A.hashCode(),  POLICY_COPY.hashCode());
        assertNotEquals(POLICY_1A.hashCode(), POLICY_2A.hashCode());
        assertNotEquals(POLICY_1A.hashCode(), POLICY_1B.hashCode());
    }
}
