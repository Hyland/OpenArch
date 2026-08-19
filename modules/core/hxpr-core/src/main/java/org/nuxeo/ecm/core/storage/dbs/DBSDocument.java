/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage.dbs;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_JOB_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.CoreSession.RETAIN_UNTIL_INDETERMINATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_POS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SYS_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.MANAGE_SECURITY;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.BaseValuesVisitor;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.UpdateCondition;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.api.lock.LockManager;
import org.nuxeo.ecm.core.api.model.BlobNotFoundException;
import org.nuxeo.ecm.core.api.model.PropertyConversionException;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.model.VersionNotModifiableException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.DocumentBlobService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.MixinNames;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.FieldImpl;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DecimalType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.StateHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Implementation of a {@link InternalDocument} for Document-Based Storage. The document is stored as a JSON-like Map. The keys
 * of the Map are the property names (including special names for system properties), and the values Map are
 * values, either:
 * <ul>
 * <li>a scalar (String, Long, Double, Boolean, Instant, BigDecimal, Binary),
 * <li>an array of scalars,
 * <li>a List of Maps, recursively,
 * <li>or another Map, recursively.
 * </ul>
 * An ACL value is stored as a list of ACEs. An ACE is a map having as keys username, permission, grant, and others.
 *
 * @since 5.9.4
 */
public class DBSDocument implements InternalDocument {

    private static final Logger log = LogManager.getLogger(DBSDocument.class);

    public static final String KEY_PATH_INTERNAL = "sys___path";

    public static final String KEY_ACE_PRINCIPAL = "principal";

    public static final String KEY_ACE_PERMISSION = "perm";

    public static final String KEY_ACE_GRANT = "grant";

    public static final String KEY_ACE_CREATOR = "creator";

    public static final String KEY_ACE_BEGIN = "begin";

    public static final String KEY_ACE_END = "end";

    public static final String KEY_ACE_STATUS = "status";

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    private static final Set<String> VERSION_WRITABLE_PROPS = new HashSet<>(Arrays.asList( //
            SYS_FULLTEXT_JOB_ID, //
            SYS_FULLTEXT_BINARY, //
            SYS_IS_TRASHED, //
            SYS_LIFECYCLE_STATE, //
            SYS_LOCK_OWNER, //
            SYS_LOCK_CREATED, //
            SYSGOV_IS_RECORD, //
            SYSGOV_RETAIN_UNTIL, //
            SYSGOV_HAS_LEGAL_HOLD //
    ));

    public static final Long INITIAL_SYS_CHANGE_TOKEN = Long.valueOf(0);

    public static final Long INITIAL_CHANGE_TOKEN = Long.valueOf(0);

