/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document;

import static java.lang.Boolean.FALSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSTEM;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_GOVERNABLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.api.model.PropertyConversionException;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationReport;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.impl.document.UploadEvent;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.rest.acl.ACEDTO;
import org.nuxeo.rest.acl.ACLMapper;
import org.nuxeo.rest.upload.UploadAttachStatus;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.UploadHandlerCleaner;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.rest.user.UserDTO;
import org.nuxeo.rest.validation.io.ValidationErrorDTO;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Helper methods to translate {@link DocumentModel} from/to {@link DocumentDTO}.
 */
@Component
public class DocumentMapper implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(DocumentMapper.class);

    public static final String ID = "id";

    public static final String UPLOAD_ID_MARKER = "uploadId";

    public static final String ALLOW_SET_ID_ON_CREATION = "hxpr.core.allowSetIdOnCreation";

    private final UploadHandler handler;

    private final ACLMapper aclMapper;

    @Value("${" + ALLOW_SET_ID_ON_CREATION + ":false}")
    boolean allowSetIdOnCreation;

    private ApplicationContext applicationContext;

    // only used for testing
    public static volatile CyclicBarrier _testAttachStatusBarrier;

    public DocumentMapper(UploadHandler handler, ACLMapper aclMapper) {
        this.handler = handler;
        this.aclMapper = aclMapper;
    }

    UploadHandlerCleaner getCleaner() {
        // As cleaner is Request scoped we cannot inject into a singleton scoped bean directly
        return applicationContext.getBean(UploadHandlerCleaner.class);
    }

    public DocumentDTO documentModelToDocumentDTO(DocumentModel doc) {
        return documentModelToDocumentDTO(doc, false);
    }

    public DocumentDTO documentModelToDocumentDTO(DocumentModel doc, boolean withEmptyLists) {
        DocumentDTO res = new DocumentDTO();
        String docId = doc.getId();
        res.setId(docId);
        res.setRepository(doc.getRepositoryName());
        res.setName(doc.getName());
        String pathAsString = doc.getPathAsString();
        res.setPath(pathAsString);
        if (StringUtils.isNotBlank(pathAsString)) {
            String[] split = pathAsString.split("/");
            res.setPathDepth(split.length);
        }
        res.setPrimaryType(doc.getType());
        DocumentRef parentRef = doc.getParentRef();
        if (parentRef != null) {
            if (parentRef instanceof IdRef parentIdRef) {
                res.setParentId(parentIdRef.toString());
                res.setParentPath(doc.getPath().removeLastSegments(1).toString());
            } else {
                res.setParentPath(parentRef.toString());
            }
            res.setAncestorIds(checkEmptyList(doc.getAncestorIds(), withEmptyLists));
        }
        res.setIsFolderish(doc.isFolder());
        res.setMixinTypes(checkEmptyList(new ArrayList<>(doc.getEffectiveMixins()), withEmptyLists));
        res.setChangeToken(doc.getChangeToken());
        res.setLifecycleState(getLifecycleState(doc));
        res.setPos(doc.getPos());

        res.setSecurityPolicies(checkEmptyList(doc.getSecurityPolicies(), withEmptyLists));
        res.setAcl(checkEmptyList(localACLsToAceDTOs(doc), withEmptyLists));
        res.setEffectiveAcl(checkEmptyList(effectiveACLsToAceDTOs(doc), withEmptyLists));
        res.setEffectivePermissions(checkEmptyList(getEffectivePermissions(doc), withEmptyLists));
        res.setFulltextBinary(doc.getBinaryFulltext());
        Lock lock = doc.getLockInfo();
        if (lock != null) {
            res.setLockCreated(lock.getCreated());
            res.setLockOwner(principalIdToUserDTO(lock.getOwner()));
        }

        if (doc.hasSchema(SYSTEM)) {
            @SuppressWarnings("unchecked")
            List<Object> contributors = (List<Object>) doc.getPropertyValue(SYS_CONTRIBUTORS);
            res.setContributors(checkEmptyList(principalIdsToUserDTO(contributors), withEmptyLists));
            Instant created = (Instant) doc.getPropertyValue(SYS_CREATED);
            res.setCreated(created);
            Object creator = doc.getPropertyValue(SYS_CREATOR);
            res.setCreator(principalIdToUserDTO(creator));
            String description = (String) doc.getPropertyValue(SYS_DESCRIPTION);
            res.setDescription(description);
            Object lastContributor = doc.getPropertyValue(SYS_LAST_CONTRIBUTOR);
            res.setLastContributor(principalIdToUserDTO(lastContributor));
            Instant modified = (Instant) doc.getPropertyValue(SYS_MODIFIED);
            res.setModified(modified);
            String title = (String) doc.getPropertyValue(SYS_TITLE);
            res.setTitle(title);
        }

        Map<String, Object> props = new HashMap<>();
        doc.visitFields(new MapBuilderValuesVisitor(props, withEmptyLists));
        res.setProperties(props);

        return res;
    }

    private <T> List<T> checkEmptyList(List<T> list, boolean withEmptyLists) {
        if (list == null || list.isEmpty()) {
            return withEmptyLists ? List.of() : null;
        } else {
            return list;
        }
    }

    private UserDTO principalIdToUserDTO(Object principalIdObject) {
        if (principalIdObject == null) {
            return null;
        }
        String principalId = (String) principalIdObject;
        NuxeoPrincipal principal;
        if (SYSTEM_USERNAME.equals(principalId)) {
            principal = new NuxeoPrincipalImpl(SYSTEM_USERNAME);
        } else {
            principal = Framework.getService(UserManager.class).getPrincipal(principalId);
            if (principal == null) {
                log.debug("No such principal: {}", principalId);
                // create a dummy principal for deleted users
                principal = new NuxeoPrincipalImpl(principalId);
            }
        }
        if (principal.getId() == null) {
            return null;
        }
        return new UserDTO(principal);
    }

    private List<UserDTO> principalIdsToUserDTO(List<Object> principalIds) {
        if (principalIds == null) {
            return null;
        }
        return principalIds.stream() //
                           .map(this::principalIdToUserDTO)
                           .filter(Objects::nonNull)
                           .toList();
    }

    private NuxeoPrincipal getPrincipal(String principalId) {
        UserManager userManager = Framework.getService(UserManager.class);
        NuxeoPrincipal principal = userManager.getPrincipal(principalId);
        if (principal == null) {
            if (Framework.isTestModeSet()) {
                // TODO do proper tests setup with pre-defined users instead
                return new NuxeoPrincipalImpl(principalId);
            }
            throw new NuxeoException("No such principal: " + principalId);
        }
        return principal;
    }

    /**
     * Returns local ACL only.
     */
    protected List<ACEDTO> localACLsToAceDTOs(DocumentModel doc) {
        ACL localACL = doc.getLocalACL();
        return aclMapper.acesToAceDTOs(localACL);
    }

    /**
     * Returns positive ACL only.
     */
    protected List<ACEDTO> effectiveACLsToAceDTOs(DocumentModel doc) {
        ACL effectiveACL = doc.getEffectiveACL();
        return aclMapper.acesToAceDTOs(effectiveACL);
    }

    protected List<String> getEffectivePermissions(DocumentModel doc) {
        Set<String> set = doc.getEffectivePermissions();
        List<String> permissions = new ArrayList<>(set);
        permissions.sort(null); // provide stable JSON representation
        return permissions;
    }

    public DocumentModel createDocumentModel(CoreSession session, DocumentRef parentRef, DocumentDTO doc,
            boolean enforceSysName) {
        DocumentModel docModel = session.newDocumentModel(parentRef, doc.getName(), doc.getPrimaryType());
        // set sys_id on creation
        if (allowSetIdOnCreation && StringUtils.isNotBlank(doc.getId())) {
            docModel.setId(doc.getId());
        }
        if (enforceSysName) {
            docModel.putContextData(CoreSession.ENFORCE_SYS_NAME, true);
        }
        createOrUpdateDocumentModel(docModel, doc, true); // creation
        return docModel;
    }

    public void updateDocumentModel(DocumentModel docModel, DocumentDTO doc) {
        createOrUpdateDocumentModel(docModel, doc, false);
    }

    /** Creates/updates the {@link DocumentModel} and deals with the attach status (including error cases). */
    protected void createOrUpdateDocumentModel(DocumentModel docModel, DocumentDTO doc, boolean create) {
        BlobsContext blobsContext = new BlobsContext();
        boolean completedAbruptly = true;
        try {
            updateDocumentModel(docModel, doc, blobsContext);
            if (create) {
                docModel.create();
                // for the creation case we know the doc id only now
            }
            blobsContext.uploads().forEach(upload -> {
                upload.setAttachDocId(docModel.getId());
                upload.setAttachStatus(UploadAttachStatus.READY);
            });
            completedAbruptly = false;
        } finally {
            if (completedAbruptly) {
                blobsContext.uploads().forEach(upload -> upload.setAttachStatus(UploadAttachStatus.ERROR));
            }
            // then let exception bubble up
        }
    }

    /** Updates the {@link DocumentModel} from the {@link DocumentDTO}. */
    protected void updateDocumentModel(DocumentModel docModel, DocumentDTO doc, BlobsContext blobsContext) {
        List<ValidationViolation> errors = new ArrayList<>();
        updatePrimaryTypeAndMixinTypes(doc, docModel);

        String lifecycleState = doc.getLifecycleState();
        if (lifecycleState != null) {
            setLifecycleState(docModel, lifecycleState);
        }

        // system
        List<String> securityPolicies = doc.getSecurityPolicies();
        if (securityPolicies != null) {
            docModel.setSecurityPolicies(securityPolicies);
        }
        String description = doc.getDescription();
        if (description != null) {
            docModel.setPropertyValue(SYS_DESCRIPTION, description);
        }
        String title = doc.getTitle();
        if (title != null) {
            docModel.setPropertyValue(SYS_TITLE, title);
        }
        // the other properties are read-only

        // Build the list of blobs as context info for updating the document from the DTO
        // The goal is to prevent the creation of new empty blobs or setting up blobs with a different key
        docModel.visitBlobs(blobAccessor -> {
            Blob blob = blobAccessor.getBlob();
            if (blob instanceof ManagedBlob managedBlob) {
                blobsContext.blobs().put(managedBlob.getKey(), managedBlob);
            }
        });

        for (Map.Entry<String, Object> prop : doc.getProperties().entrySet()) {
            String name = prop.getKey();
            if (name.startsWith(SYSGOV_PREFIX)) {
                // setting this property here will make the document under retention and no updates will be allowed
                // governance is handled separately
                continue;
            }
            Object value = prop.getValue();
            try {
                Object propertyValue = translatePropertyFromDocumentDTO(name, value, docModel, blobsContext);
                docModel.setPropertyValue(name, propertyValue);
            } catch (PropertyConversionException e) {
                errors.add(new ValidationErrorDTO(e.getMessage(), null, null, value, name));
            }
        }
        if (!errors.isEmpty()) {
            throw new DocumentValidationException(new DocumentValidationReport(errors));
        }
    }

    private void updatePrimaryTypeAndMixinTypes(DocumentDTO doc, DocumentModel docModel) {
        String type = doc.getPrimaryType();
        List<String> mixins = doc.getMixinTypes();
        if (type == null && mixins == null) {
            return;
        }
        if (mixins != null) {
            // SysGovernable is managed separately like ACL
            mixins = mixins.stream().filter(x -> !x.equals(SYS_GOVERNABLE)).toList();
        }
        docModel.setPrimaryTypeAndMixinTypes(type, mixins);
    }

    private String getLifecycleState(DocumentModel doc) {
        return (String) doc.getCoreSession().getDocumentSystemProp(doc.getRef(), SYS_LIFECYCLE_STATE, String.class);
    }

    private void setLifecycleState(DocumentModel doc, String lifecycleState) {
        doc.getCoreSession().setDocumentSystemProp(doc.getRef(), SYS_LIFECYCLE_STATE, lifecycleState);
    }

    private Blob getBlobFromPropertyOrUpload(Map<String, Object> value, String xpath, DocumentModel docModel,
            BlobsContext blobsContext) {
        Blob blob;
        String uploadId = (String) value.get(UPLOAD_ID_MARKER);
        if (uploadId != null) {
            Upload upload = handler.getUpload(uploadId);
            if (upload == null) {
                throw new DocumentValidationException("No blob found for upload id '%s'".formatted(uploadId));
            }
            getCleaner().add(uploadId);
            blobsContext.uploads().add(upload);
            blob = upload.getBlob();
            if (blob == null) {
                throw new DocumentValidationException(
                        "No blob available for upload id '%s' (already attached or incomplete)".formatted(uploadId));
            }
            fireEventforUploadUsage(blob);
            upload.setAttachStatus(UploadAttachStatus.PROCESSING);
            if (_testAttachStatusBarrier != null) {
                try {
                    // inform test thread that attach status is set
                    _testAttachStatusBarrier.await(10, SECONDS);
                    // wait for test thread to have observed status
                    _testAttachStatusBarrier.await(10, SECONDS);
                } catch (Exception e) {
                    throw new NuxeoException(e);
                }
            }
        } else if (value.containsKey(KEY_BLOB_KEY)) {
            var blobKey = (String) value.get(KEY_BLOB_KEY);
            blob = blobsContext.blobs().get(blobKey);
            if (blob == null) {
                throw new DocumentValidationException(
                        "The blob key %s does not exist in the original document".formatted(blobKey));
            }
            // Only the filename can be edited
            updateBlobFileName(blob, value);
        } else {
            blob = (Blob) docModel.getPropertyValue(xpath);
            updateBlobFileName(blob, value);
        }
        return blob;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * This visitor is called using doc.visitFields because we want to visit null values. These null values are usually
     * not output, except that we have a few "true or null" booleans for which we want null to be sent as false.
     * Also when withEmptyLists is requested, we want to express null values as empty lists.
     */
    public static class MapBuilderValuesVisitor implements ValuesVisitor {

        /**
         * These system properties are (for migration reasons) stored in db as either true or null, but never false.
         * Null has the semantics of false, and that's what we want to send to REST clients.
         */
        // TODO this characteristic really needs to be part of the field / content model
        private static final Set<String> TRUE_OR_NULL_BOOLEAN_PROPS = Set.of( //
                SYSVER_IS_CHECKED_IN, //
                SYSVER_IS_VERSION,
                SYSGOV_HAS_LEGAL_HOLD,
                SYSGOV_IS_RECORD);

        private final Deque<Map<String, Object>> mapStack = new ArrayDeque<>();

        private final Deque<List<Object>> listStack = new ArrayDeque<>();

        private final Deque<String> keyStack = new ArrayDeque<>();

        private final boolean withEmptyLists;

        private boolean toplevel;

        private Map<String, Object> map;

        private String key;

        private List<Object> list;

        private Object value;

        public MapBuilderValuesVisitor(Map<String, Object> map, boolean withEmptyLists) {
            toplevel = true;
            this.map = map;
            this.withEmptyLists = withEmptyLists;
        }

        @Override
        public boolean visitActualBlobs() {
            return false;
        }

        @Override
        public String getXPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startObject() {
            if (toplevel) {
                toplevel = false;
            } else {
                mapStack.addLast(map);
                map = new HashMap<>();
            }
        }

        @Override
        public void endObject() {
            if (mapStack.isEmpty()) {
                toplevel = true;
            } else {
                value = map;
                map = mapStack.removeLast();
            }
        }

        @Override
        public void startObjectElement(Field field) {
            if (key != null) {
                keyStack.addLast(key);
            }
            key = field.getName();
        }

        @Override
        public void endObjectElement() {
            // system schemas managed separately (for now)
            if (!key.startsWith(SYS_PREFIX)) {
                // we don't write nulls except for those that really mean false
                if (value == null && TRUE_OR_NULL_BOOLEAN_PROPS.contains(key)) {
                    value = FALSE;
                }
                if (value != null) {
                    map.put(key, value);
                }
            }
            key = keyStack.pollLast();
        }

        @Override
        public void startList() {
            if (list != null) {
                listStack.addLast(list);
            }
            list = new ArrayList<>();
        }

        @Override
        public void endList() {
            value = list;
            list = listStack.pollLast();
        }

        @Override
        public void startListElement(int i) {
            // nothing
        }

        @Override
        public void endListElement() {
            list.add(value);
        }

        @Override
        public void value(Object v, Field field) {
            if (withEmptyLists && v == null && field.getType().isListType()) {
                v = List.of();
            }
            value = v;
        }

        @Override
        public void list(List<Object> list, Field listField, Field elemField) {
            value = list;
        }

        @Override
        public void blob(Blob blob, Object state) {
            throw new UnsupportedOperationException();
        }
    }

    private Object translatePropertyToDocumentDTO(Object property) {
        if (property instanceof Object[] array) {
            return translateListToDocumentDTO(Arrays.stream(array));
        } else if (property instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) property;
            return translateListToDocumentDTO(list.stream());
        } else if (property instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) property;
            return translateMapToDocumentDTO(map);
        } else if (property instanceof Blob blob) {
            Map<String, Object> props = new HashMap<>();
            props.put(KEY_BLOB_DIGEST, blob.getDigest());
            props.put(KEY_BLOB_ENCODING, blob.getEncoding());
            props.put(KEY_BLOB_FILENAME, blob.getFilename());
            props.put(KEY_BLOB_LENGTH, blob.getLength());
            props.put(KEY_BLOB_MIME_TYPE, blob.getMimeType());
            return translateMapToDocumentDTO(props);
        } else {
            return property;
        }
    }

    /** Translates a list. Empty lists are returned as null. */
    private List<Object> translateListToDocumentDTO(Stream<Object> stream) {
        List<Object> list = stream.map(this::translatePropertyToDocumentDTO).toList();
        if (list.isEmpty()) {
            // an empty list is returned as null
            return null;
        }
        return list;
    }

    /** Translates a map, skipping null values. Empty maps are returned as null. */
    private Object translateMapToDocumentDTO(Map<String, Object> map) {
        Map<String, Object> props = new HashMap<>();
        map.forEach((k, v) -> {
            Object value = translatePropertyToDocumentDTO(v);
            if (value != null) {
                // null values are skipped
                props.put(k, value);
            }
        });
        if (props.isEmpty()) {
            // an empty map is returned as null
            return null;
        }
        return props;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object translatePropertyFromDocumentDTO(String xpath, Object value, DocumentModel docModel,
            BlobsContext blobsContext) {
        if (value == null) {
            return null;
        }
        if (value instanceof Object[] array) {
            return IntStream.range(0, array.length)
                            .mapToObj((int index) -> translatePropertyFromDocumentDTO(
                                    concatPath(xpath, index), array[index], docModel, blobsContext))
                            .toList();
        } else if (value instanceof List list) {
            return IntStream.range(0, list.size())
                            .mapToObj((int index) -> translatePropertyFromDocumentDTO(
                                    concatPath(xpath, index), list.get(index), docModel, blobsContext))
                            .toList();
        } else if (value instanceof Map map) {
            if (isMapOfBlob(map, xpath, docModel)) {
                return getBlobFromPropertyOrUpload(map, xpath, docModel, blobsContext);
            }
            Map<String, Object> props = new HashMap<>();
            map.forEach((k, v) -> props.put((String) k,
                    translatePropertyFromDocumentDTO(concatPath(xpath, k), v, docModel, blobsContext)));
            return props;
        }
        return value;
    }

    private boolean isMapOfBlob(Object value, String xpath, DocumentModel docModel) {
        try {
            return value instanceof Map map && !map.isEmpty()
                    && (map.containsKey(UPLOAD_ID_MARKER) || map.containsKey(KEY_BLOB_KEY))
                    || (xpath != null && docModel.getPropertyValue(xpath) instanceof Blob);
        } catch (PropertyNotFoundException e) {
            return false;
        }
    }

    private String concatPath(String xpath, Object child) {
        return xpath + "/" + child;
    }

    private void fireEventforUploadUsage(Blob blob) {
        UploadEvent uploadEvent = new UploadEvent(blob.getLength(), blob.getMimeType());
        uploadEvent.fire();
    }

    private void updateBlobFileName(Blob blob, Map<String, Object> metadata) {
        if (metadata.containsKey(KEY_BLOB_FILENAME)) {
            blob.setFilename((String) metadata.get(KEY_BLOB_FILENAME));
        }
    }

    /**
     * Context for blob resolution during a document update. Tracks existing managed blobs (keyed by blob key) and the
     * {@link Upload}s associated with each newly-provided blob.
     */
    record BlobsContext(Map<String, ManagedBlob> blobs, List<Upload> uploads) {
        BlobsContext() {
            this(new HashMap<>(), new ArrayList<>());
        }
    }

}
