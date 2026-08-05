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

package org.nuxeo.ecm.core.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestACE {

    private ACE ace;

    private ACE acebis;

    @BeforeEach
    public void setUp() {
        ace = ACE.builder().user("bogdan").permission("write").isGranted(false).build();
        acebis = ACE.builder()
                    .user("vlad")
                    .permission("write")
                    .creator("pas")
                    .begin(Instant.parse("2015-07-14T12:34:56Z"))
                    .end(Instant.parse("2015-08-14T12:34:56Z"))
                    .build();
    }

    @AfterEach
    public void tearDown() {
        ace = null;
    }

    @Test
    public void testGetType() {
        assertFalse(ace.isGranted());
        assertTrue(ace.isDenied());
    }

    @Test
    public void testGetPrincipals() {
        assertEquals("u:bogdan", ace.getPrincipal());
    }

    @Test
    public void testGetPermissions() {
        assertEquals("write", ace.getPermission());
    }

    @Test
    public void testEquals() {
        ACE ace2 = ACE.builder().user("bogdan").permission("write").isGranted(false).build();
        ACE ace3 = ACE.builder().user("raoul").permission("write").isGranted(false).build();
        ACE ace4 = ACE.builder().user("bogdan").permission("read").isGranted(false).build();
        ACE ace5 = ACE.builder().user("bogdan").permission("write").isGranted(true).build();

        assertEquals(ace, ace);
        assertEquals(ace, ace2);
        assertEquals(ace2, ace);
        assertNotNull(ace);
        assertNotEquals(ace, ace3);
        assertNotEquals(ace, ace4);
        assertNotEquals(ace, ace5);

        assertEquals(ace.hashCode(), ace2.hashCode());

        Instant begin = Instant.parse("2015-07-14T12:34:56Z");
        Instant end = Instant.parse("2015-08-14T12:34:56Z");
        ACE ace6 = ACE.builder().user("leela").permission("read").isGranted(false).begin(begin).end(end).build();
        ACE ace7 = ACE.builder().user("leela").permission("read").isGranted(false).begin(begin).end(end).build();
        assertEquals(ace6, ace7);

        ACE ace8 = ACE.fromId(ace7.getId());
        assertEquals(ace6, ace8);
    }

    @Test
    public void testNewConstructors() {
        Instant cal1 = Instant.parse("2015-07-14T12:34:56Z");
        Instant cal2 = Instant.parse("2015-08-14T12:34:56Z");
        ACE ace1 = ACE.builder().user("vlad").permission("write").creator("pas").begin(cal1).end(cal2).build();
        assertEquals(acebis, ace1);
    }

    @Test
    public void testACEId() {
        Instant cal1 = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant cal2 = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        ACE ace = ACE.builder().user("vlad").permission("write").creator("pas").begin(cal1).end(cal2).build();

        assertEquals("u:vlad:write:true:pas:" + cal1.toEpochMilli() + ":" + cal2.toEpochMilli(), ace.getId());

        String aceId = "bob:write:false:pablo:" + cal1.toEpochMilli() + ":" + cal2.toEpochMilli();
        ace = ACE.fromId(aceId);
        assertNotNull(ace);
        assertEquals("bob", ace.getPrincipal());
        assertEquals("write", ace.getPermission());
        assertFalse(ace.isGranted());
        assertEquals("pablo", ace.getCreator());
        assertEquals(cal1, ace.getBegin());
        assertEquals(cal2, ace.getEnd());
        assertTrue(ace.isEffective());

        aceId = "pedro:read:true:::";
        ace = ACE.fromId(aceId);
        assertNotNull(ace);
        assertEquals("pedro", ace.getPrincipal());
        assertEquals("read", ace.getPermission());
        assertTrue(ace.isGranted());
        assertNull(ace.getCreator());
        assertNull(ace.getBegin());
        assertNull(ace.getEnd());
        assertTrue(ace.isEffective());

        aceId = "pedro:read:true::" + cal1.toEpochMilli() + ":";
        ace = ACE.fromId(aceId);
        assertNotNull(ace);
        assertEquals("pedro", ace.getPrincipal());
        assertEquals("read", ace.getPermission());
        assertTrue(ace.isGranted());
        assertNull(ace.getCreator());
        assertEquals(cal1, ace.getBegin());
        assertNull(ace.getEnd());
        assertTrue(ace.isEffective());

        // Tests with a username that includes colons ":"
        aceId = "mycorp:dep:research:project23:read:true::" + cal1.toEpochMilli() + ":";
        ace = ACE.fromId(aceId);
        assertNotNull(ace);
        assertEquals("mycorp:dep:research:project23", ace.getPrincipal());
        assertEquals("read", ace.getPermission());
        assertTrue(ace.isGranted());
        assertEquals(cal1, ace.getBegin());
        assertNull(ace.getEnd());
        assertTrue(ace.isEffective());
    }

    @Test
    public void testInvalidACEId() {
        String aceId = "pedro:read:";
        assertThrows(IllegalArgumentException.class, () -> ACE.fromId(aceId));
    }
}
