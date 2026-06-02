/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 */
package org.nuxeo.ecm.core.security.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SecurityPolicyConfigDeserializeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private void assertConfig(Class<?> expectedClass, String json) throws JsonProcessingException {
        assertConfig(expectedClass, AbstractSecurityPolicyConfig.class, json);
    }

    private Map<String, AbstractSecurityPolicyConfig> assertMapFromJson(int expectedSize, String json) throws JsonProcessingException {
        Map<String, AbstractSecurityPolicyConfig> map = AbstractSecurityPolicyConfig.toAbstractSecurityPolicyConfigMap(json);
        assertEquals(expectedSize, map.size());
        return map;
    }

    private <T> T assertConfig(Class<?> expectedClass, Class<T> baseClass, String json) throws JsonProcessingException {
        T t = MAPPER.readerFor(baseClass).readValue(json);
        assertEquals(expectedClass, t.getClass());
        return t;
    }

    @Test
    public void deserializeAcl() throws JsonProcessingException {
        assertConfig(ACLSecurityPolicyConfig.class, """
                {
                  "engine": "acl"
                }""");
    }

    @Test
    public void deserializeLock() throws JsonProcessingException {
        assertConfig(LockSecurityPolicyConfig.class, """
                {
                  "engine": "lock"
                }""");
    }

    @Test
    public void deserializeRetention() throws JsonProcessingException {
        assertConfig(RetentionSecurityPolicyConfig.class, """
                {
                  "engine": "retention"
                }""");
    }

    @Test
    public void deserializeSecurityLevel() throws JsonProcessingException {
        SecurityLevelSecurityPolicyConfig config = (SecurityLevelSecurityPolicyConfig)
                assertConfig(SecurityLevelSecurityPolicyConfig.class, AbstractSecurityPolicyConfig.class,"""
                {
                  "engine": "securitylevel",
                  "projectId": "12345",
                  "contentTypes": ["Contract"],
                  "permissions": [ "read", "highlight", "comment" ],
                  "mixin": "Clearance",
                  "userAttribute": "clearance"
                }""");
        assertEquals("12345", config.getProjectId());
        assertEquals(Set.of("Contract"), config.getContentTypes());
        assertEquals("Clearance", config.getMixin());
        assertEquals("clearance", config.getUserAttribute());
    }

    @Test
    public void deserializeEmptyContentTypes() throws JsonProcessingException {
        SecurityLevelSecurityPolicyConfig config = (SecurityLevelSecurityPolicyConfig)
                assertConfig(SecurityLevelSecurityPolicyConfig.class, AbstractSecurityPolicyConfig.class,"""
                {
                  "engine": "securitylevel",
                  "contentTypes": []
                }""");
        assertEquals(Set.of(), config.getContentTypes());
    }

    @Test
    public void deserializeNullContentTypes() throws JsonProcessingException {
        SecurityLevelSecurityPolicyConfig config = (SecurityLevelSecurityPolicyConfig)
                assertConfig(SecurityLevelSecurityPolicyConfig.class, AbstractSecurityPolicyConfig.class,"""
                {
                  "engine": "securitylevel"
                }""");
        assertEquals(Set.of(), config.getContentTypes());
    }

    @Test
    public void deserializeSingleContentType() throws JsonProcessingException {
        SecurityLevelSecurityPolicyConfig config = (SecurityLevelSecurityPolicyConfig)
                assertConfig(SecurityLevelSecurityPolicyConfig.class, AbstractSecurityPolicyConfig.class,"""
                {
                  "engine": "securitylevel",
                  "contentTypes": ["Contract1"]
                }""");
        assertEquals(Set.of("Contract1"), config.getContentTypes());
    }

    @Test
    public void deserializeMultipleContentTypes() throws JsonProcessingException {
        SecurityLevelSecurityPolicyConfig config = (SecurityLevelSecurityPolicyConfig)
                assertConfig(SecurityLevelSecurityPolicyConfig.class, AbstractSecurityPolicyConfig.class,"""
                {
                  "engine": "securitylevel",
                  "contentTypes": ["Contract1", "Contract2", "Contract3"]
                }""");
        assertEquals(Set.of("Contract1", "Contract2", "Contract3"), config.getContentTypes());
    }

    @Test
    public void deserializeNoSecurityPolicies() throws JsonProcessingException {
        assertMapFromJson(0, """
                {
                }""");
    }

    @Test
    public void deserializeEachSecurityPolicy() throws JsonProcessingException {
        assertMapFromJson(4, """
                {
                  "acl": {
                    "engine": "acl"
                  },
                  "lock": {
                    "engine": "lock"
                  },
                  "retention": {
                    "engine": "retention"
                  },
                  "security clearance": {
                    "engine": "securitylevel",
                    "contentTypes": ["Contract"],
                    "permissions": [ "read", "highlight", "comment" ],
                    "mixin": "Clearance",
                    "userAttribute": "clearance"
                  }
                }""");
    }

    @Test
    public void testMapFromJsonOrder() throws JsonProcessingException {
        List<String> unsorted = List.of("iii", "v", "iv");
        List<String> sorted = List.of("iii", "iv", "v");
        var config = assertMapFromJson(1, """
                {
                  "security clearance": {
                    "engine": "securitylevel",
                    "contentTypes": ["iii", "v", "iv"],
                    "mixin": "Clearance",
                    "userAttribute": "clearance"
                  }
                }""");


        assertNotEquals(sorted, unsorted);
        assertEquals(sorted, new ArrayList<>(
                ((SecurityLevelSecurityPolicyConfig) config.get("security clearance")).getContentTypes()));
        assertEquals(TreeSet.class,
                ((SecurityLevelSecurityPolicyConfig) config.get("security clearance")).getContentTypes().getClass());
    }

    @Test
    public void testMapFromJsonNulls() throws JsonProcessingException {
        var config = assertMapFromJson(1, """
                {
                  "security clearance": {
                    "engine": "securitylevel",
                    "mixin": "Clearance",
                    "userAttribute": "clearance"
                  }
                }""");

        assertEquals(Set.of(), ((SecurityLevelSecurityPolicyConfig) config.get("security clearance"))
                        .getContentTypes());
    }

    @Test
    public void deserializeMultipleSecurityLevelPolicies() throws JsonProcessingException {
        assertMapFromJson(2, """
                {
                  "organisational clearance": {
                     "engine": "securitylevel",
                     "mixin": "Organisation",
                     "userAttribute": "rank"
                   },
                   "security clearance": {
                     "engine": "securitylevel",
                     "mixin": "Clearance",
                     "userAttribute": "user_clearance"
                   }
                }""");
    }
}
