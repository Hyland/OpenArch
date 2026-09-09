/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.document;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SCHEMA_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CHANGE_TOKEN;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_GENERATION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_PERMISSIONS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH_DEPTH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_POS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_REPOSITORY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_USER_GENERATION;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.rest.acl.ACEDTO;
import com.hyland.openarch.rest.user.UserDTO;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple document representation, useful for json serialization and swagger schema generation with the ReST API.
 */
@Schema(name = SCHEMA_NAME, description = "Represents a document.", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
// The List/Map fields below are declared with interface types that don't extend Serializable, but the concrete
// instances assigned at runtime (e.g. ArrayList, HashMap) are always Serializable in practice.
@SuppressWarnings("java:S1948")
public class DocumentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty(SYS_REPOSITORY)
    @Schema(description = "Name of the repository where the document resides.")
    protected String repository;

    @JsonProperty(SYS_ID)
    @Schema(description = """
            UUID of the document.

            **Note:** When included in request bodies for document creation, the sys_id element can be set to match one in an external system, ensuring unicity.
            Character restrictions include the following:
            * 200 characters maximum
            * Characters must be -, _, ., or alphanumeric.
            """)
    protected String id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = """
            The document type of the document.

            **Note:** Required in request bodies for document creation.
            """)
    @JsonProperty(SYS_PRIMARY_TYPE)
    protected String primaryType;

    @JsonProperty(SYS_TITLE)
    @Schema(description = "The title of the document.")
    protected String title;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_NAME)
    @Schema(description = """
            The name of the document.

            **Note:** Can be up to 100 characters.
            """)
    protected String name;

    @JsonProperty(SYS_PATH)
    @Schema(description = """
            The path to the document in the repository.

            **Note:** Computed from the sys_name of the document's ancestors. Cannot be used in all HXQL query contexts.
            """)
    protected String path;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_PATH_DEPTH)
    @Schema(description = "The number of folder levels deep into the repository the document is located.")
    protected Integer pathDepth;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_PARENT_ID)
    @Schema(description = "UUID of the document's parent.")
    protected String parentId;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_PARENT_PATH)
    @Schema(description = "The path to the document's parent in the repository.")
    protected String parentPath;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_ANCESTOR_IDS)
    @Schema(description = "List of UUIDs of the document's ancestors.")
    protected List<String> ancestorIds;

    @JsonProperty(SYS_IS_FOLDERISH)
    @Schema(description = "True/false indicator of whether the document contains the SysFolderish mixin.")
    protected Boolean isFolderish;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_IS_TRASHED)
    @Schema(description = "True when the document is trashed; omitted otherwise.")
    protected Boolean isTrashed;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_MIXIN_TYPES)
    @Schema(description = """
            Restricted list of the document mixins.

            **Note:** Restrictions include the following:
            * SysFolderish facet is never seen on document instances (only seen on the type).
            * In HXQL queries, sys_mixinTypes can only be used with the =, < >, IN, and NOT IN operators.
            """)
    protected List<String> mixinTypes;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_CHANGE_TOKEN)
    @Schema(description = """
            String bundle of the generation and the user generation, as `"<generation>-<userGeneration>"`.

            **Note:** This is a computed property, kept for clients that expect a single opaque change token. New code should use `sys_gen` and `sys_ugen` directly.
            """)
    protected String changeToken;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_GENERATION)
    @Schema(description = """
            The generation of the document.

            **Note:** The generation is incremented on each update (system or not) to the document.
            """)
    protected long generation;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_USER_GENERATION)
    @Schema(description = """
            The user generation of the document.

            **Note:** The user generation is incremented on each client update to the document.
            """)
    protected long userGeneration;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_LIFECYCLE_STATE)
    @Schema(description = "The lifecycle state of the document.")
    protected String lifecycleState;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_POS)
    @Schema(description = """
            The position of the document within its parent.

            **Note:** Value is NULL in unordered folders.
            """)
    protected Long pos;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_SECURITY_POLICIES)
    @Schema(description = """
            Names of the security policies associated with the document.

            Only included in responses when the user has the `ManageSecurity` permission.
            """)
    protected List<String> securityPolicies;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_ACL)
    @Schema(description = """
            A list of associations between a user (or group of users) and the permissions that are granted or denied.

            Only included in responses when the user has the `ManageSecurity` permission.
            """)
    protected List<ACEDTO> acl;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_EFFECTIVE_ACL)
    @Schema(description = """
            A list of associations between a user (or group of users) and the permissions that are granted or denied, \
            including those inherited from the document's ancestors.

            Only included in responses when the user has the `ManageSecurity` permission.
            """)
    protected List<ACEDTO> effectiveAcl;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_EFFECTIVE_PERMISSIONS)
    protected List<String> effectivePermissions;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_READ_ACL)
    @Schema(description = """
            The denormalized list of principals that can read the document.

            Only included in responses when the user has the `ManageSecurity` permission.
            """)
    protected List<String> readAcl;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_LOCK_CREATED)
    @Schema(type = "string", format = "date-time", description = "The date and time when the document was locked by a user.")
    protected Instant lockCreated;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_LOCK_OWNER)
    protected UserDTO lockOwner;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_FULLTEXT_BINARY)
    @Schema(description = "The full text extracted from blobs of the current document.")
    protected String fulltextBinary;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_CONTRIBUTORS)
    @Schema(description = "List of users who created or modified the document.")
    protected List<UserDTO> contributors;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_CREATED)
    @Schema(type = "string", format = "date-time", description = "The date and time when the document was created.")
    protected Instant created;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_CREATOR)
    protected UserDTO creator;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_DESCRIPTION)
    @Schema(description = "Description of the document.")
    protected String description;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_LAST_CONTRIBUTOR)
    protected UserDTO lastContributor;

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(SYS_MODIFIED)
    @Schema(type = "string", format = "date-time", description = "The date and time when the document was last modified.")
    protected Instant modified;

    @JsonIgnore
    protected Map<String, Object> properties = new HashMap<>();

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(String primaryType) {
        this.primaryType = primaryType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getPathDepth() {
        return pathDepth;
    }

    public void setPathDepth(Integer pathDepth) {
        this.pathDepth = pathDepth;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public List<String> getAncestorIds() {
        return ancestorIds;
    }

    public void setAncestorIds(List<String> ancestorIds) {
        this.ancestorIds = ancestorIds;
    }

    public Boolean getIsFolderish() {
        return isFolderish;
    }

    public void setIsFolderish(Boolean isFolderish) {
        this.isFolderish = isFolderish;
    }

    public Boolean getIsTrashed() {
        return isTrashed;
    }

    public void setIsTrashed(Boolean isTrashed) {
        this.isTrashed = isTrashed;
    }

    public List<String> getMixinTypes() {
        return mixinTypes;
    }

    public void setMixinTypes(List<String> mixinTypes) {
        this.mixinTypes = mixinTypes;
    }

    public String getChangeToken() {
        return changeToken;
    }

    public void setChangeToken(String changeToken) {
        this.changeToken = changeToken;
    }

    public long getGeneration() {
        return generation;
    }

    public void setGeneration(long gen) {
        this.generation = gen;
    }

    public long getUserGeneration() {
        return userGeneration;
    }

    public void setUserGeneration(long ugen) {
        this.userGeneration = ugen;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(String lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public Long getPos() {
        return pos;
    }

    public void setPos(Long pos) {
        this.pos = pos;
    }

    public List<String> getSecurityPolicies() {
        return securityPolicies;
    }

    public void setSecurityPolicies(List<String> securityPolicies) {
        this.securityPolicies = securityPolicies;
    }

    public List<ACEDTO> getAcl() {
        return acl;
    }

    public void setAcl(List<ACEDTO> acl) {
        this.acl = acl;
    }

    public List<ACEDTO> getEffectiveAcl() {
        return effectiveAcl;
    }

    public void setEffectiveAcl(List<ACEDTO> effectiveAcl) {
        this.effectiveAcl = effectiveAcl;
    }

    public List<String> getEffectivePermissions() {
        return effectivePermissions;
    }

    public void setEffectivePermissions(List<String> effectivePermissions) {
        this.effectivePermissions = effectivePermissions;
    }

    public List<String> getReadAcl() {
        return readAcl;
    }

    public void setReadAcl(List<String> readAcl) {
        this.readAcl = readAcl;
    }

    public Instant getLockCreated() {
        return lockCreated;
    }

    public void setLockCreated(Instant lockCreated) {
        this.lockCreated = lockCreated;
    }

    public UserDTO getLockOwner() {
        return lockOwner;
    }

    public void setLockOwner(UserDTO lockOwner) {
        this.lockOwner = lockOwner;
    }

    public String getFulltextBinary() {
        return fulltextBinary;
    }

    public void setFulltextBinary(String fulltextBinary) {
        this.fulltextBinary = fulltextBinary;
    }

    public List<UserDTO> getContributors() {
        return contributors;
    }

    public void setContributors(List<UserDTO> contributors) {
        this.contributors = contributors;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public UserDTO getCreator() {
        return creator;
    }

    public void setCreator(UserDTO creator) {
        this.creator = creator;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UserDTO getLastContributor() {
        return lastContributor;
    }

    public void setLastContributor(UserDTO lastContributor) {
        this.lastContributor = lastContributor;
    }

    public Instant getModified() {
        return modified;
    }

    public void setModified(Instant modified) {
        this.modified = modified;
    }

    @Schema(hidden = true)
    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonAnySetter
    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
