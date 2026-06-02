/*
 * (C) Copyright 2024 Hyland (http://www.hyland.com/) and others.
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

package org.nuxeo.ecm.core.security;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.DELETE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;

import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.RetentionSecurityPolicyConfig;

/**
 * Retention security policy.
 */
public class RetentionSecurityPolicy extends AbstractSecurityPolicy {

    public static final String RETENTION_ENGINE_NAME = "retention";

    private static final String RETENTION_POLICY_NAME = "retention";

    public RetentionSecurityPolicy(String repositoryName, RetentionSecurityPolicyConfig config) {
        this(repositoryName, RETENTION_POLICY_NAME, config, null);
    }

    public RetentionSecurityPolicy(String repositoryName, String name, RetentionSecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        super(repositoryName, name, config, violations);
        checkName(name, violations);
    }

    @Override
    protected boolean checkMixin(List<ValidationViolation> violations) {
        return checkNoMixin(violations);
    }

    @Override
    protected void checkContentTypes(List<ValidationViolation> violations) {
        checkNoContentTypes(violations);
    }

    private void checkName(String name, List<ValidationViolation> violations) {
        if (!RETENTION_POLICY_NAME.equals(name)) {
            SecurityPolicyViolation.add(violations, ValidationViolation.Type.ERROR,
                    "There can be only one retention policy, named: {0}", RETENTION_POLICY_NAME);
        }
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
    public boolean appliesToDocument(InternalDocument doc, NuxeoPrincipal principal, Set<String> permissions) {
        // even administrators are subject to retention: don't check principalBypass()
        return permissions.contains(DELETE) || permissions.contains(WRITE);
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        // a document cannot be deleted if under retention or legal hold
        return !doc.isUnderRetentionOrLegalHold() || InternalSession.canDeleteUndeletable(principal);
    }

    @Override
    public Predicate getQueryClause(QueryParameters queryParams, Set<String> principals) {
        return null;
    }

}
