/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.ecm.core.query.sql.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

public class TestHXQLVisitorParseString {

    private static String test(String s) {
        return HXQLVisitor.parseString(s);
    }

    @Test
    public void test() {
        assertEquals("", test("\"\""));
        assertEquals("", test("''"));
        assertEquals("foo", test("'foo'"));
        assertEquals("foo", test("\"foo\""));
        assertEquals("a\\'\"\t\n\rb", test("'a\\\\\\'\\\"\\t\\n\\rb'"));
    }

    @Test
    public void testFailures() {
        for (String s : List.of( //
                "", //
                ".", //
                "..", //
                "'\"", // mismatched quotes
                "'foo\\'", // escape at end
                "'\\X'", // invalid escape
                "'\\T'" // invalid escape (even though \t is accepted)
        )) {
            try {
                test(s);
                fail("Should have failed to parse: " + s);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
    }

}
