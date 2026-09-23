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
package com.hyland.openarch.runtime.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.test.runner.Deploy;

public class TestResourcePublisherService extends ManagementTestCase {

    @Test
    public void testRegisterResource() {
        publisherService.registerResource("dummy", "org.nuxeo:name=dummy", DummyMBean.class, new DummyService());
        publisherService.bindResources();
        Set<ObjectName> registeredNames = doQuery("org.nuxeo:name=dummy");
        assertNotNull(registeredNames);
        assertEquals(1, registeredNames.size());
    }

    @Test
    @Deploy("com.hyland.openarch.runtime.test.tests:management-tests-service.xml")
    @Deploy("com.hyland.openarch.runtime.test.tests:management-tests-contrib.xml")
    public void testRegisterFactory() {
        Set<ObjectName> registeredNames = doQuery("org.nuxeo:name=dummy");
        assertNotNull(registeredNames);
        assertEquals(1, registeredNames.size());
    }

    @Test
    public void testServerLocator() throws JMException, InvalidTargetObjectTypeException {
        MBeanServer testServer = MBeanServerFactory.createMBeanServer("test");
        ObjectName testName = new ObjectName("test:test=test");
        publisherService.bindForTest(testServer, testName, new DummyService(), DummyMBean.class);
        publisherService.bindResources();
        MBeanServer locatedServer = locatorService.lookupServer(testName);
        assertNotNull(locatedServer);
        assertTrue(locatedServer.isRegistered(testName));
    }

    @Test
    @Deploy("com.hyland.openarch.runtime.test.tests:management-tests-service.xml")
    @Deploy("com.hyland.openarch.runtime.test.tests:management-tests-contrib.xml")
    public void testXMLConfiguration() throws Exception {
        assertEquals(4, doQuery(ObjectNameFactory.formatTypeQuery("service")).size());

        Set<String> shortcutsName = publisherService.getShortcutsName();
        assertEquals(5, shortcutsName.size());
        assertTrue(shortcutsName.contains("dummy"));
    }

}
