/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.query.sql.model;

import static java.util.stream.Collectors.joining;

import java.util.List;

/**
 * The projection: what a query returns. An empty projection means {@code SELECT *}.
 */
public record SelectClause(List<Operand> elements) implements ASTNode {

    public SelectClause {
        elements = List.copyOf(elements);
    }

    public SelectClause() {
        this(List.of());
    }

    @Override
    public String toString() {
        return elements.isEmpty() ? "*" : elements.stream().map(Operand::toString).collect(joining(", "));
    }

}
