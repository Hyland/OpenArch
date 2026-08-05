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
package org.nuxeo.ecm.core.security.config;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.ExternalUserRelationsSecurityPolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration for External User Relation Security Policies.
 * <p>
 * The {@code "name"} of the policy is the key in the map of security policies.
 * <p>
 * The {@code "engine"} value cannot be changed.
 * <p>
 * The {@code "projectId"} is optional and indicates if it is associated with a Studio project.
 * <p>
 * The {@code "contentTypes"} is a list of content types to which the policy applies.
 * <p>
 * The {@code "mixin"} identifies the business security property used to store the security level of the document.
 * <p>
 * The {@code "relations"} identifies the relations taken into account by the policy.
 * <p>
 * The {@code "userAttribute"} is the user attribute used to store the user's external user id.
 * <p>
 * Json example:
 *
 * <pre>
 *   {
 *     "engine": "externaluserrelation",
 *     "projectId": "12345",
 *     "contentTypes": ["Resume"],
 *     "mixin": "WDObject",
 *     "relations": ["HRAdmin", "manager_level01"],
 *     "userAttribute": "wd_empId"
 *   }
 * </pre>
 */
public class ExternalUserRelationsSecurityPolicyConfig extends AbstractSecurityPolicyConfig {

    public static final String FIELD_NAME_RELATIONS = "relations";

    public static final String FIELD_NAME_USER_ATTRIBUTE = "userAttribute";

    private final List<String> relations;

    private final String userAttribute;

    // Used in Json deserialization
    public ExternalUserRelationsSecurityPolicyConfig() {
        this(null, null, null, null, null, null);
    }

    public ExternalUserRelationsSecurityPolicyConfig(String projectId, Set<String> contentTypes, Set<String> permissions,
            String mixin, List<String> relations, String userAttribute) {
        super(ExternalUserRelationsSecurityPolicy.EXTERNAL_USER_RELATIONS_ENGINE_NAME, projectId, contentTypes,
                permissions, mixin);
        this.relations = (relations == null || relations.isEmpty()) ? List.of() : List.copyOf(relations);
        this.userAttribute = userAttribute;
    }

    @Override
    public ExternalUserRelationsSecurityPolicyConfig withProjectId(String projectId) {
        return new ExternalUserRelationsSecurityPolicyConfig(projectId, getContentTypes(), getPermissions(), getMixin(),
                getRelations(), getUserAttribute());
    }

    @Override
    public ExternalUserRelationsSecurityPolicy newSecurityPolicy(String repositoryName, String name,
            List<ValidationViolation> violations) {
        return new ExternalUserRelationsSecurityPolicy(repositoryName, name, this, violations);
    }

    @JsonInclude
    public List<String> getRelations() {
        return relations;
    }

    @JsonInclude
    public String getUserAttribute() {
        return userAttribute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExternalUserRelationsSecurityPolicyConfig other)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(relations, other.relations)
                && Objects.equals(userAttribute, other.userAttribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), relations, userAttribute);
    }
}
