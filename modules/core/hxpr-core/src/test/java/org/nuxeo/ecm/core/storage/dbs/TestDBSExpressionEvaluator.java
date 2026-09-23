/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.query.sql.HXQLParser;
import org.nuxeo.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.tests:OSGI-INF/test-complex.xml")
public class TestDBSExpressionEvaluator {

    @Inject
    private TypeService typeService;

    private SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
    }

    private static final List<Object> list(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static final StateDiff stateDiff(Object... values) {
        assertEquals(0, values.length % 2);
        StateDiff diff = new StateDiff();
        for (int i = 0; i < values.length; i += 2) {
            diff.put((String) values[i], values[i + 1]);
        }
        return diff;
    }

    private static final State state(Object... values) {
        return stateDiff(values);
    }

    private static final Map<String, Object> map(Object... values) {
        assertEquals(0, values.length % 2);
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    @Test
    public void testMatch() throws Exception {
        Statement query = HXQLParser.parse("SELECT sys_id, cmp_addresses/*1/street FROM D WHERE " //
                + "cmp_addresses/*1/city = 'Paris'");
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);
        assertTrue(evaluator.hasWildcardProjection());
        State state = state(//
                SYS_ID, "id1", //
                "cmp_addresses",
                list( //
                        state("city", "Paris", "street", "Champs Elysees"), //
                        state("city", "Paris", "street", "Boulevard Peripherique")));
        List<Map<String, Object>> projections = evaluator.matches(state);
        assertEquals(
                list( //
                        map(SYS_ID, "id1", "cmp_addresses/*1/street", "Champs Elysees"), //
                        map(SYS_ID, "id1", "cmp_addresses/*1/street", "Boulevard Peripherique")), //
                projections);
    }

    @Test
    public void testMatch2() throws Exception {
        Statement query = HXQLParser.parse("SELECT sys_id, cmp_addresses/*1/street FROM D WHERE "
                + "cmp_addresses/*1/city = 'Paris' OR cmp_addresses/*1/number = 1");
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);
        assertTrue(evaluator.hasWildcardProjection());
        State state = state(//
                SYS_ID, "id1", //
                "cmp_addresses",
                list( //
                        state("city", "New York", "street", "Broadway", "number", 1L), //
                        state("city", "Paris", "street", "Champs Elysees", "number", 1L), //
                        state("city", "Paris", "street", "Boulevard Peripherique", "number", 2L)));
        List<Map<String, Object>> projections = evaluator.matches(state);
        assertEquals(
                list( //
                        map(SYS_ID, "id1", "cmp_addresses/*1/street", "Broadway"), //
                        map(SYS_ID, "id1", "cmp_addresses/*1/street", "Champs Elysees"), //
                        map(SYS_ID, "id1", "cmp_addresses/*1/street", "Boulevard Peripherique")), //
                projections);
    }

    @Test
    public void testWildcardCrossProduct() throws Exception {
        Statement query = HXQLParser.parse("SELECT cmp_addresses/*1/city, cmp_addresses/*2/city FROM D WHERE " //
                + "sys_id <> 'nothing'");
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);
        assertTrue(evaluator.hasWildcardProjection());
        State state = state(//
                SYS_ID, "id1", //
                "cmp_addresses",
                list( //
                        state("city", "Paris"), //
                        state("city", "London")));
        List<Map<String, Object>> projections = evaluator.matches(state);
        assertEquals(
                list( //
                        map("cmp_addresses/*1/city", "Paris", "cmp_addresses/*2/city", "Paris"), //
                        map("cmp_addresses/*1/city", "Paris", "cmp_addresses/*2/city", "London"), //
                        map("cmp_addresses/*1/city", "London", "cmp_addresses/*2/city", "Paris"), //
                        map("cmp_addresses/*1/city", "London", "cmp_addresses/*2/city", "London")), //
                projections);
    }

    @Test
    public void toStringRendersMultiPredicateQueryWithOrderBy() {
        Statement query = HXQLParser.parse("SELECT sys_id FROM D WHERE " //
                + "cmp_addresses/*1/city = 'Paris' AND cmp_addresses/*1/number = 1 ORDER BY sys_id");
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);

        String rendered = evaluator.toString();

        assertTrue(rendered.startsWith("SELECT "));
        assertTrue(rendered.contains(" WHERE "));
        assertTrue(rendered.contains(" AND "));
        assertTrue(rendered.contains(" ORDER BY "));
    }

    @Test
    public void toStringRendersQueryWithoutOrderBy() {
        Statement query = HXQLParser.parse("SELECT sys_id FROM D WHERE cmp_addresses/*1/city = 'Paris'");
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);

        String rendered = evaluator.toString();

        assertTrue(rendered.startsWith("SELECT "));
        assertTrue(rendered.contains(" WHERE "));
        assertFalse(rendered.contains(" ORDER BY "));
    }

}
