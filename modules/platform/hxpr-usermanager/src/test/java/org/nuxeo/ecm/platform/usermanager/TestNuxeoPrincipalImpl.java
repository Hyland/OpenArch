/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 *
 */

package org.nuxeo.ecm.platform.usermanager;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

/**
 * @author Florent Guillaume
 */
public class TestNuxeoPrincipalImpl {

    @Test
    public void testEquals() {
        NuxeoPrincipalImpl a = new NuxeoPrincipalImpl("foo");
        NuxeoPrincipalImpl b = new NuxeoPrincipalImpl("foo");
        NuxeoPrincipalImpl c = new NuxeoPrincipalImpl("bar");
        assertEquals(a, b);
        assertEquals(b, a);
        assertNotEquals(a, c);
        assertNotEquals(c, a);
        assertNotNull(a);
        assertNotNull(c);
    }

    @Test
    public void testHasCode() {
        NuxeoPrincipalImpl a = new NuxeoPrincipalImpl("foo");
        NuxeoPrincipalImpl b = new NuxeoPrincipalImpl("foo");
        assertEquals(a.hashCode(), b.hashCode());
        // we don't assert that hash codes are different for principals with
        // different names, as that doesn't have to be true
    }

    @Test
    public void testUserProperties() {
        NuxeoPrincipalImpl principal = new NuxeoPrincipalImpl("foo");
        principal.setUserAttributes(Collections.singletonMap("PROPERTY1", "VALUE1"));
        principal.setEmail("foo@hyland.com");
        assertEquals("VALUE1", principal.getProperties().get("PROPERTY1"));
        assertThat(principal.getProperties()).hasSize(3).containsAllEntriesOf(Map.of(
            principal.getConfig().idKey, "foo",
            "PROPERTY1", "VALUE1",
            "email", "foo@hyland.com"
        ));
        assertThat(principal.getUserAttributes()).hasSize(1).containsEntry("PROPERTY1", "VALUE1");
    }

    @Test
    public void testDoNotReturnThePassword() {
        NuxeoPrincipalImpl principal = new NuxeoPrincipalImpl("foo");
        principal.setPassword("secret");
        assertThat(principal.getProperties()).hasSize(1).containsEntry(principal.getConfig().idKey, "foo").doesNotContainValue("secret");
    }

    @Test
    public void testGetUsername() {
        NuxeoPrincipalImpl principal = new NuxeoPrincipalImpl("foo");
        assertThat(principal.getUsername()).isNull();
        principal.setUsername("john.doe");
        assertThat(principal.getUsername()).isEqualTo("john.doe");
    }

    @Test
    public void userAttributesCannotOverwriteInternalFields() {
        NuxeoPrincipalImpl principal = new NuxeoPrincipalImpl("foo");
        principal.setEmail("foo@hyland.com");
        principal.setUserAttributes(Map.of(
            principal.getConfig().idKey, "maliciousUser",
            principal.getConfig().passwordKey, "maliciousPassword",
            principal.getConfig().emailKey, "maliciousUser@evil.com",
                "USERPROPERTY", "someValue"));
        assertThat(principal.getProperties()).hasSize(3).containsAllEntriesOf(Map.of(
            principal.getConfig().idKey, "foo",
            principal.getConfig().emailKey, "foo@hyland.com",
                "USERPROPERTY", "someValue"));
        assertThat(principal.getUserAttributes()).hasSize(4).containsAllEntriesOf(Map.of(
            principal.getConfig().idKey, "maliciousUser",
            principal.getConfig().passwordKey, "maliciousPassword",
            principal.getConfig().emailKey, "maliciousUser@evil.com",
                "USERPROPERTY", "someValue"));
    }

}
