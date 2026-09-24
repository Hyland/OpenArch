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

import java.util.List;
import java.util.Set;

import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;

public class TestSecurityPolicy extends AbstractSecurityPolicy {

    public static final String TEST_ENGINE = "test";

    public TestSecurityPolicy(String repositoryName, String name, SecurityPolicyConfig config,
            List<ValidationViolation> violations) {
        super(repositoryName, name, config, violations);
    }

    @Override
    public boolean isHidden() {
        return ((TestSecurityPolicyConfig) getConfig()).isHidden();
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        return false;
    }

    @Override
    public Predicate getQueryClause(QueryParameters queryParams, Set<String> principals) {
        return null;
    }
}
