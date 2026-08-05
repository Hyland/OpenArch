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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation.Type;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.runtime.api.Framework;

public abstract class AbstractSecurityPolicy implements SecurityPolicy {

    private static final String FIELD_NAME_MIXIN = "mixin";

    private static final String FIELD_NAME_CONTENT_TYPES = "contentTypes";

    private static final String FIELD_NAME_PERMISSIONS = "permissions";

    private final String repositoryName;

    private final String name;

    private final SecurityPolicyConfig config;

    // As looking up the security properties is expensive, we cache them
    private Set<String> securityProperties;

    // flattened set of permissions managed by this policy, based on the config
    @NotNull
    private Set<String> permissions = Set.of();

    /**
     * Constructor for a security policy.
     *
     * @param repositoryName the repository name
     * @param name       the security policy name
     * @param config     the configuration for the security policy
     * @param violations the violations to fill
     */
    protected AbstractSecurityPolicy(String repositoryName, String name, SecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        this.repositoryName = repositoryName;
        this.name = name;
        this.config = config;
        checkName(violations);
        checkMixin(violations);
        checkContentTypes(violations);
        checkPermissions(violations);
        this.permissions = flattenConfigPermissions();
    }

    protected void checkName(List<ValidationViolation> violations) {
        if (StringUtils.isBlank(name)) {
            SecurityPolicyViolation.add(violations, Type.ERROR, "A security policy name must not be blank");
        }
    }

    protected boolean checkMixin(List<ValidationViolation> violations) {
        if (isBlankValue(Type.ERROR, FIELD_NAME_MIXIN, getMixin(), violations)) {
            return false;
        }
        return true;
    }

    protected boolean checkNoMixin(List<ValidationViolation> violations) {
        return isNotBlankValue(Type.ERROR, FIELD_NAME_MIXIN, getMixin(), violations);
    }

    protected void checkContentTypes(List<ValidationViolation> violations) {
        if (getContentTypes().isEmpty()) {
            SecurityPolicyViolation.add(violations, Type.WARNING,
                    "There are no content types set on the \"{0}\" security policy", getName());
            return;
        }
        getContentTypes().forEach(contentType -> isBlankElementValue(Type.ERROR, FIELD_NAME_CONTENT_TYPES, contentType,
                violations));
    }

    protected void checkNoContentTypes(List<ValidationViolation> violations) {
        if (!getContentTypes().isEmpty()) {
            SecurityPolicyViolation.add(violations, Type.ERROR,
                    "Content types must not be set on the \"{0}\" security policy", getName());
        }
    }

    protected void checkPermissions(List<ValidationViolation> violations) {
        if (config.getPermissions().isEmpty()) {
            SecurityPolicyViolation.add(violations, Type.ERROR,
                    "There are no permissions set on the \"{0}\" security policy", getName());
            return;
        }
        config.getPermissions().forEach(permission -> checkElement(Type.ERROR, Type.WARNING, FIELD_NAME_PERMISSIONS,
                permission, violations, AbstractSecurityPolicy::permissionExists));
    }

    private void checkElement(Type blankType, Type unknownType, String fieldName, String value,
            List<ValidationViolation> violations, Predicate<String> exists) {

        if (isBlankElementValue(blankType, fieldName, value, violations)) {
            return;
        }

        isUnknownElementValue(unknownType, fieldName, value, violations, exists);
    }

    private static boolean permissionExists(String permission) {
        return permission.equals(EVERYTHING) // EVERYTHING is not on the list of permissions, but it always exists
                || Framework.getService(SecurityService.class).getAllPermissions().contains(permission);
    }

    protected boolean isNotBlankValue(Type type, String fieldName, String value, // NOSONAR ignore type is always the same
            List<ValidationViolation> violations) {
        return addViolationIf(violations, () -> StringUtils.isNotBlank(value), type,
                "The {0} must not be set on security policy \"{1}\"", fieldName, getName());
    }

    protected boolean isBlankValue(Type type, String fieldName, String value, // NOSONAR ignore type is always the same
            List<ValidationViolation> violations) {
        return addViolationIf(violations, () -> StringUtils.isBlank(value), type,
                "The {0} must be set on security policy \"{1}\"", fieldName, getName());
    }

    protected boolean isBlankElementValue(Type type, String fieldName, String value,
            List<ValidationViolation> violations) {
        return addViolationIf(violations, () -> StringUtils.isBlank(value), type,
                "An element of {0} on security policy \"{1}\" is blank", fieldName, getName());
    }

    protected boolean isEmptyListValue(Type type, String fieldName, List<?> value, // NOSONAR ignore type is always the same
            List<ValidationViolation> violations) {
        return addViolationIf(violations, () -> value == null || value.isEmpty(), type,
                "The {0} must be set as a list on security policy \"{1}\"", fieldName, getName());
    }

    protected boolean isUnknownValue(Type type, String fieldName, String value, // NOSONAR ignore type is always the same
            List<ValidationViolation> violations, Predicate<String> exists) {
        return addViolationIf(violations, () -> Boolean.FALSE.equals(exists.test(value)), type,
                "Unknown {0} \"{1}\" on security policy \"{2}\"", fieldName, value, getName());
    }

