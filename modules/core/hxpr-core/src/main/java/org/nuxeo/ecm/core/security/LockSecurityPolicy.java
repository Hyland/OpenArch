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

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;

import java.util.List;
import java.util.Set;

import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.LockSecurityPolicyConfig;

/**
 * Lock security policy.
 */
public class LockSecurityPolicy extends AbstractSecurityPolicy {

    public static final String LOCK_ENGINE_NAME = "lock";

    private static final String LOCK_POLICY_NAME = "lock";

    private static final ThreadLocal<Boolean> IGNORE_LOCK_POLICY = new ThreadLocal<>();

    public static void setIgnoreLockPolicy(boolean ignore) {
        if (ignore) {
            IGNORE_LOCK_POLICY.set(TRUE);
        } else {
            IGNORE_LOCK_POLICY.remove();
        }
    }

    private static boolean isIgnoreLockPolicy() {
        Boolean ignore = IGNORE_LOCK_POLICY.get();
        if (ignore == null) {
            // don't leave an allocated null in the thead-local
            IGNORE_LOCK_POLICY.remove();
        }
        return TRUE.equals(ignore);
    }

    public LockSecurityPolicy(String repositoryName, LockSecurityPolicyConfig config) {
        this(repositoryName, LOCK_POLICY_NAME, config, null);
    }

    public LockSecurityPolicy(String repositoryName, String name, LockSecurityPolicyConfig config,
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
        if (!LOCK_POLICY_NAME.equals(name)) {
            SecurityPolicyViolation.add(violations, ValidationViolation.Type.ERROR,
                    "There can be only one lock policy, named: {0}", LOCK_POLICY_NAME);
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
        if (principalBypass(doc, principal)) {
            return false;
        }
        return permissions.contains(WRITE) && !isIgnoreLockPolicy();
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        Lock lock = doc.getLock();
        if (lock == null || principal.getId().equals(lock.getOwner())) {
            // a locked document can only be written by the lock owner
            return true;
        }
        // locked by another user => deny write
        return false;
    }

    @Override
    public void restrictEffectivePermissions(List<String> effectivePermissions, InternalDocument doc,
            NuxeoPrincipal principal, Set<String> principals, SecurityService securityService) {
        if (principal.isAdministrator()) {
            return;
        }
        super.restrictEffectivePermissions(effectivePermissions, doc, principal, principals, securityService);
    }

    @Override
    public Predicate getQueryClause(QueryParameters queryParams, Set<String> principals) {
        return null;
    }

}
