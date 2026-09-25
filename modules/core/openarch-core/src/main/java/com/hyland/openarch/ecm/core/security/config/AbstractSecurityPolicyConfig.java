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
package com.hyland.openarch.ecm.core.security.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.hyland.openarch.ecm.core.security.ACLSecurityPolicy;
import com.hyland.openarch.ecm.core.security.ExternalUserRelationsSecurityPolicy;
import com.hyland.openarch.ecm.core.security.LockSecurityPolicy;
import com.hyland.openarch.ecm.core.security.RetentionSecurityPolicy;
import com.hyland.openarch.ecm.core.security.SecurityLevelSecurityPolicy;
import com.hyland.openarch.ecm.core.security.expr.ExpressionSecurityPolicy;
import com.hyland.openarch.ecm.core.security.expr.ExpressionSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.expr.UserAttributeExpressionSecurityPolicy;
import com.hyland.openarch.ecm.core.security.expr.UserAttributeExpressionSecurityPolicyConfig;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "engine", visible = true)
@JsonSubTypes({ //
        @Type(value = ACLSecurityPolicyConfig.class, name = ACLSecurityPolicy.ACL_ENGINE_NAME),
        @Type(value = LockSecurityPolicyConfig.class, name = LockSecurityPolicy.LOCK_ENGINE_NAME),
        @Type(value = RetentionSecurityPolicyConfig.class, name = RetentionSecurityPolicy.RETENTION_ENGINE_NAME),
        @Type(value = SecurityLevelSecurityPolicyConfig.class, name = SecurityLevelSecurityPolicy.SECURITY_LEVEL_ENGINE_NAME),
        @Type(value = ExternalUserRelationsSecurityPolicyConfig.class, name = ExternalUserRelationsSecurityPolicy.EXTERNAL_USER_RELATIONS_ENGINE_NAME),
        @Type(value = ExpressionSecurityPolicyConfig.class, name = ExpressionSecurityPolicy.EXPRESSION_ENGINE_NAME),
        @Type(value = UserAttributeExpressionSecurityPolicyConfig.class, name = UserAttributeExpressionSecurityPolicy.USER_ATTRIBUTE_EXPRESSION_ENGINE_NAME) })
public abstract class AbstractSecurityPolicyConfig implements SecurityPolicyConfig {

    private static final TypeReference<LinkedHashMap<String, AbstractSecurityPolicyConfig>> CONFIG_MAP_REFERENCE = new TypeReference<>() {
    };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final String engine;

    private final String projectId;

    private final Set<String> contentTypes;

    private final Set<String> permissions;

    private final String mixin;

    protected AbstractSecurityPolicyConfig(String engine, String projectId, Set<String> contentTypes,
            Set<String> permissions, String mixin) {
        this.engine = engine;
        this.projectId = projectId;
        this.contentTypes = toSorted(contentTypes);
        this.permissions = toSorted(permissions);
        this.mixin = mixin;
    }

    public static Map<String, SecurityPolicyConfig> toSecurityPolicyConfigMap(String json) throws JacksonException {
        return toSecurityPolicyConfigMap(toAbstractSecurityPolicyConfigMap(json));
    }

    public static Map<String, AbstractSecurityPolicyConfig> toAbstractSecurityPolicyConfigMap(String json)
            throws JacksonException {
        Map<String, AbstractSecurityPolicyConfig> config = MAPPER.readValue(json, CONFIG_MAP_REFERENCE);
        config.replaceAll((k, v) -> v.cleanUp()); // makes sure values are normalized
        return config;
    }

    /**
     * Converts a {@code Map<String, AbstractSecurityPolicyConfig>} to {@code Map<String, SecurityPolicyConfig>}.
     * This method is useful as deserialization results in a {@code Map<String, AbstractSecurityPolicyConfig>} rather
     * than a {@code Map<String, SecurityPolicyConfig>.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, SecurityPolicyConfig> toSecurityPolicyConfigMap(
            Map<String, AbstractSecurityPolicyConfig> config) {
        return (Map<String, SecurityPolicyConfig>) (Map<String, ?>) config;
    }

    /**
     * Deserialization does not use constructors to set field values. This method allows subclasses to perform post
     * deserialization operations. For example, to change the ordering of collections or to initialise fields that
     * should not be {@code null}. A {@code null} may have been set for new fields that have been added to the
     * subclass since it was stored. The order of a collection is normally lost during deserialization.
     * @return an instance of the subclass created with a constructor rather than deserialization. The default
     * implementation returns {@code this}.
     */
    public AbstractSecurityPolicyConfig cleanUp() {
        return (AbstractSecurityPolicyConfig) withProjectId(projectId);
    }

    @Override
    public String getEngine() {
        return engine;
    }

    @Override
    public String getProjectId() {
        return projectId;
    }

    @Override
    public Set<String> getContentTypes() {
        return contentTypes;
    }

    @Override
    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String getMixin() {
        return mixin;
    }

    protected Set<String> toSorted(Set<String> set) {
        return set == null ? Set.of() : new TreeSet<>(set);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractSecurityPolicyConfig that)) {
            return false;
        }
        return Objects.equals(engine, that.engine) && //
                Objects.equals(projectId, that.projectId) && //
                Objects.equals(contentTypes, that.contentTypes) && //
                Objects.equals(permissions, that.permissions) && //
                Objects.equals(mixin, that.mixin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engine, projectId, contentTypes, permissions, mixin);
    }

}
