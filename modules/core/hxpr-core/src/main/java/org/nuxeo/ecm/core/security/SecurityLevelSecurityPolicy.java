/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.security;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;

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
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation.Type;
import org.nuxeo.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;

public class SecurityLevelSecurityPolicy extends AbstractSecurityPolicy {

    private static final Logger log = LogManager.getLogger(SecurityLevelSecurityPolicy.class);

    public static final String SECURITY_LEVEL_ENGINE_NAME = "securitylevel";

    private static final String FIELD_NAME_USER_ATTRIBUTE = "userAttribute";

    // cache for fast access
    private String securityProperty;

    public SecurityLevelSecurityPolicy(String repositoryName, String name, SecurityLevelSecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        super(repositoryName, name, config, violations);
        checkUserAttribute(violations);
    }

    private void checkUserAttribute(List<ValidationViolation> violations) {
        isBlankValue(Type.ERROR, FIELD_NAME_USER_ATTRIBUTE, getUserAttribute(), violations);

        // We can't check that the userAttribute exists in the IdP, so it might encounter an issue when trying to use it
    }

    private String getUserAttribute() {
        return getConfig().getUserAttribute();
    }

    @Override
    public SecurityLevelSecurityPolicyConfig getConfig() {
        return (SecurityLevelSecurityPolicyConfig) super.getConfig();
    }

    private String getSecurityProperty() {
        if (securityProperty == null) {
            Set<String> properties = getSecurityProperties();
            securityProperty = properties.size() == 1 ? properties.iterator().next() : null;
        }
        return securityProperty;
    }

    @Override
    protected boolean principalBypass(InternalDocument doc, NuxeoPrincipal principal) {
        return super.principalBypass(doc, principal)
                // creator also bypasses
                || principal.getId().equals(doc.getPropertyValue(SYS_CREATOR));
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        Long principalLevel = getPrincipalLevel(principal);
        if (principalLevel == null) {
            // deny if user does not have access level
            return false;
        }
        String property = getSecurityProperty();
        if (property == null) {
            // deny if property is unknown or the mixin contains several properties
            log.error("SecurityLevelSecurityPolicy {} has invalid mixin {}", getName(), getMixin());
            return false;
        }
        Long docLevel = (Long) doc.getPropertyValue(property);
        if (docLevel == null) {
            // allow if document does not have a set value for the access level property
            return true;
        }
        return principalLevel >= docLevel;
    }

    @Override
    public Operand getQueryClause(QueryParameters queryParams, Set<String> principals) {
        NuxeoPrincipal principal = queryParams.principal();
        String principalId = principal.getId();
        Long principalLevel = getPrincipalLevel(principal);
        String property = getSecurityProperty();
        if (property == null) {
            // deny if property is unknown or the mixin contains several properties
            log.error("SecurityLevelSecurityPolicy {} has invalid mixin {}", getName(), getMixin());
            return BooleanLiteral.LIT_FALSE;
        }

        // documents created by the principal (sys_creator = '{principalId}')
        Predicate isCreator = Predicates.eq(SYS_CREATOR, principalId);
        // documents that do not specify access level ({businessProperty} IS NULL)
        Predicate docNoLevel = Predicates.isnull(property);
        Predicate clause = Predicates.or(isCreator, docNoLevel);
        if (principalLevel != null) {
            // documents with access level <= the principal's ({businessProperty} <= {principalLevel})
            Predicate principalHasLevel = Predicates.lte(property, principalLevel);
            clause = Predicates.or(clause, principalHasLevel);
        }
        // examples: (assuming businessProperty = docLevel)
        // 1. (sys_creator = 'alice') OR (docLevel IS NULL), if alice has no principalLevel
        // 2. ((sys_creator = 'bob') OR (docLevel IS NULL)) OR (docLevel <= 2), if bob has principalLevel = 2
        return clause;
    }

    private Long getPrincipalLevel(NuxeoPrincipal principal) {
        Map<String, Object> properties = principal.getProperties();
        String userAttribute = getUserAttribute();
        if (properties == null || !properties.containsKey(userAttribute)) {
            return null;
        }
        Object value = properties.get(userAttribute);
        if (value instanceof String stringValue) {
            return Long.valueOf(stringValue);
        }
        if (value instanceof Number numericValue) {
            return numericValue.longValue();
        }
        log.debug("Principal {} has invalid attribute {} = {}", principal.getId(), userAttribute, value);
        return null;
    }
}
