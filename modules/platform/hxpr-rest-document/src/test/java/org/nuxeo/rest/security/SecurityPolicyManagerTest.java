/*
 * (C) Copyright 2026 Hyland (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SecurityPolicyManagerTest {

    @Test
    void projectIdLogTokenMasksUnsafeProjectId() {
        String projectId = "PF00000\nmalicious";

        String token = SecurityPolicyManager.projectIdLogToken(projectId);

        assertNotEquals(projectId, token);
        assertEquals("<invalid-project-id>", token);
        assertFalse(token.isEmpty());
    }

    @Test
    void projectIdLogTokenReturnsSafeProjectIdAsIs() {
        String projectId = "PF00000";

        String token = SecurityPolicyManager.projectIdLogToken(projectId);

        assertEquals(projectId, token);
    }

    @Test
    void projectIdLogTokenHandlesNullAndBlankValues() {
        assertEquals("<null>", SecurityPolicyManager.projectIdLogToken(null));
        assertEquals("<blank>", SecurityPolicyManager.projectIdLogToken("   "));
    }
}
