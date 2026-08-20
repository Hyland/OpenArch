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
package org.nuxeo.rest.query.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.query.sql.NXQLSerializer;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.rest.query.io.WhereClauseDTO.RestPredicate;
import org.nuxeo.rest.query.io.WhereClauseDTO.RestPredicateParameter;

public class PredicateBuilderTest {

    /** The predicate built for the given operator and parameter values, or null if it was dropped. */
    protected static String build(RestPredicate predicate, Map<String, Object> parameters) {
        Optional<Predicate> built = PredicateBuilder.build(predicate, parameters);
        return built.map(NXQLSerializer::serialize).orElse(null);
    }

    protected static String build(String operator, Object value) {
        return build(RestPredicate.of(SYS_TITLE, operator, "p"), value == null ? Map.of() : Map.of("p", value));
    }

    @Test
    public void testComparisons() {
        assertEquals("sys_title = 'a'", build("=", "a"));
        assertEquals("sys_title <> 'a'", build("!=", "a"));
        assertEquals("sys_title <> 'a'", build("<>", "a"));
        assertEquals("sys_title < 'a'", build("<", "a"));
        assertEquals("sys_title >= 'a'", build(">=", "a"));
        assertEquals("sys_title LIKE 'a%'", build("LIKE", "a%"));
        assertEquals("sys_title NOT ILIKE 'a%'", build("NOT ILIKE", "a%"));
        // the operator name is case insensitive
        assertEquals("sys_title LIKE 'a%'", build("like", "a%"));
    }

    /**
     * A value keeps the type it arrived with, so a number is a number and a string is a string. It is the backend that
     * coerces to the field's real type.
     */
    @Test
    public void testValueTypes() {
        assertEquals("sys_title = 3", build("=", 3));
        assertEquals("sys_title = 3", build("=", 3L));
        assertEquals("sys_title = 3.5", build("=", 3.5));
        assertEquals("sys_title = '3'", build("=", "3"));
        assertEquals("sys_title = TRUE", build("=", Boolean.TRUE));
        assertEquals("sys_title = TIMESTAMP '2020-01-02T03:04:05.000Z'",
                build("=", Instant.parse("2020-01-02T03:04:05Z")));
    }

    /** A value that was not supplied drops the predicate, so one definition can serve a partly-filled search form. */
    @Test
    public void testMissingValueDropsPredicate() {
        assertEquals(null, build("=", null));
        assertEquals(null, build("=", ""));
        assertEquals(null, build("=", "   "));
        assertEquals(null, build("LIKE", null));
        assertEquals(null, build("IS NULL", null));
        assertEquals(null, build("FULLTEXT", null));
    }

    @Test
    public void testStaticValue() {
        // a predicate with no parameter name carries its value inline
        assertEquals("sys_title = 'File3'", build(RestPredicate.ofStatic(SYS_TITLE, "=", "File3"), Map.of()));
    }

    @Test
    public void testDefaultValue() {
        RestPredicate predicate = new RestPredicate(SYS_TITLE, "=", "p", "fallback");
        assertEquals("sys_title = 'fallback'", build(predicate, Map.of()));
        assertEquals("sys_title = 'supplied'", build(predicate, Map.of("p", "supplied")));
    }

    @Test
    public void testIsNull() {
        assertEquals("sys_title IS NULL", build("IS NULL", Boolean.TRUE));
        assertEquals("sys_title IS NOT NULL", build("IS NULL", Boolean.FALSE));
        // a non-boolean value is not a decision either way
        assertEquals(null, build("IS NULL", "yes"));
    }

    @Test
    public void testEmpty() {
        assertEquals("sys_title = ''", build("EMPTY", null));
        assertEquals("sys_title = ''", build("ISEMPTY", null));
    }

    @Test
    public void testFulltext() {
        // a plain field name is qualified with sys_fulltext
        assertEquals("sys_fulltext.sys_title = 'a'", build("FULLTEXT", "a"));
        assertEquals("sys_fulltext.sys_title = 'a'", build("FULLTEXT ALL", "a"));
        // one that already is a full-text field is left alone
        assertEquals("sys_fulltext = 'a'",
                build(RestPredicate.of("sys_fulltext", "FULLTEXT", "p"), Map.of("p", "a")));
        assertEquals("sys_fulltext.sys_description = 'a'",
                build(RestPredicate.of("sys_fulltext.sys_description", "FULLTEXT", "p"), Map.of("p", "a")));
        // full-text operators are stripped from the term
        assertEquals("sys_fulltext.sys_title = 'a b'", build("FULLTEXT", "a & b"));
        assertEquals("sys_fulltext.sys_title = 'a* b'", build("FULLTEXT", "a* b-"));
    }

