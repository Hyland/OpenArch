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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.ACLSecurityPolicyConfig;

/**
 * Default ACL-based security policy.
 */
public class ACLSecurityPolicy extends AbstractSecurityPolicy {

    public static final String ACL_ENGINE_NAME = "acl";

    private static final String ACL_POLICY_NAME = "acl";

    public ACLSecurityPolicy(String repositoryName, ACLSecurityPolicyConfig config) {
        this(repositoryName, ACL_POLICY_NAME, config, null);
    }

    public ACLSecurityPolicy(String repositoryName, String name, ACLSecurityPolicyConfig config,
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
        if (!ACL_POLICY_NAME.equals(name)) {
            SecurityPolicyViolation.add(violations, ValidationViolation.Type.ERROR,
                    "There can be only one ACL policy, named: {0}", ACL_POLICY_NAME);
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
        return !principalBypass(doc, principal);
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        ACL effectiveAcl = doc.getEffectiveACL();
        return hasPermission(effectiveAcl, principals, permissions);
    }

    private boolean hasPermission(ACL effectiveAcl, Set<String> principals, Set<String> permissions) {
        if (!effectiveAcl.isEmpty()) {
            return effectiveAcl.getAccess(principals, permissions).toBoolean();
        }
        // no ACL on that doc, including inheritance - by default deny
        return false;
    }

    @Override
    public void restrictEffectivePermissions(List<String> effectivePermissions, InternalDocument doc,
            NuxeoPrincipal principal, Set<String> principals, SecurityService securityService) {
        if (principalBypass(doc, principal)) {
            return;
        }
        // don't delegate to AbstractSecurityPolicy
        // because we want to avoid recomputing the effective ACL several times
        // so we use our specialized hasPermission method
        ACL effectiveAcl = doc.getEffectiveACL();
        for (ListIterator<String> it = effectivePermissions.listIterator(); it.hasNext();) {
            String permission = it.next();
            // if the permission is a group, all sub-permissions must be granted
            for (String perm : securityService.getAllPermissionsContainedIn(permission)) {
                Set<String> permissions = securityService.getAllPermissionsContaining(perm);
                // no need to check appliesToDocument() here, we already checked principalBypass()
                // call specialized hasPermission() with precomputed effective acl
                if (!hasPermission(effectiveAcl, principals, permissions)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public void checkAllowedPropertyUpdate(String xpath) {
        if (xpath.equals(SYS_ACL)) {
            throw new DocumentSecurityException("Property update is not allowed: " + SYS_ACL);
        }
    }

    @Override
    public Predicate getQueryClause(QueryParameters queryParams, Set<String> principals) {
        LiteralList list = new LiteralList();
        principals.forEach(p -> list.add(new StringLiteral(p)));
        return new Predicate(new Reference(SYS_READ_ACL), Operator.IN, list);
    }

}
