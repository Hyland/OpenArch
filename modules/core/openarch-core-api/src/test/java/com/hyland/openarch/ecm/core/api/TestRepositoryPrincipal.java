/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.impl.UserPrincipal;

public class TestRepositoryPrincipal {

    @Test
    public void test() {
        RepositoryPrincipal principal = new UserPrincipal("john", null, false);

        assertEquals("john", principal.getId());

        principal.setFirstName("john");
        assertEquals("john", principal.getFirstName());

        principal.setLastName("paul");
        assertEquals("paul", principal.getLastName());
    }

    @Test
    public void testEquals() {
        RepositoryPrincipal john1 = new UserPrincipal("john", null, false);
        RepositoryPrincipal john2 = new UserPrincipal("john", null, true);

        assertEquals(john1, john2);
        assertEquals(john1.hashCode(), john2.hashCode());

        RepositoryPrincipal jim = new UserPrincipal("jim", null, false);
        assertNotEquals(john1, jim);
        assertNotEquals(jim, john1);

        UserPrincipal johnWithUsername = new UserPrincipal("john", null, false);
        johnWithUsername.setUsername("john.doe");
        assertNotEquals(john1, johnWithUsername);
        assertNotEquals(john1.hashCode(), johnWithUsername.hashCode());

        UserPrincipal johnWithUsernameCopy = new UserPrincipal("john", null, false);
        johnWithUsernameCopy.setUsername("john.doe");
        assertEquals(johnWithUsername, johnWithUsernameCopy);
        assertEquals(johnWithUsername.hashCode(), johnWithUsernameCopy.hashCode());
    }

    @Test
    public void testUsername() {
        RepositoryPrincipal principal = new UserPrincipal("john", null, false);
        assertNull(principal.getUsername());

        principal.setUsername("john.doe");
        assertEquals("john.doe", principal.getUsername());
    }

    @Test
    public void testSystemPrincipalUsername() {
        RepositoryPrincipal system = new SystemPrincipal("system");
        assertNull(system.getUsername());

        assertThrows(UnsupportedOperationException.class, () -> system.setUsername("anything"));
    }
}
