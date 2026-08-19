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
package org.nuxeo.ecm.core.api;

import static java.util.Objects.requireNonNull;

/**
 * A condition under which a document update or move is done.
 *
 * @param syntax the syntax in which the condition is expressed
 * @param condition the condition
 */
public record UpdateCondition(Syntax syntax, String condition) {

    /** The syntax in which an {@link UpdateCondition} is expressed. */
    public enum Syntax {
        /** HXQL predicate, for instance {@code sys_title = 'foo'}. */
        HXQL,
        /** Expression syntax, for instance {@code content.sys_title == "foo"}. */
        EXPR,
    }

    public UpdateCondition {
        requireNonNull(syntax, "syntax");
        requireNonNull(condition, "condition");
    }

    /**
     * Returns a condition expressed as an HXQL predicate.
     *
     * @param hxql the HXQL predicate
     */
    public static UpdateCondition hxql(String hxql) {
        return new UpdateCondition(Syntax.HXQL, hxql);
    }

    /**
     * Returns a condition expressed in the expression syntax.
     *
     * @param expr the expression
     */
    public static UpdateCondition expr(String expr) {
        return new UpdateCondition(Syntax.EXPR, expr);
    }
}
