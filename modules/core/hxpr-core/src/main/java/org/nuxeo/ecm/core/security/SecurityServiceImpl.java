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

package org.nuxeo.ecm.core.security;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYONE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.registry.MapRegistry;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.config.AbstractSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.ComponentStartOrders;
import com.hyland.openarch.runtime.model.DefaultComponent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.hyland.hxpr.config.Configurable;
import com.hyland.hxpr.config.ConfigurationService;
import com.hyland.hxpr.config.ConfigurationWithVersion;

/**
 * Component for permissions-related contributions.
 */
public class SecurityServiceImpl extends DefaultComponent implements SecurityService, Configurable<SecurityPolicyService> {

    private static final Logger log = LogManager.getLogger(SecurityServiceImpl.class);

    public static final String CONFIG_SECURITY = "security";

    private static final String PERMISSIONS_EXTENSION_POINT = "permissions";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // for each permission, all the group permissions that imply it (including itself and Everything)
    private Map<String, Set<String>> expandedPermissions;

    // for each permission, all the group permissions that are implied by it (including itself and Everything)
    private Map<String, Set<String>> containedPermissions;

    // all permissions (without Everything)
    private Set<String> allPermissions;

    private final Map<String, SecurityPolicyService> securityPolicyServices = new ConcurrentHashMap<>();

    @Override
    public int getApplicationStartedOrder() {
        // security service needed by repository
        return ComponentStartOrders.REPOSITORY - 1;
    }

