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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-schemas-contrib.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/testquery-core-types-contrib.xml")
public class TestNXQLQueryBuilder {

    @Inject
    protected CoreSession session;

    @Test
    public void testBuildIsNullQuery() throws Exception {
        List<PredicateDefinition> predicates = List.of( //
                new PredicateDefinition("sys_fulltext", "FULLTEXT", "search_fulltext_all"),
                new PredicateDefinition(SYS_TITLE, "LIKE", "search_title"),
                new PredicateDefinition(SYS_MODIFIED, "IS NULL", "search_isPresent"));
        WhereClauseDefinition whereClause = new WhereClauseDefinition(null, "sys_parentId = ?", predicates, true);

        SortInfo sortInfos = new SortInfo(SYS_TITLE, true);
        String[] params = { "foo" };

        DocumentModel model = mock(DocumentModel.class);
        DocumentType type = session.getSchemaManager().getDocumentType("AdvancedSearch");
        when(model.getDocumentType()).thenReturn(type);

        when(model.getPropertyValue("search_title")).thenReturn("bar");

        String query = NXQLQueryBuilder.getQuery(model, whereClause, params, sortInfos);
        assertEquals("SELECT * FROM SysContent WHERE sys_title LIKE 'bar' AND (sys_parentId = 'foo') ORDER BY sys_title",
                query);

        when(model.getPropertyValue("search_isPresent")).thenReturn(Boolean.TRUE);
        query = NXQLQueryBuilder.getQuery(model, whereClause, params, sortInfos);
        assertEquals(
                "SELECT * FROM SysContent WHERE sys_title LIKE 'bar' AND sys_modified IS NULL AND (sys_parentId = 'foo') ORDER BY sys_title",
                query);

        // only boolean available in schema without default value
        when(model.getPropertyValue("search_isPresent")).thenReturn(Boolean.FALSE);
        query = NXQLQueryBuilder.getQuery(model, whereClause, params, sortInfos);
        assertEquals(
                "SELECT * FROM SysContent WHERE sys_title LIKE 'bar' AND sys_modified IS NOT NULL AND (sys_parentId = 'foo') ORDER BY sys_title",
                query);

        query = NXQLQueryBuilder.getQuery("SELECT * FROM ? WHERE ? = '?'",
                new Object[] { SYS_CONTENT, SYS_TITLE, null }, false, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = ''", query);
    }

    @Test
    public void testBuildInQuery() throws Exception {
        List<PredicateDefinition> predicates = List.of(new PredicateDefinition(SYS_TITLE, "IN", "mydc_subjects"));
        WhereClauseDefinition whereClause = new WhereClauseDefinition(null, null, predicates, false);

        DocumentModel model = mock(DocumentModel.class);
        DocumentType type = session.getSchemaManager().getDocumentType(SYS_FILE);
        when(model.getDocumentType()).thenReturn(type);

        when(model.getPropertyValue("mydc_subjects")).thenReturn(new String[] { "foo", "bar" });
        String query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_title IN ('foo', 'bar')", query);

        when(model.getPropertyValue("mydc_subjects")).thenReturn(new String[] { "foo" });
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'foo'", query);

        // criteria with no values are removed
        when(model.getPropertyValue("mydc_subjects")).thenReturn(new String[] {});
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent", query);
    }

    @Test
    public void testBuildInIntegersQuery() throws Exception {
        List<PredicateDefinition> predicates = List.of(new PredicateDefinition("size", "IN", "search_integerlist"));
        WhereClauseDefinition whereClause = new WhereClauseDefinition(null, null, predicates, false);

        DocumentModel model = mock(DocumentModel.class);
        DocumentType type = session.getSchemaManager().getDocumentType("AdvancedSearch");
        when(model.getDocumentType()).thenReturn(type);

        @SuppressWarnings("boxing")
        Integer[] array1 = new Integer[] { 1, 2, 3 };
        when(model.getPropertyValue("search_integerlist")).thenReturn(array1);
        String query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size IN (1, 2, 3)", query);

        @SuppressWarnings("boxing")
        Integer[] array2 = new Integer[] { 1 };
        when(model.getPropertyValue("search_integerlist")).thenReturn(array2);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size = 1", query);

        // criteria with no values are removed
        Integer[] array3 = new Integer[0];
        when(model.getPropertyValue("search_integerlist")).thenReturn(array3);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent", query);

        // arrays of long work too
        @SuppressWarnings("boxing")
        Long[] array4 = new Long[] { 1L, 2L, 3L };
        when(model.getPropertyValue("search_integerlist")).thenReturn(array4);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size IN (1, 2, 3)", query);

        // lists work too
        @SuppressWarnings("boxing")
        List<Long> list = Arrays.asList(1L, 2L, 3L);
        when(model.getPropertyValue("search_integerlist")).thenReturn(list);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size IN (1, 2, 3)", query);
    }

    @Test
    public void testBuildNotInIntegersQuery() throws Exception {
        List<PredicateDefinition> predicates = List.of(new PredicateDefinition("size", "NOT IN", "search_integerlist"));
        WhereClauseDefinition whereClause = new WhereClauseDefinition(null, null, predicates, false);

        DocumentModel model = mock(DocumentModel.class);
        DocumentType type = session.getSchemaManager().getDocumentType("AdvancedSearch");
        when(model.getDocumentType()).thenReturn(type);

        @SuppressWarnings("boxing")
        Integer[] array1 = new Integer[] { 1, 2, 3 };
        when(model.getPropertyValue("search_integerlist")).thenReturn(array1);
        String query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size NOT IN (1, 2, 3)", query);

        @SuppressWarnings("boxing")
        Integer[] array2 = new Integer[] { 1 };
        when(model.getPropertyValue("search_integerlist")).thenReturn(array2);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size != 1", query);

        // criteria with no values are removed
        Integer[] array3 = new Integer[0];
        when(model.getPropertyValue("search_integerlist")).thenReturn(array3);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent", query);

        // arrays of long work too
        @SuppressWarnings("boxing")
        Long[] array4 = new Long[] { 1L, 2L, 3L };
        when(model.getPropertyValue("search_integerlist")).thenReturn(array4);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size NOT IN (1, 2, 3)", query);

        // lists work too
        @SuppressWarnings("boxing")
        List<Long> list = Arrays.asList(1L, 2L, 3L);
        when(model.getPropertyValue("search_integerlist")).thenReturn(list);
        query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM SysContent WHERE size NOT IN (1, 2, 3)", query);
    }

    @Test
    public void testBuildInIntegersEmptyQuery() throws Exception {
        String pattern = "SELECT * FROM SysContent WHERE sys_parentId = ? and my_foo IN (?)";
        Object[] params = new Object[] { "docId", "" };
        String query = NXQLQueryBuilder.getQuery(pattern, params, true, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_parentId = 'docId' and my_foo IN ('')", query);
        params = new Object[] { "docId", new String[] { "foo", "bar" } };
        query = NXQLQueryBuilder.getQuery(pattern, params, true, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_parentId = 'docId' and my_foo IN ('foo', 'bar')", query);
    }

    @Test
    public void testBuildInNotIntegersEmptyQuery() throws Exception {
        String pattern = "SELECT * FROM SysContent WHERE sys_parentId = ? and my_foo NOT IN (?)";
        Object[] params = new Object[] { "docId", "" };
        String query = NXQLQueryBuilder.getQuery(pattern, params, true, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_parentId = 'docId' and my_foo NOT IN ('')", query);
        params = new Object[] { "docId", new String[] { "foo", "bar" } };
        query = NXQLQueryBuilder.getQuery(pattern, params, true, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_parentId = 'docId' and my_foo NOT IN ('foo', 'bar')", query);
    }

    // @Since 5.9
    @Test
    public void testSortedColumnQuery() throws Exception {
        String pattern = "SELECT * FROM SysContent WHERE SORTED_COLUMN IS NOT NULL";
        String query = NXQLQueryBuilder.getQuery(pattern, null, true, null);
        assertEquals("SELECT * FROM SysContent WHERE sys_id IS NOT NULL", query);
        SortInfo sortInfo = new SortInfo(SYS_TITLE, true);
        query = NXQLQueryBuilder.getQuery(pattern, null, true, null, sortInfo);
        assertEquals("SELECT * FROM SysContent WHERE sys_title IS NOT NULL ORDER BY sys_title", query);
        sortInfo = new SortInfo(SYS_CREATED, true);
        SortInfo sortInfo2 = new SortInfo(SYS_TITLE, true);
        query = NXQLQueryBuilder.getQuery(pattern, null, true, null, sortInfo, sortInfo2);
        assertEquals("SELECT * FROM SysContent WHERE sys_created IS NOT NULL ORDER BY sys_created, sys_title", query);
    }

    // @Since 5.9.2
    @Test
    public void testCustomSelectStatement() throws Exception {
        List<PredicateDefinition> predicates = List.of( //
                new PredicateDefinition("sys_fulltext", "FULLTEXT", "search_fulltext_all"),
                new PredicateDefinition(SYS_TITLE, "LIKE", "search_title"),
                new PredicateDefinition(SYS_MODIFIED, "IS NULL", "search_isPresent"));
        WhereClauseDefinition whereClause = new WhereClauseDefinition("SELECT * FROM Note", "sys_parentId = ?",
                predicates, true);

        String[] params = { "foo" };

        DocumentModel model = mock(DocumentModel.class);
        DocumentType type = session.getSchemaManager().getDocumentType("AdvancedSearch");
        when(model.getDocumentType()).thenReturn(type);

        when(model.getPropertyValue("search_title")).thenReturn("bar");
        String query = NXQLQueryBuilder.getQuery(model, whereClause, params);
        assertEquals("SELECT * FROM Note WHERE sys_title LIKE 'bar' AND (sys_parentId = 'foo')", query);
    }

    // @Since 7.3
    @Test
    public void testPredicatesWithHint() throws Exception {
        List<PredicateDefinition> predicates = List.of( //
                new PredicateDefinition(SYS_TITLE, "LIKE", "search_title", null, "ES: INDEX(sys_title.custom)"),
                new PredicateDefinition(SYS_TITLE, "FULLTEXT", "search_title", null, "ES: ANALYZER(fr_analyzer)"),
                new PredicateDefinition("sys_fulltext", "FULLTEXT ALL", "search_title", null,
                        "ES: INDEX(sys_title.fulltext) OPERATOR(fuzzy)"),
                new PredicateDefinition(SYS_TITLE, "FULLTEXT", "search_title"),
                new PredicateDefinition("sys_fulltext.sys_description", "FULLTEXT", "search_title"));
        WhereClauseDefinition whereClause = new WhereClauseDefinition("SELECT * FROM Note", null, predicates, false);

        DocumentModel model = mock(DocumentModel.class);
        DocumentType type = session.getSchemaManager().getDocumentType("AdvancedSearch");
        when(model.getDocumentType()).thenReturn(type);

        when(model.getPropertyValue("search_title")).thenReturn("foo");
        String query = NXQLQueryBuilder.getQuery(model, whereClause, null);
        assertEquals("SELECT * FROM Note WHERE " + //
                "/*+ES: INDEX(sys_title.custom) */ sys_title LIKE 'foo' AND " + //
                "/*+ES: ANALYZER(fr_analyzer) */ sys_fulltext.sys_title = 'foo' AND " + //
                "/*+ES: INDEX(sys_title.fulltext) OPERATOR(fuzzy) */ sys_fulltext = 'foo' AND " + //
                "sys_fulltext.sys_title = 'foo' AND " + //
                "sys_fulltext.sys_description = 'foo'", query);
    }

    @Test
    public void iCanFindNamedParameters() {
        // Given a regexp when I put named parameters
        String pattern = "SELECT * FROM SysContent WHERE sys_parentId = " + ":parentIdVal AND my_foo IN (:param1, "
                + ":param2) AND sys_title = \":pouet\" AND sys_description = "
                + "':desc' AND sys_description = 'ihfifehi:desc'";
        String query = pattern.replaceAll(NXQLQueryBuilder.REGEXP_EXCLUDE_DOUBLE_QUOTE, StringUtils.EMPTY);
        query = query.replaceAll(NXQLQueryBuilder.REGEXP_EXCLUDE_QUOTE, StringUtils.EMPTY);
        Pattern p1 = Pattern.compile(NXQLQueryBuilder.REGEXP_NAMED_PARAMETER);
        Matcher m1 = p1.matcher(query);

        List<String> matches = new ArrayList<>();
        // I have to find them
        while (m1.find()) {
            matches.add(m1.group().substring(m1.group().indexOf(":") + 1));
        }
        assertEquals(3, matches.size());
        assertEquals("parentIdVal", matches.get(0));
        assertEquals("param1", matches.get(1));
        assertEquals("param2", matches.get(2));
    }

    @Test
    public void iCanReplacePattern() {

        // Given a regexp when I put named parameters
        String pattern1 = "SELECT * FROM SysContent WHERE sys_title = :myTitle";
        String pattern2 = "SELECT * FROM SysContent WHERE sys_title = :title";
        String pattern3 = "SELECT * FROM SysContent WHERE sys_title = :title OR sys_title = :titleLonger";
        String pattern4 = "SELECT * FROM SysContent WHERE sys_title = :titleLongest OR sys_title = :title OR sys_title = :titleLonger";
        String pattern5 = "SELECT * FROM SysContent WHERE sys_title = :titleLonger OR sys_title = :title";

        String replacedPattern1 = NXQLQueryBuilder.buildPattern(pattern1, ":title", "'test'");
        String replacedPattern2 = NXQLQueryBuilder.buildPattern(pattern2, ":title", "'test'");
        String replacedPattern2a = NXQLQueryBuilder.buildPattern(pattern2, ":title", "' $0'");
        String replacedPattern2b = NXQLQueryBuilder.buildPattern(pattern2, ":title", "' :title'");
        String replacedPattern3 = NXQLQueryBuilder.buildPattern(pattern3, ":title", "'test'");
        String replacedPattern4 = NXQLQueryBuilder.buildPattern(pattern4, ":title", "'test'");
        String replacedPattern5 = NXQLQueryBuilder.buildPattern(pattern5, ":title", "'test'");

        assertEquals("SELECT * FROM SysContent WHERE sys_title = :myTitle", replacedPattern1);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'test'", replacedPattern2);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = ' $0'", replacedPattern2a);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = ' :title'", replacedPattern2b);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = 'test' OR sys_title = :titleLonger", replacedPattern3);
        assertEquals(
                "SELECT * FROM SysContent WHERE sys_title = :titleLongest OR sys_title = 'test' OR sys_title = :titleLonger",
                replacedPattern4);
        assertEquals("SELECT * FROM SysContent WHERE sys_title = :titleLonger OR sys_title = 'test'", replacedPattern5);
    }

}
