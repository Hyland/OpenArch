/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.Statement;

/**
 * @since 12.0
 */
public class TestQueryParameterBinder {

    protected static String bind(String query, Map<String, Object> parameters) {
        return HXQLSerializer.serialize(QueryParameterBinder.bind(HXQLParser.parse(query), parameters));
    }

    protected static void check(String expected, String query, Map<String, Object> parameters) {
        assertEquals(expected, bind(query, parameters));
    }

    @Test
    public void testScalar() {
        check("SELECT * FROM SysContent WHERE sys_title = 'File3'",
                "SELECT * FROM SysContent WHERE sys_title = :byTitle", Map.of("byTitle", "File3"));
        check("SELECT * FROM SysContent WHERE size = 3", "SELECT * FROM SysContent WHERE size = :size",
                Map.of("size", 3));
        check("SELECT * FROM SysContent WHERE size = 3.5", "SELECT * FROM SysContent WHERE size = :size",
                Map.of("size", 3.5));
        check("SELECT * FROM SysContent WHERE sysver_isVersion = TRUE",
                "SELECT * FROM SysContent WHERE sysver_isVersion = :v", Map.of("v", Boolean.TRUE));
        check("SELECT * FROM SysContent WHERE sys_created = TIMESTAMP '2020-01-02T03:04:05.000Z'",
                "SELECT * FROM SysContent WHERE sys_created = :d",
                Map.of("d", Instant.parse("2020-01-02T03:04:05Z")));
    }

    @Test
    public void testParameterOnTheLeft() {
        // the old textual substitution could not see a parameter in first position
        check("SELECT * FROM SysContent WHERE 'File3' = sys_title",
                "SELECT * FROM SysContent WHERE :byTitle = sys_title", Map.of("byTitle", "File3"));
    }

    @Test
    public void testSameParameterTwice() {
        check("SELECT * FROM SysContent WHERE sys_title = 'a' OR sys_name = 'a'",
                "SELECT * FROM SysContent WHERE sys_title = :n OR sys_name = :n", Map.of("n", "a"));
    }

    @Test
    public void testList() {
        check("SELECT * FROM SysContent WHERE sys_title IN ('a', 'b')",
                "SELECT * FROM SysContent WHERE sys_title IN :subjects", Map.of("subjects", List.of("a", "b")));
        check("SELECT * FROM SysContent WHERE sys_title NOT IN ('a', 'b')",
                "SELECT * FROM SysContent WHERE sys_title NOT IN :subjects",
                Map.of("subjects", new String[] { "a", "b" }));
        // a single value is a list of one
        check("SELECT * FROM SysContent WHERE sys_title IN ('a')",
                "SELECT * FROM SysContent WHERE sys_title IN :subjects", Map.of("subjects", "a"));
        // mixed literals and parameters
        check("SELECT * FROM SysContent WHERE sys_title IN ('a', 'lit')",
                "SELECT * FROM SysContent WHERE sys_title IN (:a, 'lit')", Map.of("a", "a"));
    }

    @Test
    public void testBetween() {
        check("SELECT * FROM SysContent WHERE size BETWEEN 1 AND 3",
                "SELECT * FROM SysContent WHERE size BETWEEN :min AND :max", Map.of("min", 1, "max", 3));
        check("SELECT * FROM SysContent WHERE size BETWEEN 1 AND 3",
                "SELECT * FROM SysContent WHERE size BETWEEN 1 AND :max", Map.of("max", 3));
    }

    @Test
    public void testLikeAndStartsWith() {
        check("SELECT * FROM SysContent WHERE sys_title LIKE 'a%'",
                "SELECT * FROM SysContent WHERE sys_title LIKE :pat", Map.of("pat", "a%"));
        check("SELECT * FROM SysContent WHERE sys_path STARTSWITH '/a/b'",
                "SELECT * FROM SysContent WHERE sys_path STARTSWITH :p", Map.of("p", "/a/b"));
    }

