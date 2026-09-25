/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

public class TestDBSInvalidationsList {

    @SuppressWarnings("resource") // StringBuilderWriter doesn't need to be closed
    @Test
    public void testSerialization() throws Exception {
        DBSInvalidationsList invals;
        StringBuilderWriter writer;
        String actual;

        invals = new DBSInvalidationsList();
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{}", writer.toString());

        DBSInvalidations inval = new DBSInvalidations();
        inval.add("foo");
        inval.setNodeId("n1");
        inval.setTimestamp(123L);
        invals.add(inval);
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        actual = writer.toString();
        JSONAssert.assertEquals(actual, """
                {
                  "invalidations": [
                    {
                      "ids": ["foo"],
                      "nodeId": "n1",
                      "timestamp": 123
                    }
                  ]
                }
                """, actual, true);

        inval = new DBSInvalidations();
        inval.setAll();
        inval.setNodeId("n2");
        inval.setTimestamp(456L);
        invals.add(inval);
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        actual = writer.toString();
        JSONAssert.assertEquals(actual, """
                {
                  "invalidations": [
                    {
                      "ids": ["foo"],
                      "nodeId": "n1",
                      "timestamp": 123
                    },
                    {
                      "all": true,
                      "nodeId": "n2",
                      "timestamp": 456
                    }
                  ]
                }
                """, actual, true);
    }

    @Test
    public void testDeserialization() throws IOException {
        DBSInvalidationsList invals;

        invals = DBSInvalidationsList.deserialize("");
        assertNull(invals);

        var jsonMessage = """
                {
                    "invalidations": [
                        {
                            "ids": ["foo", "bar"],
                            "nodeId": "node123"
                        },
                        {
                            "all": true
                        }
                    ]
                }
                """;
        invals = DBSInvalidationsList.deserialize(jsonMessage);
        assertEquals(2, invals.getInvalidations().size());
    }

    @Test
    public void testDeserializeBackwardInvalidations() throws IOException {
        DBSInvalidationsList list;

        long timestamp = Instant.now().toEpochMilli();
        var jsonMessage = """
                {
                    "ids": ["foo", "bar"],
                    "nodeId": "node123",
                    "timestamp": %d
                }
                """.formatted(timestamp);
        list = DBSInvalidationsList.deserialize(jsonMessage);
        assertEquals(1, list.getInvalidations().size());
        var invals = list.getInvalidations().get(0);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);
        assertEquals("node123", invals.getNodeId());
        assertEquals(timestamp, invals.getTimestamp().longValue());
    }

    @Test
    public void testToString() {
        var invals = new DBSInvalidationsList();
        assertEquals("DBSInvalidationsList()", invals.toString());

        var inval = new DBSInvalidations();
        inval.setAll();
        inval.setNodeId("node123");
        inval.setTimestamp(12345L);
        invals.add(inval);
        assertEquals("DBSInvalidationsList(invalidations=[DBSInvalidations(all=true, nodeId=node123, timestamp=12345)])",
                invals.toString());

        var inval2 = new DBSInvalidations();
        inval2.add("foo");
        inval2.setNodeId("node456");
        inval2.setTimestamp(23456L);
        invals.add(inval2);
        assertEquals("DBSInvalidationsList(invalidations=[DBSInvalidations(all=true, nodeId=node123, timestamp=12345), " +
                        "DBSInvalidations(ids=[foo], nodeId=node456, timestamp=23456)])",
                invals.toString());
    }

    @Test
    public void testIfInvalidationsIsEmpty() {
        var invals = new DBSInvalidationsList();
        assertTrue(invals.isEmpty());

        var inval = new DBSInvalidations();
        inval.setAll();
        invals.add(inval);
        assertFalse(invals.isEmpty());

        invals.clear();
        assertTrue(invals.isEmpty());
    }

    @Test
    public void testAddInvalidations() {
        var invals = new DBSInvalidationsList();
        var inval1 = new DBSInvalidations();
        inval1.add("foo");
        inval1.setNodeId("n1");
        invals.add(inval1);
        assertEquals(1, invals.getInvalidations().size());

        // Test that a null invalidation will do nothing
        invals.add(null);
        assertEquals(1, invals.getInvalidations().size());

        // Test that we can merge two invalidations list
        var invals2 = new DBSInvalidationsList();
        var inval2 = new DBSInvalidations();
        inval2.add("bar");
        inval2.setNodeId("n2");
        invals2.add(inval2);
        invals.add(invals2);
        assertEquals(2, invals.getInvalidations().size());
    }

    @Test
    public void testMergeInvalidationsForSameNodeOrTimestamp() {
        var invals = new DBSInvalidationsList();
        var inval1 = new DBSInvalidations();
        inval1.add("foo");
        inval1.setNodeId("n1");
        inval1.setTimestamp(123L);
        invals.add(inval1);
        assertEquals(1, invals.getInvalidations().size());

        var inval2 = new DBSInvalidations();
        inval2.setNodeId("n1");
        inval2.setTimestamp(123L);
        inval2.add("bar");
        invals.add(inval2); // same node and timestamp
        assertEquals(1, invals.getInvalidations().size()); // has been merged

        var inval3 = new DBSInvalidations();
        inval3.setNodeId("n2"); // different node id
        inval3.setTimestamp(123L);
        inval3.add("baz");
        invals.add(inval3);
        assertEquals(2, invals.getInvalidations().size()); // not merged

        var inval4 = new DBSInvalidations();
        inval4.setNodeId("n1");
        inval4.setTimestamp(456L); // different timestamp
        inval4.add("qux");
        invals.add(inval4);
        assertEquals(3, invals.getInvalidations().size()); // not merged either

        var inval5 = new DBSInvalidations(true); // all
        inval5.setNodeId("n1");
        inval5.setTimestamp(123L);
        invals.add(inval5);
        assertEquals(3, invals.getInvalidations().size()); // has been merged with first
    }

    @Test
    public void testClearInvalidations() {
        var invals = new DBSInvalidationsList();
        var inval1 = new DBSInvalidations();
        inval1.add("foo");
        inval1.setNodeId("n1");
        var inval2 = new DBSInvalidations();
        inval2.add("bar");
        inval2.setNodeId("n2");
        invals.add(inval1);
        invals.add(inval2);
        assertEquals(2, invals.getInvalidations().size());

        invals.clear();
        assertEquals(0, invals.getInvalidations().size());
    }

    @Test
    public void testMinimizeDBSInvalidations() {
        var inval = new DBSInvalidations();
        inval.add("foo");
        inval.add("bar");
        inval.setNodeId("123");
        inval.setTimestamp(456L);
        var minimized = inval.minimize();
        assertTrue(minimized.all);
        assertEquals("123", minimized.getNodeId());
        assertEquals(456L, minimized.getTimestamp());
    }

    @Test
    public void testMinimizeDBSInvalidationsList() {
        var invals = new DBSInvalidationsList();
        var inval1 = new DBSInvalidations();
        inval1.add("foo");
        inval1.setNodeId("n1");
        var inval2 = new DBSInvalidations();
        inval2.add("bar");
        inval2.setNodeId("n2");
        var inval3 = new DBSInvalidations(true);
        invals.add(inval1);
        invals.add(inval2);
        invals.add(inval3);
        var minimized = invals.minimize();
        var list = minimized.getInvalidations();
        assertEquals(3, list.size());
        assertTrue(list.get(0).all);
        assertTrue(list.get(1).all);
        assertTrue(list.get(2).all);
    }

}
