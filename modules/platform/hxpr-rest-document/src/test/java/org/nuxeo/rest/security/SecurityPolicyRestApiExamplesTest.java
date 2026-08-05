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
package org.nuxeo.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.GET_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PATCH_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_FOR_PROJECT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_FOR_PROJECT_RESPONSE_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_REQUEST_EXAMPLE;
import static org.nuxeo.rest.security.SecurityPolicyRequestConstants.PUT_RESPONSE_EXAMPLE;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.security.config.AbstractSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;

import com.fasterxml.jackson.core.JsonProcessingException;

class SecurityPolicyRestApiExamplesTest {

    private void assertMapNameConfig(int expectedSize, String json) throws JsonProcessingException {
        Map<String, SecurityPolicyConfig> config = AbstractSecurityPolicyConfig.toSecurityPolicyConfigMap(
                AbstractSecurityPolicyConfig.toAbstractSecurityPolicyConfigMap(json));
        assertEquals(expectedSize, config.size());
    }

    @Test
    void deserializeExamples() throws JsonProcessingException {
        assertMapNameConfig(1, GET_RESPONSE_EXAMPLE);
        assertMapNameConfig(1, PUT_REQUEST_EXAMPLE);
        assertMapNameConfig(1, PUT_RESPONSE_EXAMPLE);
        assertMapNameConfig(1, PUT_FOR_PROJECT_REQUEST_EXAMPLE);
        assertMapNameConfig(1, PUT_FOR_PROJECT_RESPONSE_EXAMPLE);
        assertMapNameConfig(2, PATCH_RESPONSE_EXAMPLE);
    }
}
