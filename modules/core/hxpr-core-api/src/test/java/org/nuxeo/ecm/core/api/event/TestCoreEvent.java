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

package org.nuxeo.ecm.core.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.event.impl.CoreEventImpl;

import java.util.HashMap;
import java.util.Map;

public class TestCoreEvent {

    @Test
    public void testDefaultConstructor() {
        CoreEvent ev = new CoreEventImpl(null, null, null, null, null, null);

        assertNull(ev.getCategory());
        assertNull(ev.getComment());
        assertNull(ev.getEventId());
        assertEquals(2, ev.getInfo().size());
        assertNull(ev.getPrincipal());
        assertNull(ev.getSource());
        assertNotNull(ev.getDate());
    }

    @Test
    public void testGetCategory() {
        CoreEvent ev = new CoreEventImpl(null, null, null, null, "test", null);

        assertEquals("test", ev.getCategory());
        Map<String, Object> map = new HashMap<>();
        map.put("category", "lorem ipsum");
        ev = new CoreEventImpl(null, null, map, null, null, null);
        assertEquals("lorem ipsum", ev.getCategory());
        ev = new CoreEventImpl(null, null, null, null, null, null);
        assertNull(ev.getCategory());
    }

    @Test
    public void testGetComment() {
        CoreEvent ev = new CoreEventImpl(null, null, null, null, null, "lorem ipsum");

        assertEquals("lorem ipsum", ev.getComment());
        Map<String, Object> map = new HashMap<>();
        map.put("comment", "lorem ipsum");
        ev = new CoreEventImpl(null, null, map, null, null, null);
        assertEquals("lorem ipsum", ev.getComment());
        ev = new CoreEventImpl(null, null, null, null, null, null);
        assertNull(ev.getComment());
    }

    @Test
    public void testToString() {
        Map<String, Object> map = new HashMap<>();
        map.put("comment", "mapComment");
        map.put("category", "mapCategory");
        CoreEvent ev = new CoreEventImpl("testId", "testSource", map, null,
                "testCategory", "testComment");
        String expected = "CoreEventImpl { eventId: testId, source: testSource" +
                ", info: {category=mapCategory, comment=mapComment}, date: " + ev.getDate() + ", principal id: " +
                ", comment: testComment, category: testCategory}";
        assertEquals(expected, ev.toString());
    }

}
