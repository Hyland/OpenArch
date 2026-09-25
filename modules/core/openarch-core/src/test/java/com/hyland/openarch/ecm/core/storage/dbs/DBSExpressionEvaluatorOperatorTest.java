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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

/**
 * Exercises the NXQL operator branches of {@link com.hyland.openarch.ecm.core.storage.ExpressionEvaluator} through the concrete
 * {@link DBSExpressionEvaluator}, evaluating each operator against a hand-built {@link State}.
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("com.hyland.openarch.ecm.core.schema")
@Deploy("com.hyland.openarch.ecm.core")
@Deploy("com.hyland.openarch.ecm.core.tests:OSGI-INF/test-complex.xml")
public class DBSExpressionEvaluatorOperatorTest {

    @Inject
    private TypeService typeService;

    private SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = typeService.getDefaultSchemaManager();
    }

    private static List<Object> list(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static State state(Object... values) {
        StateDiff diff = new StateDiff();
        for (int i = 0; i < values.length; i += 2) {
            diff.put((String) values[i], values[i + 1]);
        }
        return diff;
    }

    /** A document with a single address: number=2, street="Rue", city="Paris". */
    private static State oneAddress() {
        return state(SYS_ID, "id1", "cmp_addresses", list(state("number", 2L, "street", "Rue", "city", "Paris")));
    }

    private boolean whereMatches(String where, State state) {
        Statement query = HXQLParser.parse("SELECT sys_id FROM D WHERE " + where);
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);
        return !evaluator.matches(state).isEmpty();
    }

    @Test
    public void equalityOperators() {
        assertTrue(whereMatches("cmp_addresses/*1/city = 'Paris'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city = 'London'", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city <> 'London'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city <> 'Paris'", oneAddress()));
    }

    @Test
    public void comparisonOperators() {
        assertTrue(whereMatches("cmp_addresses/*1/number < 5", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/number < 2", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/number > 1", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/number > 2", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/number <= 2", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/number >= 2", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/number >= 3", oneAddress()));
    }

    @Test
    public void likeOperators() {
        assertTrue(whereMatches("cmp_addresses/*1/city LIKE 'Par%'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city LIKE 'Lon%'", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city ILIKE 'par%'", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city NOT LIKE 'Lon%'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city NOT LIKE 'Par%'", oneAddress()));
    }

    @Test
    public void inOperators() {
        assertTrue(whereMatches("cmp_addresses/*1/city IN ('Paris', 'London')", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city IN ('London', 'Berlin')", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city NOT IN ('London')", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city NOT IN ('Paris')", oneAddress()));
    }

    @Test
    public void betweenOperators() {
        assertTrue(whereMatches("cmp_addresses/*1/number BETWEEN 1 AND 3", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/number BETWEEN 5 AND 9", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/number NOT BETWEEN 5 AND 9", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/number NOT BETWEEN 1 AND 3", oneAddress()));
    }

    @Test
    public void nullOperators() {
        State noStreet = state(SYS_ID, "id1", "cmp_addresses", list(state("number", 2L, "city", "Paris")));
        assertTrue(whereMatches("cmp_addresses/*1/street IS NULL", noStreet));
        assertFalse(whereMatches("cmp_addresses/*1/city IS NULL", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city IS NOT NULL", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/street IS NOT NULL", noStreet));
    }

    @Test
    public void booleanOperators() {
        assertTrue(whereMatches("cmp_addresses/*1/city = 'Paris' AND cmp_addresses/*1/number = 2", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city = 'Paris' AND cmp_addresses/*1/number = 9", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city = 'London' OR cmp_addresses/*1/number = 2", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city = 'London' OR cmp_addresses/*1/number = 9", oneAddress()));
        assertTrue(whereMatches("NOT cmp_addresses/*1/city = 'London'", oneAddress()));
        assertFalse(whereMatches("NOT cmp_addresses/*1/city = 'Paris'", oneAddress()));
    }

    @Test
    public void multiExpression() {
        assertTrue(whereMatches("cmp_addresses/*1/city = 'Paris' AND cmp_addresses/*1/number = 2 "
                + "AND cmp_addresses/*1/street = 'Rue'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city = 'Paris' AND cmp_addresses/*1/number = 2 "
                + "AND cmp_addresses/*1/street = 'Other'", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city = 'X' OR cmp_addresses/*1/number = 9 "
                + "OR cmp_addresses/*1/street = 'Rue'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city = 'X' OR cmp_addresses/*1/number = 9 "
                + "OR cmp_addresses/*1/street = 'Y'", oneAddress()));
    }

    @Test
    public void ancestorIdsColumn() {
        State placed = state(SYS_ID, "id1", SYS_ANCESTOR_IDS, list("anc1", "anc2"));
        assertTrue(whereMatches("sys_ancestorIds = 'anc1'", placed));
        assertFalse(whereMatches("sys_ancestorIds = 'nope'", placed));
        assertTrue(whereMatches("sys_ancestorIds <> 'nope'", placed));
        assertFalse(whereMatches("sys_ancestorIds <> 'anc1'", placed));

        // placeless document (no ancestors)
        State placeless = state(SYS_ID, "id1");
        assertFalse(whereMatches("sys_ancestorIds = 'anc1'", placeless));
        assertTrue(whereMatches("sys_ancestorIds <> 'anc1'", placeless));
    }

    @Test
    public void mixinTypesColumn() {
        State withFacet = state(SYS_ID, "id1", SYS_MIXIN_TYPES, list("Facet1", "Facet2"));
        assertTrue(whereMatches("sys_mixinTypes = 'Facet1'", withFacet));
        assertFalse(whereMatches("sys_mixinTypes = 'Missing'", withFacet));
    }

    @Test
    public void nullValueUsesTernaryLogic() {
        // a null value makes scalar comparisons neither true nor false, so they do not match
        State noNumber = state(SYS_ID, "id1", "cmp_addresses", list(state("city", "Paris")));
        assertFalse(whereMatches("cmp_addresses/*1/number < 5", noNumber));
        assertFalse(whereMatches("cmp_addresses/*1/number > 5", noNumber));
        assertFalse(whereMatches("cmp_addresses/*1/number = 5", noNumber));
        assertTrue(whereMatches("cmp_addresses/*1/number IS NULL", noNumber));
    }

    @Test
    public void wildcardMatchesAnyCorrelatedValue() {
        State two = state(SYS_ID, "id1", "cmp_addresses",
                list(state("number", 1L, "city", "London"), state("number", 2L, "city", "Paris")));
        assertTrue(whereMatches("cmp_addresses/*1/city = 'Paris'", two));
        assertTrue(whereMatches("cmp_addresses/*1/number = 1", two));
        assertFalse(whereMatches("cmp_addresses/*1/city = 'Berlin'", two));
    }

    @Test
    public void likeWildcardVariants() {
        assertTrue(whereMatches("cmp_addresses/*1/city LIKE 'Par_s'", oneAddress()));
        assertTrue(whereMatches("cmp_addresses/*1/city LIKE 'P_r%'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city LIKE 'P_r_'", oneAddress()));
    }

    @Test
    public void startsWithOnField() {
        assertTrue(whereMatches("cmp_addresses/*1/city STARTSWITH 'Paris'", oneAddress()));
        assertFalse(whereMatches("cmp_addresses/*1/city STARTSWITH 'Xyz'", oneAddress()));
    }

    /** A document whose friends each have their own list of addresses (nested correlated wildcards). */
    private static State friends() {
        return state(SYS_ID, "id1", "cmp_friends",
                list(state("name", "Alice", "address", list(state("city", "Paris"), state("city", "London"))),
                        state("name", "Bob", "address", list(state("city", "Berlin")))));
    }

    @Test
    public void nestedWildcardTraversal() {
        assertTrue(whereMatches("cmp_friends/*1/name = 'Alice'", friends()));
        assertFalse(whereMatches("cmp_friends/*1/name = 'Carol'", friends()));
        assertTrue(whereMatches("cmp_friends/*1/address/*2/city = 'London'", friends()));
        assertFalse(whereMatches("cmp_friends/*1/address/*2/city = 'Tokyo'", friends()));
    }

    @Test
    public void nestedCorrelationBindsSameFriend() {
        // Alice lives in Paris, Bob in Berlin: the *1 correlation must bind name and address to the same friend
        assertTrue(whereMatches("cmp_friends/*1/name = 'Alice' AND cmp_friends/*1/address/*2/city = 'Paris'", friends()));
        assertFalse(
                whereMatches("cmp_friends/*1/name = 'Alice' AND cmp_friends/*1/address/*2/city = 'Berlin'", friends()));
    }

    @Test
    public void nestedProjectionReturnsCartesianRows() {
        Statement query = HXQLParser.parse("SELECT cmp_friends/*1/name, cmp_friends/*1/address/*2/city FROM D "
                + "WHERE cmp_friends/*1/name = 'Alice'");
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(null, query);
        evaluator.parse(schemaManager);

        assertTrue(evaluator.hasWildcardProjection());
        // Alice has two addresses -> two projection rows
        assertEquals(2, evaluator.matches(friends()).size());
    }
}
