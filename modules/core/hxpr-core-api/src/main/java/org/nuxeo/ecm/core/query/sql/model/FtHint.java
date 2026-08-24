/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.sql.model;

import java.util.List;

/**
 * A full-text hint attached to a {@link Reference}, written {@code /*+FT: ... *}{@code /} in NXQL.
 * <p>
 * This is not an operand: it decorates a reference rather than standing on its own.
 */
public record FtHint(String index, String analyzer, String operator) {

    /** The hinted index fields, parsed into names and boosts. */
    public List<FieldHint> indexFields() {
        if (index == null) {
            return List.of();
        }
        return List.of(index.split(",")).stream().map(FieldHint::parse).toList();
    }

    /** The hinted index field names, without their boosts. */
    public String[] indexFieldNames() {
        return indexFields().stream().map(FieldHint::field).toArray(String[]::new);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("/*+FT: ");
        if (index != null) {
            sb.append("INDEX(").append(index).append(") ");
        }
        if (analyzer != null) {
            sb.append("ANALYZER(").append(analyzer).append(") ");
        }
        if (operator != null) {
            sb.append("OPERATOR(").append(operator).append(") ");
        }
        return sb.append("*/").toString();
    }

    /**
     * A field named by a hint, with an optional boost.
     */
    public record FieldHint(String field, float boost) {

        public static final float DEFAULT_BOOST = 1.0F;

        /** Parses {@code field} or {@code field^boost}. */
        public static FieldHint parse(String indexField) {
            String[] parsed = indexField.split("\\^");
            return new FieldHint(parsed[0], parsed.length > 1 ? Float.parseFloat(parsed[1]) : DEFAULT_BOOST);
        }
    }

}
