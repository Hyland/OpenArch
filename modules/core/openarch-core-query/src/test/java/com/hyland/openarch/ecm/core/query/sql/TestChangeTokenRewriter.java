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
package com.hyland.openarch.ecm.core.query.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.QueryParseException;

/**
 * Tests that HXQL queries on {@code sys_changeToken} are rewritten into queries on the underlying generations.
 */
public class TestChangeTokenRewriter {

    protected static void check(String expected, String query) {
        assertEquals(expected, HXQLSerializer.serialize(HXQLParser.parse(query)));
    }

    @Test
    public void testEq() {
        check("SELECT * FROM SysContent WHERE sys_gen = 3 AND sys_ugen = 2",
                "SELECT * FROM SysContent WHERE sys_changeToken = '3-2'");
    }

    @Test
    public void testNotEq() {
        check("SELECT * FROM SysContent WHERE sys_gen <> 3 OR sys_ugen <> 2",
                "SELECT * FROM SysContent WHERE sys_changeToken <> '3-2'");
    }

    @Test
    public void testIn() {
        var e = assertThrows(QueryParseException.class,
                () -> HXQLParser.parse("SELECT * FROM SysContent WHERE sys_changeToken IN ('3-2', '5-0')"));
        assertEquals("Operator IN cannot be used on sys_changeToken", e.getMessage());
    }

    @Test
    public void testNotIn() {
        var e = assertThrows(QueryParseException.class,
                () -> HXQLParser.parse("SELECT * FROM SysContent WHERE sys_changeToken NOT IN ('3-2', '5-0')"));
        assertEquals("Operator NOT IN cannot be used on sys_changeToken", e.getMessage());
    }

    @Test
    public void testNested() {
        check("SELECT * FROM SysContent WHERE sys_title = 'foo' AND (sys_gen = 3 AND sys_ugen = 2)",
                "SELECT * FROM SysContent WHERE sys_title = 'foo' AND sys_changeToken = '3-2'");
    }

    @Test
    public void testUnchanged() {
        var predicate = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_title = 'foo' AND sys_name = 'bar'")
                                  .getWhereClause()
                                  .predicate();
        assertSame(predicate, ChangeTokenRewriter.rewrite(predicate));
    }

    @Test
    public void testParameter() {
        assertEquals("SELECT * FROM SysContent WHERE sys_gen = 3 AND sys_ugen = 2",
                HXQLSerializer.serialize(
                        HXQLParser.parse("SELECT * FROM SysContent WHERE sys_changeToken = :ct", Map.of("ct", "3-2"))));
    }

    @Test
    public void testInvalidOperator() {
        var e = assertThrows(QueryParseException.class,
                () -> HXQLParser.parse("SELECT * FROM SysContent WHERE sys_changeToken > '3-2'"));
        assertEquals("Operator > cannot be used on sys_changeToken", e.getMessage());
    }

    @Test
    public void testInvalidToken() {
        var e = assertThrows(QueryParseException.class,
                () -> HXQLParser.parse("SELECT * FROM SysContent WHERE sys_changeToken = 'foo'"));
        assertEquals("Invalid change token: 'foo'", e.getMessage());
    }

    @Test
    public void testNonStringToken() {
        var e = assertThrows(QueryParseException.class,
                () -> HXQLParser.parse("SELECT * FROM SysContent WHERE sys_changeToken = 3"));
        assertEquals("Expected a change token value, got: 3", e.getMessage());
    }
}
