/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.nxql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestTextSearchCleaner {

    @Test
    public void testCleaner() throws Exception {
        assertEquals("= 'a'", NXQLQueryBuilder.serializeFullText("a"));
        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a b"));
        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText(" a b "));
        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a  b"));
        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a & b"));
        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a : b"));
        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a | b"));

        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a { b"));

        assertEquals("= 'a b c d e f'", NXQLQueryBuilder.serializeFullText("a#b|c  d+e*f"));
        assertEquals("= '\"a b\"'", NXQLQueryBuilder.serializeFullText("\"a b\""));
        assertEquals("= '\"a b\"'", NXQLQueryBuilder.serializeFullText("\"a-b\""));
        assertEquals("= '\"a -b \"'", NXQLQueryBuilder.serializeFullText("\"a -b \""));

        assertEquals("= 'a* b'", NXQLQueryBuilder.serializeFullText("a* b-"));

        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a*b"));

        assertEquals("= 'a  b'", NXQLQueryBuilder.serializeFullText("a*-b"));

        assertEquals("= 'a -b'", NXQLQueryBuilder.serializeFullText("*a -b"));

        assertEquals("= 'a -bc*'", NXQLQueryBuilder.serializeFullText("a | -bc*"));

        assertEquals("= 'a b'", NXQLQueryBuilder.serializeFullText("a !#$%&()*+,-./:;<=>?@^`{|}~ b"));

        // raw sanitizeFulltextInput API that does not wrap the input with the
        // quote and the predicate operator
        assertEquals("some stuff", NXQLQueryBuilder.sanitizeFulltextInput("some & stuff\\"));

        // test negative queries
        assertEquals("= 'a -b'", NXQLQueryBuilder.serializeFullText("a !#$%&()*+,-./:;<=>?@^`{|}~ -b"));
    }

}
