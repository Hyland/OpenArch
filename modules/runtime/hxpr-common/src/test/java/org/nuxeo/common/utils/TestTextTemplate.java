/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.common.utils;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * @author sfermigier
 */
public class TestTextTemplate {

    private final String templateText = "test ${var1} and ${var2} and ${var3}";

    private final String processedText = "test value1 and value2 and value3";

    private final String templateText2 = "test ${var.decrypt.var1}"; // will value ${#var1}

    private final String processedText2 = "test value1";

    @Test
    public void testWithMap() {
        Map<String, String> testVariables = new HashMap<>();
        testVariables.put("var1", "value1");
        testVariables.put("var2", "value2");
        testVariables.put("var3", "value3");
        testVariables.put("var.decrypt.var1", "${var1}");
        TextTemplate tt = new TextTemplate(testVariables);
        assertEquals(processedText, tt.processText(templateText));
        assertEquals(processedText2, tt.processText(templateText2));
    }

    @Test
    public void testWithProperties() {
        Properties vars = new Properties();
        vars.setProperty("k1", "v1");
        TextTemplate tt = new TextTemplate(vars);

        assertEquals(vars.stringPropertyNames(), tt.getVariables().stringPropertyNames());
        assertEquals("v1", tt.getVariable("k1"));

        tt.setVariable("k2", "v2");
        String text = tt.processText("${k1}-${k2}");
        assertEquals("v1-v2", text);
    }

    @Test
    public void testSetVariables() {
        Properties vars = new Properties();
        vars.setProperty("k1", "v1");
        TextTemplate tt = new TextTemplate(vars);
        Map<String, String> map = new HashMap<>();
        map.put("k2", "v2");
        map.put("k3", "v3");
        tt.setVariables(map);
        String text = tt.processText("${k1}-${k2}-${k3}");
        assertEquals("v1-v2-v3", text);
    }

    @Test
    public void testParameterExpansion() {
        // Empty TextTemplate
        TextTemplate tt = new TextTemplate();
        assertEquals("baz", tt.processText("${foo:=baz}"));
        Properties vars = new Properties();
        vars.setProperty("foo", "bar");
        // TextTemplate with var "foo"
        tt = new TextTemplate(vars);
        assertEquals("bar", tt.processText("${foo:=baz}"));
        assertEquals("<foo>${myUnresolvedExpression}</foo>",
                tt.processText("<foo>${myUnresolvedExpression}</foo>"));
        vars.setProperty("myUnresolvedExpression", "");
        assertEquals("<foo></foo>", tt.processText("<foo>${myUnresolvedExpression}</foo>"));
    }

    @Test
    public void testParameterExpansionExtraBrace() {
        TextTemplate tt = new TextTemplate();
        // check behavior when there's another closing brace further in the line
        assertEquals("deffoo bar}", tt.processText("${foo:=deffoo} bar}"));
    }

    @Test
    public void testEscapeVariable() {
        TextTemplate templates = new TextTemplate();
        templates.setVariable("pfouh", "bar");
        assertEquals("bar${pfouh}", templates.processText("${pfouh}$${pfouh}"));
        // $$ unchanged if not in front of a {
        assertEquals("$mypass", templates.processText("$mypass"));
        assertEquals("my$pass", templates.processText("my$pass"));
        assertEquals("mypass$", templates.processText("mypass$"));
        assertEquals("$$mypass", templates.processText("$$mypass"));
        assertEquals("my$$pass", templates.processText("my$$pass"));
        assertEquals("mypass$$", templates.processText("mypass$$"));
        // $$ unchanged if used in a crypto value (cf org.nuxeo.common.codec.Crypto.CRYPTO_PATTERN)
        assertEquals("{$$CZjbsiX748UF583qkbinsQ==}", templates.processText("{$$CZjbsiX748UF583qkbinsQ==}"));
    }

    @Test
    public void testTrim() {
        TextTemplate tt = new TextTemplate();
        tt.setTrim(true);
        tt.setVariable("pfough", " bar");
        assertEquals("bar${pfough}", tt.processText("${pfough}$${pfough}"));
        tt.setVariable("pfough", "bar ");
        assertEquals("bar${pfough}", tt.processText("${pfough}$${pfough}"));
        tt.setVariable("pfough", " bar ");
        assertEquals("bar${pfough}", tt.processText("${pfough}$${pfough}"));
    }

}
