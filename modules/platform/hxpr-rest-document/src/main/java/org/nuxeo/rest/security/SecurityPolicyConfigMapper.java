/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.security;

import static org.nuxeo.ecm.core.security.ACLSecurityPolicy.ACL_ENGINE_NAME;
import static org.nuxeo.ecm.core.security.ExternalUserRelationsSecurityPolicy.EXTERNAL_USER_RELATIONS_ENGINE_NAME;
import static org.nuxeo.ecm.core.security.LockSecurityPolicy.LOCK_ENGINE_NAME;
import static org.nuxeo.ecm.core.security.RetentionSecurityPolicy.RETENTION_ENGINE_NAME;
import static org.nuxeo.ecm.core.security.SecurityLevelSecurityPolicy.SECURITY_LEVEL_ENGINE_NAME;
import static org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicy.EXPRESSION_ENGINE_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.security.config.ACLSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.ExternalUserRelationsSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.LockSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.RetentionSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.ecm.core.security.expr.ExpressionSecurityPolicyConfig;
import org.springframework.stereotype.Component;

@Component
public class SecurityPolicyConfigMapper {

    public SecurityPolicyConfigDTO toConfigDTO(SecurityPolicyConfig config) {
        if (config instanceof SecurityLevelSecurityPolicyConfig policyConfig) {
            return new SecurityPolicyConfigDTO(SECURITY_LEVEL_ENGINE_NAME, policyConfig.getMixin(),
                    policyConfig.getProjectId(), policyConfig.getContentTypes(), policyConfig.getPermissions(),
                    policyConfig.getUserAttribute(), null, null);
        }
        if (config instanceof ExternalUserRelationsSecurityPolicyConfig policyConfig) {
            return new SecurityPolicyConfigDTO(EXTERNAL_USER_RELATIONS_ENGINE_NAME, policyConfig.getMixin(),
                    policyConfig.getProjectId(), policyConfig.getContentTypes(), policyConfig.getPermissions(),
                    policyConfig.getUserAttribute(), new HashSet<>(policyConfig.getRelations()), null);
        }
        if (config instanceof ExpressionSecurityPolicyConfig policyConfig) {
            return new SecurityPolicyConfigDTO(EXPRESSION_ENGINE_NAME, policyConfig.getMixin(),
                    policyConfig.getProjectId(), policyConfig.getContentTypes(), policyConfig.getPermissions(),
                    null, null, policyConfig.getExpression());
        }
        return new SecurityPolicyConfigDTO(config.getEngine(), config.getMixin(), config.getProjectId(),
                config.getContentTypes(), config.getPermissions(), null, null, null);
    }

    public SecurityPolicyConfig toConfig(SecurityPolicyConfigDTO config) {
        return switch (config.engine()) {
        case ACL_ENGINE_NAME -> new ACLSecurityPolicyConfig();
        case LOCK_ENGINE_NAME -> new LockSecurityPolicyConfig();
        case RETENTION_ENGINE_NAME -> new RetentionSecurityPolicyConfig();
        case SECURITY_LEVEL_ENGINE_NAME -> new SecurityLevelSecurityPolicyConfig(config.projectId(),
                    config.contentTypes(), config.permissions(), config.mixin(), config.userAttribute());
        case EXTERNAL_USER_RELATIONS_ENGINE_NAME -> {
            List<String> relations = config.relations() == null ? new ArrayList<>() : config.relations().stream().toList();
            yield new ExternalUserRelationsSecurityPolicyConfig(config.projectId(),
                    config.contentTypes(), config.permissions(), config.mixin(), relations,
                    config.userAttribute());
        }
        case EXPRESSION_ENGINE_NAME -> new ExpressionSecurityPolicyConfig(config.projectId(), config.contentTypes(),
                config.permissions(), config.mixin(), config.expression());
        default -> throw new IllegalArgumentException(
                "Could not resolve type id '" + config.engine() + "' : known type ids = [" + ACL_ENGINE_NAME + ", "
                        + LOCK_ENGINE_NAME + ", " + RETENTION_ENGINE_NAME + ", " + SECURITY_LEVEL_ENGINE_NAME + ", "
                        + EXTERNAL_USER_RELATIONS_ENGINE_NAME + ", " + EXPRESSION_ENGINE_NAME + "]");
        };
    }

    public Map<String, SecurityPolicyConfigDTO> convertToDTO(Map<String, SecurityPolicyConfig> configMap) {
        Map<String, SecurityPolicyConfigDTO> dtoMap = new HashMap<>();
        if (configMap == null) {
            return dtoMap;
        }
        for (Map.Entry<String, SecurityPolicyConfig> entry : configMap.entrySet()) {
            SecurityPolicyConfigDTO dto = toConfigDTO(entry.getValue());
            dtoMap.put(entry.getKey(), dto);
        }
        return dtoMap;
    }

    public Map<String, SecurityPolicyConfig> convertFromDTO(Map<String, SecurityPolicyConfigDTO> dtoMap) {
        Map<String, SecurityPolicyConfig> configMap = new HashMap<>();
        for (Map.Entry<String, SecurityPolicyConfigDTO> entry : dtoMap.entrySet()) {
            SecurityPolicyConfig config = toConfig(entry.getValue());
            configMap.put(entry.getKey(), config);
        }
        return configMap;
    }

}
