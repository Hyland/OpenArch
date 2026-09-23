/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation.Type;
import org.nuxeo.ecm.core.security.SecurityPolicy;
import org.nuxeo.ecm.core.security.SecurityPolicyViolation;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.springframework.stereotype.Component;

/**
 * Provides a framework for limited validation of security policies request config in the REST API layer.
 * If errors (not warnings) are found in this class, invalid {@link SecurityPolicyConfig} entries are removed.
 * <br/>
 * Prior to this validation there may have been failures to parse or patch the JSON into {@link SecurityPolicyConfig}
 * objects.
 * <br/>
 * The service layer may later augment the list of violations if there are any issues, when the
 * {@link SecurityPolicyConfig} objects are turned into {@link SecurityPolicy} objects. A single issue may be
 * added for each policy.
 * <br>
 * There is no need to check the following cases:
 * <ul>
 *   <li>for unknown engine names, as these result in an InvalidTypeIdException before either PUT endpoint method is
 *   called. See SecurityPolicyConfigPutTest.put_badEngineName. In the case of PATCH, the same exception is thrown
 *   during processing.</li>
 *   <li>that all system-wide immutable policies still exist as they are added back in at the end of processing PUT
 *   and PATCH operations.</li>
 *   <li>that all system-wide policies and those from other projects are present after a PUT {projectId} operation as
 *   any policies in the request with the same name as these will already have been discarded by a test resulting
 *   in an error in this class.</li>
 *   <li>the JSON structure in the case of PATCH, as there would have been an exception creating
 *   {@link SecurityPolicyConfig} instances from the patched JSON if there had been.</li>
 *   <li>for service layer checks related to invalid policy names or fields within a policy's config as these violations
 *   are added later.</li>
 * </ul>
 * This class will add:
 * <ul>
 *     <li>a warning if a simple PUT or PATCH request includes a policy name that is used by an immutable policy.</li>
 *     <li>an error if a PUT for a project is using the same policy name as another project or a system-wide policy.</li>
 * </ul>
 */
@Component
public class SecurityPolicyValidator {

    private static final String WARNING_IMMUTABLE = "Security policy \"{0}\" may not be modified - ignored";

    private static final String ERROR_ALREADY_IN_USE = "Security policy \"{0}\" is already defined in another project or is system-wide";

    private final List<ValidationViolation> violations = new ArrayList<>();

    public List<ValidationViolation> getViolations() {
        return violations;
    }

    public void validatePut(Map<String, SecurityPolicyConfig> requestConfig, Set<String> immutablePolicyNames) {
        removeIfError(requestConfig, List.of(
                nameUsed(Type.WARNING, WARNING_IMMUTABLE, immutablePolicyNames)));
    }

    public void validatePutProject(Map<String, SecurityPolicyConfig> requestConfig, Set<String> nonProjectNames) {
        removeIfError(requestConfig, List.of(
                nameUsed(Type.ERROR, ERROR_ALREADY_IN_USE, nonProjectNames)));
    }

    public void validatePatch(Map<String, SecurityPolicyConfig> requestConfig, Set<String> immutablePolicyNames) {
        // currently the same as PUT
        validatePut(requestConfig, immutablePolicyNames);
    }

    private BiFunction<String, SecurityPolicyConfig, SecurityPolicyViolation> nameUsed(Type violationType,
            String violationMessage, Set<String> names) {
        return (name, config) -> names.contains(name)
                ? new SecurityPolicyViolation(violationType, violationMessage, name)
                : null;
    }

    private void removeIfError(Map<String, SecurityPolicyConfig> requestConfig,
            List<BiFunction<String, SecurityPolicyConfig, SecurityPolicyViolation>> checks) {
        requestConfig.entrySet().removeIf(entry -> {
            for (var check : checks) {
                SecurityPolicyViolation violation = check.apply(entry.getKey(), entry.getValue());
                if (violation != null) {
                    getViolations().add(violation);
                    if (violation.getType() == Type.ERROR) {
                        return true;
                    }
                }
            }
            return false;
        });
    }
}
