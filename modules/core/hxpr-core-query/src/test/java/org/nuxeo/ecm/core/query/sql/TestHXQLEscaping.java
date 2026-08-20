/*
 * (C) Copyright 2013-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestHXQLEscaping {

    @Test
    public void testEscapeString() throws Exception {
        assertEquals("", HXQL.escapeStringInner(""));
        assertEquals("a\\'b", HXQL.escapeStringInner("a'b"));
        assertEquals("a\\\\b", HXQL.escapeStringInner("a\\b"));
        assertEquals("a\\\\\\'b", HXQL.escapeStringInner("a\\'b"));
        assertEquals("a\\nb", HXQL.escapeStringInner("a\nb"));
        assertEquals("a\\rb", HXQL.escapeStringInner("a\rb"));
        assertEquals("a\\r\\nb", HXQL.escapeStringInner("a\r\nb"));

        assertEquals("''", HXQL.escapeString(""));
        assertEquals("'a\\'b'", HXQL.escapeString("a'b"));
        assertEquals("'a\\\\b'", HXQL.escapeString("a\\b"));
        assertEquals("'a\\\\\\'b'", HXQL.escapeString("a\\'b"));
        assertEquals("'a\\nb'", HXQL.escapeString("a\nb"));
        assertEquals("'a\\rb'", HXQL.escapeString("a\rb"));
    }

}
