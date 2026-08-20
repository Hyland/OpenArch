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
 * A node of an NXQL syntax tree.
 * <p>
 * The hierarchy is sealed, so code that walks a tree can switch over it exhaustively and the compiler will point at
 * every place that has to handle a new kind of node. There is no visitor: a walk is a recursive switch, which keeps
 * the traversal next to the work it does instead of spread over a framework.
 */
public sealed interface ASTNode permits Operand, SQLQuery, SelectClause, FromClause, WhereClause, OrderByClause, OrderByExpr {

}
