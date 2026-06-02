/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.storage.dbs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.storage.QueryOptimizer;
import org.nuxeo.ecm.core.storage.QueryOptimizer.PrefixInfo;
import org.nuxeo.ecm.core.storage.QueryOptimizer.ReferencePrefixAnalyzer;
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
        Expression predicate = query.getWhereClause().predicate;
        ReferencePrefixAnalyzer visitor = getOptimizer().new ReferencePrefixAnalyzer();
        predicate.accept(visitor);
        PrefixInfo info = (PrefixInfo) predicate.getInfo();
        assertNotNull(info);
        assertEquals(prefix, info.prefix);
        assertEquals(count, info.count);
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
        query = getOptimizer().optimize(query);
        Expression expr = query.getWhereClause().predicate;
        assertEquals("(((sys_title = 'foo') AND (sys_primaryType = 't'))" //
                + " AND ((sys_acl/*1/principal = 'bob') AND (sys_acl/*1/permission = 'Read')))", expr.toString());

        PrefixInfo info;

        MultiExpression me = (MultiExpression) expr;
        info = (PrefixInfo) me.getInfo();
        assertEquals("", info.prefix);
        assertEquals(0, info.count);

        MultiExpression me0 = (MultiExpression) me.predicates.get(0);
        info = (PrefixInfo) me0.getInfo();
        assertEquals("", info.prefix);
        assertEquals(0, info.count);

        MultiExpression me1 = (MultiExpression) me.predicates.get(1);
        info = (PrefixInfo) me1.getInfo();
        assertEquals("sys_acl/*1", info.prefix);
        assertEquals(2, info.count);

        Expression me10 = me1.predicates.get(0);
        info = (PrefixInfo) me10.getInfo();
        assertEquals("sys_acl/*1", info.prefix);
        assertEquals(1, info.count);

        Expression me11 = me1.predicates.get(1);
        info = (PrefixInfo) me11.getInfo();
        assertEquals("sys_acl/*1", info.prefix);
        assertEquals(1, info.count);
    }

}
