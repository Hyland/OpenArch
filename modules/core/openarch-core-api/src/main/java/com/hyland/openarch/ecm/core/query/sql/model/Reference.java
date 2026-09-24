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

import com.hyland.openarch.ecm.core.query.sql.HXQLSerializer;

/**
 * A reference to a field, optionally cast or carrying a full-text hint.
 * <p>
 * A reference never has both a cast and a hint: the grammar offers one or the other.
 */
public record Reference(String name, String cast, FtHint ftHint) implements Operand {

    public Reference(String name) {
        this(name, null, null);
    }

    /** @since 5.6 */
    public Reference(String name, String cast) {
        this(name, cast, null);
    }

    /** @since 5.6 */
    public Reference(Reference other, String cast) {
        this(other.name, cast, null);
    }

    /** @since 7.3 */
    public Reference(Reference other, FtHint hint) {
        this(other.name, null, hint);
    }

    @Override
    public String toString() {
        return HXQLSerializer.serialize(this);
    }

}
