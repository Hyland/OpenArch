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

package org.nuxeo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.RuntimeMessage.Level;
import org.nuxeo.runtime.RuntimeMessage.Source;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class RuntimeInitializationTest {

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:MyComp1.xml")
    @Deploy("org.nuxeo.runtime.test.tests:MyComp2.xml")
    public void testContributions() { // NOSONAR (no need to assert anything)
        // do nothing
    }

    protected void checkDupe(boolean detected) {
        List<String> errors = Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR);
        List<RuntimeMessage> errorMessages = Framework.getRuntime().getMessageHandler().getRuntimeMessages(Level.ERROR);
        if (detected) {
            assertEquals(List.of("Duplicate component name: my.comp2"), errors);
            assertEquals(List.of(Source.COMPONENT),
                    errorMessages.stream().map(RuntimeMessage::getSource).toList());
            assertEquals(List.of("my.comp2"),
                    errorMessages.stream().map(RuntimeMessage::getSourceId).toList());
        } else {
            assertTrue(errors.isEmpty());
        }
    }

    protected void checkErrorSource(Source source, String sourceId) {
        List<RuntimeMessage> errors = Framework.getRuntime().getMessageHandler().getRuntimeMessages(Level.ERROR);
        assertFalse(errors.isEmpty());
        errors.forEach(e -> assertEquals(source, e.getSource()));
        errors.forEach(e -> assertEquals(sourceId, e.getSourceId()));
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:MyComp1.xml")
    @Deploy("org.nuxeo.runtime.test.tests:MyComp2.xml")
    @Deploy("org.nuxeo.runtime.test.tests:CopyOfMyComp2.xml")
    public void testContributionsWithDuplicateComponent() {
        checkDupe(true);
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:MyComp1.xml")
    @Deploy("org.nuxeo.runtime.test.tests:MyComp2.xml")
    @Deploy("org.nuxeo.runtime.test.tests:MyComp2.xml")
    public void testContributionsWithDuplicateComponentSameFile() {
        // dupe annotation on tests not detected (?)
        checkDupe(false);
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:invalid-component-activate.xml")
    public void testInvalidComponentActivate() {
        assertEquals(List.of(
                "Failed to activate component: org.nuxeo.runtime.RuntimeInitializationTestComponent (java.lang.RuntimeException: Fail on activate)"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR));
        checkErrorSource(Source.COMPONENT, "invalid.comp.activate");
        assertTrue(Framework.getRuntime().getMessageHandler().getMessages(Level.WARNING).isEmpty());
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:invalid-component-activate-message.xml")
    public void testInvalidComponentActivateMessage() {
        assertEquals(List.of("Error message on activate"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR));
        checkErrorSource(Source.COMPONENT, "invalid.comp.activate.message");
        assertEquals(List.of("Warn message on activate"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.WARNING));
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:invalid-component.xml")
    public void testInvalidComponent() {
        assertEquals(List.of(
                "Bad extension declaration (no target attribute specified) on component 'invalid.comp'",
                "Warning: target extension point 'xp' of 'invalid.comp' is unknown. Check your extension in component invalid.comp",
                "Warning: target extension point 'null' of 'invalid.comp' is unknown. Check your extension in component invalid.comp"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR));
        checkErrorSource(Source.EXTENSION, "invalid.comp");
        assertTrue(Framework.getRuntime().getMessageHandler().getMessages(Level.WARNING).isEmpty());
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:invalid-component-class.xml")
    public void testInvalidComponentClass() {
        assertEquals(List.of(
                "Failed to instantiate component: org.nuxeo.runtime.Foo (org.nuxeo.runtime.RuntimeServiceException: java.lang.ClassNotFoundException: org.nuxeo.runtime.Foo)"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR));
        checkErrorSource(Source.COMPONENT, "invalid.comp.class");
        assertTrue(Framework.getRuntime().getMessageHandler().getMessages(Level.WARNING).isEmpty());
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:invalid-component-start.xml")
    public void testInvalidComponentStart() {
        assertEquals(List.of(
                "Component invalid.comp.start notification of application started failed: Fail on start"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR));
        checkErrorSource(Source.COMPONENT, "invalid.comp.start");
        assertTrue(Framework.getRuntime().getMessageHandler().getMessages(Level.WARNING).isEmpty());
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:invalid-component-start-message.xml")
    public void testInvalidComponentStartMessage() {
        assertEquals(List.of("Error message on start"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.ERROR));
        checkErrorSource(Source.COMPONENT, "invalid.comp.start.message");
        assertEquals(List.of("Warn message on start"),
                Framework.getRuntime().getMessageHandler().getMessages(Level.WARNING));
    }

    /**
     * @since 11.3
     */
    @Test
    @Order(Order.DEFAULT + 1) // run last
    @Deploy("org.nuxeo.runtime.test.tests")
    public void testInvalidBundle() throws Exception {
        List<RuntimeMessage> messages = Framework.getRuntime().getMessageHandler().getRuntimeMessages(Level.ERROR);
        assertEquals(4, messages.size());

        RuntimeMessage message = messages.get(0);
        assertTrue(message.getMessage().startsWith("Could not resolve registration from file:"));
        assertTrue(message.getMessage()
                          .endsWith("invalid-xml.xml (org.xml.sax.SAXParseException; lineNumber: 1; columnNumber: 2; "
                                  + "The markup in the document preceding the root element must be well-formed.)"));
        assertEquals(Level.ERROR, message.getLevel());
        assertEquals(Source.BUNDLE, message.getSource());
        assertEquals("org.nuxeo.runtime.test.tests", message.getSourceId());

        message = messages.get(1);
        assertTrue(message.getMessage().startsWith("Could not resolve registration from file:"));
        assertTrue(message.getMessage().endsWith("invalid-xml-other.xml)"));
        assertEquals(Level.ERROR, message.getLevel());
        assertEquals(Source.BUNDLE, message.getSource());
        assertEquals("org.nuxeo.runtime.test.tests", message.getSourceId());

        message = messages.get(2);
        assertEquals("Unknown component 'invalid-file.xml' referenced by bundle 'org.nuxeo.runtime.test.tests'",
                message.getMessage());
        assertEquals(Level.ERROR, message.getLevel());
        assertEquals(Source.BUNDLE, message.getSource());
        assertEquals("org.nuxeo.runtime.test.tests", message.getSourceId());

        message = messages.get(3);
        assertTrue(message.getMessage().startsWith("Could not resolve registration from file:"));
        assertTrue(
                message.getMessage()
                       .endsWith(
                               "invalid-xml-missing-component.xml (Expected \"<component>\" tag for component registration, "
                                       + "resolved object 'ExtensionImpl {target: my.comp, point:xp, contributor:null}' instead.)"));
        assertEquals(Level.ERROR, message.getLevel());
        assertEquals(Source.BUNDLE, message.getSource());
        assertEquals("org.nuxeo.runtime.test.tests", message.getSourceId());
    }

}