    // in particular : (colon), / (slash) and % (percent) are not allowed
    private static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z0-9-_.]+");

    private static final int ID_MAX_LEN = 200;

    private final SchemaManager schemaManager;

    protected final DBSDocumentState docState;

    protected DocumentType type;

    protected final DBSSession session;

    protected boolean readonly;

    private final SecurityPolicyService securityPolicyService;

    protected static final Set<String> VALID_SYS_PROPS = Set.of( //
            SYS_FULLTEXT_JOB_ID, //
            SYS_IS_TRASHED, //
            SYS_LIFECYCLE_STATE, //
            SYS_SECURITY_POLICIES, //
            SYS_READ_ACL);

    public DBSDocument(DBSDocumentState docState, DocumentType type, DBSSession session, boolean readonly) {
        // no state for NullDocument (parent of placeless children)
        this.docState = docState;
        this.type = type;
        this.session = session;
        schemaManager = session.getSchemaManager();
        this.readonly = readonly;
        securityPolicyService = Framework.getService(SecurityService.class)
                                         .getSecurityPolicyService(session.getRepositoryName());
    }

    /**
     * Creates a document whose state is not yet persisted or available in the DBSTransactionState.
     *
     * @see #create
     */
    public static DBSDocument newUnCreatedDocument(InternalDocument parent, String name, String typeName,
            InternalSession session) {
        DocumentType type = session.getSchemaManager().getDocumentType(typeName);
        if (type == null) {
            throw new IllegalArgumentException("Unknown document type: " + typeName);
        }
        DBSDocumentState docState = ((DBSSession) session).newState();
        docState.put(SYS_PRIMARY_TYPE, typeName);
        if (parent != null) {
            docState.put(SYS_PARENT_ID, parent.getId());
        }
        docState.put(SYS_NAME, name);
        return new DBSDocument(docState, type, (DBSSession) session, false);
    }

    // called by DocumentImpl
    public void setName(String name) {
        docState.put(SYS_NAME, name);
    }

    private boolean isCreated() {
        return session.getRootId().equals(getId()) || docState.get(SYS_SYS_CHANGE_TOKEN) != null;
    }

    /**
     * Actually creates the document in the DBSTransactionState, for later persistence during
     * {@link InternalSession#save}.
     *
     * @see #newUnCreatedDocument
     */
    public void create() {
        if (isCreated()) {
            throw new NuxeoException("Document already created");
        }
        session.create(docState);
    }

    public boolean isDirty() {
        return docState.isDirty();
    }

    @Override
    public DocumentType getType() {
        return type;
    }

    @Override
    public InternalSession getInternalSession() {
        return session;
    }

    @Override
    public String getRepositoryName() {
        return session.getRepositoryName();
    }

    @Override
    public String getId() {
        return docState == null ? null : docState.getId();
    }

    @Override
    public void setId(String id) {
        if (isCreated()) {
            throw new NuxeoException("Document already created");
        }
        if (!ID_PATTERN.matcher(id).matches() || id.length() > ID_MAX_LEN) {
            throw new PropertyException("Invalid id: " + id);
        }
        docState.put(SYS_ID, id);
    }

    @Override
    public String getName() {
        return docState.getName();
    }

    @Override
    public Long getPos() {
        return (Long) docState.get(SYS_POS);
    }

    @Override
    public String getParentId() {
        return docState.getParentId();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAncestorIds() {
        List<String> ids = (List<String>) docState.get(SYS_ANCESTOR_IDS);
        return ids == null ? List.of() : Collections.unmodifiableList(ids);
    }

    @Override
    public InternalDocument getParent() {
        return getParent(false);
    }

    @Override
    public InternalDocument getParent(boolean allowUnknownType) {
        String parentId = getParentId();
        return parentId == null ? null : session.getDocument(parentId, allowUnknownType);
    }

    @Override
    public boolean isVersionable() {
        return hasMixin(MixinNames.SYS_VERSIONABLE);
    }

    @Override
    public boolean isVersion() {
        return TRUE.equals(docState.get(SYSVER_IS_VERSION));
    }

    @Override
    public String getPath() {
        String name = getName();
        String parentId = getParentId();
        if (parentId == null) {
            if ("".equals(name)) {
                return "/"; // root
            } else {
                return name; // placeless, no slash
            }
        }
        LinkedList<String> list = new LinkedList<>();
        list.addFirst(name);
        while (parentId != null) {
            InternalDocument parentDoc = session.getDocument(parentId, true); //allowUnknownType=true
            if (parentDoc == null) {
                break; // Stop if the parent document is null, indicating it might be deleted
            }
            name = parentDoc.getName();
            parentId = parentDoc.getParentId();
            list.addFirst(name);
        }
        return StringUtils.join(list, '/');
    }

    @Override
    public ACL getLocalACL() {
        return stateToAcl(docState.get(SYS_ACL), getId());
    }

    @Override
    public ACL getEffectiveACL() {
        ACL effectiveAcl = new ACLImpl();
        boolean processReadVersion = false;
        InternalDocument current = this;
        LOOP: //
        do {
            if (current.isVersion()) {
                processReadVersion = true;
            }
            ACL acl = current.getLocalACL();
            if (acl != null) {
                for (ACE ace : acl) {
                    if (ace.isBlockingInheritance()) {
                        break LOOP;
                    }
                    effectiveAcl.add(ace);
                    if (processReadVersion && READ_VERSION.equals(ace.getPermission())) {
                        effectiveAcl.add(ACE.builder(ace).permission(READ).build());
                    }
                }
            }
            current = current.getParent(true);
        } while (current != null);
        return effectiveAcl;
    }

    @Override
    public void setACL(ACL acl, boolean overwrite, NuxeoPrincipal principal) {
        if (!overwrite && acl == null) {
            return;
        }
        if (acl != null) {
            checkACL(acl);
        }
        if (!overwrite) {
            acl = updateACL(getLocalACL(), acl);
        }
        docState.put(SYS_ACL, aclToState(acl));

        if (isCreated()) {
            // update read acls
            session.updateDenormalizedPropertiesWithDescendants(getId(), principal);
        } // else denormalization will happen at create() time
    }

    /**
     * Check ACL validity.
     */
    private void checkACL(ACL acl) {
        for (ACE ace : acl) {
            // check principal
            ace.checkPrincipal();
            // check negative ACE
            if (ace.isGranted() || ace.isBlockingInheritance()) {
                continue;
            }
            // allow Write, as we're sure it doesn't include Read
            if (ace.getPermission().equals(WRITE)) {
                continue;
            }
            throw new IllegalArgumentException("Negative ACL not allowed: " + ace);
        }
    }

    /**
     * Returns the merge of two ACLs.
     */
    private ACL updateACL(ACL curAcl, ACL addAcl) {
        if (curAcl == null) {
            return addAcl;
        }
        ACL newAcl = curAcl.clone(); // clone as we may modify ACLs
        newAcl.addAll(addAcl);
        return newAcl;
    }

    private static Object aclToState(ACL acl) {
        if (acl == null || acl.isEmpty()) {
            return null;
        }
        List<Object> aceList = new ArrayList<>(acl.size());
        for (ACE ace : acl) {
            State aceMap = new State(3);
            aceMap.put(KEY_ACE_PRINCIPAL, ace.getPrincipal());
            aceMap.put(KEY_ACE_PERMISSION, ace.getPermission());
            aceMap.put(KEY_ACE_GRANT, ace.isGranted());
            String creator = ace.getCreator();
            if (creator != null) {
                aceMap.put(KEY_ACE_CREATOR, creator);
            }
            Instant begin = ace.getBegin();
            if (begin != null) {
                aceMap.put(KEY_ACE_BEGIN, begin);
            }
            Instant end = ace.getEnd();
            if (end != null) {
                aceMap.put(KEY_ACE_END, end);
            }
            Long status = ace.getLongStatus();
            if (status != null) {
                aceMap.put(KEY_ACE_STATUS, status);
            }
            aceList.add(aceMap);
        }
        return aceList;
    }

    private static ACL stateToAcl(Object aclSer, String id) {
        ACL acl = new ACLImpl();
        if (aclSer == null) {
            return acl;
        }
        @SuppressWarnings("unchecked")
        List<Object> aceList = (List<Object>) aclSer;
        for (Object aceSer : aceList) {
            if (aceSer == null) {
                // debug corrupted databases
                log.warn("A null ACE has been detected on document: {}, acl: {}, ignoring it", id, aclSer);
                continue;
            }
            State aceMap = (State) aceSer;
            Boolean granted = (Boolean) aceMap.get(KEY_ACE_GRANT);
            String principal = (String) aceMap.get(KEY_ACE_PRINCIPAL);
            String permission = (String) aceMap.get(KEY_ACE_PERMISSION);
            if (granted == null || principal == null || permission == null) {
                // debug corrupted databases
                log.warn("An ACE with null element has been detected on document: {}, acl: {}, ignoring it", id,
                        aclSer);
                continue;
            }
            String creator = (String) aceMap.get(KEY_ACE_CREATOR);
            Instant begin = (Instant) aceMap.get(KEY_ACE_BEGIN);
            Instant end = (Instant) aceMap.get(KEY_ACE_END);
            // status not read, ACE always computes it on read
            ACE ace = ACE.builder()
                         .principal(principal)
                         .permission(permission)
                         .isGranted(granted)
                         .creator(creator)
                         .begin(begin)
                         .end(end)
                         .build();
            acl.add(ace);
        }
        return acl;
    }

    @Override
    public boolean hasPermission(NuxeoPrincipal principal, String permission) {
        if (!isCreated() && Set.of(WRITE, MANAGE_SECURITY).contains(permission)) {
            return true;
        }
        return securityPolicyService.hasPermission(this, principal, permission);
    }

    @Override
    public Set<String> getEffectivePermissions(NuxeoPrincipal principal) {
        return securityPolicyService.getEffectivePermissions(this, principal);
    }

    @Override
    public InternalDocument getChild(String name) {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        return session.getChild(getId(), name);
    }

    @Override
    public List<InternalDocument> getChildren() {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        return session.getChildren(getId());
    }

    @Override
    public List<String> getChildrenIds() {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        return session.getChildrenIds(getId());
    }

    @Override
    public boolean hasChild(String name) {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        return session.hasChild(getId(), name);
    }

    @Override
    public boolean hasChildren() {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        return session.hasChildren(getId());
    }

    @Override
    public void orderBefore(String src, String dest) {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        InternalDocument srcDoc = getChild(src);
        if (srcDoc == null) {
            throw new DocumentNotFoundException("Document " + this + " has no child: " + src);
        }
        InternalDocument destDoc;
        if (dest == null) {
            destDoc = null;
        } else {
            destDoc = getChild(dest);
            if (destDoc == null) {
                throw new DocumentNotFoundException("Document " + this + " has no child: " + dest);
            }
        }
        session.orderBefore(getId(), srcDoc.getId(), destDoc == null ? null : destDoc.getId());
    }

    // simple property only
    @Override
    public Object getPropertyValue(String name) {
        return docState.get(name);
    }

    // simple property only
    @Override
    public void setPropertyValue(String name, Object value) {
        docState.put(name, value);
    }

    // helpers for getValue / setValue

    /**
     * Gets a child state into which we will want to write data.
     * <p>
     * Creates it if needed.
     */
    protected static State getChildForWrite(State state, String name) {
        return (State) state.computeIfAbsent(name, k -> new State());
    }

    /**
     * Gets a child state which is a list.
     */
    protected static List<State> getChildAsList(State state, String name) {
        @SuppressWarnings("unchecked")
        List<State> list = (List<State>) state.get(name);
        if (list == null) {
            list = List.of();
        }
        return list;
    }

    @Override
    public Object getValue(String xpath) throws PropertyException {
        return getValueObject(docState.getState(), xpath);
    }

    @Override
    public void setValue(String xpath, Object value) throws PropertyException {
        // markDirty has to be called *before* we change the state
        docState.markDirty();
        setValueObject(docState.getState(), xpath, value);
    }

    @Override
    public void visitValues(ValuesVisitor visitor) {
        DocumentVisitor documentVisitor = new DocumentVisitor(visitor, iterationProviderForState(visitor.keySorter()));
        documentVisitor.visitState(docState.getState(), type);
    }

    @Override
    public void visitBlobs(Consumer<BlobAccessor> blobVisitor) {
        visitValues(new BlobsVisitor(blobVisitor, docState::markDirty));
    }

    @Override
    public void visitFields(ValuesVisitor visitor) {
        DocumentVisitor documentVisitor = new DocumentVisitor(visitor, iterationProviderForFields());
        State state = docState.getState();
        // primary type + static mixins from the type
        documentVisitor.visitState(state, type);
        // mixin types
        for (String mixin : getMixins()) {
            CompositeType mixinType = schemaManager.getMixin(mixin);
            if (mixinType != null) {
                documentVisitor.visitState(state, mixinType);
            }
        }
    }

    @Override
    public String replaceBlobDigest(String key, String newKey, String newDigest) {
        MutableObject<String> oldDigestHolder = new MutableObject<>();
        visitBlobs(accessor -> replaceBlobDigest(accessor, key, newKey, newDigest, oldDigestHolder));
        return oldDigestHolder.getValue();
    }

    protected void replaceBlobDigest(BlobAccessor accessor, String key, String newKey, String newDigest,
            MutableObject<String> oldDigestHolder) {
        Blob blob = accessor.getBlob();
        if (!(blob instanceof SimpleManagedBlob managedBlob)) {
            return;
        }
        if (!managedBlob.getKey().equals(key)) {
            return;
        }
        // if there was no digest, return a non-null value nevertheless
        // as a real null is a signal that no replacement was done
        oldDigestHolder.setValue(String.valueOf(blob.getDigest()));
        accessor.setBlob(managedBlob.withKeyAndDigest(newKey, newDigest));
    }

    protected DocumentBlobService getDocumentBlobService() {
        return Framework.getService(DocumentBlobService.class);
    }

    @Override
    public void makeRecord() {
        docState.put(SYSGOV_IS_RECORD, TRUE);
        getDocumentBlobService().notifyMakeRecord(this);
    }

    @Override
    public boolean isRecord() {
        return TRUE.equals(docState.get(SYSGOV_IS_RECORD));
    }

    @Override
    public void setRetainUntil(Instant retainUntil) throws PropertyException {
        Instant current = (Instant) docState.get(SYSGOV_RETAIN_UNTIL);
        if (!allowNewRetention(current, retainUntil)) {
            throw new PropertyException(
                    "Cannot reduce retention time from: " + current + " to: "
                            + (retainUntil == null ? "null" : retainUntil));
        }
        docState.put(SYSGOV_RETAIN_UNTIL, retainUntil);
        DocumentBlobService documentBlobService = getDocumentBlobService();
        boolean hasLegalHold = hasLegalHold();
        if (!hasLegalHold && RETAIN_UNTIL_INDETERMINATE.equals(retainUntil)) {
            documentBlobService.notifySetLegalHold(this, true);
        } else {
            if (!hasLegalHold && RETAIN_UNTIL_INDETERMINATE.equals(current)) {
                documentBlobService.notifySetLegalHold(this, false);
            }
            documentBlobService.notifySetRetainUntil(this, retainUntil);
        }
    }

    @Override
    public Instant getRetainUntil() {
        return (Instant) docState.get(SYSGOV_RETAIN_UNTIL);
    }

    @Override
    public void setLegalHold(boolean hold) {
        docState.put(SYSGOV_HAS_LEGAL_HOLD, hold ? TRUE : null);
        if (hold || !RETAIN_UNTIL_INDETERMINATE.equals(getRetainUntil())) {
            getDocumentBlobService().notifySetLegalHold(this, hold);
        }
    }

    @Override
    public boolean hasLegalHold() {
        return TRUE.equals(docState.get(SYSGOV_HAS_LEGAL_HOLD));
    }

    @Override
    public InternalDocument checkIn(NuxeoPrincipal principal) {
        return checkIn(principal, false);
    }

    @Override
    public InternalDocument checkIn(NuxeoPrincipal principal, boolean minor) {
        if (!isCreated()) {
            throw new NuxeoException("Document not yet created");
        }
        Long v = getVersion();
        Long w = getMinorVersion();
        long version = v == null ? 0 : v;
        long minorVersion = w == null ? 0 : w;
        if (minor) {
            minorVersion++;
        } else {
            version++;
            minorVersion = 0;
        }
        setVersion(version, minorVersion);
        return checkInInternal(principal);
    }

    private InternalDocument checkInInternal(NuxeoPrincipal principal) {
        if (isRecord()) {
            throw new PropertyException("Record cannot be checked in: " + getId());
        } else if (isVersion()) {
            throw new VersionNotModifiableException("Version cannot be checked in: " + getId());
        } else {
            InternalDocument version = session.checkIn(this, principal);
            getDocumentBlobService().freezeVersion(version);
            return version;
        }
    }

    @Override
    public void checkOut() {
        if (isVersion()) {
            throw new VersionNotModifiableException("Version cannot be checked out: " + getId());
        } else {
            session.checkOut(this);
        }
    }

    @Override
    public boolean isCheckedOut() {
        if (isVersion()) {
            return false;
        } else {
            return !TRUE.equals(docState.get(SYSVER_IS_CHECKED_IN));
        }
    }

    @Override
    public Instant getVersionCreationDate() {
        return (Instant) docState.get(SYSVER_CREATED);
    }

    @Override
    public Long getVersion() {
        return (Long) docState.get(SYSVER_VERSION);
    }

    @Override
    public Long getMinorVersion() {
        return (Long) docState.get(SYSVER_MINOR_VERSION);
    }

    private void setVersion(long version, long minorVersion) {
        setPropertyValue(SYSVER_VERSION, Long.valueOf(version));
        setPropertyValue(SYSVER_MINOR_VERSION, Long.valueOf(minorVersion));
    }

    protected boolean isEqualOnVersion(Object ob, String key) {
        if (isVersion()) {
            return ob.equals(docState.get(key));
        } else {
            return false;
        }
    }

    @Override
    public InternalDocument getWorkingCopy() {
        if (isVersion()) {
            return getParent();
        } else {
            return null;
        }
    }

    @Override
    public boolean isFolder() {
        return TRUE.equals(docState.get(SYS_IS_FOLDERISH));
    }

    @Override
    public void setReadOnly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public boolean isReadOnly() {
        return readonly;
    }

    @Override
    public void remove(NuxeoPrincipal principal) {
        session.remove(getId(), principal);
    }

    @Override
    public void removeSingleton(NuxeoPrincipal principal) {
        session.removeDocument(getId(), principal);
    }

    @Override
    public void setSystemProp(String name, Object value) {
        if (name.startsWith(SYS_FULLTEXT_BINARY)) {
            if (session.fulltextStoredInBlob) {
                if (!(value instanceof String)) {
                    throw new PropertyException("Property " + name + " must be a string");
                }
                setPropertyBlobData(name, (String) value);
                return;
            }
        } else if (!VALID_SYS_PROPS.contains(name)) {
            // cannot be set by user code
            throw new PropertyNotFoundException(name);
        }
        if (value instanceof Object[] array) {
            value = Arrays.asList(array);
        }
        if (value instanceof List list && list.isEmpty()) {
            value = null;
        }
        setPropertyValue(name, value);
    }

    @Override
    public Object getSystemProp(String name, Class<?> type) {
        if (!VALID_SYS_PROPS.contains(name)) {
            throw new PropertyNotFoundException(name);
        }
        Object value = getPropertyValue(name);
        if (value == null) {
            if (type == Boolean.class) {
                value = Boolean.FALSE;
            } else if (type == Long.class) {
                value = Long.valueOf(0);
            } else if (type == List.class) {
                value = List.of();
            }
        }
        return value;
    }

    @Override
    public String getChangeToken() {
        Long sysChangeToken = docState.getSysChangeToken();
        Long changeToken = docState.getChangeToken();
        return buildUserVisibleChangeToken(sysChangeToken, changeToken);
    }

    @Override
    public boolean validateUserVisibleChangeToken(String userVisibleChangeToken) {
        if (userVisibleChangeToken == null) {
            return true;
        }
        return docState.validateUserVisibleChangeToken(userVisibleChangeToken);
    }

    @Override
    public void markUserChange() {
        session.markUserChange(getId());
    }

    @Override
    public void setUpdateCondition(UpdateCondition condition, NuxeoPrincipal principal) throws QueryParseException {
        session.setUpdateCondition(getId(), condition, principal);
    }

    protected String getSchema(String xpath) {
        switch (xpath) {
        case SYSVER_MINOR_VERSION:
        case SYSVER_VERSION:
        case SYS_FULLTEXT_JOB_ID:
        case SYS_IS_TRASHED:
        case SYS_LIFECYCLE_STATE:
        case SYS_SECURITY_POLICIES:
        case SYS_BLOB_KEYS:
        case SYS_READ_ACL:
            return "__sys__";
        default:
        }
        if (xpath.startsWith(SYS_FULLTEXT_BINARY)) {
            return "__sys__";
        }
        String[] segments = xpath.split("/");
        Field field = getField(getType(), segments[0]);
        if (field == null) {
            throw new PropertyNotFoundException(xpath);
        }
        return field.getDeclaringType().getName();
    }

    @Override
    public Set<String> getEffectiveMixins() {
        Set<String> mixins = new HashSet<>(getType().getMixins());
        mixins.addAll(getMixins());
        return mixins;
    }

    @Override
    public List<String> getMixins() {
        @SuppressWarnings("unchecked")
        List<String> mixins = (List<String>) docState.get(SYS_MIXIN_TYPES);
        if (mixins == null) {
            return List.of();
        }
        return Collections.unmodifiableList(mixins);
    }

    @Override
    public boolean hasMixin(String mixin) {
        return getEffectiveMixins().contains(mixin);
    }

    @Override
    public boolean setPrimaryTypeAndMixinTypes(String typeName, Collection<String> mixins) {
        // new type and mixins
        if (typeName == null) {
            typeName = type.getName();
        }
        if (mixins == null) {
            mixins = getMixins();
        }

        DocumentType newType = schemaManager.getDocumentType(typeName);
        if (newType == null) {
            throw new IllegalArgumentException(typeName);
        }
        List<Object> newMixins = new ArrayList<>();
        Set<String> newSchemas = new HashSet<>();
        for (String newMixin : mixins) {
            CompositeType ct = schemaManager.getMixin(newMixin);
            if (ct == null) {
                throw new IllegalArgumentException(newMixin);
            }
            newMixins.add(newMixin);
            newSchemas.addAll(ct.getSchemaNames());
        }
        newSchemas.addAll(newType.getSchemaNames());
        newMixins.removeAll(newType.getMixins()); // instance mixins exclude those from type
        if (newMixins.isEmpty()) {
            newMixins = null;
        } else {
            newMixins.sort(null); // ensure stable representation in database
        }

        // old type and mixins

        DocumentType oldType = getType();
        @SuppressWarnings("unchecked")
        List<Object> oldMixins = (List<Object>) docState.get(SYS_MIXIN_TYPES);
        Set<String> oldSchemas = new HashSet<>();
        if (oldMixins != null) {
            for (Object oldMixin : oldMixins) {
                CompositeType ct = schemaManager.getMixin((String) oldMixin);
                if (ct == null) {
                    // ignore invalid old mixin
                    continue;
                }
                oldSchemas.addAll(ct.getSchemaNames());
            }
        }
        oldSchemas.addAll(oldType.getSchemaNames());

        // remove the fields belonging to the schemas that are gone
        Set<String> removedSchemas = new HashSet<>(oldSchemas);
        removedSchemas.removeAll(newSchemas);
        for (String schemaName : removedSchemas) {
            Schema schema = schemaManager.getSchema(schemaName);
            for (Field field : schema.getFields()) {
                docState.put(field.getName(), null); // remove any value
            }
        }

        type = newType;
        docState.put(SYS_PRIMARY_TYPE, typeName);
        docState.put(SYS_MIXIN_TYPES, newMixins);

        denormalizeSysIsFolderish(docState, newMixins);

        return oldType != newType || !(ListUtils.isEqualList(oldMixins, newMixins));
    }

    // denormalize sys_isFolderish from sys_mixinTypes and sys_primaryType
    private void denormalizeSysIsFolderish(DBSDocumentState docState, List<Object> mixins) {
        boolean isFolderish = mixins != null && mixins.contains(MixinNames.SYS_FOLDERISH);
        isFolderish |= getType().isFolder();
        docState.put(SYS_IS_FOLDERISH, isFolderish ? Boolean.TRUE : null);
    }

    @Override
    public Lock getLock() {
        // for performance reasons, get lock from cached state
        return getDocumentLock();
    }

    /**
     * Gets the lock from this document.
     *
     * @return the lock, or {@code null} if no lock is set
     * @since 7.4
     */
    protected Lock getDocumentLock() {
        String owner = (String) docState.get(SYS_LOCK_OWNER);
        if (owner == null) {
            return null;
        }
        Instant created = (Instant) docState.get(SYS_LOCK_CREATED);
        return new Lock(owner, created);
    }

    @Override
    public Lock setLock(Lock lock) {
        if (lock == null) {
            throw new NullPointerException("Attempt to use null lock on: " + getId());
        }
        try {
            Lock res = getInternalSession().getLockManager().setLock(getId(), lock);
            if (res == null) {
                // lock success
                setDocumentLock(lock, true);
            }
            return res;
        } catch (DocumentNotFoundException e) {
            // recently created and unsaved document, just set lock locally
            return setDocumentLock(lock, false);
        }
    }

    /**
     * Sets a lock on this document.
     *
     * @param lock the lock to set
     * @param force if {@code true} then ignore previous lock
     * @return {@code null} if locking succeeded, or the existing lock if locking failed
     * @since 7.4
     */
    protected Lock setDocumentLock(Lock lock, boolean force) {
        if (!force) {
            String owner = (String) docState.get(SYS_LOCK_OWNER);
            if (owner != null) {
                // return old lock to denote failure
                Instant created = (Instant) docState.get(SYS_LOCK_CREATED);
                return new Lock(owner, created);
            }
        }
        docState.put(SYS_LOCK_OWNER, lock.getOwner());
        docState.put(SYS_LOCK_CREATED, lock.getCreated());
        return null;
    }

    @Override
    public Lock removeLock(String owner) {
        try {
            Lock lock = getInternalSession().getLockManager().removeLock(getId(), owner);
            if (lock == null || !lock.getFailed()) {
                removeDocumentLock(null);
            }
            return lock;
        } catch (DocumentNotFoundException e) {
            return removeDocumentLock(owner);
        }
    }

    /**
     * Removes a lock from this recently created and unsaved document.
     *
     * @param owner the owner to check, or {@code null} for no check
     * @return {@code null} if there was no lock or if removal succeeded, or a lock if it blocks removal due to owner
     *         mismatch
     * @since 7.4
     */
    protected Lock removeDocumentLock(String owner) {
        String oldOwner = (String) docState.get(SYS_LOCK_OWNER);
        if (oldOwner == null) {
            // no previous lock
            return null;
        }
        Instant oldCreated = (Instant) docState.get(SYS_LOCK_CREATED);
        if (!LockManager.canLockBeRemoved(oldOwner, owner)) {
            // existing mismatched lock, flag failure
            return new Lock(oldOwner, oldCreated, true);
        }
        // remove lock
        docState.put(SYS_LOCK_OWNER, null);
        docState.put(SYS_LOCK_CREATED, null);
        // return old lock
        return new Lock(oldOwner, oldCreated);
    }

    public static boolean isVersionWritableProperty(String name) {
        return VERSION_WRITABLE_PROPS.contains(name) //
                || name.startsWith(SYS_FULLTEXT_BINARY);
    }

    protected BlobInfo getBlobInfo(State state) throws PropertyException {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey((String) state.get(KEY_BLOB_KEY));
        blobInfo.setFilename((String) state.get(KEY_BLOB_FILENAME));
        blobInfo.setMimeType((String) state.get(KEY_BLOB_MIME_TYPE));
        blobInfo.setEncoding((String) state.get(KEY_BLOB_ENCODING));
        blobInfo.setDigest((String) state.get(KEY_BLOB_DIGEST));
        blobInfo.setLength((Long) state.get(KEY_BLOB_LENGTH));
        return blobInfo;
    }

    protected void setBlobInfo(State state, BlobInfo blobInfo) throws PropertyException {
        state.put(KEY_BLOB_KEY, blobInfo.getKey());
        state.put(KEY_BLOB_FILENAME, blobInfo.getFilename());
        state.put(KEY_BLOB_MIME_TYPE, blobInfo.getMimeType());
        state.put(KEY_BLOB_ENCODING, blobInfo.getEncoding());
        state.put(KEY_BLOB_DIGEST, blobInfo.getDigest());
        state.put(KEY_BLOB_LENGTH, blobInfo.getLength());
    }

    private Field getField(ComplexType type, String name) {
        Field field = type.getField(name);
        if (field != null || !(type instanceof DocumentType)) {
            return field;
        }
        // check mixins
        for (String mixin : getMixins()) {
            CompositeType mixinType = schemaManager.getMixin(mixin);
            if (mixinType != null) {
                field = mixinType.getField(name);
                if (field != null) {
                    return field;
                }
            }
        }
        return null;
    }

    /**
     * Gets a value (may be complex/list) from the document at the given xpath.
     */
    protected Object getValueObject(State state, String xpath) throws PropertyException {
        String[] segments = xpath.split("/");

        /*
         * During this loop state may become null if we read an uninitialized complex property (DBS), in that case the
         * code must treat it as reading uninitialized values for its children.
         */
        ComplexType parentType = getType();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            Field field = getField(parentType, segment);
            if (field == null) {
                throw new PropertyNotFoundException(xpath);
            }
            String name = field.getName(); // normalize from segment
            Type type = field.getType();

            // check if we have a complex list index in the next position
            if (i < segments.length - 1 && StringUtils.isNumeric(segments[i + 1])) {
                int index = Integer.parseInt(segments[i + 1]);
                i++;
                if (!type.isListType() || ((ListType) type).getFieldType().isSimpleType()) {
                    throw new PropertyNotFoundException(xpath);
                }
                List<State> list = state == null ? List.of() : getChildAsList(state, name);
                if (index >= list.size()) {
                    throw new PropertyNotFoundException(xpath);
                }
                // find complex list state
                state = list.get(index);
                parentType = (ComplexType) ((ListType) type).getFieldType();
                if (i == segments.length - 1) {
                    // last segment
                    return getValueComplex(state, parentType, xpath);
                } else {
                    // not last segment
                    continue;
                }
            }

            if (i == segments.length - 1) {
                // last segment
                return state == null ? null : getValueField(state, field, xpath);
            } else {
                // not last segment
                if (type.isSimpleType()) {
                    // scalar
                    throw new PropertyNotFoundException(xpath);
                } else if (type.isComplexType()) {
                    // complex property
                    state = state == null ? null : (State) state.get(name);
                    // here state can be null (DBS), continue loop with it, meaning uninitialized for read
                    parentType = (ComplexType) type;
                } else {
                    // list
                    // array of scalars, or complex list but next segment was not numeric
                    throw new PropertyNotFoundException(xpath);
                }
            }
        }
        throw new AssertionError("not reached");
    }

    protected Object getValueField(State state, Field field, String xpath) throws PropertyException {
        Type type = field.getType();
        String name = field.getName();
        String xp = xpath == null ? name : xpath + '/' + name;
        if (type.isSimpleType()) {
            // scalar
            if (state == null) {
                throw new NullPointerException();
            }
            Object value = state.get(name);
            // cater for type mismatches
            if (value instanceof List list) {
                if (list.isEmpty()) {
                    value = null;
                } else if (list.size() == 1) {
                    // data migration not done in database, return a simple value anyway
                    value = list.get(0);
                } else {
                    log.warn("Property {}: expected a simple value but read a list: {} ", name, list);
                    value = list.get(0);
                }
            }
            return value;
        } else if (type.isComplexType()) {
            // complex property
            State childState = (State) state.get(name);
            if (childState == null) {
                return null;
            }
            return getValueComplex(childState, (ComplexType) type, xp);
        } else if (type.isListType()) {
            // list
            Type fieldType = ((ListType) type).getFieldType();
            if (fieldType.isSimpleType()) {
                // simple list
                Object value = state.get(name);
                if (value != null && !(value instanceof List l)) {
                    // cater for type mismatches
                    // data migration not done in database, return an array anyway
                    value = List.of(value);
                }
                return value;
            } else {
                // complex list
                List<State> childStates = getChildAsList(state, name);
                List<Object> list = new ArrayList<>(childStates.size());
                int i = 0;
                for (State childState : childStates) {
                    String xpi = xp + '/' + i++;
                    Object value = getValueComplex(childState, (ComplexType) fieldType, xpi);
                    list.add(value);
                }
                return list;
            }
        } else {
            // any
            return StateHelper.toMap(state.get(name));
        }
    }

    protected Object getValueComplex(State state, ComplexType complexType, String xpath) throws PropertyException {
        if (state == null) {
            // corrupted or invalid list element in storage
            state = new State();
        }
        if (TypeConstants.isBlobType(complexType)) {
            return getValueBlob(state, xpath);
        }
        Map<String, Object> map = new HashMap<>();
        for (Field field : complexType.getFields()) {
            String name = field.getName();
            String xp = xpath + '/' + name;
            Object value = getValueField(state, field, xp);
            map.put(name, value);
        }
        return map;
    }

    protected Blob getValueBlob(State state, String xpath) throws PropertyException {
        BlobInfo blobInfo = getBlobInfo(state);
        DocumentBlobService blobService = Framework.getService(DocumentBlobService.class);
        try {
            return blobService.readBlob(blobInfo, this, xpath);
        } catch (IOException e) {
            throw new BlobNotFoundException("Unable to find blob with key: " + blobInfo.getKey(), e);
        }
    }

    /**
     * Sets a value (may be complex/list) into the document at the given xpath.
     */
    protected void setValueObject(State state, String xpath, Object value) throws PropertyException {
        if (value instanceof List list && list.isEmpty()) {
            // normalize empty lists to null
            value = null;
        }
        String[] segments = xpath.split("/");

        ComplexType parentType = getType();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            Field field = getField(parentType, segment);
            if (field == null) {
                throw new PropertyNotFoundException(xpath);
            }
            String name = field.getName(); // normalize from segment
            Type type = field.getType();

            // check if we have a complex list index in the next position
            if (i < segments.length - 1 && StringUtils.isNumeric(segments[i + 1])) {
                int index = Integer.parseInt(segments[i + 1]);
                i++;
                if (!type.isListType() || ((ListType) type).getFieldType().isSimpleType()) {
                    throw new PropertyNotFoundException(xpath);
                }
                List<State> list = getChildAsList(state, name);
                if (index >= list.size()) {
                    throw new PropertyNotFoundException(xpath);
                }
                // find complex list state
                state = list.get(index);
                field = ((ListType) type).getField();
                if (i == segments.length - 1) {
                    // last segment
                    setValueComplex(state, field, xpath, value);
                } else {
                    // not last segment
                    parentType = (ComplexType) field.getType();
                }
                continue;
            }

            if (i == segments.length - 1) {
                // last segment
                setValueField(state, field, xpath, value);
            } else {
                // not last segment
                if (type.isSimpleType()) {
                    // scalar
                    throw new PropertyNotFoundException(xpath);
                } else if (type.isComplexType()) {
                    // complex property
                    state = getChildForWrite(state, name);
                    parentType = (ComplexType) type;
                } else {
                    // list but next segment was not numeric
                    throw new PropertyNotFoundException(xpath);
                }
            }
        }
    }

    protected void setValueField(State state, Field field, String xpath, Object value) throws PropertyException {
        Type type = field.getType();
        String name = field.getName(); // normalize from map key
        // TODO we could check for read-only here
        if (type.isSimpleType()) {
            // scalar
            value = convert(value, type, xpath);
            state.put(name, value);
        } else if (type.isComplexType()) {
            // complex property
            State childState = getChildForWrite(state, name);
            setValueComplex(childState, field, xpath, value);
        } else if (type.isListType()) {
            // list
            ListType listType = (ListType) type;
            Type fieldType = listType.getFieldType();
            if (fieldType.isSimpleType()) {
                // simple list
                List<Object> list;
                if (value == null) {
                    list = null;
                } else {
                    if (value instanceof Object[] array) {
                        list = Arrays.asList(array);
                    } else if (value instanceof List l) {
                        list = l;
                    } else {
                        throw new PropertyConversionException(value.getClass(), List.class, xpath);
                    }
                    if (list.isEmpty()) {
                        // normalize empty lists to null
                        list = null;
                    } else {
                        // convert list elements
                        list = list.stream().map(v -> convert(v, fieldType, xpath)).toList();
                    }
                }
                state.put(name, list);
            } else {
                // complex list
                if (value != null && !(value instanceof List)) {
                    throw new PropertyException("Expected List value for: " + name + ", got "
                            + value.getClass().getName() + " instead");
                }
                List<?> values = (List<?>) value;
                List<State> childStates;
                if (value == null || values.isEmpty()) {
                    childStates = null;
                } else {
                    Field listField = listType.getField();
                    childStates = new ArrayList<>(values.size());
                    int i = 0;
                    for (Object v : values) {
                        State childState = new State();
                        setValueComplex(childState, listField, xpath + '/' + i, v);
                        childStates.add(childState);
                        i++;
                    }
                }
                state.put(name, childStates);
            }
        } else {
            // any
            state.put(name, StateHelper.toState(value));
        }
    }

    private Object convert(Object value, Type type, String xpath) {
        if (value == null) {
            return null;
        }
        try {
            return type.convert(value);
        } catch (TypeException e) {
            throw new PropertyConversionException(value.getClass(), e.getExpectedType(), xpath);
        }
    }

    protected void setValueComplex(State state, Field field, String xpath, Object value) throws PropertyException {
        ComplexType complexType = (ComplexType) field.getType();
        if (TypeConstants.isBlobType(complexType)) {
            if (value == null || value instanceof Blob) {
                setValueBlob(state, (Blob) value, xpath);
                return;
            }
            if (!(value instanceof Map)) {
                throw new PropertyException(
                        "Expected Blob value for: " + xpath + ", got " + value.getClass().getName() + " instead");
            }
            // fall through with Map
        }
        if (value != null && !(value instanceof Map)) {
            throw new PropertyException(
                    "Expected Map value for: " + xpath + ", got " + value.getClass().getName() + " instead");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = value == null ? Collections.emptyMap() : (Map<String, Object>) value;
        Set<String> keys = new HashSet<>(map.keySet());
        for (Field f : complexType.getFields()) {
            String name = f.getName();
            keys.remove(name);
            value = map.get(name);
            setValueField(state, f, xpath + '/' + name, value);
        }
        if (!keys.isEmpty()) {
            throw new PropertyException("Unknown key: " + keys.iterator().next() + " for " + xpath);
        }
    }

    protected void setValueBlob(State state, Blob blob, String xpath) throws PropertyException {
        BlobInfo blobInfo = new BlobInfo();
        DocumentBlobService blobService = Framework.getService(DocumentBlobService.class);
        try {
            blobInfo.setKey(blobService.writeBlob(blob, this, xpath));
        } catch (IOException e) {
            String blobDebug;
            if (blob instanceof ManagedBlob managedBlob) {
                blobDebug = managedBlob.getKey() + " of provider: " + managedBlob.getProviderId();
            } else {
                blobDebug = String.valueOf(blob);
            }
            throw new PropertyException(
                    "Cannot get blob info for blob: %s while setting property: %s of document: %s at path: %s".formatted(
                            blobDebug, xpath, getId(), getPath()),
                    e);
        }
        if (blob != null) {
            blobInfo.setFilename(blob.getFilename());
            blobInfo.setMimeType(blob.getMimeType());
            blobInfo.setEncoding(blob.getEncoding());
            blobInfo.setDigest(blob.getDigest());
            blobInfo.setLength(blob.getLength() == -1 ? null : Long.valueOf(blob.getLength()));
        }
        setBlobInfo(state, blobInfo);
    }

    protected void setPropertyBlobData(String xpath, String string) {
        Blob blob = string == null ? null : Blobs.createBlob(string);
        DocumentBlobService blobService = Framework.getService(DocumentBlobService.class);
        String key;
        try {
            key = blobService.writeBlob(blob, this, xpath);
        } catch (IOException e) {
            throw new PropertyException("Cannot write binary for doc: " + getId(), e);
        }
        setPropertyValue(xpath, key);
    }

    private record ValueAndField(Object value, Field field) {
    }

    private interface IterationProvider {
        List<ValueAndField> get(State state, ComplexType type);
    }

    /** How to iterate for state-based recursion. */
    public IterationProvider iterationProviderForState(Comparator<String> keySorter) {
        return (state, type) -> {
            Collection<Entry<String, Object>> entries = state.entrySet();
            if (keySorter != null) {
                entries = new ArrayList<>(entries);
                ((List<Entry<String, Object>>) entries).sort(Comparator.comparing(e -> e.getKey(), keySorter));
            }
            List<ValueAndField> list = new ArrayList<>();
            for (Entry<String, Object> e : entries) {
                String name = e.getKey();
                Field field = getField(type, name);
                if (field == null) {
                    // TODO XXX system property
                    continue;
                }
                Object value = e.getValue();
                list.add(new ValueAndField(value, field));
            }
            return list;
        };
    }

    /** How to iterate for field-based recursion, which can include null values. */
    public IterationProvider iterationProviderForFields() {
        return (state, type) -> {
            List<ValueAndField> list = new ArrayList<>();
            for (Field field : type.getFields()) {
                Object value = state.get(field.getName());
                list.add(new ValueAndField(value, field));
            }
            return list;
        };
    }

    private class DocumentVisitor {

        private final ValuesVisitor visitor;

        private final IterationProvider iterationProvider;

        public DocumentVisitor(ValuesVisitor visitor, IterationProvider iterationProvider) {
            this.visitor = visitor;
            this.iterationProvider = iterationProvider;
        }

        public void visitState(State state, ComplexType type) {
            // complex property or blob
            if (visitor.visitActualBlobs() && TypeConstants.isBlobType(type)) {
                Blob blob = getValueBlob(state, visitor.getXPath());
                visitor.blob(blob, state);
            } else {
                visitor.startObject();
                for (ValueAndField vf : iterationProvider.get(state, type)) {
                    visitor.startObjectElement(vf.field);
                    visitValue(vf.value, vf.field);
                    visitor.endObjectElement();
                }
                visitor.endObject();
            }
        }

        protected void visitValue(Object value, Field field) {
            Type type = field.getType();
            if (value == null) {
                // only happens for field-based iteration when there is no value for a field
                visitor.value(null, field);
            } else if (type.isSimpleType()) {
                // scalar
                visitor.value(value, field);
            } else if (type.isComplexType()) {
                // complex property
                if (!(value instanceof State state)) {
                    log.error("Invalid state {} for type {}", value, type);
                    return;
                }
                visitState(state, (ComplexType) type);
            } else if (type.isListType()) {
                // list
                Field elemField = ((ListType) type).getField();
                Type fieldType = elemField.getType();
                if (fieldType.isSimpleType()) {
                    // simple list
                    if (!(value instanceof List list)) {
                        log.error("Invalid list {} for type {}", value, type);
                        return;
                    }
                    visitor.list(list, field, elemField);
                } else {
                    // complex list
                    if (!(value instanceof List<?> list)) {
                        log.error("Invalid list {} for type {}", value, type);
                        return;
                    }
                    if (list.isEmpty()) {
                        throw new ArrayIndexOutOfBoundsException(0);
                    }
                    if (!(list.get(0) instanceof State)) {
                        log.error("Invalid list {} for type {}", value, type);
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<State> states = (List<State>) list;
                    visitor.startList();
                    int i = 0;
                    for (State state : states) {
                        visitor.startListElement(i++);
                        visitState(state, (ComplexType) fieldType);
                        visitor.endListElement();
                    }
                    visitor.endList();
                }
            } else {
                // any
                visitAnyValue(field.getName(), value);
            }
        }

        protected void visitAnyValue(String key, Object value) {
            if (value instanceof State state) {
                visitAnyState(state);
            } else if (value instanceof List list) {
                visitAnyList(list);
            } else {
                Type type = scalarType(value);
                if (type == null) {
                    log.error("Invalid scalar {}", value);
                    type = StringType.INSTANCE;
                }
                visitor.value(value, new FieldImpl(key, null, type, null, null));
            }
        }

        protected void visitAnyState(State state) {
            visitor.startObject();
            for (Entry<String, Object> en : state.entrySet()) {
                String key = en.getKey();
                Object v = en.getValue();
                if (v == null) {
                    continue; // ignore null values
                }
                visitor.startObjectElement(new FieldImpl(key, null, null, null, null));
                visitAnyValue(key, v);
                visitor.endObjectElement();
            }
            visitor.endObject();
        }

        protected void visitAnyList(List list) {
            visitor.startList();
            int i = 0;
            for (Object v : list) {
                visitor.startListElement(i++);
                visitAnyValue("", v);
                visitor.endListElement();
            }
            visitor.endList();
        }

        protected Type scalarType(Object value) {
            if (value instanceof String) {
                return StringType.INSTANCE;
            } else if (value instanceof Boolean) {
                return BooleanType.INSTANCE;
            } else if (value instanceof Long) {
                return LongType.INSTANCE;
            } else if (value instanceof Double) {
                return DoubleType.INSTANCE;
            } else if (value instanceof BigDecimal) {
                return DecimalType.INSTANCE;
            } else if (value instanceof Instant) {
                return DateType.INSTANCE;
            } else {
                return null;
            }
        }
    }

    public class BlobsVisitor extends BaseValuesVisitor implements BlobAccessor {

        private final Consumer<BlobAccessor> blobVisitor;

        private final Runnable markDirty;

        private Blob blob;

        private State state;

        public BlobsVisitor(Consumer<BlobAccessor> blobVisitor, Runnable markDirty) {
            this.blobVisitor = blobVisitor;
            this.markDirty = markDirty;
        }

        @Override
        public boolean visitActualBlobs() {
            return true;
        }

        @Override
        public void blob(Blob blob, Object state) {
            this.blob = blob;
            this.state = (State) state;
            blobVisitor.accept(this);
        }

        // BlobAccessor

        // getXPath is from superclass

        @Override
        public Blob getBlob() {
            return blob;
        }

        @Override
        public void setBlob(Blob blob) {
            markDirty.run();
            this.blob = blob;
            setValueBlob(state, blob, getXPath());
        }
    }

    // also used as a regexp for split
    public static final String TOKEN_SEP = "-";

    /**
     * Builds the user-visible change token from low-level change token and system change token information.
     *
     * @param sysChangeToken the system change token
     * @param changeToken the change token
     * @return the user-visible change token
     * @since 9.2
     */
    public static String buildUserVisibleChangeToken(Long sysChangeToken, Long changeToken) {
        if (sysChangeToken == null || changeToken == null) {
            return null;
        }
        return sysChangeToken.toString() + TOKEN_SEP + changeToken.toString();
    }

    /**
     * Validates that the passed user-visible change token is compatible with the current change token.
     *
     * @param sysChangeToken the system change token
     * @param changeToken the change token
     * @param userVisibleChangeToken the user-visible change token
     * @return {@code false} if the change token is not valid
     * @since 9.2
     */
    public static boolean validateUserVisibleChangeToken(Long sysChangeToken, Long changeToken,
            String userVisibleChangeToken) {
        if (sysChangeToken == null || changeToken == null) {
            return true;
        }
        // we only compare the change token, not the system change token, to allow background system updates
        String[] parts = userVisibleChangeToken.split(TOKEN_SEP);
        if (parts.length != 2) {
            return false; // invalid format
        }
        return parts[1].equals(changeToken.toString());
    }

    /**
     * Updates a change token to its new value.
     *
     * @param changeToken the change token (not {@code null})
     * @return the updated change token
     * @since 9.2
     */
    public static Long updateChangeToken(Long changeToken) {
        return Long.valueOf(changeToken.longValue() + 1);
    }

    @Override
    public boolean isUnderRetentionOrLegalHold() {
        Instant retainUntil;
        return hasLegalHold()
                || (((retainUntil = getRetainUntil()) != null) && Instant.now().isBefore(retainUntil));
    }

    protected boolean allowNewRetention(Instant current, Instant retainUntil) {
        if (current == null) {
            return true;
        }
        if (current.compareTo(CoreSession.RETAIN_UNTIL_INDETERMINATE) == 0) {
            return true;
        }
        if (retainUntil == null) {
            // setting back to null is allowed if retention has already expired
            return Instant.now().isAfter(current);
        }
        // can only extend retention
        return retainUntil.isAfter(current);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + getName() + ',' + getId() + ')';
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DBSDocument other && docState == other.docState;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(docState);
    }

}