    /**
     * Values are bound into the tree, so nothing a value contains can be read as syntax. The textual substitution this
     * replaces would have turned the first case into an extra {@code OR} clause and substituted inside the literal in
     * the second and third.
     */
    @Test
    public void testValuesCannotInjectSyntax() {
        check("SELECT * FROM SysContent WHERE sys_title = 'x\\' OR sys_title LIKE \\'%'",
                "SELECT * FROM SysContent WHERE sys_title = :byTitle",
                Map.of("byTitle", "x' OR sys_title LIKE '%"));
        // a quoted occurrence of the parameter name is text, not a parameter
        check("SELECT * FROM SysContent WHERE sys_title = ':byTitle' AND sys_name = 'zz'",
                "SELECT * FROM SysContent WHERE sys_title = ':byTitle' AND sys_name = :byTitle",
                Map.of("byTitle", "zz"));
        // a value that looks like a parameter is not substituted again
        check("SELECT * FROM SysContent WHERE sys_title = ':b' AND sys_name = 'BEE'",
                "SELECT * FROM SysContent WHERE sys_title = :a AND sys_name = :b",
                Map.of("a", ":b", "b", "BEE"));
    }

    @Test
    public void testMissingParameter() {
        var e = assertThrows(QueryParseException.class,
                () -> bind("SELECT * FROM SysContent WHERE sys_title = :byTitle", Map.of()));
        assertEquals("No value for query parameter :byTitle", e.getMessage());
        // an explicit null is treated as missing
        Map<String, Object> nullValued = new HashMap<>();
        nullValued.put("byTitle", null);
        assertThrows(QueryParseException.class,
                () -> bind("SELECT * FROM SysContent WHERE sys_title = :byTitle", nullValued));
    }

    /** A null element inside a list parameter is a bad request, not an internal error. */
    @Test
    public void testNullInListParameter() {
        String query = "SELECT * FROM SysContent WHERE sys_title IN :subjects";
        var e = assertThrows(QueryParseException.class,
                () -> bind(query, Map.of("subjects", Arrays.asList("a", null))));
        assertEquals("Null value in query parameter :subjects", e.getMessage());
        // an array is rejected the same way
        var arrayError = assertThrows(QueryParseException.class,
                () -> bind(query, Map.of("subjects", new String[] { "a", null })));
        assertEquals("Null value in query parameter :subjects", arrayError.getMessage());
    }

    @Test
    public void testListWhereScalarExpected() {
        var e = assertThrows(QueryParseException.class,
                () -> bind("SELECT * FROM SysContent WHERE sys_title = :p", Map.of("p", List.of("a", "b"))));
        assertEquals("Parameter :p is a list, but a single value is expected here", e.getMessage());
    }

    @Test
    public void testUnsupportedValueType() {
        assertThrows(QueryParseException.class,
                () -> bind("SELECT * FROM SysContent WHERE sys_title = :p", Map.of("p", Map.of("a", "b"))));
    }

    @Test
    public void testNoParameters() {
        check("SELECT * FROM SysContent WHERE sys_title = 'a' ORDER BY sys_title",
                "SELECT * FROM SysContent WHERE sys_title = 'a' ORDER BY sys_title", Map.of());
        check("SELECT * FROM SysContent", "SELECT * FROM SysContent", null);
    }

    @Test
    public void testPreservesOtherClauses() {
        check("SELECT sys_title FROM SysContent WHERE a = 'x' ORDER BY sys_title DESC",
                "SELECT sys_title FROM SysContent WHERE a = :p ORDER BY sys_title DESC", Map.of("p", "x"));
    }

    /** Parsing with values binds them, which is how a query holding parameters is made executable. */
    @Test
    public void testParseBinds() {
        Statement query = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_title = :p AND a IN :values",
                Map.of("p", "File3", "values", List.of(1, 2)));
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'File3' AND a IN (1, 2)",
                HXQLSerializer.serialize(query));
        assertThrows(QueryParseException.class,
                () -> HXQLParser.parse("SELECT * FROM SysContent WHERE sys_title = :p", Map.of()));
    }

}
