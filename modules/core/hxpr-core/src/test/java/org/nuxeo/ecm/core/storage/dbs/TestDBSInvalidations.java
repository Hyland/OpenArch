/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

public class TestDBSInvalidations {

    @SuppressWarnings("resource") // StringBuilderWriter doesn't need to be closed
    @Test
    public void testSerialization() throws IOException {
        DBSInvalidations invals;
        StringBuilderWriter writer;

        invals = new DBSInvalidations();
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{}", writer.toString());

        invals = new DBSInvalidations();
        invals.add("foo");
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{\"ids\":[\"foo\"]}", writer.toString());

        invals.add("bar");
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{\"ids\":[\"bar\",\"foo\"]}", writer.toString()); // non-deterministic order

        invals = new DBSInvalidations();
        invals.setAll();
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{\"all\":true}", writer.toString());

        invals = new DBSInvalidations();
        invals.add("foo");
        invals.setNodeId("node123");
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{\"ids\":[\"foo\"],\"nodeId\":\"node123\"}", writer.toString());

        invals = new DBSInvalidations();
        invals.add("foo");
        invals.setNodeId("node123");
        invals.setTimestamp(12345L);
        writer = new StringBuilderWriter();
        invals.serialize(writer);
        assertEquals("{\"ids\":[\"foo\"],\"nodeId\":\"node123\",\"timestamp\":12345}", writer.toString());
    }

    @Test
    public void testDeserialization() throws IOException {
        DBSInvalidations invals;
        ByteArrayInputStream bain;

        bain = new ByteArrayInputStream("".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertNull(invals);

        bain = new ByteArrayInputStream("x".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertNull(invals);

        bain = new ByteArrayInputStream("A".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertTrue(invals.all);

        bain = new ByteArrayInputStream(",foo".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("", "foo")), invals.ids);

        bain = new ByteArrayInputStream(",foo,bar".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("", "foo", "bar")), invals.ids);

        bain = new ByteArrayInputStream("{\"all\": \"true\"}".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertTrue(invals.all);

        bain = new ByteArrayInputStream("{\"ids\": [\"foo\",\"bar\"]}".getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertFalse(invals.all);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);

        var jsonMessage = """
                {
                    "ids": ["foo", "bar"],
                    "nodeId": "node123"
                }
                """;
        bain = new ByteArrayInputStream((jsonMessage).getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);
        assertEquals("node123", invals.getNodeId());
        assertNull(invals.getTimestamp());

        long timestamp = Instant.now().toEpochMilli();
        jsonMessage = """
                {
                    "ids": ["foo", "bar"],
                    "nodeId": "node123",
                    "timestamp": %d
                }
                """.formatted(timestamp);
        bain = new ByteArrayInputStream(jsonMessage.getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);
        assertEquals("node123", invals.getNodeId());
        assertEquals(timestamp, invals.getTimestamp().longValue());

        // Test invalid messages
        var jsonInvalidMessage = """
                {
                    "unknown": "test",
                    "ids": ["foo", "bar"]
                }
                """;
        assertThrows(UnrecognizedPropertyException.class,
                () -> DBSInvalidations.deserialize(new ByteArrayInputStream(jsonInvalidMessage.getBytes())));

        var jsonInvalidMessage2 = """
                {
                    "ids": ["foo", "bar"]
                    "nodeId": "node123"
                }
                """;
        assertThrows(StreamReadException.class,
                () -> DBSInvalidations.deserialize(new ByteArrayInputStream(jsonInvalidMessage2.getBytes())));

        var jsonInvalidMessage3 = """
                {
                    "nodeId": "test",
                    "ids": "foo"
                }
                """;
        assertThrows(MismatchedInputException.class,
                () -> DBSInvalidations.deserialize(new ByteArrayInputStream(jsonInvalidMessage3.getBytes())));
    }

    @Test
    public void testBackwardCompatibilitySerialization() throws IOException {
        DBSInvalidations invals;
        ByteArrayInputStream bain;

        var jsonMessage = """
                {
                    "ids": ["foo", "bar"],
                    "nodeId": "node123",
                    "timestamp": 12345
                }
                """;
        bain = new ByteArrayInputStream((jsonMessage).getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);
        assertEquals("node123", invals.getNodeId());
        assertEquals(12345L, invals.getTimestamp().longValue());

        var jsonMessageList = """
                {
                    "invalidations": [
                        {
                            "ids": ["foo"],
                            "nodeId": "node123",
                            "timestamp": 12345
                        },
                        {
                            "ids": ["bar"],
                            "nodeId": "node123",
                            "timestamp": 12345
                        }
                    ]
                }
                """;
        bain = new ByteArrayInputStream((jsonMessageList).getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);
        assertEquals("node123", invals.getNodeId());
        assertEquals(12345L, invals.getTimestamp().longValue());

        var jsonMessageListWithAll = """
                {
                    "invalidations": [
                        {
                            "ids": ["foo"]
                        },
                        {
                            "all": true,
                            "nodeId": "node123",
                            "timestamp": 12345
                        },
                        {
                            "ids": ["bar"],
                            "nodeId": "node123",
                            "timestamp": 12345
                        }
                    ]
                }
                """;
        bain = new ByteArrayInputStream((jsonMessageListWithAll).getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertTrue(invals.all);
        assertNull(invals.ids);

        var jsonMessageWithDifferentNodes = """
                {
                    "invalidations": [
                        {
                            "ids": ["foo"],
                            "nodeId": "node123",
                            "timestamp": 12345
                        },
                        {
                            "ids": ["bar"],
                            "nodeId": "node456",
                            "timestamp": 12345
                        }
                    ]
                }
                """;
        bain = new ByteArrayInputStream((jsonMessageWithDifferentNodes).getBytes());
        invals = DBSInvalidations.deserialize(bain);
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), invals.ids);
        assertEquals("node123", invals.getNodeId());
        assertEquals(12345L, invals.getTimestamp().longValue());
    }

    @Test
    public void testToString() {
        var invals = new DBSInvalidations(true);
        assertEquals("DBSInvalidations(all=true, nodeId=null, timestamp=null)", invals.toString());

        invals.setNodeId("node123");
        invals.setTimestamp(12345L);
        assertEquals("DBSInvalidations(all=true, nodeId=node123, timestamp=12345)", invals.toString());

        invals = new DBSInvalidations(false);
        invals.add("foo");
        invals.add("bar");
        assertEquals("DBSInvalidations(ids=[bar, foo], nodeId=null, timestamp=null)", invals.toString());

        invals.setTimestamp(12345L);
        assertEquals("DBSInvalidations(ids=[bar, foo], nodeId=null, timestamp=12345)", invals.toString());

        invals.setNodeId("node123");
        assertEquals("DBSInvalidations(ids=[bar, foo], nodeId=node123, timestamp=12345)", invals.toString());
    }
}