    @Test
    public void testIn() {
        assertEquals("sys_title IN ('a', 'b')", build("IN", List.of("a", "b")));
        assertEquals("sys_title IN ('a', 'b')", build("IN", new String[] { "a", "b" }));
        assertEquals("sys_title IN (1, 2, 3)", build("IN", new Integer[] { 1, 2, 3 }));
        assertEquals("sys_title IN (1, 2, 3)", build("IN", List.of(1L, 2L, 3L)));
        // a one-element list is an equality
        assertEquals("sys_title = 'a'", build("IN", List.of("a")));
        assertEquals("sys_title = 1", build("IN", new Integer[] { 1 }));
        // an empty list drops the predicate
        assertEquals(null, build("IN", List.of()));
        assertEquals(null, build("IN", new String[0]));
    }

    @Test
    public void testNotIn() {
        assertEquals("sys_title NOT IN ('a', 'b')", build("NOT IN", List.of("a", "b")));
        assertEquals("sys_title <> 'a'", build("NOT IN", List.of("a")));
        assertEquals(null, build("NOT IN", List.of()));
    }

    @Test
    public void testBetween() {
        assertEquals("sys_title BETWEEN 1 AND 3",
                build(new RestPredicate(SYS_TITLE, "BETWEEN",
                        List.of(RestPredicateParameter.of("min"), RestPredicateParameter.of("max"))),
                        Map.of("min", 1, "max", 3)));
        // an open-ended range is a plain comparison
        assertEquals("sys_title >= 1",
                build(new RestPredicate(SYS_TITLE, "BETWEEN",
                        List.of(RestPredicateParameter.of("min"), RestPredicateParameter.of("max"))),
                        Map.of("min", 1)));
        assertEquals("sys_title <= 3",
                build(new RestPredicate(SYS_TITLE, "BETWEEN",
                        List.of(RestPredicateParameter.of("min"), RestPredicateParameter.of("max"))),
                        Map.of("max", 3)));
        assertEquals(null,
                build(new RestPredicate(SYS_TITLE, "BETWEEN",
                        List.of(RestPredicateParameter.of("min"), RestPredicateParameter.of("max"))), Map.of()));
    }

    @Test
    public void testStartsWith() {
        assertEquals("sys_title STARTSWITH '/a'", build("STARTSWITH", "/a"));
        // STARTSWITH takes a single prefix, so several of them are a disjunction
        assertEquals("sys_title STARTSWITH '/a' OR sys_title STARTSWITH '/b'",
                build("STARTSWITH", List.of("/a", "/b")));
        assertEquals("sys_title STARTSWITH '/a'", build("STARTSWITH", List.of("/a")));
        assertEquals(null, build("STARTSWITH", List.of()));
    }

    /** The operator itself can come from a request parameter, which is how a search form lets the user pick one. */
    @Test
    public void testOperatorFromParameter() {
        RestPredicate predicate = new RestPredicate(SYS_TITLE, ":op", List.of(RestPredicateParameter.of("p")));
        assertEquals("sys_title LIKE 'a'", build(predicate, Map.of("op", "LIKE", "p", "a")));
        assertEquals("sys_title = 'a'", build(predicate, Map.of("op", "=", "p", "a")));
        var e = assertThrows(NuxeoException.class, () -> build(predicate, Map.of("p", "a")));
        assertTrue(e.getMessage().contains("No operator supplied"), e.getMessage());
    }

    @Test
    public void testUnknownOperator() {
        var e = assertThrows(NuxeoException.class, () -> build("SOUNDSLIKE", "a"));
        assertEquals("Unsupported predicate operator: SOUNDSLIKE", e.getMessage());
        assertEquals(400, e.getStatusCode());
    }

    /** A value cannot smuggle syntax in, whatever it contains. */
    @Test
    public void testValuesCannotInjectSyntax() {
        assertEquals("sys_title = 'x\\' OR sys_title LIKE \\'%'", build("=", "x' OR sys_title LIKE '%"));
        assertEquals("sys_title IN ('a\\'b', 'c')", build("IN", List.of("a'b", "c")));
    }

    @Test
    public void testBuildAll() {
        List<RestPredicate> predicates = List.of( //
                RestPredicate.of(SYS_TITLE, "LIKE", "title"), //
                RestPredicate.of("sys_modified", "IS NULL", "isPresent"), //
                RestPredicate.of("mydc_subjects", "IN", "subjects"));
        // only the predicates whose values were supplied survive
        assertEquals(List.of("sys_title LIKE 'bar'"),
                PredicateBuilder.build(predicates, Map.of("title", "bar"))
                                .stream()
                                .map(NXQLSerializer::serialize)
                                .toList());
        assertEquals(List.of("sys_title LIKE 'bar'", "sys_modified IS NULL", "mydc_subjects IN ('a', 'b')"),
                PredicateBuilder.build(predicates,
                        Map.of("title", "bar", "isPresent", Boolean.TRUE, "subjects", List.of("a", "b")))
                                .stream()
                                .map(NXQLSerializer::serialize)
                                .toList());
    }

}
