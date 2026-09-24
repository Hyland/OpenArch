/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.platform.usermanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.NuxeoGroup;

public class TestNuxeoGroupImpl {

    @Test
    public void testConstructor() {
        NuxeoGroup group = new NuxeoGroupImpl("myid", "mygroup");

        assertEquals("mygroup", group.getName());
        assertEquals("mygroup", group.toString());

        assertNull(group.getMemberUsers());
        assertNull(group.getMemberGroups());
        assertNull(group.getParentGroups());

        List<String> users = Arrays.asList("joe", "jane");
        group.setMemberUsers(users);
        assertEquals(users, group.getMemberUsers());

        List<String> groups = Arrays.asList("sales", "admin");
        group.setMemberGroups(groups);
        assertEquals(groups, group.getMemberGroups());

        List<String> parentGroups = Arrays.asList("xx", "yy");
        group.setParentGroups(parentGroups);
        assertEquals(parentGroups, group.getParentGroups());
    }

    @Test
    public void testEquals() {
        NuxeoGroup group1 = new NuxeoGroupImpl("myid", "mygroup");
        NuxeoGroup group2 = new NuxeoGroupImpl("myid", "mygroup");
        NuxeoGroup group3 = new NuxeoGroupImpl("yourgroupId", "yourgroup");

        assertEquals(group1, group1);
        assertEquals(group1, group2);
        assertEquals(group1.hashCode(), group2.hashCode());
        assertNotNull(group1);
        assertNotEquals(group1, group3);
        assertEquals("mygroup", group2.getName());

        group3.setName("mygroup");
        assertEquals(group1, group3);
        assertEquals(group1.hashCode(), group3.hashCode());
    }

}
