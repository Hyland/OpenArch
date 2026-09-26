/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.query.io;

import static com.hyland.openarch.rest.query.conf.NamedQueryHelper.TITLE_SORT;
import static com.hyland.openarch.rest.query.io.WhereClauseDTO.RestPredicate;

import java.util.List;
import java.util.Map;

public abstract class AbstractQueryIOTest {

    protected static final String DEFAULT_SELECT_STATEMENT = "SELECT * FROM SysContent";

    protected static final String REPO_ID = "test";

    protected static final Long LIMIT = -1L;

    protected static final Integer OFFSET = 0;

    protected static final List<String> SORT = List.of(TITLE_SORT);

    protected static final Integer COUNT_UPTO = 0;

    protected static final Boolean TRACK_TOTAL_COUNT = true;

    protected static final Integer TRACK_TOTAL_COUNT_UPTO = 0;

    protected static final Map<String, Object> PARAMETERS = Map.of();

    protected static final RestPredicate predicates = RestPredicate.of("sys_fulltext", "FULL_TEXT", "fulltext");

}
