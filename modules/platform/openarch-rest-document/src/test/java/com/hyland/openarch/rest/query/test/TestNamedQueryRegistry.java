/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.query.test;

import static com.hyland.openarch.rest.query.conf.NamedQueryHelper.BASIC_FILTER_CLAUSE;
import static com.hyland.openarch.rest.query.conf.NamedQueryHelper.FULLTEXT_PREDICATE;
import static com.hyland.openarch.rest.query.conf.NamedQueryHelper.TITLE_SORT;
import static com.hyland.openarch.rest.query.spec.QueryAssembler.DEFAULT_SELECT_STATEMENT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.rest.query.conf.DefaultNamedQueryRegistry;
import com.hyland.openarch.rest.query.io.WhereClauseDTO;
import com.hyland.openarch.rest.query.spec.NamedQueryDefinition;
import com.hyland.openarch.rest.query.spec.NamedQueryRegistry;

/**
 * Override default named queries for tests.
 * <p>
 * Shows how one contribution can be added to the default registry via
 * {@link TestNamedQueryConfig#produceFolderListing2()}, and shows also that the whole registry can be overridden thanks
 * to {@link TestNamedQueryConfig#handler()}, overriding the default bean for the registry class.
 *
 * @see TestNamedQueryConfig
 */
public class TestNamedQueryRegistry implements NamedQueryRegistry {

    public static final NamedQueryDefinition FOLDER_LISTING_TEST = new NamedQueryDefinition("folder_listing_test", null, WhereClauseDTO.of( //
            DEFAULT_SELECT_STATEMENT + " WHERE sys_parentId = :parentId AND " + BASIC_FILTER_CLAUSE, //
            List.of(FULLTEXT_PREDICATE)), List.of(TITLE_SORT), null);

    public static final NamedQueryDefinition FOLDER_LISTING_TEST2 = new NamedQueryDefinition("folder_listing_test2", null,
            WhereClauseDTO.of( //
                    DEFAULT_SELECT_STATEMENT + " WHERE sys_parentId = :parentId AND " + BASIC_FILTER_CLAUSE, //
                    List.of(FULLTEXT_PREDICATE)),
            List.of(TITLE_SORT), null);

    // static build of queries map

    static final List<NamedQueryDefinition> NAMED_QUERIES = List.of( //
            // added for tests
            FOLDER_LISTING_TEST //
    );

    static final Map<String, NamedQueryDefinition> NAMED_QUERIES_MAP = new HashMap<>();

    static {
        NAMED_QUERIES.forEach(v -> {
            NAMED_QUERIES_MAP.put(v.name(), v);
            v.aliases().forEach(a -> NAMED_QUERIES_MAP.put(a, v));
        });
    }

    protected final DefaultNamedQueryRegistry defaultRegistry;

    public TestNamedQueryRegistry(DefaultNamedQueryRegistry defaultRegistry) {
        this.defaultRegistry = defaultRegistry;
    }

    @Override
    public Map<String, NamedQueryDefinition> getNamedQueries() {
        Map<String, NamedQueryDefinition> all = new HashMap<>();
        all.putAll(defaultRegistry.getNamedQueries());
        all.putAll(NAMED_QUERIES_MAP);
        return all;
    }

    @Override
    public NamedQueryDefinition getNamedQuery(String name) {
        if (NAMED_QUERIES_MAP.containsKey(name)) {
            return NAMED_QUERIES_MAP.get(name);
        }
        return defaultRegistry.getNamedQuery(name);
    }

}
