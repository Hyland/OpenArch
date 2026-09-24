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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;

class SecurityPolicyConfigMapperTest {

    private SecurityPolicyConfigMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SecurityPolicyConfigMapper();
    }

    @Test
    void testToConfigDTO() {
        SecurityPolicyConfig config = new SecurityLevelSecurityPolicyConfig("projectId", Set.of("contentTypes"),
                Set.of("permissions"), "mixin", "userAttribute");
        SecurityPolicyConfigDTO dto = mapper.toConfigDTO(config);
        assertThat(dto.engine()).isEqualTo("securitylevel");
        assertThat(dto.projectId()).isEqualTo("projectId");
        assertThat(dto.contentTypes()).contains("contentTypes");
        assertThat(dto.permissions()).contains("permissions");
        assertThat(dto.mixin()).isEqualTo("mixin");
        assertThat(dto.userAttribute()).isEqualTo("userAttribute");
    }

    @Test
    void testToConfig() {
        SecurityPolicyConfigDTO dto = new SecurityPolicyConfigDTO("securitylevel", "mixin", "projectId",
                Set.of("contentTypes"), Set.of("permissions"), "userAttribute", null, null);
        SecurityPolicyConfig config = mapper.toConfig(dto);
        assertThat(config).isInstanceOf(SecurityLevelSecurityPolicyConfig.class);
        assertThat(config.getProjectId()).isEqualTo("projectId");
        assertThat(config.getContentTypes()).contains("contentTypes");
        assertThat(config.getPermissions()).contains("permissions");
        assertThat(config.getMixin()).isEqualTo("mixin");
    }

    @Test
    void testConvertMapOfConfigToDTO() {
        Map<String, SecurityPolicyConfig> configMap = new HashMap<>();
        configMap.put("key", new SecurityLevelSecurityPolicyConfig("projectId", Set.of("contentTypes"),
                Set.of("permissions"), "mixin", "userAttribute"));
        Map<String, SecurityPolicyConfigDTO> dtoMap = mapper.convertToDTO(configMap);
        assertThat(dtoMap).hasSize(1).containsKey("key");
        assertThat(dtoMap.get("key").engine()).isEqualTo("securitylevel");
    }

    @Test
    void testConvertMapOfDTOToConfig() {
        Map<String, SecurityPolicyConfigDTO> dtoMap = new HashMap<>();
        dtoMap.put("key", new SecurityPolicyConfigDTO("securitylevel", "mixin", "projectId", Set.of("contentTypes"),
                Set.of("permissions"), "userAttribute", null, null));
        Map<String, SecurityPolicyConfig> configMap = mapper.convertFromDTO(dtoMap);
        assertThat(configMap).hasSize(1).containsKey("key");
        assertThat(configMap.get("key")).isInstanceOf(SecurityLevelSecurityPolicyConfig.class);
    }
}
