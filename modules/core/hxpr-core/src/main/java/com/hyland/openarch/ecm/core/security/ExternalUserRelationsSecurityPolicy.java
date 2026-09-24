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

import static com.hyland.openarch.ecm.core.security.config.ExternalUserRelationsSecurityPolicyConfig.FIELD_NAME_USER_ATTRIBUTE;
import static com.hyland.openarch.ecm.core.security.config.ExternalUserRelationsSecurityPolicyConfig.FIELD_NAME_RELATIONS;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Predicates;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation.Type;
import com.hyland.openarch.ecm.core.security.config.ExternalUserRelationsSecurityPolicyConfig;

import com.hyland.openarch.config.Configurable;

public class ExternalUserRelationsSecurityPolicy extends AbstractSecurityPolicy
        implements Configurable<ExternalUserRelationsSecurityPolicy.RelationsState> {

    /**
     * The configuration this policy holds: the relations, in the internal form this policy queries them in rather than
     * the public form the API exchanges, and when they last changed.
     *
     * @param relations the relations, inverted
     * @param lastModified when the relations last changed
     */
    public record RelationsState(Map<String, Map<String, List<String>>> relations, Instant lastModified) {
    }

    private static final Logger log = LogManager.getLogger(ExternalUserRelationsSecurityPolicy.class);

    public static final String EXTERNAL_USER_RELATIONS_ENGINE_NAME = "externaluserrelations";

    private static final String CONFIG_EXTERNAL_USER_RELATIONS_PREFIX = "externaluserrelations-";

    private static final String CONFIG_RELATIONS = "relations";

    private static final String CONFIG_LAST_MODIFIED = "lastModified";

    // the relations (a map of user to map of relation to users managed by this user for this relation) and when they
    // last changed; an immutable record holding read-only maps, in a volatile field for safe publishing
    private volatile RelationsState relationsState; // NOSONAR

    // cache for fast access
    private String docEmployeeIdProperty;

    public ExternalUserRelationsSecurityPolicy(String repositoryName, String name,
            ExternalUserRelationsSecurityPolicyConfig config, List<ValidationViolation> violations) {
        super(repositoryName, name, config, violations);
        checkRelations(violations);
        checkUserAttribute(violations);
        // until it's filled in from persisted configuration or user-provided data
        relationsState = new RelationsState(Map.of(), Instant.now());
    }

    private void checkRelations(List<ValidationViolation> violations) {
        isEmptyListValue(Type.ERROR, FIELD_NAME_RELATIONS, getRelations(), violations);
    }

    private void checkUserAttribute(List<ValidationViolation> violations) {
        isBlankValue(Type.ERROR, FIELD_NAME_USER_ATTRIBUTE, getUserAttribute(), violations);
        // We can't check that the userAttribute exists in the IdP,
        // so we might encounter an issue later when trying to use it
    }

    private List<String> getRelations() {
        return getConfig().getRelations();
    }

    private String getUserAttribute() {
        return getConfig().getUserAttribute();
    }

    private String getDocEmployeeIdProperty() {
        if (docEmployeeIdProperty == null) {
            Set<String> properties = getSecurityProperties();
            docEmployeeIdProperty = properties.size() == 1 ? properties.iterator().next() : null;
        }
        return docEmployeeIdProperty;
    }

    @Override
    public ExternalUserRelationsSecurityPolicyConfig getConfig() {
        return (ExternalUserRelationsSecurityPolicyConfig) super.getConfig();
    }

    @Override
    public boolean hasPermission(InternalDocument doc, NuxeoPrincipal principal, Set<String> principals,
            String permission, Set<String> permissions) {
        // find external user id from principal
        String userId = getExternalUserId(principal);
        if (userId == null) {
            // deny if principal does not have an external user id
            return false;
        }

        // find employee id from doc
        String property = getDocEmployeeIdProperty();
        if (property == null) {
            // deny if property is unknown or the mixin contains several properties
            log.error("ExternalUserRelationsSecurityPolicy {} has invalid mixin {}", getName(), getMixin());
            return false;
        }

        String docEmployeeId = (String) doc.getPropertyValue(property);
        if (docEmployeeId == null) {
            // deny if document does not have a set value for the employee
            return false;
        }

        // allow if employee is the principal
        if (docEmployeeId.equals(userId)) {
            return true;
        }

        // allow if employee id is in list of relations
        if (hasRelationBetween(userId, docEmployeeId)) {
            return true;
        }

        // otherwise deny
        return false;
    }

    @Override
    public Operand getQueryClause(QueryParameters queryParams, Set<String> principals) {
        NuxeoPrincipal principal = queryParams.principal();
        String externalUserId = getExternalUserId(principal);

        String property = getDocEmployeeIdProperty();
        if (property == null) {
            // deny if property is unknown or the mixin contains several properties
            log.error("ExternalUserRelationsSecurityPolicy {} has invalid mixin {}", getName(), getMixin());
            return BooleanLiteral.LIT_FALSE;
        }

        // documents that do not specify an employee
        Predicate docNoEmployee = Predicates.isnull(property);

        // if principal does not have an external user id, only access regular documents
        if (externalUserId == null) {
            // example (assuming docEmployeeIdProperty = wd_empId):
            // (wd_empId IS NULL)
            return docNoEmployee;
        }

        // principal
        Set<String> externalUserIds = new HashSet<>();
        externalUserIds.add(externalUserId);
        // other users in relation with the principal
        getUsersInRelationWith(externalUserId, externalUserIds);
        Predicate matchingEmployee = Predicates.in(property, externalUserIds);

        // example (assuming docEmployeeIdProperty = wd_empId):
        // (wd_empId IS NULL) OR (wd_empId IN ('alice', 'bob', 'carol'))
        return Predicates.or(docNoEmployee, matchingEmployee);
    }

    private String getExternalUserId(NuxeoPrincipal principal) {
        Map<String, Object> properties = principal.getProperties();
        if (properties == null) {
            return null;
        }
        Object attr = properties.get(getUserAttribute());
        if (attr == null) {
            return null;
        }
        if (!(attr instanceof String s)) {
            log.debug("Principal {} has non-string attribute {} = {}", principal.getId(), FIELD_NAME_USER_ATTRIBUTE,
                    attr);
            return null;
        }
        return s.isBlank() ? null : s;
    }

    /**
     * Checks if the first user is in relation with the other user.
     */
    private boolean hasRelationBetween(String id, String otherId) {
        Map<String, List<String>> relationToUsers = relationsState.relations().get(id);
        if (relationToUsers != null) {
            for (String relation : getRelations()) {
                List<String> relationUsers = relationToUsers.get(relation);
                if (relationUsers != null) {
                    if (relationUsers.contains(otherId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets all the users in relation with the given user.
     */
    private void getUsersInRelationWith(String id, Set<String> ids) {
        Map<String, List<String>> relationToUsers = relationsState.relations().get(id);
        if (relationToUsers != null) {
            for (String relation : getRelations()) {
                List<String> relationUsers = relationToUsers.get(relation);
                if (relationUsers != null) {
                    ids.addAll(relationUsers);
                }
            }
        }
    }

    /**
     * Sets the external user relations.
     * <p>
     * This is typically called from a REST endpoint.
     * <p>
     * Config is a JSON-like map of the form:
     *
     * <pre>
     * <code>
     *   {
     *     "alice": {
     *       "HRAdmin": ["bob"],
     *       "SupervisoryReports": ["carol"]
     *     },
     *     "dan": {
     *       "HRAdmin": ["bob"],
     *       "SupervisoryReports": ["bob"]
     *     },
     *     ...
     *   }
     * </code>
     * </pre>
     *
     * In the above, alice has bob as her admin, and carol as her manager; and dan has bob as his admin and also as his
     * manager.
     */
    public void setExternalUserRelations(Map<String, Map<String, List<String>>> publicConfig) {
        // Set locally and persist (sending cluster invalidations) as one operation, which also gives the comparison
        // below a configuration no concurrent writer can change under it.
        // Keep the current lastModified when the relations do not change: it then really means "when the relations last
        // changed", and the resulting configuration is identical, so the configuration service skips the write and its
        // invalidations rather than persisting a fresh timestamp. The comparison is against the persisted relations,
        // which is what "current" is here: comparing with the ones this node holds would keep a timestamp older than
        // the stored one when this node lags behind, and so move lastModified backwards for relations that did change.
        updateConfigurationGlobally(getRepositoryName(), current -> {
            Map<String, Map<String, List<String>>> relations = invertRelations(publicConfig);
            // a stored configuration with no usable timestamp counts as changed, so that one is written for it
            boolean unchanged = relations.equals(current.relations()) && current.lastModified() != null;
            return new RelationsState(relations, unchanged ? current.lastModified() : Instant.now());
        });
        var sanitizedConfig = StringUtils.truncate(publicConfig.toString(), 1024);
        log.info("Setting external user relations on repository {} for policy {} with {} user(s): {}",
                getRepositoryName(), getName(), publicConfig.size(), sanitizedConfig);
    }

    /**
     * Gets the external user relations.
     */
    public Map<String, Object> getExternalUserRelations() {
        var inverted = invertRelations(relationsState.relations());
        @SuppressWarnings("unchecked")
        Map<String, Object> publicConfig = (Map<String, Object>) (Object) inverted;
        var sanitizedConfig = StringUtils.truncate(publicConfig.toString(), 1024);
        log.info(
                "Getting external user relations on repository {} for policy {} last modified at {} with {} user(s): {}",
                getRepositoryName(), getName(), getExternalUserRelationsLastModified(), publicConfig.size(),
                sanitizedConfig);
        return publicConfig;
    }

    /**
     * Gets the last modified date for the external user relations.
     */
    public Instant getExternalUserRelationsLastModified() {
        return relationsState.lastModified();
    }

    /**
     * Inverts the direction of the relations, because that's what's relevant for the algorithm implemented above.
     * <p>
     * We want:
     *
     * <pre>
     * <code>
     *   {
     *     "bob": {
     *       "HRAdmin": ["alice", "dan"]
     *       "SupervisoryReports": ["dan"]
     *     },
     *     "carol": {
     *       "SupervisoryReports": ["alice"]
     *     },
     *     ...
     *   }
     * </code>
     * </pre>
     *
     * Which expresses that bob is admin of alice and dan; bob is manager of dan; and carol is manager of alice.
     * <p>
     * Note that this method is an involution, it's its own inverse.
     */
    private static Map<String, Map<String, List<String>>> invertRelations(
            Map<String, Map<String, List<String>>> input) {
        Map<String, Map<String, List<String>>> output = new HashMap<>();
        for (var en : input.entrySet()) {
            String sourceId = en.getKey();
            for (var e : en.getValue().entrySet()) {
                String relation = e.getKey();
                for (String targetId : e.getValue()) {
                    output.computeIfAbsent(targetId, k -> new HashMap<>())
                          .computeIfAbsent(relation, k -> new ArrayList<>())
                          .add(sourceId);
                }
            }
        }
        return output;
    }

    // Configurable
    @Override
    public RelationsState getLocalState(String repositoryName) {
        return relationsState;
    }

    // Configurable
    @Override
    public void setLocalState(String repositoryName, RelationsState state) {
        if (!this.getRepositoryName().equals(repositoryName)) {
            // we don't want to set the config for another repository, nothing to do
            return;
        }
        relationsState = state;
    }

    // Configurable
    @Override
    public String getConfigurableConfigName() {
        return CONFIG_EXTERNAL_USER_RELATIONS_PREFIX + getName();
    }

    // Configurable
    /**
     * The persisted configuration is of the following form. It keeps the relation in the "public" format.
     *
     * <pre>
     * <code>
     *   {
     *     "lastModified": "2024-08-01T12:23:54Z",
     *     "relations": {
     *       "alice": {
     *         "HRAdmin": ["bob"],
     *         "SupervisoryReports": ["carol"]
     *       },
     *       "dan": {
     *         "HRAdmin": ["bob"],
     *         "SupervisoryReports": ["bob"]
     *       },
     *       ...
     *     }
     *   }
     * </code>
     * </pre>
     */
    @Override
    public Map<String, Object> toConfigMap(RelationsState state) {
        return Map.of(CONFIG_RELATIONS, invertRelations(state.relations()), //
                CONFIG_LAST_MODIFIED, formatInstant(state.lastModified()));
    }

    // Configurable
    @Override
    public RelationsState fromConfigMap(String repositoryName, Map<String, Object> config) {
        Object relations = config.get(CONFIG_RELATIONS);
        if (!(relations instanceof Map)) {
            log.debug("Invalid persisted relations: {}", relations);
            relations = Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Map<String, List<String>>> publicConfig = (Map<String, Map<String, List<String>>>) relations;
        Object lm = config.get(CONFIG_LAST_MODIFIED);
        return new RelationsState(invertRelations(publicConfig), parseInstant(lm));
    }

    private String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Instant parseInstant(Object obj) {
        if (obj instanceof String s) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException e) {
                // fall through
            }
        }
        if (obj != null) {
            log.debug("Invalid persisted instant: {}", obj);
        }
        return null;
    }

}