    private boolean isUnknownElementValue(Type type, String fieldName, String value, // NOSONAR ignore unused return value
            List<ValidationViolation> violations, Predicate<String> exists) {
        return addViolationIf(violations, () -> Boolean.FALSE.equals(exists.test(value)), type,
                "An element of {0} \"{1}\" on security policy \"{2}\" is unknown", fieldName, value, getName());
    }

    private boolean addViolationIf(List<ValidationViolation> violations, BooleanSupplier condition, Type type,
            String messageKey, Object... parameters) {
        if (condition.getAsBoolean()) {
            SecurityPolicyViolation.add(violations, type, messageKey, parameters);
            return true;
        }
        return false;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public String getRepositoryName() {
        return repositoryName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SecurityPolicyConfig getConfig() {
        return config;
    }

    protected String getMixin() {
        return config.getMixin();
    }

    protected Set<String> getContentTypes() {
        return config.getContentTypes();
    }

    /**
     * The set of permissions managed by this security policy.
     * An empty set implies all permissions are managed by the policy.
     * @return the set of permissions
     */
    @NotNull
    @Override
    public Set<String> getPermissions() {
        return permissions;
    }

    private Set<String> flattenConfigPermissions() {
        if (config.getPermissions().contains(EVERYTHING)) {
            // we're past validation and the policy manages all permissions -> empty set now implies all permissions
            return Set.of();
        }
        Set<String> flattenedPermissions = new HashSet<>();
        SecurityService securityService = Framework.getService(SecurityService.class);
        config.getPermissions().forEach(permission -> flattenedPermissions.addAll(securityService.getAllPermissionsContainedIn(permission)));
        return flattenedPermissions;
    }

    // this must be called only at runtime, because at config time the content model may not be ready yet
    private void cacheSecurityProperties() {
        Set<String> properties = new HashSet<>();
        SchemaManager schemaManager = Framework.getService(TypeService.class).getSchemaManager(repositoryName);
        String mixinName = config.getMixin();
        if (mixinName != null) {
            CompositeType mixin = schemaManager.getMixin(mixinName);
            if (mixin != null) {
                mixin.getSchemas().forEach(schema -> schema.getFields().forEach(f -> properties.add(f.getName())));
            }
        }
        securityProperties = properties;
    }

    protected Set<String> getSecurityProperties() {
        if (securityProperties == null) {
            cacheSecurityProperties();
        }
        return securityProperties;
    }

    /**
     * Checks whether the principal bypasses the security policy completely.
     *
     * @param doc the document
     * @param principal the principal
     * @return {@code true} if the security policy should be bypassed
     */
    protected boolean principalBypass(InternalDocument doc, NuxeoPrincipal principal) {
        return principal.isAdministrator();
    }

    @Override
    public boolean appliesToDocument(InternalDocument doc, NuxeoPrincipal principal, Set<String> permissions) {
        if (principalBypass(doc, principal)) {
            return false;
        }
        // check document's sys_secpol (except for global policies)
        if (!isGlobal()) {
            List<?> secpol = (List<?>) doc.getPropertyValue(SYS_SECURITY_POLICIES);
            if (secpol == null || !secpol.contains(getName())) {
                // this security policy isn't listed in the doc's sys_secpol
                return false;
            }
        }
        // check requested permissions
        Set<String> policyPermissions = getPermissions();
        if (!policyPermissions.isEmpty() && Collections.disjoint(permissions, policyPermissions)) {
            // this security policy handles permissions that do not intersect with the requested permissions
            return false;
        }
        // don't need to check mixin, it's implied by sys_secpol
        return true;
    }

    @Override
    public void checkAllowedMixinUpdate(String mixin) {
        if (mixin.equals(getMixin())) {
            throw new DocumentSecurityException(
                    "Update of mixin: %s is denied by security policy: %s".formatted(mixin, getName()));
        }
    }

    @Override
    public void checkAllowedPropertyUpdate(String xpath) {
        if (getSecurityProperties().contains(xpath)) {
            throw new DocumentSecurityException(
                    "Update of property: %s is denied as it is used by security policy: %s".formatted(xpath, getName()));
        }
    }

    // this default implementation just iterates on each permission and delegates to hasPermission
    @Override
    public void restrictEffectivePermissions(List<String> effectivePermissions, InternalDocument doc,
            NuxeoPrincipal principal, Set<String> principals, SecurityService securityService) {
        for (ListIterator<String> it = effectivePermissions.listIterator(); it.hasNext();) {
            String permission = it.next();
            // if the permission is a group, all sub-permissions must be granted
            for (String perm : securityService.getAllPermissionsContainedIn(permission)) {
                Set<String> permissions = securityService.getAllPermissionsContaining(perm);
                if (!appliesToDocument(doc, principal, permissions)) {
                    continue;
                }
                if (!hasPermission(doc, principal, principals, perm, permissions)) {
                    it.remove();
                    break;
                }
            }
        }
    }

}
