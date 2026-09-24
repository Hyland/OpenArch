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

package com.hyland.openarch.ecm.core.security;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hyland.openarch.ecm.core.DocumentImpl;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Predicates;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation.Type;
import com.hyland.openarch.ecm.core.security.config.ACLSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.LockSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.RetentionSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.expr.UserAttributeExpressionSecurityPolicy;
import com.hyland.openarch.ecm.core.security.expr.UserAttributeExpressionSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession;
import com.hyland.openarch.ecm.core.storage.dbs.StateAccessor;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.openarch.config.Configurable;
import com.hyland.openarch.config.ConfigurationService;

/**
 * Implementation for security policy service (security for one repository).
 * <p>
 * It holds the list of configured security policies for a repository.
 */
public class SecurityPolicyServiceImpl implements SecurityPolicyService {

    private final SecurityService securityService;

    private final String repositoryName;

    private final List<SecurityPolicy> securityPolicies;

    private final Map<String, SecurityPolicyConfig> securityPolicyConfigMap;

    private final ACLDenormalizer aclDenormalizer;

    public SecurityPolicyServiceImpl(SecurityService securityService, String repositoryName,
            List<SecurityPolicy> securityPolicies) {
        this.securityService = securityService;
        this.repositoryName = repositoryName;
        this.securityPolicies = securityPolicies;
        securityPolicyConfigMap = securityPolicies.stream()
                                                  .collect(toMap(SecurityPolicy::getName, SecurityPolicy::getConfig));
        aclDenormalizer = new ACLDenormalizer(securityService);
    }

    /** Constructor for default instantiation. */
    public SecurityPolicyServiceImpl(SecurityService securityService, String repositoryName) {
        this(securityService, repositoryName, getDef(repositoryName));
    }

    /** Constructor instantiating via a Map. */
    public SecurityPolicyServiceImpl(SecurityService securityService, String repositoryName,
            Map<String, SecurityPolicyConfig> config, List<ValidationViolation> violations) {
        this(securityService, repositoryName, build(repositoryName, config, violations));
    }

    private static List<SecurityPolicy> getDef(String repositoryName) {
        return List.of( //
                new ACLSecurityPolicy(repositoryName, new ACLSecurityPolicyConfig()), //
                new LockSecurityPolicy(repositoryName, new LockSecurityPolicyConfig()), //
                new RetentionSecurityPolicy(repositoryName, new RetentionSecurityPolicyConfig()), //
                new UserAttributeExpressionSecurityPolicy(repositoryName, new UserAttributeExpressionSecurityPolicyConfig()));
    }

    private static final String WARNING_IMMUTABLE = "Security policy \"{0}\" may not be modified - ignored";

    private static final String ERROR_ALREADY_IN_USE = "Security policy \"{0}\" is already defined in another project or is system-wide";

    private static final String ERROR_PROJECT_CONFLICT = "Security policy \"{0}\" is has conflicting projects";

    @Override
    public void registerConfigurables() {
        securityPolicies.forEach(this::registerConfigurable);
    }

    @Override
    public void unregisterConfigurables() {
        securityPolicies.forEach(this::unregisterConfigurable);
    }