    @Override
    public void start() {
        securityPolicyServices.clear();
        Map<String, PermissionDescriptor> permissions = this.<MapRegistry> getExtensionPointRegistry(
                PERMISSIONS_EXTENSION_POINT).getContributions();
        verifyPermissions(permissions);
        computePermissionGroups(permissions);
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            configService.registerConfigurable(this);
        }
    }

    @Override
    public void stop() throws InterruptedException {
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            configService.unregisterConfigurable(this);
        }
        securityPolicyServices.clear();
    }

    // check that all included permissions are defined
    private void verifyPermissions(Map<String, PermissionDescriptor> permissions) {
        for (PermissionDescriptor p : permissions.values()) {
            List<String> unknownPermissions = p.getSubPermissions()
                                               .stream()
                                               .filter(k -> !permissions.containsKey(k))
                                               .toList();
            if (!unknownPermissions.isEmpty()) {
                throw new NuxeoException(String.format("Permission(s) %s included by '%s' are unknown",
                        unknownPermissions, p.getName()));
            }
        }
    }

    private void computePermissionGroups(Map<String, PermissionDescriptor> permissions) {
        // scanning sub permissions to collect direct group membership
        Map<String, Set<String>> permissionGroups = new HashMap<>();
        for (PermissionDescriptor mpd : permissions.values()) {
            String perm = mpd.getName();
            for (String subPermission : mpd.getSubPermissions()) {
                permissionGroups.computeIfAbsent(subPermission, k -> new TreeSet<>()).add(perm);
            }
            // add entry (maybe with empty set) for perm itself
            permissionGroups.computeIfAbsent(perm, k -> new TreeSet<>());
        }
        // building the transitive closure on groups membership with a recursive method
        expandedPermissions = new HashMap<>();
        Set<String> done = new HashSet<>();
        for (Entry<String, Set<String>> groupEntry : permissionGroups.entrySet()) {
            String permission = groupEntry.getKey();
            Set<String> groups = groupEntry.getValue();
            Set<String> allGroups = computeAllGroups(permissionGroups, permission, done);
            groups.addAll(allGroups);
            // same but also with itself and Everything
            groups = new HashSet<>(groups);
            groups.add(permission);
            groups.add(EVERYTHING);
            expandedPermissions.put(permission, Collections.unmodifiableSet(groups));
        }
        containedPermissions = new HashMap<>();
        for (Entry<String, Set<String>> expandedPermission : expandedPermissions.entrySet()) {
            expandedPermission.getValue().forEach(permission -> containedPermissions.computeIfAbsent(permission, k -> new HashSet<>()).add(expandedPermission.getKey()));
        }
        // all permissions except Everything
        Set<String> all = new HashSet<>(permissionGroups.keySet());
        all.remove(EVERYTHING);
        allPermissions = Collections.unmodifiableSet(all);
    }

    private Set<String> computeAllGroups(Map<String, Set<String>> mergedGroups, String permissionName,
            Set<String> done) {
        Set<String> allGroups = mergedGroups.get(permissionName);
        if (allGroups == null) {
            allGroups = new TreeSet<>();
        }
        if (done.add(permissionName)) {
            for (String directGroupName : new TreeSet<>(allGroups)) {
                allGroups.addAll(computeAllGroups(mergedGroups, directGroupName, done));
            }
        }
        return allGroups;
    }

    @Override
    public Set<String> getAllPermissions() {
        return allPermissions;
    }

    @Override
    public Set<String> getAllPermissionsContaining(String permission) {
        return expandedPermissions.getOrDefault(permission, Set.of());
    }

    public Set<String> getAllPermissionsContainedIn(String permission) {
        return containedPermissions.getOrDefault(permission, Set.of());
    }

    @Override
    public Set<String> expandPrincipal(NuxeoPrincipal principal) {
        Set<String> principals = new HashSet<>();
        principals.add(ACE.USER_PREFIX + principal.getId());
        principals.add(EVERYONE);
        for (String group : principal.getAllGroups()) {
            principals.add(ACE.GROUP_PREFIX + group);
        }
        for (String eu : principal.getExternalUserMappings()) {
            principals.add(ACE.USER_PREFIX + eu);
        }
        for (String eg : principal.getExternalGroupMappings()) {
            principals.add(ACE.GROUP_PREFIX + eg);
        }
        return principals;
    }

    @Override
    public SecurityPolicyService getSecurityPolicyService(String repositoryName) {
        return securityPolicyServices.computeIfAbsent(repositoryName, k -> new SecurityPolicyServiceImpl(this, k));
    }

    @Override
    public void setSecurityPolicies(String repositoryName, Map<String, SecurityPolicyConfig> config) {
        List<ValidationViolation> violations = new ArrayList<>();
        setSecurityPolicies(repositoryName, config, null, violations, false);
        ValidationReport.checkViolations(violations, true, SecurityPolicyValidationException::new);
    }

    // called from REST endpoint to modify security policies
    @Override
    public void setSecurityPolicies(String repositoryName, Map<String, SecurityPolicyConfig> config, String projectId,
            List<ValidationViolation> violations, boolean validateOnly) {
        if (validateOnly) {
            // validate against the persisted policies, which is what a write would be computed from
            getSecurityPoliciesWithVersion(repositoryName).policies().applyUpdate(config, projectId, violations);
            return;
        }
        // Merge into the persisted policies, not the ones held locally: those lag behind while a cluster invalidation is
        // in flight, and this write replaces the whole configuration, so a project update computed from them would drop
        // a policy another node has just added for a different project. The update runs under the lock of this
        // configuration, and setting the policies locally and persisting them is one operation, so that concurrent
        // writers cannot set the local policies in one order and persist in the other.
        updateConfigurationGlobally(repositoryName, current -> {
            // the update is applied again when another node writes in between, so the violations are collected afresh
            // each time and only those of the run that went through are reported
            violations.clear();
            SecurityPolicyService updated = current.applyUpdate(config, projectId, violations);
            // an update with errors is not applied: keeping the policies as they are stored writes nothing, since they
            // are what the configuration service just read
            return new ValidationReport(violations).removeWarnings().hasViolation() ? current : updated;
        });
    }

    // called from the v2 REST endpoints to modify security policies with optimistic locking
    @Override
    public void setSecurityPolicies(String repositoryName, Map<String, SecurityPolicyConfig> config, String projectId,
            List<ValidationViolation> violations, boolean validateOnly, long expectedVersion) {
        // merge into and validate against the persisted policies the version refers to, not the ones held locally: those
        // lag behind while a cluster invalidation is in flight, and this write replaces the whole configuration
        SecurityPolicyService service = getSecurityPoliciesWithVersion(repositoryName).policies()
                                                                                     .applyUpdate(config, projectId,
                                                                                             violations);
        if (validateOnly || new ValidationReport(violations).removeWarnings().hasViolation()) {
            return;
        }
        // Persist with optimistic locking, then set locally, as one operation: if the stored version has moved the write
        // is rejected and the local policies are left unchanged, and two concurrent writers set the local policies in
        // the order they persisted, so this node cannot end up enforcing older policies than it stored.
        setConfigurationGloballyIfVersionMatches(repositoryName, service, expectedVersion);
    }

    @Override
    public PoliciesWithVersion getSecurityPoliciesWithVersion(String repositoryName) {
        ConfigurationWithVersion snapshot = Framework.getService(ConfigurationService.class)
                                                    .readConfigurationForUpdate(repositoryName, this);
        // reading the persisted policies may fail, and is then left to fail: an update computed from the local policies
        // instead would be persisted under a version describing different content, overwriting what this node has not seen
        return new PoliciesWithVersion(fromConfigMap(repositoryName, snapshot.config()), snapshot.version());
    }

    // Configurable
    @Override
    public SecurityPolicyService getLocalState(String repositoryName) {
        return getSecurityPolicyService(repositoryName);
    }

    // Configurable
    @Override
    public void setLocalState(String repositoryName, SecurityPolicyService newService) {
        SecurityPolicyService oldService = securityPolicyServices.put(repositoryName, newService);
        // make sure that security policies that are Configurable are properly registered/unregistered
        if (oldService != null) {
            oldService.unregisterConfigurables();
        }
        newService.registerConfigurables();
    }

    // Configurable
    @Override
    public String getConfigurableConfigName() {
        return CONFIG_SECURITY;
    }

    /** The persisted form of the policies held by {@code service}. */
    // Configurable
    @Override
    public Map<String, Object> toConfigMap(SecurityPolicyService service) {
        Map<String, SecurityPolicyConfig> config = service.getSecurityPolicyConfigMap();
        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(config), Map.class);
        } catch (JacksonException e) {
            // Should not get here
            log.error("Invalid config writing security policy: {}", config);
            return Collections.emptyMap();
        }
    }

    /**
     * A detached {@link SecurityPolicyService} over the given persisted configuration, i.e. the policies as they are
     * stored rather than the ones registered on this node. Used both to set a configuration locally and to compute an
     * optimistic-locking update from the persisted state.
     */
    // Configurable
    @Override
    public SecurityPolicyService fromConfigMap(String repositoryName, Map<String, Object> map) {
        Map<String, SecurityPolicyConfig> config = AbstractSecurityPolicyConfig.toSecurityPolicyConfigMap(
                MAPPER.writeValueAsString(map));
        List<ValidationViolation> violations = new ArrayList<>();
        SecurityPolicyService service = new SecurityPolicyServiceImpl(this, repositoryName, config, violations);
        logViolations(violations);
        return service;
    }

    private void logViolations(List<ValidationViolation> violations) {
        // There is not much else we can do other than not start.
        violations.forEach(violation -> {
            if (violation.getType() == ValidationViolation.Type.ERROR) {
                log.error(violation.getMessage(null));
            } else  {
                log.debug(violation.getMessage(null));
            }
        });
    }

}
