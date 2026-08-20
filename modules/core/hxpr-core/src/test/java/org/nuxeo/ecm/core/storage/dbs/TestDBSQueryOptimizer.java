/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.storage.QueryOptimizer;
import org.nuxeo.ecm.core.storage.QueryAnnotations;
import org.nuxeo.ecm.core.storage.QueryAnnotations.PrefixInfo;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class TestDBSQueryOptimizer {

    protected DBSQueryOptimizer getOptimizer() {
        return new DBSQueryOptimizer(null) {
            // overriding these avoids the use of a SchemaManager in these tests
            @Override
            protected Set<String> getDocumentTypeNamesForMixin(String mixin) {
                return Collections.emptySet();
            }

            @Override
            protected Set<String> getDocumentTypeNamesExtending(String typeName) {
                return Collections.singleton(typeName);
            }

            @Override
            protected boolean isTypeRelation(String typeName) {
                return false;
            }
        };
    }

    @Test
    public void testGetCorrelatedWildcardPrefix() {
        assertEquals("", getCorrelatedWildcardPrefix("foo"));
        assertEquals("", getCorrelatedWildcardPrefix("foo:bar"));
        assertEquals("", getCorrelatedWildcardPrefix("foo/bar"));
        assertEquals("", getCorrelatedWildcardPrefix("foo/*"));
        assertEquals("", getCorrelatedWildcardPrefix("foo/*/bar"));
        assertEquals("", getCorrelatedWildcardPrefix("foo/*/bar/*9/baz"));
        assertEquals("", getCorrelatedWildcardPrefix("foo/*123"));
        assertEquals("foo/*123", getCorrelatedWildcardPrefix("foo/*123/bar"));
        assertEquals("foo/*123", getCorrelatedWildcardPrefix("foo/*123/bar/*"));
        assertEquals("foo/*123", getCorrelatedWildcardPrefix("foo/*123/bar/*/baz"));
        assertEquals("foo/*123", getCorrelatedWildcardPrefix("foo/*123/bar/*456/baz"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/principal"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/permission"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/grant"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/creator"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/begin"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/end"));
        assertEquals("sys_acl/*1", getCorrelatedWildcardPrefix("sys_acl/*1/status"));
    }

    protected String getCorrelatedWildcardPrefix(String name) {
        return getOptimizer().getCorrelatedWildcardPrefix(name);
    }

    @Test
    public void testReferencePrefixAnalyzer() {
        checkPrefixInfo("foo = 'abc'", "", 0);
        checkPrefixInfo("foo/*1/gee = 'abc'", "foo/*1", 1);
        checkPrefixInfo("foo/*1/gee = 'abc' AND foo/*1/bar = 'def'", "foo/*1", 2);
        checkPrefixInfo("foo/*1/gee = 'abc' AND foo/*2/bar = 'def'", "", 0);
        checkPrefixInfo("(foo/*1/gee = 'abc' OR foo/*1/bar = 'def') AND (foo/*1/gee = 'aaa' OR foo/*1/bar = 'ddd')",
                "foo/*1", 4);
        checkPrefixInfo("(foo/*1/gee = 'abc' OR foo/*1/bar = 'def') AND (foo/*666/gee = 'aaa' OR foo/*1/bar = 'ddd')",
                "", 0);
        checkPrefixInfo("(foo/*1/gee = 'abc' OR foo/*1/bar = 'def') AND (toto = 'aaa' OR foo/*1/bar = 'ddd')", "", 0);
    }

    protected void checkPrefixInfo(String clause, String prefix, int count) {
        SQLQuery query = SQLQueryParser.parse("SELECT p FROM t WHERE " + clause);
        Predicate predicate = query.getWhereClause().predicate();
        QueryOptimizer optimizer = getOptimizer();
        PrefixInfo info = optimizer.analyzePrefixes(predicate);
        assertNotNull(info);
        assertEquals(prefix, info.prefix());
        assertEquals(count, info.count());
    }

    @Test
    public void testFindPrefix() {
        List<String> strings;
        List<String> withPrefix;

        strings = new ArrayList<>();
        withPrefix = new ArrayList<>();
        assertNull(QueryOptimizer.findPrefix(strings, withPrefix));

        strings = new ArrayList<>(Arrays.asList("", "foo", "bar"));
        withPrefix = new ArrayList<>();
        assertNull(QueryOptimizer.findPrefix(strings, withPrefix));

        strings = new ArrayList<>(Arrays.asList("", "foo", "foo/bar", "smurf"));
        withPrefix = new ArrayList<>();
        assertEquals("foo", QueryOptimizer.findPrefix(strings, withPrefix));
        assertEquals(Arrays.asList("", "smurf"), strings);
        assertEquals(Arrays.asList("foo/bar"), withPrefix);
    }

    @Test
    public void testPrefixGroupingInOptimizedQuery() {
        String clause = "sys_title = 'foo' AND sys_acl/*1/principal = 'bob' AND sys_acl/*1/permission = 'Read'";
        SQLQuery query = SQLQueryParser.parse("SELECT p FROM t WHERE " + clause);
        QueryOptimizer optimizer = getOptimizer();
        query = optimizer.optimize(query);
        QueryAnnotations annotations = optimizer.getAnnotations();
        Predicate expr = query.getWhereClause().predicate();
        // AND is associative, so the rendering is flat; the grouping is asserted structurally below
        assertEquals("sys_title = 'foo' AND sys_primaryType = 't'" //
                + " AND sys_acl/*1/principal = 'bob' AND sys_acl/*1/permission = 'Read'", expr.toString());

        MultiExpression me = (MultiExpression) expr;
        assertEquals(new PrefixInfo("", 0), annotations.getPrefixInfo(me));

        MultiExpression me0 = (MultiExpression) me.predicates.get(0);
        assertEquals(new PrefixInfo("", 0), annotations.getPrefixInfo(me0));

        MultiExpression me1 = (MultiExpression) me.predicates.get(1);
        assertEquals(new PrefixInfo("sys_acl/*1", 2), annotations.getPrefixInfo(me1));

        Predicate me10 = me1.predicates.get(0);
        assertEquals(new PrefixInfo("sys_acl/*1", 1), annotations.getPrefixInfo(me10));

        Predicate me11 = me1.predicates.get(1);
        assertEquals(new PrefixInfo("sys_acl/*1", 1), annotations.getPrefixInfo(me11));
    }

}
