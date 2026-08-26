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
package org.nuxeo.ecm.core.query;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The syntax a query, or a condition, is expressed in.
 */
@Schema(enumAsRef = true)
public enum QuerySyntax {

    /** HXQL, for instance {@code SELECT * FROM SysContent WHERE sys_title = 'foo'}. */
    HXQL(QuerySyntax.HXQL_VALUE),

    /** The expression syntax, for instance {@code content.sys_title == "foo"}. */
    EXPR(QuerySyntax.EXPR_VALUE);

    public static final String HXQL_VALUE = "hxql";

    public static final String EXPR_VALUE = "expr";

    private final String value;

    QuerySyntax(String value) {
        this.value = value;
    }

    /** The name used in JSON. Parsing is case-insensitive. */
    @JsonValue
    public String value() {
        return value;
    }

    /** The default syntax, used when none is specified. */
    public static QuerySyntax defaulted(QuerySyntax syntax) {
        return syntax == null ? HXQL : syntax;
    }

    /** The syntax, or {@code null} if it is the default one and therefore goes without saying. */
    public static QuerySyntax nonDefault(QuerySyntax syntax) {
        return syntax == HXQL ? null : syntax;
    }

}
