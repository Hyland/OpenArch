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

/**
 * @author Florent Guillaume
 */
public class OrderByExpr implements Operand {

    public final Reference reference;

    public final boolean isDescending;

    public OrderByExpr(Reference reference, boolean isDescending) {
        this.reference = reference;
        this.isDescending = isDescending;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isDescending ? 1231 : 1237);
        result = prime * result + (reference == null ? 0 : reference.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof OrderByExpr orderByExpr) {
            return equals(orderByExpr);
        }
        return false;
    }

    private boolean equals(OrderByExpr other) {
        if (isDescending != other.isDescending) {
            return false;
        }
        if (reference == null) {
            if (other.reference != null) {
                return false;
            }
        } else if (!reference.equals(other.reference)) {
            return false;
        }
        return true;
    }

    @Override
    public void accept(IVisitor visitor) {
        visitor.visitOrderByExpr(this);
    }

    @Override
    public String toString() {
        if (isDescending) {
            return reference.toString() + " DESC";
        } else {
            return reference.toString();
        }
    }

}
