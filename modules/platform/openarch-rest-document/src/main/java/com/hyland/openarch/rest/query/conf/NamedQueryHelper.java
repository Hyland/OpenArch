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
package com.hyland.openarch.rest.query.conf;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_POS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITIONS_CONTAINER_DOCUMENT_TYPE;
import static com.hyland.openarch.ecm.core.api.RenditionsConstants.RENDITION_DOCUMENT_TYPE;
import static com.hyland.openarch.rest.query.spec.QueryAssembler.DEFAULT_SELECT_STATEMENT;
import static com.hyland.openarch.rest.query.spec.QueryAssembler.SORTED_COLUMN;

import java.util.List;

import com.hyland.openarch.rest.query.io.QuickFilterDTO;
import com.hyland.openarch.rest.query.io.WhereClauseDTO;
import com.hyland.openarch.rest.query.io.WhereClauseDTO.RestPredicate;
import com.hyland.openarch.rest.query.spec.NamedQueryDefinition;

/**

 */
public class NamedQueryHelper {

    private NamedQueryHelper() {
        // utility class
    }

    // sorts

    public static final String TITLE_SORT = SYS_TITLE;

    public static final String MODIFIED_SORT = "sys_modified desc";

    public static final String POS_SORT = SYS_POS;

    // clauses

    // Keep the documents the rendition service creates out of the named query results; plain HXQL still finds them.
    public static final String BASIC_FILTER_CLAUSE = "%s=0 AND %s NOT IN ('%s', '%s')".formatted(SYSVER_IS_VERSION,
            SYS_PRIMARY_TYPE, RENDITION_DOCUMENT_TYPE, RENDITIONS_CONTAINER_DOCUMENT_TYPE);

    // predicates

    public static final RestPredicate FULLTEXT_PREDICATE = RestPredicate.of(SYS_FULLTEXT, "FULLTEXT", "fulltext");

    public static final RestPredicate TITLE_FULLTEXT_PREDICATE = RestPredicate.of(SYS_TITLE, "FULLTEXT", "title");

    public static final RestPredicate PARENT_PREDICATE = RestPredicate.of(SYS_PARENT_ID, "=", "parentId");

    public static final RestPredicate CREATED_PREDICATE = RestPredicate.of("sys_created", "BETWEEN", "created_min",
            "created_max");

    public static final RestPredicate MODIFIED_PREDICATE = RestPredicate.of("sys_modified", "BETWEEN", "modified_min",
            "modified_max");

    public static final RestPredicate PATH_PREDICATE = RestPredicate.of(SYS_PATH, "STARTSWITH", "path");

    // quick filters

    public static final QuickFilterDTO NO_FOLDER_FILTER = QuickFilterDTO.of("noFolder",
            "%s!='SysFolderish'".formatted(SYS_MIXIN_TYPES));

    public static final QuickFilterDTO MOST_RECENT_FILTER = QuickFilterDTO.of("mostRecent", null,
            List.of(MODIFIED_SORT));

    // queries

    public static final NamedQueryDefinition FOLDER_LISTING = new NamedQueryDefinition("folder_listing",
            List.of("document_content"), //
            WhereClauseDTO.of( //
                    "%s WHERE %s=:parentId AND %s".formatted(DEFAULT_SELECT_STATEMENT, SYS_PARENT_ID,
                            BASIC_FILTER_CLAUSE), //
                    List.of(FULLTEXT_PREDICATE)), //
            List.of(TITLE_SORT), null);

    public static final NamedQueryDefinition ADVANCED_FOLDER_LISTING = new NamedQueryDefinition(
            "advanced_folder_listing",
            List.of("advanced_document_content"), //
            WhereClauseDTO.of( //
                    DEFAULT_SELECT_STATEMENT + " WHERE " + BASIC_FILTER_CLAUSE, //
                    List.of(TITLE_FULLTEXT_PREDICATE, PARENT_PREDICATE)), List.of(TITLE_SORT), //
            null);

    public static final NamedQueryDefinition TREE_CHILDREN = new NamedQueryDefinition("tree_children", null, //
            WhereClauseDTO.of( //
                    "%s WHERE %s=:parentId AND %s='SysFolderish' AND %s".formatted(DEFAULT_SELECT_STATEMENT,
                            SYS_PARENT_ID, SYS_MIXIN_TYPES, BASIC_FILTER_CLAUSE), //
                    null), //
            List.of(POS_SORT, TITLE_SORT), null);

    public static final NamedQueryDefinition DOCUMENT_SUGGESTION = new NamedQueryDefinition(
            "default_document_suggestion", null, //
            WhereClauseDTO.of( //
                    "%s WHERE %s=:fulltext AND %s".formatted(DEFAULT_SELECT_STATEMENT, SYS_FULLTEXT, //
                            BASIC_FILTER_CLAUSE), null),
            null, // sort by fulltext relevance
            null);

    public static final NamedQueryDefinition SIMPLE_SEARCH = new NamedQueryDefinition("simple_search", null, //
            WhereClauseDTO.of( //
                    "%s WHERE %s IS NOT NULL AND %s IS NOT NULL AND %s".formatted(DEFAULT_SELECT_STATEMENT,
                            SYS_PARENT_ID, SORTED_COLUMN, BASIC_FILTER_CLAUSE), //
                    List.of(FULLTEXT_PREDICATE)),
            null, // sort by fulltext relevance
            null);

    public static final NamedQueryDefinition ADVANCED_SEARCH = new NamedQueryDefinition("advanced_search", null, //
            WhereClauseDTO.of( //
                    "%s WHERE %s IS NOT NULL AND %s IS NOT NULL AND %s".formatted(DEFAULT_SELECT_STATEMENT,
                            SYS_PARENT_ID, SORTED_COLUMN, BASIC_FILTER_CLAUSE), //
                    List.of(FULLTEXT_PREDICATE, CREATED_PREDICATE, MODIFIED_PREDICATE, PATH_PREDICATE)),
            null, // sort by fulltext relevance
            List.of(NO_FOLDER_FILTER, MOST_RECENT_FILTER));

}
