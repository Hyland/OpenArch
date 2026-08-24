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

import org.nuxeo.ecm.core.query.sql.HXQLSerializer;

import java.util.Objects;

/**
 * A boolean-valued expression: an operator applied to one or two operands.
 * <p>
 * Every expression in a query is a predicate, HXQL having no arithmetic, so this is the only expression node besides
 * {@link MultiExpression}, which is an operator applied to a whole list of them.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public sealed class Predicate implements Operand permits MultiExpression {

    public final Operator operator;

    public final Operand lvalue;

    /** {@code null} for a unary operator. */
    public final Operand rvalue;

    public Predicate(Operand lvalue, Operator operator, Operand rvalue) {
        this.lvalue = lvalue;
        this.rvalue = rvalue;
        this.operator = operator;
    }

    @Override
    public String toString() {
        return HXQLSerializer.serialize(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        // a MultiExpression is never equal to a plain predicate, it compares its own list too
        if (!(obj instanceof Predicate other) || obj.getClass() != getClass()) {
            return false;
        }
        return operator.equals(other.operator) && Objects.equals(lvalue, other.lvalue)
                && Objects.equals(rvalue, other.rvalue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, lvalue, rvalue);
    }

}
