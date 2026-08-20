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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An expression for an single operator with an arbitrary number of operands.
 * <p>
 * It extends {@link Predicate} but it's really not a real Predicate (some users of Predicate expect it to have lvalue
 * and rvalue fields, which are null in this class).
 *
 * @author Florent Guillaume
 */
public final class MultiExpression extends Predicate {

    public final List<Predicate> predicates;

    public MultiExpression(Operator operator, List<Predicate> predicates) {
        super(null, operator, null);
        this.predicates = predicates;
    }

    /**
     * Copy constructor.
     *
     * @since 10.3
     */
    public MultiExpression(MultiExpression other) {
        this(other.operator, new ArrayList<>(other.predicates));
    }

    @Override
    public String toString() {
        if (predicates.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            // this is not really valid NXQL but sufficient for debugging
            sb.append(operator);
            sb.append("()");
            return sb.toString();
        }
        if (predicates.size() == 1) {
            // here debugging loses the operator, but this shouldn't be an issue for semantics
            return predicates.get(0).toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Iterator<Predicate> it = predicates.iterator(); it.hasNext();) {
            Predicate predicate = it.next();
            sb.append(predicate.toString());
            if (it.hasNext()) {
                sb.append(' ');
                sb.append(operator);
                sb.append(' ');
            }
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof MultiExpression multiExpression) {
            return equals(multiExpression);
        }
        return false;
    }

    protected boolean equals(MultiExpression other) {
        return predicates.equals(other.predicates) && super.equals(other);
    }

    @Override
    public int hashCode() {
        return predicates.hashCode() + super.hashCode();
    }

}
