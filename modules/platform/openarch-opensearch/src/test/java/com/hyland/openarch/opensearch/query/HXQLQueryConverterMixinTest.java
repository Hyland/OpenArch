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
package com.hyland.openarch.opensearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.schema.SchemaManagerDTO;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import tools.jackson.databind.ObjectMapper;

class HXQLQueryConverterMixinTest {

    private HXQLQueryConverter converter;

    @BeforeEach
    void setUp() {
        var model = new ObjectMapper().readValue("""
                {
                  "schemas": {
                    "system": {
                      "prefix": "sys",
                      "fields": {
                        "sys_primaryType": { "type": "string" },
                        "sys_mixinTypes": { "list": { "type": "string" } }
                      }
                    }
                  },
                  "mixinTypes": {
                    "SysHiddenInNavigation": { "noPerDocumentQuery": true },
                    "UnusedTypeOnly": { "noPerDocumentQuery": true },
                    "Downloadable": {},
                    "DynamicOnly": {}
                  },
                  "primaryTypes": {
                    "SysContent": {
                      "schemas": ["system"]
                    },
                    "SysRendition": {
                      "extends": "SysContent",
                      "mixins": ["SysHiddenInNavigation"]
                    },
                    "CustomRendition": {
                      "extends": "SysRendition"
                    },
                    "Document": {
                      "extends": "SysContent",
                      "mixins": ["Downloadable"]
                    }
                  }
                }
                """, ContentModelDTO.class);
        converter = new HXQLQueryConverter(new SchemaManagerDTO(model, null, false));
    }

    private Query filter(String predicate) {
        return converter.toESQueryBuilder(HXQLQueryConverter.parse("SELECT * FROM SysContent WHERE " + predicate), null)
                        .build().toQuery().bool().must().getFirst().constantScore().filter();
    }

    private void assertTerms(Query query, String field, String... values) {
        assertEquals(field, query.terms().field());
        assertEquals(List.of(values), query.terms().terms().value().stream().map(value -> value.stringValue()).toList());
    }

    private void assertTypeOnlyFilter(Query query) {
        assertEquals(1, query.bool().should().size());
        assertTerms(query.bool().should().getFirst(), "sys_primaryType", "CustomRendition", "SysRendition");
    }

    @ParameterizedTest
    @ValueSource(strings = { "= 'SysHiddenInNavigation'", "IN ('SysHiddenInNavigation')" })
    void typeOnlyMixinsUseCurrentTypesIncludingSubtypes(String comparison) {
        assertTypeOnlyFilter(filter("sys_mixinTypes " + comparison));
    }

    @ParameterizedTest
    @ValueSource(strings = { "<> 'SysHiddenInNavigation'", "!= 'SysHiddenInNavigation'",
            "NOT IN ('SysHiddenInNavigation')" })
    void typeOnlyExclusionDoesNotDependOnIndexedMixins(String comparison) {
        assertTypeOnlyFilter(filter("sys_mixinTypes " + comparison).bool().mustNot().getFirst());
    }

    @ParameterizedTest
    @ValueSource(strings = { "= 'Downloadable'", "IN ('Downloadable')", "<> 'Downloadable'", "NOT IN ('Downloadable')" })
    void dynamicMixinsMatchEitherCurrentTypesOrIndexedMixins(String comparison) {
        Query query = filter("sys_mixinTypes " + comparison);
        if (comparison.startsWith("<>") || comparison.startsWith("NOT IN")) {
            query = query.bool().mustNot().getFirst();
        }
        assertEquals(2, query.bool().should().size());
        assertTerms(query.bool().should().get(0), "sys_primaryType", "Document");
        assertTerms(query.bool().should().get(1), "sys_mixinTypes", "Downloadable");
    }

    @ParameterizedTest
    @ValueSource(strings = { "IN", "NOT IN" })
    void mixedListsOnlyCheckDynamicMixinsInTheIndex(String operator) {
        Query query = filter("sys_mixinTypes " + operator
                + " ('SysHiddenInNavigation', 'Downloadable', 'DynamicOnly')");
        if ("NOT IN".equals(operator)) {
            query = query.bool().mustNot().getFirst();
        }
        assertEquals(2, query.bool().should().size());
        assertTerms(query.bool().should().get(0), "sys_primaryType", "CustomRendition", "Document", "SysRendition");
        assertTerms(query.bool().should().get(1), "sys_mixinTypes", "Downloadable", "DynamicOnly");
    }

    @ParameterizedTest
    @ValueSource(strings = { "DynamicOnly", "UnknownMixin" })
    void mixinsWithoutTypesStillCheckTheIndex(String mixin) {
        Query query = filter("sys_mixinTypes = '" + mixin + "'");
        assertEquals(1, query.bool().should().size());
        assertTerms(query.bool().should().getFirst(), "sys_mixinTypes", mixin);
    }

    @Test
    void unusedTypeOnlyMixinMatchesNothing() {
        assertTrue(filter("sys_mixinTypes = 'UnusedTypeOnly'").isMatchNone());
        assertTrue(filter("sys_mixinTypes <> 'UnusedTypeOnly'").bool().mustNot().getFirst().isMatchNone());
    }

    @Test
    void emptyListsMatchNothingOrEverything() {
        Query positive = converter.makeQueryFromSimpleExpression(Operator.IN, "sys_mixinTypes", null, new String[0],
                null, null).filter.build().toQuery();
        Query negative = converter.makeQueryFromSimpleExpression(Operator.NOTIN, "sys_mixinTypes", null, new String[0],
                null, null).filter.build().toQuery();
        assertTrue(positive.isMatchNone());
        assertTrue(negative.bool().mustNot().getFirst().isMatchNone());
    }

    @Test
    void nestedBooleanExpressionsExpandMixins() {
        Query query = converter.toESQueryBuilder(HXQLQueryConverter.parse("""
                SELECT * FROM SysContent
                WHERE NOT (sys_mixinTypes = 'SysHiddenInNavigation' OR sys_mixinTypes = 'Downloadable')
                """), null).build().toQuery();
        List<Query> alternatives = query.bool().must().getFirst().bool().mustNot().getFirst().bool().should();
        assertTypeOnlyFilter(alternatives.get(0).constantScore().filter());
        assertTerms(alternatives.get(1).constantScore().filter().bool().should().getFirst(), "sys_primaryType", "Document");
    }
}
