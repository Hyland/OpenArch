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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_EFFECTIVE_PERMISSIONS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH_DEPTH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_POS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_REPOSITORY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_GOVERNABLE;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.REST;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.api.model.PropertyConversionException;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationReport;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.SystemPropertyValues;
import org.nuxeo.ecm.core.event.impl.document.UploadEvent;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
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
import com.hyland.openarch.runtime.api.Framework;
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
        Map<String, Object> system = SystemPropertyValues.forOutput(doc, REST);
        DocumentDTO res = new DocumentDTO();
        res.setId((String) system.get(SYS_ID));
        res.setRepository((String) system.get(SYS_REPOSITORY));
        res.setName((String) system.get(SYS_NAME));
        res.setPath((String) system.get(SYS_PATH));
        res.setPathDepth((Integer) system.get(SYS_PATH_DEPTH));
        res.setPrimaryType((String) system.get(SYS_PRIMARY_TYPE));
        res.setParentId((String) system.get(SYS_PARENT_ID));
        res.setParentPath((String) system.get(SYS_PARENT_PATH));
        res.setAncestorIds(checkEmptyList(stringList(system.get(SYS_ANCESTOR_IDS)), withEmptyLists));
        res.setIsFolderish((Boolean) system.get(SYS_IS_FOLDERISH));
        res.setIsTrashed((Boolean) system.get(SYS_IS_TRASHED));
        res.setMixinTypes(sortedStringList(system.get(SYS_MIXIN_TYPES), withEmptyLists));
        res.setChangeToken((String) system.get(SYS_CHANGE_TOKEN));
        res.setLifecycleState((String) system.get(SYS_LIFECYCLE_STATE));
        res.setPos((Long) system.get(SYS_POS));
        res.setSecurityPolicies(sortedStringList(system.get(SYS_SECURITY_POLICIES), withEmptyLists));
        res.setAcl(checkEmptyList(aclToAceDTOs(system.get(SYS_ACL)), withEmptyLists));
        res.setEffectiveAcl(checkEmptyList(aclToAceDTOs(system.get(SYS_EFFECTIVE_ACL)), withEmptyLists));
        res.setEffectivePermissions(sortedStringList(system.get(SYS_EFFECTIVE_PERMISSIONS), withEmptyLists));
        res.setReadAcl(checkEmptyList(stringList(system.get(SYS_READ_ACL)), withEmptyLists));
        res.setFulltextBinary((String) system.get(SYS_FULLTEXT_BINARY));
        res.setLockCreated((Instant) system.get(SYS_LOCK_CREATED));
        res.setLockOwner(principalIdToUserDTO(system.get(SYS_LOCK_OWNER)));
        res.setContributors(
                checkEmptyList(principalIdsToUserDTO(objectList(system.get(SYS_CONTRIBUTORS))), withEmptyLists));
        res.setCreated((Instant) system.get(SYS_CREATED));
        res.setCreator(principalIdToUserDTO(system.get(SYS_CREATOR)));
        res.setDescription((String) system.get(SYS_DESCRIPTION));
        res.setLastContributor(principalIdToUserDTO(system.get(SYS_LAST_CONTRIBUTOR)));
        res.setModified((Instant) system.get(SYS_MODIFIED));
        res.setTitle((String) system.get(SYS_TITLE));

        Map<String, Object> props = new HashMap<>();
        doc.visitFields(new MapBuilderValuesVisitor(props, withEmptyLists));
        props.keySet().removeIf(name -> SystemPropertyValues.isRepositoryManagedField(doc, name));
        system.forEach((name, value) -> {
            if (value != null && (name.startsWith("sysgov_") || name.startsWith("sysver_"))) {
                props.put(name, value);
            }
        });
        res.setProperties(props);

        return res;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> objectList(Object value) {
        return (List<Object>) value;
    }

    private <T> List<T> checkEmptyList(List<T> list, boolean withEmptyLists) {
        if (list == null || list.isEmpty()) {
            return withEmptyLists ? List.of() : null;
        } else {
            return list;
        }
    }

    private List<String> sortedStringList(Object value, boolean withEmptyLists) {
        List<String> list = checkEmptyList(stringList(value), withEmptyLists);
        return list == null || list.isEmpty() ? list : list.stream().sorted().toList();
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

    /**
     * Returns local ACL only.
     */
    protected List<ACEDTO> aclToAceDTOs(Object value) {
        return value == null ? null : aclMapper.acesToAceDTOs((ACL) value);
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

        setSystemProperty(docModel, SYS_LIFECYCLE_STATE, doc.getLifecycleState());
        setSystemProperty(docModel, SYS_SECURITY_POLICIES, doc.getSecurityPolicies());
        setSystemProperty(docModel, SYS_DESCRIPTION, doc.getDescription());
        setSystemProperty(docModel, SYS_TITLE, doc.getTitle());

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

    protected void setSystemProperty(DocumentModel docModel, String name, Object value) {
        if (value != null) {
            docModel.setPropertyValue(name, value);
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
     * This visitor is called using doc.visitFields because we want to express null list values as empty lists when
     * requested. Repository-managed fields are projected separately.
     */
    public static class MapBuilderValuesVisitor implements ValuesVisitor {

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
            if (value != null) {
                map.put(key, value);
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

    @SuppressWarnings("unchecked")
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