    private void registerConfigurable(SecurityPolicy policy) {
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            if (policy instanceof Configurable configurable) {
                configService.registerConfigurable(configurable);
            }
        }
    }

    private void  unregisterConfigurable(SecurityPolicy policy) {
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            if (policy instanceof Configurable configurable) {
                configService.unregisterConfigurable(configurable);
            }
        }
    }

    @Override
    public SecurityPolicyService applyUpdate(Map<String, SecurityPolicyConfig> configMap, String projectId,
            List<ValidationViolation> violations) {
        List<SecurityPolicy> newSecurityPolicies;
        if (configMap != null) {
            Map<String, SecurityPolicyConfig> newConfigMap = new HashMap<>(configMap); // don't change caller data

            Map<String, SecurityPolicyConfig> hidden = new HashMap<>();
            securityPolicies.forEach(sp -> {
                // hidden policies shouldn't be contributed, and are ignored with a warning
                if (sp.isHidden()) {
                    String name = sp.getName();
                    hidden.put(name, sp.getConfig()); // keep it for later
                    if (newConfigMap.remove(name) != null) {
                        violations.add(new SecurityPolicyViolation(Type.WARNING, WARNING_IMMUTABLE, name));
                    }
                }
            });
            if (projectId != null) {
                newConfigMap.replaceAll((name, sp) -> {
                    // check that old policies being overridden don't have a different project id
                    SecurityPolicyConfig oldConfig = securityPolicyConfigMap.get(name);
                    if (oldConfig != null && !projectId.equals(oldConfig.getProjectId())) {
                        violations.add(new SecurityPolicyViolation(Type.ERROR, ERROR_ALREADY_IN_USE, name));
                    }
                    // check that new policies don't have a project id conflicting with the global one
                    String policyProjectId = sp.getProjectId();
                    if (policyProjectId == null) {
                        // set proper project id on new policy
                        sp = sp.withProjectId(projectId);
                    } else if (!policyProjectId.equals(projectId)) {
                        violations.add(new SecurityPolicyViolation(Type.ERROR, ERROR_PROJECT_CONFLICT, name));
                    }
                    return sp;
                });
                // also re-add all other existing policies that don't belong to this project
                securityPolicyConfigMap.forEach((name, config) -> {
                    if (!projectId.equals(config.getProjectId())) {
                        newConfigMap.putIfAbsent(name, config);
                    }
                });
            }

            // always keep pre-existing hidden policies
            newConfigMap.putAll(hidden);

            // construct all policies (could add further violations)
            newSecurityPolicies = build(repositoryName, newConfigMap, violations);
        } else {
            newSecurityPolicies = securityPolicies;
        }

        return new SecurityPolicyServiceImpl(securityService, repositoryName, newSecurityPolicies);
    }

    private static List<SecurityPolicy> build(String repositoryName, Map<String, SecurityPolicyConfig> configMap,
            List<ValidationViolation> violations) {
        return configMap.entrySet()
                        .stream()
                        .map(e -> e.getValue().newSecurityPolicy(repositoryName, e.getKey(), violations))
                        .toList();
    }

    @Override
    public List<SecurityPolicy> getSecurityPolicies() {
        return securityPolicies;
    }

    @Override
    public Map<String, SecurityPolicyConfig> getSecurityPolicyConfigMap() {
        return securityPolicyConfigMap;
    }

    @Override
    public boolean hasPermission(InternalDocument doc, RepositoryPrincipal principal, String permission) {
        Set<String> principals = securityService.expandPrincipal(principal);
        Set<String> permissions = securityService.getAllPermissionsContaining(permission);
        for (SecurityPolicy sp : securityPolicies) {
            if (!sp.appliesToDocument(doc, principal, permissions)) {
                continue;
            }
            if (!sp.hasPermission(doc, principal, principals, permission, permissions)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<String> getSecurityPolicies(InternalDocument doc) {
        List<String> list = (List<String>) doc.getPropertyValue(SYS_SECURITY_POLICIES);
        if (list == null) {
            list = List.of();
        }
        return list;
    }

    @Override
    public void checkAllowedMixinUpdate(InternalDocument doc, RepositoryPrincipal principal, String mixin) {
        if (doc.hasPermission(principal, MANAGE_SECURITY)) {
            return;
        }
        for (SecurityPolicy securityPolicy : securityPolicies) {
            securityPolicy.checkAllowedMixinUpdate(mixin);
        }
    }

    @Override
    public void checkAllowedPropertyUpdate(InternalDocument doc, RepositoryPrincipal principal, String xpath) {
        if (doc.hasPermission(principal, MANAGE_SECURITY)) {
            return;
        }
        for (SecurityPolicy securityPolicy : securityPolicies) {
            securityPolicy.checkAllowedPropertyUpdate(xpath);
        }
    }

    @Override
    public Set<String> getEffectivePermissions(InternalDocument doc, RepositoryPrincipal principal) {
        Set<String> principals = securityService.expandPrincipal(principal);
        List<String> effectivePermissions = new ArrayList<>(securityService.getAllPermissions());
        for (SecurityPolicy sp : securityPolicies) {
            sp.restrictEffectivePermissions(effectivePermissions, doc, principal, principals, securityService);
        }
        return new HashSet<>(effectivePermissions);
    }

    @Override
    public void setSecurityPolicies(DocumentModel doc) {
        SchemaManager schemaManager = Framework.getService(TypeService.class).getSchemaManager(repositoryName);
        Set<String> docTypes = schemaManager.getDocumentTypeNamesExtendedBy(doc.getType());
        if (docTypes == null) {
            // unknown type, shouldn't happen
            return;
        }
        List<String> secPolNames = new ArrayList<>();
        for (SecurityPolicy securityPolicy : securityPolicies) {
            if (securityPolicy.isGlobal()) {
                // global security policies aren't set in sys_secpol
                continue;
            }
            if (!Collections.disjoint(docTypes, securityPolicy.getConfig().getContentTypes())) {
                secPolNames.add(securityPolicy.getName());
            }
        }
        if (!secPolNames.isEmpty()) {
            secPolNames.sort(null); // stable ordering
            InternalDocument idoc = ((DocumentImpl) doc).getInternalDocument();
            setSecurityPolicies(idoc, secPolNames);
        }
    }

    @Override
    public void setSecurityPolicies(InternalDocument doc, List<String> newSecurityPolicies) {
        List<String> oldSecurityPolicies = getSecurityPolicies(doc);
        if (newSecurityPolicies == null) {
            newSecurityPolicies = List.of();
        }
        // check that new policies are valid
        newSecurityPolicies.forEach(policy -> {
            if (!securityPolicyConfigMap.containsKey(policy)) {
                throw new IllegalArgumentException("Unknown security policy: " + policy);
            }
        });

        // map of policy to its non-null mixin (could be precomputed)
        Map<String, String> policyMixins = new HashMap<>();
        securityPolicyConfigMap.forEach((policy, config) -> {
            String mixin = config.getMixin();
            if (mixin != null) {
                policyMixins.put(policy, mixin);
            }
        });
        Set<String> oldMixins = doc.getEffectiveMixins();
        Set<String> newMixins = computeNewMixins(oldSecurityPolicies, newSecurityPolicies, oldMixins, policyMixins);

        // update the mixins on the document
        doc.setPrimaryTypeAndMixinTypes(null, newMixins);

        // set the security policies on the document
        if (newSecurityPolicies.isEmpty()) {
            newSecurityPolicies = null;
        }
        doc.setValue(SYS_SECURITY_POLICIES, newSecurityPolicies);
    }

    // public for tests
    public static Set<String> computeNewMixins(List<String> oldPolicies, List<String> newPolicies,
            Set<String> oldMixins, Map<String, String> policyMixins) {
        Set<String> oldPoliciesMixins = oldPolicies.stream()
                                                   .map(policyMixins::get)
                                                   .filter(Objects::nonNull)
                                                   .collect(toSet());
        Set<String> newPoliciesMixins = newPolicies.stream()
                                                   .map(policyMixins::get)
                                                   .filter(Objects::nonNull)
                                                   .collect(toSet());
        Set<String> newMixins = new HashSet<>(oldMixins);
        newMixins.removeAll(oldPoliciesMixins);
        newMixins.addAll(newPoliciesMixins);
        return newMixins;
    }

    @Override
    public Statement addSecurityPolicies(Statement query, QueryParameters queryParams) {
        RepositoryPrincipal principal = queryParams.principal();
        if (principal.isAdministrator()) {
            // no security check needed, including security policies
            return query;
        }
        WhereClause where = query.getWhereClause();
        Predicate predicate = where == null ? null : where.predicate();
        predicate = addQueryClauses(predicate, queryParams);
        return query.withPredicate(predicate);
    }

    private Predicate addQueryClauses(Predicate predicate, QueryParameters queryParams) {
        Operand current = predicate;
        if (current == null) {
            // neutral element for AND
            current = BooleanLiteral.LIT_TRUE;
        }
        Set<String> principals = securityService.expandPrincipal(queryParams.principal());

        for (SecurityPolicy sp : securityPolicies) {
            if (!sp.getPermissions().isEmpty() && !sp.getPermissions().contains(READ)) {
                // skip security policies that don't apply to READ
                continue;
            }
            Operand clause = sp.getQueryClause(queryParams, principals);
            if (clause == null) {
                throw new IllegalStateException("Null clause returned for policy: " + sp.getName());
            }
            if (!sp.isGlobal()) {
                // The clause must now be wrapped to be only evaluated if the policy is relevant for the document:
                //     sys_secpol.contains(<policy>)
                // but if the policy is not relevant we want this to be ignored.
                // To ignore, given that all the policies are ANDed together, we must return true.
                // Therefore the complete predicate must evaluate to:
                //     IF sys_secpol.contains(<policy>) THEN <clause> ELSE true
                // Which is in pure boolean logic:
                //     (NOT sys_secpol.contains(<policy>)) OR <clause>
                // The (NOT sys_secpol.contains(<policy>)) is expressed in HXQL as:
                //     (sys_secpol <> <policy>)
                // which btw will be turned into the OpenSearch "bool" query:
                //     "must_not" : [
                //       {
                //         "term" : {
                //           "sys_secpol" : {
                //             "value" : "<policy>"
                //           }
                //         }
                //       }
                //     ]
                // In the end we have the HXQL:
                //   (sys_secpol <> <policy>) OR <clause>
                Predicate filter = Predicates.noteq(SYS_SECURITY_POLICIES, sp.getName());
                clause = orClauses(filter, clause, sp);
            }
            current = andClauses(current, clause, sp);
        }

        if (current instanceof BooleanLiteral bl) {
            if (bl.value()) {
                return null; // no WHERE clause
            } else {
                return Predicates.FALSE; // always evaluate to false
            }
        } else if (current instanceof Predicate p) {
            return p;
        }
        throw new IllegalStateException("Failed to evaluate policies, got: " + current);
    }

    /**
     * Returns BooleanLiteral or Predicate.
     *
     * @param left Predicate, never null
     * @param right Predicate or BooleanLiteral, never null
     */
    private Operand orClauses(Predicate left, Operand right, SecurityPolicy sp) {
        // constant fold "<left> OR <right>"
        if (right instanceof BooleanLiteral bl) {
            if (bl.value()) {
                return BooleanLiteral.LIT_TRUE;
            } else {
                return left;
            }
        } else if (right instanceof Predicate p) {
            return Predicates.or(left, p);
        }
        throw new IllegalStateException("Failed to evaluate policy: " + sp.getName() + " got: " + right);
    }

    /**
     * Returns BooleanLiteral or Predicate or null.
     *
     * @param left Predicate or BooleanLiteral, never null
     * @param right Predicate or BooleanLiteral, never null
     */
    private Operand andClauses(Operand left, Operand right, SecurityPolicy sp) {
        // constant fold "<left> AND <right>"
        if (left instanceof BooleanLiteral bl)  {
            if (bl.value()) {
                return right;
            } else {
                return BooleanLiteral.LIT_FALSE;
            }
        } else if (right instanceof BooleanLiteral bl) {
            if (bl.value()) {
                return left;
            } else {
                return BooleanLiteral.LIT_FALSE;
            }
        } else if (right instanceof Predicate p) {
            return new Predicate(left, Operator.AND, p);
        }
        throw new IllegalStateException("Failed to evaluate policy: " + sp.getName() + " got: " + right);
    }

    @Override
    public void updateDenormalizedProperties(String id, StateAccessor stateAccessor) {
        aclDenormalizer.updateDenormalizedProperties(id, stateAccessor);
    }

    @Override
    public void updateDenormalizedPropertiesNoCache(Collection<String> ids, StateAccessor stateAccessor) {
        aclDenormalizer.updateDenormalizedPropertiesNoCache(ids, stateAccessor);
    }

    @Override
    public void updateDenormalizedPropertiesWithDescendants(String id, StateAccessor stateAccessor, DBSSession session) {
        aclDenormalizer.updateDenormalizedPropertiesWithDescendants(id, stateAccessor, session);
    }

}
