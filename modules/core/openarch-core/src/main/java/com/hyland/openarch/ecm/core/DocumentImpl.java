/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core;

import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CHANGE_TOKEN;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_PERMISSIONS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.utils.IdUtils;
import com.hyland.openarch.common.utils.Path;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentContext;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentService;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.LockException;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.api.ValuesVisitor;
import com.hyland.openarch.ecm.core.api.model.PropertyNotFoundException;
import com.hyland.openarch.ecm.core.api.model.resolver.DocumentPropertyObjectResolverImpl;
import com.hyland.openarch.ecm.core.api.model.resolver.PropertyObjectResolver;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.model.DocumentEventSpec;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.model.InternalDocument.BlobAccessor;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.ecm.core.model.WriteResult;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.MixinNames;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.CompositeType;
import com.hyland.openarch.ecm.core.security.SecurityService;
import com.hyland.openarch.ecm.core.storage.dbs.DBSDocument;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession;
import com.hyland.openarch.runtime.api.Framework;

public class DocumentImpl implements DocumentModel {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(DocumentImpl.class);

    private static final Pattern DOT_DIGITS_PATTERN = Pattern.compile("(.*)\\.\\d+$");

    private static final Pattern NAME_INVALID = Pattern.compile("^[- .,;?!:/\\\\'\"]*$");

    private static final int NAME_MAX_SIZE = 100;

    private DBSDocument doc;

    private final RepositoryPrincipal principal;

    private Map<String, Serializable> contextData;

    public DocumentImpl(InternalDocument doc, RepositoryPrincipal principal) {
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
        // stamp sys_* fields and run any registered document enrichers, before persistence
        if (!isVersion()) {
            enrichOnCreate();
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

    private void enrichOnCreate() {
        DocumentEnrichmentService service = Framework.getService(DocumentEnrichmentService.class);
        if (service != null) {
            var context = new DocumentEnrichmentContext(getPrincipal(), Instant.now(), doc);
            service.enrichOnCreate(context);
        }
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

    @Override
    public RepositoryPrincipal getPrincipal() {
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
                throw new RepositoryException("Missing parent");
            }
            String parentPath = parent.getPath();
            if (parentPath == null) {
                throw new RepositoryException("Version without working copy");
            }
            if (!parentPath.startsWith("/")) {
                throw new RepositoryException("Placeless document");
            }
            String name = getName();
            if (name == null) {
                throw new RepositoryException("Missing name");
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
        setPropertyValue(SYS_ID, id);
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
    public String getPathAsString() {
        return doc.getPath();
    }

    @Override
    public Path getPath() {
        String path = getPathAsString();
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
        SchemaManager schemaManager = doc.getSchemaManager();
        Set<String> schemaNames = new HashSet<>(doc.getType().getSchemaNames());
        for (String mixins : getEffectiveMixins()) {
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
        SchemaManager schemaManager = doc.getSchemaManager();
        for (String mixin : getEffectiveMixins()) {
            CompositeType mixinType = schemaManager.getMixin(mixin);
            if (mixinType != null && mixinType.hasSchema(schemaName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setPrimaryTypeAndMixinTypes(String typeName, Collection<String> mixins) {
        doc.setPrimaryTypeAndMixinTypes(typeName, mixins, principal);
    }

    @Override
    public void setMixins(Collection<String> mixins) {
        setPrimaryTypeAndMixinTypes(null, mixins);
    }

    @Override
    public void addMixin(String mixin) {
        Set<String> mixins = new HashSet<>(getEffectiveMixins());
        mixins.add(mixin);
        setMixins(mixins);
    }

    @Override
    public Set<String> getEffectiveMixins() {
        return doc.getEffectiveMixins();
    }

    @Override
    public boolean hasMixin(String mixin) {
        return getEffectiveMixins().contains(mixin);
    }

    @Override
    public boolean isLocked() {
        return getLockInfo() != null;
    }

    @Override
    public Lock setLock() throws LockException {
        return setValue(SYS_LOCK_OWNER, principal.getId(), principal).lock();
    }

    @Override
    public Lock getLockInfo() {
        String owner = (String) getPropertyValue(SYS_LOCK_OWNER);
        return owner == null ? null : new Lock(owner, (Instant) getPropertyValue(SYS_LOCK_CREATED));
    }

    @Override
    public Lock removeLock() throws LockException {
        return setValue(SYS_LOCK_OWNER, null, principal).lock();
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
        Long version = doc.getVersion();
        return version == null ? 0 : version;
    }

    @Override
    public Long getMinorVersion() {
        Long version = doc.getMinorVersion();
        return version == null ? 0 : version;
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

    @Override
    public ACL getLocalACL() {
        return (ACL) getPropertyValue(SYS_ACL);
    }

    @Override
    public ACL getEffectiveACL() {
        return (ACL) getPropertyValue(SYS_EFFECTIVE_ACL);
    }

    @Override
    public void setACL(ACL acl, boolean overwrite) {
        WriteResult result = doc.setACL(acl, overwrite, principal);
        List<DocumentEventSpec> events = result.events();
        getCoreSession().publishWriteEvents(this, events);
    }

    @Override
    public Set<String> getEffectivePermissions() {
        return new HashSet<>(castList(getPropertyValue(SYS_EFFECTIVE_PERMISSIONS)));
    }

    @Override
    public boolean isTrashed() {
        return TRUE.equals(getPropertyValue(SYS_IS_TRASHED));
    }

    @Override
    public boolean isFolder() {
        return doc.isFolder();
    }

    @Override
    public boolean isVersionable() {
        return hasMixin(MixinNames.SYS_VERSIONABLE);
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
    public void markUserChange() {
        doc.markUserChange();
    }

    @Override
    public void setUpdateCondition(UpdateCondition condition) throws QueryParseException {
        doc.setUpdateCondition(condition, principal);
    }

    @Override
    public Object getPropertyValue(String xpath) throws PropertyException {
        return doc.getValue(xpath, principal);
    }

    @Override
    public void setPropertyValue(String xpath, Object value) throws PropertyException {
        setValue(xpath, value, principal);
    }

    private WriteResult setValue(String xpath, Object value, RepositoryPrincipal principal) {
        WriteResult result = doc.setValue(xpath, value, principal);
        List<DocumentEventSpec> events = result.events();
        if (!events.isEmpty()) { // avoid constructing session if no events
            getCoreSession().publishWriteEvents(this, events);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return (List<T>) value;
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
        doc = (DBSDocument) session.getDocumentByUUID(getId());
    }

    @Override
    public DocumentModel clone() throws CloneNotSupportedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getGeneration() {
        return doc.getGeneration();
    }

    @Override
    public long getUserGeneration() {
        return doc.getUserGeneration();
    }

    @Override
    public String getChangeToken() {
        return (String) getPropertyValue(SYS_CHANGE_TOKEN);
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
