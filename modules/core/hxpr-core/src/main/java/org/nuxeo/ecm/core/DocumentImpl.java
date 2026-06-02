/*
 * (C) Copyright 2023 Hyland (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core;

import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.CREATE_CHILD;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE_VERSION;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.LockException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.model.resolver.DocumentPropertyObjectResolverImpl;
import org.nuxeo.ecm.core.api.model.resolver.PropertyObjectResolver;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalDocument.BlobAccessor;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.MixinNames;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.storage.dbs.DBSDocument;
import org.nuxeo.ecm.core.storage.dbs.DBSSession;
import org.nuxeo.runtime.api.Framework;

public class DocumentImpl implements DocumentModel {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(DocumentImpl.class);

    private static final Set<String> READWRITE_SYS = Set.of( //
            SYS_DESCRIPTION, //
            SYS_TITLE, //
            SYSGOV_IS_RECORD //
    );

    private static final Set<String> READWRITE_SYSVER = Set.of( //
            SYSVER_TITLE, //
            SYSVER_DESCRIPTION //
    );

    private static final Pattern DOT_DIGITS_PATTERN = Pattern.compile("(.*)\\.\\d+$");

    private static final Pattern NAME_INVALID = Pattern.compile("^[- .,;?!:/\\\\'\"]*$");

    private static final int NAME_MAX_SIZE = 100;

    private DBSDocument doc;

    private final NuxeoPrincipal principal;

    private Map<String, Serializable> contextData;

    public DocumentImpl(InternalDocument doc, NuxeoPrincipal principal) {
        this.doc = (DBSDocument) doc;
        this.principal = principal;
    }

    public InternalDocument getInternalDocument() {
        return doc;
    }

    @Override
    public void create() {
        // check permission CreateChild on parent
        InternalDocument parent = doc.getParent();
        if (parent != null) {
            checkPermission(parent, CREATE_CHILD);
        } else if (!principal.isAdministrator()) {
            throw new DocumentSecurityException("Cannot create placeless document");
        }

        // Enforce: SysEmbeddings creation requires parent SysHasEmbeddings marker mixin
        SysEmbeddingsPolicies.enforceEmbeddingsParentMixinOrThrow(doc);

        sanitizeNameAndTitle();
        // sys_created, sys_creator, sys_modified, sys_lastContributor
        if (!isVersion()) {
            addSysCreatedProperties();
        }
        // security policies
        Framework.getService(SecurityService.class)
                .getSecurityPolicyService(getRepositoryName())
                .setSecurityPolicies(this);
        doc.create();

        // check permission Write on doc itself
        checkPermission(WRITE);
    }

    // protected for unit tests
    protected void sanitizeNameAndTitle() {
        String title;
        try {
            title = (String) doc.getPropertyValue(SYS_TITLE);
        } catch (PropertyNotFoundException e) {
            title = null;
        }

        // sys_name
        String originalName = doc.getName();
        if (isBlank(originalName)) {
            originalName = title;
        }
        String name = handleName(doc, doc.getParent(), originalName, enforceSysName(this));
        doc.setName(name);

        // sys_title
        if (isBlank(title)) {
            title = originalName;
            if (isBlank(title)) {
                title = name;
            }
            try {
                doc.setPropertyValue(SYS_TITLE, title);
            } catch (PropertyNotFoundException e) {
                // ignore
            }
        }
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            name = "";
        } else {
            name = name.trim();
            name = DBSSession.normalize(name);
        }
        if (name.length() > NAME_MAX_SIZE) {
            name = name.substring(0, NAME_MAX_SIZE).trim();
        }
        name = name.replace('/', '-');
        name = name.replace('\\', '-');
        if (NAME_INVALID.matcher(name).matches()) {
            name = IdUtils.generateStringId();
        }
        return name;
    }

    private static boolean enforceSysName(DocumentModel doc) {
        return TRUE.equals(doc.getContextData(CoreSession.ENFORCE_SYS_NAME));
    }

    // check unicity in parent (best effort, not thread-safe nor cluster-safe)
    // but MongoDB unique index will prevent collisions anyway
    public static String handleName(InternalDocument doc, InternalDocument parent,  String originalName, boolean enforceSysName) {
        String name = SysEmbeddingsPolicies.handleEmbeddingsNamePolicy(doc, originalName, enforceSysName);
        name = sanitizeName(name);
        if (enforceSysName && !name.equals(originalName)) {
            throw new PropertyException("Invalid name: " + originalName);
        }
        if (parent != null && parent.hasChild(name)) {
            if (enforceSysName) {
                throw new ConcurrentUpdateException("Duplicate name in parent: " + name);
            }
            Matcher m = DOT_DIGITS_PATTERN.matcher(name);
            if (m.matches()) {
                // remove trailing dot and digits
                name = m.group(1);
            }
            // add dot + unique digits
            name += "." + IdUtils.generateStringId();
        }
        return name;
    }

    private void addSysCreatedProperties() {
        var now = Instant.now();
        var principalId = getPrincipal().getActingId();
        doc.setPropertyValue(SYS_CREATED, now);
        doc.setPropertyValue(SYS_CREATOR, principalId);
        doc.setPropertyValue(SYS_MODIFIED, now);
        doc.setPropertyValue(SYS_LAST_CONTRIBUTOR, principalId);
        addContributor(principalId);
    }

    private void addContributor(String principalId) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) doc.getPropertyValue(SYS_CONTRIBUTORS);
        if (list == null) {
            // no contributors yet, create it
            doc.setPropertyValue(SYS_CONTRIBUTORS, List.of(principalId));
            return;
        }
        if (list.contains(principalId)) {
            // already present
            return;
        }
        // add to existing contributors
        List<Object> newList = new ArrayList<>(list.size() + 1);
        newList.addAll(list);
        newList.add(principalId);
        doc.setPropertyValue(SYS_CONTRIBUTORS, newList);
    }

    private void checkPermission(String permission) {
        checkPermission(doc, permission);
    }

    private void checkPermission(InternalDocument doc, String permission) {
        if (!doc.hasPermission(principal, permission)) {
            log.debug("Permission '{}' is not granted to '{}' on document {} ({} - {})", permission,
                    principal.getId(), doc.getPath(), doc.getId(), doc.getType().getName());
            throw new DocumentSecurityException(
                    String.format(GENERIC_DOCUMENT_SECURITY_ERROR, permission, principal.getId(), doc.getId()));
        }
    }

    private void checkSecurityMixinsUpdate(Collection<String> mixins) {
        Set<String> mixinsAdded = mixins.stream().filter(mixin -> !hasMixin(mixin)).collect(Collectors.toSet());
        Set<String> mixinsRemoved = getEffectiveMixins().stream().filter(mixin -> !mixins.contains(mixin)).collect(Collectors.toSet());
        Set<String> mixinsUpdated = new HashSet<>();
        mixinsUpdated.addAll(mixinsAdded);
        mixinsUpdated.addAll(mixinsRemoved);
        SecurityService securityService = Framework.getService(SecurityService.class);
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(getRepositoryName());
        for (String mixin : mixinsUpdated) {
            securityPolicyService.checkAllowedMixinUpdate(doc, principal, mixin);
        }
    }

    private void checkSecurityPropertyUpdate(String xpath) {
        SecurityService securityService = Framework.getService(SecurityService.class);
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(getRepositoryName());
        securityPolicyService.checkAllowedPropertyUpdate(doc, principal, xpath);
    }

    @Override
    public NuxeoPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public SessionImpl getCoreSession() {
        return new SessionImpl(getRepositoryName(), principal);
    }

    @Override
    public String getRepositoryName() {
        return doc.getRepositoryName();
    }

    @Override
    public DocumentRef getRef() {
        String id = getId();
        if (id != null) {
            return new IdRef(id);
        } else {
            // document not yet created
            InternalDocument parent = doc.getParent();
            if (parent == null) {
                throw new NuxeoException("Missing parent");
            }
            String parentPath = parent.getPath();
            if (parentPath == null) {
                throw new NuxeoException("Version without working copy");
            }
            if (!parentPath.startsWith("/")) {
                throw new NuxeoException("Placeless document");
            }
            String name = getName();
            if (name == null) {
                throw new NuxeoException("Missing name");
            }
            return new PathRef(parentPath, name);
        }
    }

    @Override
    public DocumentRef getParentRef() {
        String parentId = doc.getParentId();
        return parentId == null ? null : new IdRef(parentId);
    }

    @Override
    public List<String> getAncestorIds() {
        return doc.getAncestorIds();
    }

    @Override
    public String getId() {
        return doc.getId();
    }

    @Override
    public void setId(String id) {
        doc.setId(id);
    }

    @Override
    public String getName() {
        return doc.getName();
    }

    @Override
    public Long getPos() {
        return doc.getPos();
    }

    @Override
    public String getTitle() {
        String title;
        try {
            title = (String) doc.getValue(SYS_TITLE);
        } catch (PropertyNotFoundException e) {
            title = null;
        }
        if (title == null) {
            title = getName();
            if (title == null) {
                title = getId();
            }
        }
        return title;
    }

    @Override
    public String getPathAsString() {
        return doc.getPath();
    }

    @Override
    public Path getPath() {
        String path = doc.getPath();
        return path == null ? null : new Path(path);
    }

    @Override
    public String getType() {
        return doc.getType().getName();
    }

    @Override
    public DocumentType getDocumentType() {
        return doc.getType();
    }

    @Override
    public Set<String> getSchemas() {
        SchemaManager schemaManager = Framework.getService(TypeService.class).getSchemaManager(getRepositoryName());
        Set<String> schemaNames = new HashSet<>(doc.getType().getSchemaNames());
        for (String mixins : doc.getMixins()) {
            CompositeType mixinType = schemaManager.getMixin(mixins);
            if (mixinType != null) {
                schemaNames.addAll(mixinType.getSchemaNames());
            }
        }
        return schemaNames;
    }

    @Override
    public boolean hasSchema(String schemaName) {
        DocumentType type = doc.getType();
        if (type.hasSchema(schemaName)) {
            return true;
        }
        SchemaManager schemaManager = Framework.getService(TypeService.class).getSchemaManager(getRepositoryName());
        for (String mixin : doc.getMixins()) {
            CompositeType mixinType = schemaManager.getMixin(mixin);
            if (mixinType != null && mixinType.hasSchema(schemaName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setPrimaryTypeAndMixinTypes(String typeName, Collection<String> mixins) {
        checkPermission(WRITE);
        if (mixins != null) {
            checkSecurityMixinsUpdate(mixins);
            // Enforce: cannot remove SysHasEmbeddings when SysEmbeddings children exist
            SysEmbeddingsPolicies.enforceHasEmbeddingsMixinUpdateOrThrow(doc, mixins);
        }
        doc.setPrimaryTypeAndMixinTypes(typeName, mixins);
    }

    @Override
    public void setMixins(Collection<String> mixins) {
        setPrimaryTypeAndMixinTypes(null, mixins);
    }

    @Override
    public void addMixin(String mixin) {
        Set<String> mixins = new HashSet<>();
        mixins.addAll(doc.getMixins());
        mixins.add(mixin);
        setMixins(mixins);
    }

    @Override
    public Set<String> getEffectiveMixins() {
        return doc.getEffectiveMixins();
    }

    @Override
    public boolean hasMixin(String mixin) {
        return doc.hasMixin(mixin);
    }

    @Override
    public boolean isLocked() {
        return getLockInfo() != null;
    }

    @Override
    public Lock setLock() throws LockException {
        return getCoreSession().setLock(getRef());
    }

    @Override
    public Lock getLockInfo() {
        checkPermission(READ);
        return doc.getLock();
    }

    @Override
    public Lock removeLock() throws LockException {
        return getCoreSession().removeLock(getRef());
    }

    @Override
    public boolean isCheckedOut() {
        return doc.isCheckedOut();
    }

    @Override
    public void checkOut() {
        getCoreSession().checkOut(getRef());
    }

    @Override
    public DocumentRef checkIn() {
        return getCoreSession().checkIn(getRef());
    }

    @Override
    public DocumentRef checkIn(boolean minor) {
        return getCoreSession().checkIn(getRef(), minor);
    }

    @Override
    public Long getVersion() {
        Long v = doc.getVersion();
        return v == null ? 0 : v;
    }

    @Override
    public Long getMinorVersion() {
        Long v = doc.getMinorVersion();
        return v == null ? 0 : v;
    }

    @Override
    public boolean isRecord() {
        return doc.isRecord();
    }

    @Override
    public Instant getRetainUntil() {
        return doc.getRetainUntil();
    }

    @Override
    public boolean hasLegalHold() {
        return doc.hasLegalHold();
    }

    @Override
    public boolean isUnderRetentionOrLegalHold() {
        return doc.isUnderRetentionOrLegalHold();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getSecurityPolicies() {
        checkPermission(READ);
        return (List<String>) doc.getSystemProp(SYS_SECURITY_POLICIES, List.class);
    }

    @Override
    public void setSecurityPolicies(List<String> securityPolicies) {
        checkPermission(MANAGE_SECURITY);
        SecurityService securityService = Framework.getService(SecurityService.class);
        SecurityPolicyService securityPolicyService = securityService.getSecurityPolicyService(getRepositoryName());
        securityPolicyService.setSecurityPolicies(doc, securityPolicies);
    }

    @Override
    public ACL getLocalACL() {
        checkPermission(READ);
        return doc.getLocalACL();
    }

    @Override
    public ACL getEffectiveACL() {
        checkPermission(READ);
        return doc.getEffectiveACL();
    }

    @Override
    public void setACL(ACL acl, boolean overwrite) {
        checkPermission(MANAGE_SECURITY);
        doc.setACL(acl, overwrite, principal);
    }

    @Override
    public Set<String> getEffectivePermissions() {
        // no permission check necessary
        return doc.getEffectivePermissions(principal);
    }

    @Override
    public boolean isTrashed() {
        return TRUE.equals(doc.getPropertyValue(SYS_IS_TRASHED));
    }

    @Override
    public boolean isFolder() {
        return hasMixin(MixinNames.SYS_FOLDERISH);
    }

    @Override
    public boolean isVersionable() {
        return doc.isVersionable();
    }

    @Override
    public  boolean isGovernable() {
        return hasMixin(MixinNames.SYS_GOVERNABLE);
    }

    @Override
    public boolean isVersion() {
        return doc.isVersion();
    }

    @Override
    public boolean isDirty() {
        return doc.isDirty();
    }

    @Override
    public Map<String, Serializable> getContextData() {
        if (contextData == null) {
            // lazily initialized to save space
            contextData = new HashMap<>();
        }
        return contextData;
    }

    @Override
    public Serializable getContextData(String key) {
        if (contextData == null) {
            return null;
        }
        return contextData.get(key);
    }

    @Override
    public void putContextData(String key, Serializable value) {
        if (value == null) {
            if (contextData == null) {
                return;
            }
            contextData.remove(key);
            if (contextData.isEmpty()) {
                contextData = null;
            }
        } else {
            getContextData().put(key, value);
        }
    }

    @Override
    public void copyContextData(DocumentModel other) {
        Map<String, Serializable> otherContextData = ((DocumentImpl) other).contextData;
        if (otherContextData != null && !otherContextData.isEmpty()) {
            getContextData().putAll(otherContextData);
        }
    }

    @Override
    public void setUpdateCondition(String condition) throws QueryParseException {
        doc.setUpdateCondition(condition);
    }

    @Override
    public Object getPropertyValue(String xpath) throws PropertyException {
        return doc.getValue(xpath);
    }

    @Override
    public void setPropertyValue(String xpath, Object value) throws PropertyException {
        checkPermission(WRITE);
        checkSecurityPropertyUpdate(xpath);

        // change token
        String changeToken = (String) getContextData(CoreSession.CHANGE_TOKEN);
        boolean userChange = StringUtils.isNotEmpty(changeToken);
        if (!doc.validateUserVisibleChangeToken(changeToken)) {
            throw new ConcurrentUpdateException("Invalid change token").addInfo(doc.getId());
        }
        userChange = userChange || TRUE.equals(getContextData(CoreSession.USER_CHANGE));
        putContextData(CoreSession.USER_CHANGE, null);
        if (userChange) {
            doc.markUserChange();
        }

        // pre-save auto-checkout
        boolean disableAutoCheckOut = TRUE.equals(getContextData(CoreSession.DISABLE_AUTO_CHECKOUT));
        putContextData(CoreSession.DISABLE_AUTO_CHECKOUT, null);
        if (!doc.isCheckedOut() && !isVersion() && !disableAutoCheckOut) {
            checkOut(); // includes sending events
        }

        // version write
        boolean allowVersionWrite = TRUE.equals(getContextData(CoreSession.ALLOW_VERSION_WRITE));
        putContextData(CoreSession.ALLOW_VERSION_WRITE, null);
        if (doc.isVersion() && !allowVersionWrite) {
            // check if we can write a version
            if (checkReadOnlyIgnoredWrite(xpath, value)) {
                return;
            }
        }
        if (xpath.startsWith(SYSVER_PREFIX) && !READWRITE_SYSVER.contains(xpath)) {
            return; // ignore write to read-only version property
        }

        if (xpath.startsWith(SYS_PREFIX) && !READWRITE_SYS.contains(xpath)) {
            return; // ignore write to read-only system property
        }
        doc.setValue(xpath, value);
    }

    /** Checks for ignored writes. May throw. */
    private boolean checkReadOnlyIgnoredWrite(String xpath, Object value) throws PropertyException {
        if (!doc.isReadOnly() || DBSDocument.isVersionWritableProperty(xpath)) {
            // do write
            return false;
        }
        if (!xpath.startsWith(SYS_PREFIX) && !xpath.startsWith(SYSVER_PREFIX)) {
            throw new PropertyException("Cannot set property on a version: " + xpath);
        }
        // for writable system props we are lenient of attempts to re-write identical values
        // ignore write if value can quickly be detected as unchanged
        if (value instanceof Object[] array && array.length == 0) {
            // normalize empty arrays to null
            value = null;
        }
        Object oldValue;
        try {
            oldValue = doc.getValue(xpath);
        } catch (PropertyNotFoundException e) {
            // ignore here, well be thrown again on actual write in caller
            return false;
        }
        if (Objects.deepEquals(value, oldValue)) {
            // unchanged, ignore write even though strictly speaking writing the version is not allowed
            return true;
        }
        if (xpath.equals(SYSVER_TITLE) || xpath.equals(SYSVER_DESCRIPTION)) {
            // version title and description changes are allowed if the user has WRITE_VERSION
            checkPermission(WRITE_VERSION);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void visitValues(ValuesVisitor visitor) {
        // TODO visitor has the capability to write blobs
        doc.visitValues(visitor);
    }

    @Override
    public void visitBlobs(Consumer<BlobAccessor> blobVisitor) {
        // TODO visitor has the capability to write blobs
        doc.visitBlobs(blobVisitor);
    }

    @Override
    public void visitFields(ValuesVisitor visitor) {
        // TODO visitor has the capability to write blobs
        doc.visitFields(visitor);
    }

    @Override
    public void reset() {
        // nothing
    }

    @Override
    public void refresh(int refreshFlags, String[] schemas) {
        refresh();
    }

    @Override
    public void refresh() {
        // this gets called when the doc originates from a previous transaction
        // we must get a current session
        RepositoryService repositoryService = Framework.getService(RepositoryService.class);
        InternalSession session = repositoryService.getSession(getRepositoryName());
        doc = (DBSDocument) session.getDocumentByUUID(doc.getId());
    }

    @Override
    public DocumentModel clone() throws CloneNotSupportedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getChangeToken() {
        return doc.getChangeToken();
    }

    @Override
    public String getBinaryFulltext() {
        return getCoreSession().getBinaryFulltext(getRef());
    }

    @Override
    public PropertyObjectResolver getObjectResolver(String xpath) {
        return DocumentPropertyObjectResolverImpl.create(this, xpath);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DocumentImpl other && doc.equals(other.doc);
    }

    @Override
    public int hashCode() {
        return doc.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + getName() + ',' + getId() + ',' + getPrincipal().getId() + ')';
    }
}
