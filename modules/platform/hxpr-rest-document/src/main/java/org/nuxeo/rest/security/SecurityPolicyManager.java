/*
 * (C) Copyright 2024 Hyland (http://nuxeo.com/) and others.
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
package org.nuxeo.rest.security;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.security.SecurityPolicy;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityPolicyValidationException;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.security.config.AbstractSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonPatch;

@Component
public class SecurityPolicyManager {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyManager.class);

    // Allow logging only canonical project ids made of safe identifier characters.
    private static final Pattern LOGGABLE_PROJECT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    protected SecurityService getSecurityService() {
        return Framework.getService(SecurityService.class);
    }

    /**
     * Consolidates the security policies from the request with the internal hidden security policies (such as acl, lock
     * or retention).
     */
    public void put(String repoId, boolean validateOnly, Map<String, SecurityPolicyConfig> config) {
        log.info("Setting security policies: {}", config == null ? null : config.keySet());
        persistConfig(repoId, config, null, validateOnly);
    }

    /**
     * Consolidates the original security policies with those in the request. Any original security policies with the
     * same {@code projectId} are removed and policies from the request are added. Any security policies in the request
     * that have the same name as an original security policies (after the removal) are just ignored, as they are likely
     * to be system-wide security policies (such as acl, lock or retention) or for a different project. If the
     * {@code projectId} is {@code null} in any request policies the field is automatically set.
     */
    public void putProject(String repoId, String projectId, boolean validateOnly,
            Map<String, SecurityPolicyConfig> config) {
        Objects.requireNonNull(projectId);
        if (log.isInfoEnabled()) {
            log.info("Setting security policies for project {}: {}", projectIdLogToken(projectId),
                config == null ? null : config.keySet());
        }
        persistConfig(repoId, config, projectId, validateOnly);
    }

    /**
     * Patch security policies in the original config. Hidden entries (such as acl) are initially removed so that any
     * attempt to patch them will fail. They are added back into the map returned by this method.
     */
    public void patch(ObjectMapper mapper, String repoId, boolean validateOnly, Object patchBody) throws IOException {
        String patchBodyAsString = mapper.writeValueAsString(patchBody);
        JsonNode patch = mapper.readTree(patchBodyAsString);
        JsonNode configTree = mapper.valueToTree(getConfigWithoutHidden(repoId, null, false));
        String patchedJson = mapper.writeValueAsString(JsonPatch.apply(patch, configTree));
        Map<String, SecurityPolicyConfig> config = AbstractSecurityPolicyConfig.toSecurityPolicyConfigMap(patchedJson);
        var sanitizedPatchBody = StringUtils.truncate(patchBodyAsString, 1024);
        log.info("Patching security policies: {}", sanitizedPatchBody);
        persistConfig(repoId, config, null, validateOnly);
    }

    /**
     * Returns the config without hidden security policies, and filtering only those for the given project if there is
     * one specified.
     * <p>
     * Note: This method is not called, if there are validation violations. A SecurityPolicyValidationException will
     * have been thrown, resulting in errors and warnings being returned. If there are only warnings and validateOnly is
     * false, this method is called.
     */
    public Map<String, SecurityPolicyConfig> getConfigWithoutHidden(String repoId, String projectId,
            boolean validateOnly) {
        if (validateOnly) {
            return null; // NOSONAR
        }
        SecurityPolicyService securityPolicyService = getSecurityService().getSecurityPolicyService(repoId);
        Set<String> hidden = securityPolicyService.getSecurityPolicies()
                                                  .stream()
                                                  .filter(SecurityPolicy::isHidden)
                                                  .map(SecurityPolicy::getName)
                                                  .collect(toSet());
        Map<String, SecurityPolicyConfig> config = securityPolicyService.getSecurityPolicyConfigMap();
        config = config.entrySet()
                       .stream()
                       // filter out hidden security policies
                       .filter(en -> !hidden.contains(en.getKey()))
                       // if the project id is specified, include only the security policies for this project
                       .filter(en -> (projectId == null || projectId.equals(en.getValue().getProjectId())))
                       .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (log.isInfoEnabled()) {
            log.info("Getting security policies for project {}: {}", projectIdLogToken(projectId), config.keySet());
        }
        return new TreeMap<>(config);
    }

    /**
     * Returns a predicate that matches policies with the product id set.
     *
     * @param projectId the project id. If {@code null} then the predicate will match all policies.
     * @return a predicate that matches project specific policies.
     */
    public Predicate<SecurityPolicyConfig> project(String projectId) {
        return config -> projectId == null || projectId.equals(config.getProjectId());
    }

    protected static String projectIdLogToken(String projectId) {
        if (projectId == null) {
            return "<null>";
        }
        if (StringUtils.isBlank(projectId)) {
            return "<blank>";
        }
        if (LOGGABLE_PROJECT_ID.matcher(projectId).matches()) {
            return projectId;
        }
        return "<invalid-project-id>";
    }

    private void persistConfig(String repoId, Map<String, SecurityPolicyConfig> config, String projectId,
            boolean validateOnly) {
        List<ValidationViolation> violations = new ArrayList<>();
        getSecurityService().setSecurityPolicies(repoId, config, projectId, violations, validateOnly);
        ValidationReport.checkViolations(violations, !validateOnly, SecurityPolicyValidationException::new);
    }

}
