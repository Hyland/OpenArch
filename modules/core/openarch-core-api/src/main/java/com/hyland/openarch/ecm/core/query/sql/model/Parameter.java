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
package com.hyland.openarch.ecm.core.query.sql.model;

import com.hyland.openarch.ecm.core.query.sql.HXQLSerializer;

/**
 * A named query parameter, written {@code :name} in NXQL.
 * <p>
 * A parameter stands in for a value that is not known when the query is written. It must be replaced by a
 * {@link Literal} or a {@link LiteralList} before the query can be executed; see {@code QueryParameterBinder}. A
 * parameter therefore never reaches a query visitor, and {@link #accept} says so rather than silently doing nothing.
 *
 * @since 12.0
 */
public record Parameter(String name) implements Operand {

    public Parameter {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Parameter name cannot be empty");
        }
    }

    @Override
    public String toString() {
        return HXQLSerializer.serialize(this);
    }

}
