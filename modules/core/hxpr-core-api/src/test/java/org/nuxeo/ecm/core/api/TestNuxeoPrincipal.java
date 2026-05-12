/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;

public class TestNuxeoPrincipal {

    @Test
    public void test() {
        NuxeoPrincipal principal = new UserPrincipal("john", null, false);

        assertEquals("john", principal.getId());

        principal.setFirstName("john");
        assertEquals("john", principal.getFirstName());

        principal.setLastName("paul");
        assertEquals("paul", principal.getLastName());
    }

    @Test
    public void testEquals() {
        NuxeoPrincipal john1 = new UserPrincipal("john", null, false);
        NuxeoPrincipal john2 = new UserPrincipal("john", null, true);

        assertEquals(john1, john2);
        assertEquals(john1.hashCode(), john2.hashCode());

        NuxeoPrincipal jim = new UserPrincipal("jim", null, false);
        assertNotEquals(john1, jim);
        assertNotEquals(jim, john1);
    }
}
