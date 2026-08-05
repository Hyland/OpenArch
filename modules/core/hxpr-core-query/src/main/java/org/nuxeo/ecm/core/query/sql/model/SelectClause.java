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

import java.util.Collection;

import com.google.common.collect.Iterables;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class SelectClause extends Clause {

    public final SelectList elements;

    public SelectClause() {
        this(new SelectList());
    }

    public SelectClause(SelectList elements) {
        super("SELECT");
        this.elements = elements;
    }

    public void add(String alias, Operand element) {
        elements.put(alias, element);
    }

    public void add(Operand element) {
        elements.put(element.toString(), element);
    }

    public Operand get(String alias) {
        return elements.get(alias);
    }

    public Reference getVariable(String alias) {
        return (Reference) elements.get(alias);
    }

    public Literal getLiteral(String alias) {
        return (Literal) elements.get(alias);
    }

    public Function getFunction(String alias) {
        return (Function) elements.get(alias);
    }

    public Expression getExpression(String alias) {
        return (Expression) elements.get(alias);
    }

    public Operand get(int i) {
        return Iterables.get(elements.values(), i);
    }

    public String getAlias(int i) {
        return Iterables.get(elements.keySet(), i);
    }

    public Reference getVariable(int i) {
        return (Reference) get(i);
    }

    public Literal getLiteral(int i) {
        return (Literal) get(i);
    }

    public Function getFunction(int i) {
        return (Function) get(i);
    }

    public Expression getExpression(int i) {
        return (Expression) get(i);
    }

    public SelectList getSelectList() {
        return elements;
    }

    public int count() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * @since 9.1
     */
    public Collection<Operand> operands() {
        return elements.values();
    }

    /**
     * @since 9.1
     */
    public boolean containsOperand(Object operand) {
        return elements.containsValue(operand);
    }

    @Override
    public void accept(IVisitor visitor) {
        visitor.visitSelectClause(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof SelectClause sc) {
            return elements.equals(sc.elements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public String toString() {
        return elements.toString();
    }

}
