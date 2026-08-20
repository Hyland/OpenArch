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
package org.nuxeo.ecm.core.query.sql.model;

import java.util.Iterator;
import java.util.List;

import org.nuxeo.ecm.core.query.sql.HXQLSerializer;

/**
 * A parenthesised list of values, as used by {@code IN} and {@code BETWEEN}.
 */
public record LiteralList(List<Literal> elements) implements Operand, Iterable<Literal> {

    public LiteralList {
        elements = List.copyOf(elements);
    }

    public LiteralList() {
        this(List.of());
    }

    public Literal get(int i) {
        return elements.get(i);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    @Override
    public Iterator<Literal> iterator() {
        return elements.iterator();
    }

    @Override
    public String toString() {
        return HXQLSerializer.serialize(this);
    }

}
