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

import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import tools.jackson.databind.ObjectMapper;

public class SecurityPolicyConfigSerializeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private void assertConfig(SecurityPolicyConfig config, String expectedJson) throws Exception {
        String json = MAPPER.writeValueAsString(config);
        JSONAssert.assertEquals(expectedJson, json, NON_EXTENSIBLE);
    }

    @Test
    public void serializeAcl() throws Exception {
        assertConfig(new ACLSecurityPolicyConfig(), """
                {
                  "engine": "acl",
                  "permissions": ["Everything"]
                }""");
    }

    @Test
    public void serializeLock() throws Exception {
        assertConfig(new LockSecurityPolicyConfig(), """
                {
                  "engine": "lock",
                  "permissions": ["Write"]
                }""");
    }

    @Test
    public void serializeRetention() throws Exception {
        assertConfig(new RetentionSecurityPolicyConfig(), """
                {
                  "engine": "retention",
                  "permissions": ["Delete", "Write"]
                }""");
    }

    @Test
    public void serializeSecurityLevel() throws Exception {
        assertConfig(new SecurityLevelSecurityPolicyConfig("12345", Set.of("Contract"),
                Set.of("read", "highlight", "comment"), "Clearance", "clearance"), """
                {
                  "engine": "securitylevel",
                  "projectId": "12345",
                  "contentTypes": ["Contract"],
                  "permissions": [ "read", "highlight", "comment" ],
                  "mixin": "Clearance",
                  "userAttribute": "clearance"
                }""");
    }
}
