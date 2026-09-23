/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.security.expr;

import java.io.Serializable;
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
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.AbstractSecurityPolicy;
import com.hyland.hxpr.expr.ExprEvaluator;
import com.hyland.hxpr.expr.ExpressionVisitor;
import com.hyland.hxpr.expr.ast.Expr;

public class UserAttributeExpressionSecurityPolicy extends AbstractSecurityPolicy {

    private static final Logger log = LogManager.getLogger(UserAttributeExpressionSecurityPolicy.class);

    public static final String USER_ATTRIBUTE_EXPRESSION_ENGINE_NAME = "userAttributeExpression";

    public static final String SECURITY_EXPRESSION_ATTRIBUTE_NAME = "_SecurityExpression";

    private static final String FAILED_TO_EVALUATE_ON_SECURITY_POLICY = "Failed to evaluate: {} on security policy: {}: {}";

    private static final String USER_ATTRIBUTE_EXPRESSION_POLICY_NAME = "userAttributeExpression";

    public UserAttributeExpressionSecurityPolicy(String repositoryName,
            UserAttributeExpressionSecurityPolicyConfig config) {
        this(repositoryName, USER_ATTRIBUTE_EXPRESSION_POLICY_NAME, config, null);
    }

    public UserAttributeExpressionSecurityPolicy(String repositoryName, String name,
            UserAttributeExpressionSecurityPolicyConfig config, List<ValidationViolation> violations) {
        super(repositoryName, name, config, violations);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    protected void checkContentTypes(List<ValidationViolation> violations) {
        checkNoContentTypes(violations);
    }

    @Override
    protected boolean checkMixin(List<ValidationViolation> violations) {
        return checkNoMixin(violations);
    }

    private Object getUserAttributeSecurityExpression(NuxeoPrincipal principal) {
        Map<String, Serializable> userAttributes = principal.getUserAttributes();
        return userAttributes != null ? userAttributes.get(SECURITY_EXPRESSION_ATTRIBUTE_NAME) : null;
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        if (principal.isAdministrator()) {
            return true;
        }

        var context = ExprEvaluator.documentContext(doc, principal);

        Object extSecExpr = getUserAttributeSecurityExpression(principal);
        if (!(extSecExpr instanceof String expressionString)) {
            return true;
        }
        try {
            Expr extExpr = new ExpressionVisitor().parse(expressionString);
            Object value = new ExprEvaluator(context, getSchemaManager()).evaluateValue(extExpr);
            if (value == null) {
                return false;
            } else if (value instanceof Boolean bool) {
                return bool;
            }
            log.error(FAILED_TO_EVALUATE_ON_SECURITY_POLICY, expressionString, getName(), value);
        } catch (IllegalArgumentException e) {
            log.error(FAILED_TO_EVALUATE_ON_SECURITY_POLICY, expressionString, getName(), e.getMessage());
        }
        return false;
    }

    @Override
    public Operand getQueryClause(QueryParameters queryParams, Set<String> principals) {
        var context = ExprEvaluator.queryContext(queryParams.principal());
        Object extSecExpr = getUserAttributeSecurityExpression(queryParams.principal());
        if (!(extSecExpr instanceof String expressionString)) {
            return BooleanLiteral.LIT_TRUE;
        }
        try {
            Expr extExpr = new ExpressionVisitor().parse(expressionString);
            Operand operand = new ExprEvaluator(context, getSchemaManager()).evaluateOperand(extExpr);
            if (operand instanceof Predicate || operand instanceof BooleanLiteral) {
                return operand;
            }
            log.error(FAILED_TO_EVALUATE_ON_SECURITY_POLICY, expressionString, getName(), operand);
        } catch (IllegalArgumentException e) {
            log.error(FAILED_TO_EVALUATE_ON_SECURITY_POLICY, expressionString, getName(), e.getMessage());
        }
        return BooleanLiteral.LIT_FALSE;
    }
}
