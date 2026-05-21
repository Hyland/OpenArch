/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.query.sql.model;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public enum Operator implements ASTNode {

    SUM("+"), //
    SUB("-"), //
    MUL("*"), //
    DIV("/"), //
    LT("<"), //
    GT(">"), //
    EQ("="), //
    NOTEQ("<>"), //
    LTEQ("<="), //
    GTEQ(">="), //
    AND("AND"), //
    NOT("NOT"), //
    OR("OR"), //
    LIKE("LIKE"), //
    ILIKE("ILIKE"), //
    IN("IN"), //
    BETWEEN("BETWEEN"), //
    NOTLIKE("NOT LIKE"), //
    NOTILIKE("NOT ILIKE"), //
    NOTIN("NOT IN"), //
    NOTBETWEEN("NOT BETWEEN"), //
    STARTSWITH("STARTSWITH"), //
    ISNULL("IS NULL"), //
    ISNOTNULL("IS NOT NULL");

    private String string;

    private Operator(String string) {
        this.string = string;
    }

    @Override
    public String toString() {
        return string;
    }

    @Override
    public void accept(IVisitor visitor) {
        visitor.visitOperator(this);
    }

}
