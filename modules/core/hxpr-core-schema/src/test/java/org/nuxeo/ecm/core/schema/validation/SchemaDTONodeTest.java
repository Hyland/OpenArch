/*
 * (C) Copyright 2022 Hyland (https://www.hyland.com/) and others.
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
 *     David Edwards
 */
package org.nuxeo.ecm.core.schema.validation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.ANONYMOUS_TYPE;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class SchemaDTONodeTest {

    @Test
    public void testGetNode() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode1");

        Map<String, Object> expectedMap = Map.of("rootNode1Node1",
                Map.of("rootNode1Node2", Map.of("rootNode1Node3", "finalNode1")));
        assertEquals(expectedMap, rootNode.getNode());

        SchemaDTONode nextNode = rootNode.get("rootNode1Node1");
        expectedMap = Map.of("rootNode1Node2", Map.of("rootNode1Node3", "finalNode1"));
        assertEquals(expectedMap, nextNode.getNode());

        nextNode = nextNode.get("rootNode1Node2");
        expectedMap = Map.of("rootNode1Node3", "finalNode1");
        assertEquals(expectedMap, nextNode.getNode());

        nextNode = nextNode.get("rootNode1Node3");
        assertEquals("finalNode1", nextNode.getNode());

        final SchemaDTONode finalNode = nextNode.get("finalNode1");
        assertNull(finalNode.getNode());
        assertNull(finalNode.get("any"));

        assertNull(rootNode.get("foobar").getNode());
    }

    @Test
    public void testGetNodeComplex() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "complexRoot1");
        SchemaDTONode finalNode = rootNode.get("complexNode1");
        assertNotNull(finalNode);

        String[] expectedArr = new String[] { "finalString1", "finalString2" };
        assertArrayEquals(expectedArr, (String[]) finalNode.getNode());

        rootNode = new SchemaDTONode(testData, "complexRoot2");
        finalNode = rootNode.get("complexNode1");
        Object expectedObj = new SchemaDTONode(testData, "rootNode1");
        assertEquals(expectedObj, finalNode.getNode());
    }

    @Test
    public void testAsMap() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode1");
        Map<String, Object> expectedMap = Map.of("rootNode1Node1",
                Map.of("rootNode1Node2", Map.of("rootNode1Node3", "finalNode1")));
        assertNotNull(rootNode);
        assertEquals(expectedMap, rootNode.asMap());

        expectedMap = Map.of("rootNode1Node2", Map.of("rootNode1Node3", "finalNode1"));
        SchemaDTONode nextNode = rootNode.get("rootNode1Node1");
        assertNotNull(nextNode);
        assertEquals(expectedMap, nextNode.asMap());

        expectedMap = Map.of("rootNode1Node3", "finalNode1");
        nextNode = nextNode.get("rootNode1Node2");
        assertNotNull(nextNode);
        assertEquals(expectedMap, nextNode.asMap());

        final SchemaDTONode finalNode = nextNode.get("rootNode1Node3");
        assertNotNull(finalNode);
        assertNull(finalNode.asMap());

        assertNull(finalNode.get("finalNode1").asMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAsMapComplex() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "complexRoot1");
        Map<String, Object> expectedMap = (Map<String, Object>) testData.get("complexRoot1");
        assertNotNull(rootNode);
        assertEquals(expectedMap, rootNode.asMap());

        SchemaDTONode finalNode = rootNode.get("complexNode1");
        assertNotNull(finalNode);
        assertNull(finalNode.asMap());
    }

    @Test
    public void testAsList() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "complexRoot3");

        assertNull(rootNode.asList());

        SchemaDTONode nextNode = rootNode.get("complexNode1");

        List<String> expectedList = List.of("finalString1", "finalString2");

        assertEquals(expectedList, nextNode.asList());
    }

    @Test
    public void testGetRoot() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode2");
        assertNull(rootNode.getParentNode());

        SchemaDTONode secondNode = rootNode.get("rootNode2Node1");
        assertEquals(rootNode, secondNode.getParentNode());

        SchemaDTONode thirdNode = secondNode.get("rootNode2Node2");
        assertEquals(secondNode, thirdNode.getParentNode());

        assertEquals(rootNode, thirdNode.getParentNode().getParentNode());
    }

    @Test
    public void testGetPath() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode1");
        String expectedPath = "rootNode1";

        assertEquals(expectedPath, rootNode.getPath());

        SchemaDTONode nextNode = rootNode.get("rootNode1Node1");
        expectedPath = "rootNode1/rootNode1Node1";
        assertEquals(expectedPath, nextNode.getPath());

        nextNode = nextNode.get("rootNode1Node2");
        expectedPath = "rootNode1/rootNode1Node1/rootNode1Node2";
        assertEquals(expectedPath, nextNode.getPath());

        assertEquals(ANONYMOUS_TYPE, nextNode.createAnonymousNode().getPath());
    }

    @Test
    public void testGetKey() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode2");
        assertEquals("rootNode2", rootNode.getKey());

        SchemaDTONode nextNode = rootNode.get("rootNode2Node1");
        assertEquals("rootNode2Node1", nextNode.getKey());

        nextNode = nextNode.get("rootNode2Node2");
        assertEquals("rootNode2Node2", nextNode.getKey());

        assertEquals(ANONYMOUS_TYPE, rootNode.createAnonymousNode().getKey());
    }

    @Test
    public void testCreateAnonymousNode() {
        Map<String, Object> testData = testData();
        SchemaDTONode anonymousNode;
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode1");
        anonymousNode = rootNode.createAnonymousNode();
        assertEquals(rootNode.asMap().get("rootNode1"), anonymousNode.asMap().get(ANONYMOUS_TYPE));

        SchemaDTONode nextNode = rootNode.get("rootNode1Node1");
        anonymousNode = nextNode.createAnonymousNode();
        assertEquals(nextNode.asMap().get("rootNode1Node1"), anonymousNode.asMap().get(ANONYMOUS_TYPE));
    }

    @Test
    public void testContainsKey() {
        Map<String, Object> testData = testData();
        SchemaDTONode rootNode = new SchemaDTONode(testData, "rootNode3");

        assertTrue(rootNode.containsKey("Node1"));
        assertTrue(rootNode.containsKey("Node2"));
        assertFalse(rootNode.containsKey("fooBar"));
        assertFalse(rootNode.get("Node1").get("rootNode1Node1").get("rootNode1Node1Node1").containsKey("finalNode1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAssertEquals() throws Exception {
        SchemaDTONode rootNode1 = new SchemaDTONode(testData(), "rootNode1");
        SchemaDTONode rootNode2 = new SchemaDTONode(testData(), "rootNode1");

        assertEquals(rootNode1, rootNode1);
        assertEquals(rootNode1, rootNode2);

        // hashCode()
        assertEquals(rootNode1.hashCode(), rootNode2.hashCode());
        assertEquals(rootNode1.get("rootNode1Node1").hashCode(), rootNode2.get("rootNode1Node1").hashCode());
        assertEquals(rootNode1.get("foobar").hashCode(), rootNode2.get("foobar").hashCode());

        // parentNode == null
        assertNotEquals(rootNode1, rootNode2.get("rootNode1Node1"));
        assertNotEquals(rootNode2.get("rootNode1Node1"), rootNode1);

        // node == null
        assertNotEquals(rootNode1, rootNode2.get("foobar"));
        assertNotEquals(rootNode2.get("foobar"), rootNode1.get("rootNode1Node1"));
        assertEquals(rootNode2.get("foobar"), rootNode1.get("foobar"));

        // node != node
        SchemaDTONode node1 = new SchemaDTONode(testData(), "rootNode1");
        SchemaDTONode node2 = new SchemaDTONode(Map.of("rootNode1", "finalNode"), "rootNode1");
        assertNotEquals(node1, node2);

        // key
        node1 = new SchemaDTONode(testData(), null);
        node2 = new SchemaDTONode(testData(), null);
        assertEquals(node1, node2);
        Field node1Field = node1.getClass().getDeclaredField("node");
        node1Field.setAccessible(true);
        node1Field.set(node1, rootNode1.getNode());
        assertNotEquals(rootNode1, node1);
        assertNotEquals(node1, rootNode1);
        assertNotEquals(node1.hashCode(), rootNode1.hashCode());

        assertNotEquals(new SchemaDTONode((Map<String, Object>) testData().get("rootNode1"), "rootNode1Node1"),
                rootNode1.get("rootNode1Node1"));
        assertNotEquals("rootNode2", rootNode1);
        assertNotEquals(null, rootNode1);
    }

    private Map<String, Object> testData() {
        Map<String, Object> data = new HashMap<>();
        data.put("rootNode1",
                Map.of("rootNode1Node1", Map.of("rootNode1Node2", Map.of("rootNode1Node3", "finalNode1"))));
        data.put("rootNode2",
                Map.of("rootNode2Node1", Map.of("rootNode2Node2", Map.of("rootNode2Node3", "finalNode2"))));
        data.put("rootNode3", Map.of("Node1", Map.of("rootNode1Node1", Map.of("rootNode1Node1Node1", "finalNode1")),
                "Node2", "finalNode2"));
        data.put("complexRoot1", Map.of("complexNode1", new String[] { "finalString1", "finalString2" }));
        data.put("complexRoot2", Map.of("complexNode1", new SchemaDTONode(data, "rootNode1")));
        data.put("complexRoot3", Map.of("complexNode1", List.of("finalString1", "finalString2")));
        return data;
    }
}
