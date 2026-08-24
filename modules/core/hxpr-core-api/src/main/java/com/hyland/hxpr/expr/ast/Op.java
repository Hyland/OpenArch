/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.expr.ast;

public enum Op {

    ADD("+"), //
    SUB("-"), //
    MUL("*"), //
    DIV("/"), //
    EQ("=="), //
    NOTEQ("!="), //
    LT("<"), //
    GT(">"), //
    LTEQ("<="), //
    GTEQ(">="), //
    AND("&&"), //
    OR("||"), //
    NOT("!");

    private final String pretty;

    private Op(String pretty) {
        this.pretty = pretty;
    }

    public String toStringPretty() {
        return pretty;
    }

}
