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
package org.nuxeo.ecm.core.query.sql;

import java.util.Map;

import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.Statement;
import org.nuxeo.ecm.core.query.sql.parser.HXQLVisitor;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public final class HXQLParser {

    // Utility class
    private HXQLParser() {
    }

    public static Statement parse(String string) throws QueryParseException {
        return new HXQLVisitor().parse(string);
    }

    /**
     * Parses a query and binds its named parameters, the {@code :name} placeholders standing in for values.
     * <p>
     * Binding belongs with parsing: a parameter is bound on the tree, where a value cannot be read as syntax, and a
     * tree that kept its parameters is not executable. So whoever holds the values parses with them.
     *
     * @throws QueryParseException if the query references a parameter that has no value, or a value cannot be used
     *             where its parameter appears
     * @since 12.0
     */
    public static Statement parse(String string, Map<String, Object> parameters) throws QueryParseException {
        return QueryParameterBinder.bind(parse(string), parameters);
    }

    /**
     * Parses a bare {@code WHERE} clause fragment, without the {@code WHERE} keyword.
     * <p>
     * This is how a clause written on its own, such as a query configuration's quick filter, gets into a tree.
     *
     * @since 12.0
     */
    public static Predicate parsePredicate(String clause) throws QueryParseException {
        return new HXQLVisitor().parsePredicate(clause);
    }

    /**
     * Parses a field name on its own.
     * <p>
     * A field name goes into a query as an identifier, which NXQL has no way to quote, so a name coming from a request
     * has to be checked against the grammar rather than escaped.
     *
     * @since 12.0
     */
    public static Reference parseReference(String name) throws QueryParseException {
        return new HXQLVisitor().parseReference(name);
    }

}
