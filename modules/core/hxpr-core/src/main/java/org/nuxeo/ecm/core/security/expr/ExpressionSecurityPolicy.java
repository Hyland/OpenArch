/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.security.expr;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation.Type;
import org.nuxeo.ecm.core.security.AbstractSecurityPolicy;
import org.nuxeo.ecm.core.security.SecurityPolicyViolation;
import org.nuxeo.ecm.core.security.expr.ast.Expr;

public class ExpressionSecurityPolicy extends AbstractSecurityPolicy {

    private static final Logger log = LogManager.getLogger(ExpressionSecurityPolicy.class);

    public static final String EXPRESSION_ENGINE_NAME = "expression";

    public static final String EXPRESSION_POLICY_EXPRESSION_KEY = "expression";

    public static final String CONTEXT_CONTENT = "content";

    public static final String CONTEXT_USER = "user";

    private final String expressionString;

    private final Expr expr;

    public ExpressionSecurityPolicy(String repositoryName, String name, ExpressionSecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        super(repositoryName, name, config, violations);
        expressionString = getExpression();
        this.expr = getExpr(violations);
    }

    private String getExpression() {
        return ((ExpressionSecurityPolicyConfig) getConfig()).getExpression();
    }

    private Expr getExpr(List<ValidationViolation> violations) {
        try {
            return new ExpressionVisitor().parse(expressionString);
        } catch (IllegalArgumentException e) {
            SecurityPolicyViolation.add(violations, Type.ERROR,
                    "Invalid expression: {0} on security policy: {1}: {2}", expressionString, getName(),
                    e.getMessage());
            return null;
        }
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        try {
            var context = Map.of( //
                    CONTEXT_CONTENT, doc, //
                    CONTEXT_USER, principal);
            Object value = new ExprEvaluator(context).evaluateValue(expr);
            if (value == null) {
                return false;
            } else if (value instanceof Boolean bool) {
                return bool;
            }
            log.error("Failed to evaluate: {} on security policy: {}: {}", expressionString, getName(), value);
        } catch (IllegalArgumentException e) {
            log.error("Failed to evaluate: {} on security policy: {}: {}", expressionString, getName(), e.getMessage());
        }
        return false;
    }

    @Override
    public Operand getQueryClause(QueryParameters queryParams, Set<String> principals) {
        try {
            var context = Map.of( //
                    CONTEXT_CONTENT, ExprEvaluator.CONTENT, //
                    CONTEXT_USER, queryParams.principal());
            Operand operand = new ExprEvaluator(context).evaluateOperand(expr);
            if (operand instanceof Predicate || operand instanceof BooleanLiteral) {
                return operand;
            }
            log.error("Failed to evaluate: {} on security policy: {}: {}", expressionString, getName(), operand);
        } catch (IllegalArgumentException e) {
            log.error("Failed to evaluate: {} on security policy: {}: {}", expressionString, getName(), e.getMessage());
        }
        return BooleanLiteral.LIT_FALSE;
    }

}
