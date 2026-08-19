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
package org.nuxeo.rest.document;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.api.CoreSession.RETAIN_UNTIL_INDETERMINATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_VIEWED;
import static org.nuxeo.rest.document.DocumentMapper.UPLOAD_ID_MARKER;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UpdateCondition;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.utils.BlobUtils;
import org.nuxeo.rest.bulkimport.BulkImportUploadDTO;
import org.nuxeo.rest.document.utils.ACLChangesHandler;
import org.nuxeo.rest.upload.UploadDTO;
import org.nuxeo.rest.upload.UploadService;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;

import jakarta.servlet.http.Part;
import jakarta.ws.rs.core.Response;

/**
 * {@link DocumentDTO} manager.
 */
@Component
public class DocumentManager {

    public static final String DEFAULT_REPOSITORY_ID = "default";

    public static final String BASE_DOCUMENT_PATH = "/api/documents"; // NOSONAR

    private static final Logger log = LoggerFactory.getLogger(DocumentManager.class);

    DocumentMapper documentMapper;

    ACLChangesHandler aclChangesHandler;

    UploadHandler uploadHandler;

    ObjectMapper objectMapper;

    UploadService uploadService;

    public DocumentManager(DocumentMapper documentMapper, ACLChangesHandler aclChangesHandler,
            UploadHandler uploadHandler, ObjectMapper objectMapper, UploadService uploadService) {
        this.documentMapper = documentMapper;
        this.aclChangesHandler = aclChangesHandler;
        this.uploadHandler = uploadHandler;
        this.objectMapper = objectMapper;
        this.uploadService = uploadService;
    }

    protected CoreSession getSession(String repoId) {
        return CoreInstance.getCoreSession(repoId);
    }

    /**
     * @param repoId         repository name
     * @param docRef         document reference
     * @return the document
     */
    public DocumentDTO getDocument(String repoId, DocumentRef docRef) {
        return getDocument(repoId, docRef, false, false);
    }

    /**
     * @param repoId         repository name
     * @param docRef         document reference
     * @param withAuditEvent whether to fire a view audit event. You should set this to true when this method is not
     *                       called from another method that already fired an audit event.
     * @param withEmptyLists whether to keep empty lists instead of replacing them with null
     * @return the document
     */
    public DocumentDTO getDocument(String repoId, DocumentRef docRef, boolean withAuditEvent, boolean withEmptyLists) {
        CoreSession session = getSession(repoId);
        DocumentModel document = session.getDocument(docRef);
        if (withAuditEvent) {
            fireViewAuditEvent(session, document);
        }
        return documentMapper.documentModelToDocumentDTO(document, withEmptyLists);
    }

    public DocumentAncestorsDTO getDocumentAncestors(String repoId, DocumentRef docRef) {
        DocumentAncestorsDTO ancestorsDTO = new DocumentAncestorsDTO();
        CoreSession session = getSession(repoId);
        ancestorsDTO.setAncestors(
                session.getAncestorDocuments(docRef)
                       .stream()
                       .map(model -> model == null ? null : documentMapper.documentModelToDocumentDTO(model))
                       .toList());
        fireViewAuditEvent(session, session.getDocument(docRef));
        return ancestorsDTO;
    }

    public DocumentDTO createDocument(String repoId, DocumentRef parentRef, DocumentDTO doc, boolean enforceSysName) {
        if (StringUtils.isBlank(doc.getPrimaryType())) {
            throw new NuxeoException("Missing document type for document creation: primaryType",
                    Response.Status.BAD_REQUEST.getStatusCode());
        }
        CoreSession session = getSession(repoId);
        DocumentModel document = documentMapper.createDocumentModel(session, parentRef, doc, enforceSysName);
        if (doc.getAcl() != null && !doc.getAcl().isEmpty()) {
            aclChangesHandler.applyACLChange(doc, document.getRef(), session);
        }
        applyGovernance(session, document, doc);
        session.save();
        document.refresh();
        return documentMapper.documentModelToDocumentDTO(document);
    }

    public DocumentDTO updateDocument(String repoId, DocumentRef docRef, DocumentDTO doc, UpdateCondition condition) {
        CoreSession session = getSession(repoId);
        DocumentDTO currentDocument = getDocument(repoId, docRef);
        if (aclChangesHandler.isChangeInACL(currentDocument, doc)) {
            aclChangesHandler.applyACLChange(doc, docRef, session);
        }
        DocumentModel docModel = session.getDocument(docRef);
        // remove legal hold first, to allow properties updates
        if (docModel.isRecord() && FALSE.equals(getLegalHold(doc))) {
            session.setLegalHold(docRef, false, null);
        }
        Instant currentTime = Instant.now();
        if (docModel.isRecord() && RETAIN_UNTIL_INDETERMINATE.equals(docModel.getRetainUntil())
                && retainUntil(doc) != null && currentTime.isBefore(retainUntil(doc))) {
            // Retention is currently indeterminate and an actual retention date is requested.
            // In order to allow properties updates, first remove all retention,
            // then updateDocumentModel will set properties,
            // then applyGovernance will set the new retention date.
            session.setRetainUntil(docRef, currentTime, null);
        }
        documentMapper.updateDocumentModel(docModel, doc);
        applyGovernance(session, session.getDocument(docRef), doc);
        if (condition != null) {
            docModel.setUpdateCondition(condition);
        }
        session.save();
        docModel.refresh();
        return documentMapper.documentModelToDocumentDTO(docModel);
    }

    public DocumentDTO patchDocument(String repoId, DocumentRef docRef, String patchString, UpdateCondition condition)
            throws JacksonException {
        // TODO transform patch "test" statements into conditional updates, for better atomicity
        JsonNode patch = objectMapper.readTree(patchString);
        // compute a DocumentDTO with explicit empty lists, so that patch can add to them using mylist/-
        DocumentDTO document = getDocument(repoId, docRef, false, true); // with empty lists
        JsonNode patched = Jackson3JsonPatch.apply(patch, objectMapper.convertValue(document, JsonNode.class));
        document = objectMapper.treeToValue(patched, DocumentDTO.class);
        return updateDocument(repoId, docRef, document, condition);
    }

    public DocumentDTO copyDocument(String repoId, DocumentRef src, DocumentRef dst, String name) {
        CoreSession session = getSession(repoId);
        DocumentModel copy = session.copy(src, dst, name);
        session.save();
        copy.refresh();
        return documentMapper.documentModelToDocumentDTO(copy);
    }

    public DocumentDTO moveDocument(String repoId, DocumentRef docRef, DocumentRef parentRef,
            UpdateCondition condition) {
        CoreSession session = getSession(repoId);
        DocumentModel moved = session.move(docRef, parentRef, null, condition);
        session.save();
        moved.refresh();
        return documentMapper.documentModelToDocumentDTO(moved);
    }

    public DocumentDTO checkinDocument(String repoId, DocumentRef docRef, boolean minor) {
        CoreSession session = getSession(repoId);
        DocumentRef checkedInDoc = session.checkIn(docRef, minor);
        session.save();
        return getDocument(repoId, checkedInDoc);
    }

    public DocumentDTO setLock(String repoId, DocumentRef docRef) {
        CoreSession session = getSession(repoId);
        session.setLock(docRef);
        session.save();
        return getDocument(repoId, docRef);
    }

    public DocumentDTO removeLock(String repoId, DocumentRef docRef) {
        CoreSession session = getSession(repoId);
        session.removeLock(docRef);
        session.save();
        return getDocument(repoId, docRef);
    }

    public void deleteDocument(String repoId, DocumentRef docRef) {
        CoreSession session = getSession(repoId);
        session.removeDocument(docRef);
        session.save();
    }

    public List<DocumentDTO> getChildren(String repoId, DocumentRef docRef) {
        return getSession(repoId).getChildren(docRef).stream().map(documentMapper::documentModelToDocumentDTO).toList();
    }

    public boolean isRecord(String repoId, DocumentRef docRef) {
        return getSession(repoId).isRecord(docRef);
    }

    public DocumentDTO makeRecord(String repoId, DocumentRef docRef) {
        CoreSession session = getSession(repoId);
        session.setRecord(docRef, true);
        session.save();
        return getDocument(repoId, docRef);
    }

    public DocumentDTO setLegalHold(String repoId, DocumentRef docRef, Boolean hold, String comment) {
        CoreSession session = getSession(repoId);
        session.setLegalHold(docRef, hold, comment);
        session.save();
        return documentMapper.documentModelToDocumentDTO(session.getDocument(docRef));
    }

    public DocumentDTO setRetainUntil(String repoId, DocumentRef docRef, Instant until, String comment) {
        CoreSession session = getSession(repoId);
        session.setRetainUntil(docRef, until, comment);
        session.save();
        return documentMapper.documentModelToDocumentDTO(session.getDocument(docRef));
    }

    public DocumentDTO restoreVersion(String repoId, DocumentRef versionRef) {
        CoreSession session = getSession(repoId);
        DocumentModel doc = session.restoreVersion(versionRef);
        session.save();
        doc.refresh();
        return documentMapper.documentModelToDocumentDTO(doc);
    }

    public DocumentDTO uploadAndCreateDocument(String repoId, DocumentRef parentRef, DocumentDTO doc,
            boolean enforceSysName, Collection<Part> multiParts) {
        uploadMultiParts(doc, multiParts);
        return createDocument(repoId, parentRef, doc, enforceSysName);
    }

    public DocumentDTO uploadAndUpdateDocument(String repoId, DocumentRef docRef, DocumentDTO doc,
            Collection<Part> multiParts) {
        uploadMultiParts(doc, multiParts);
        return updateDocument(repoId, docRef, doc, null);
    }

    /**
     * Uploads multipart files and updates the document with new upload IDs.
     * <p>
     * The document provided should have upload Ids, that match the part names in the multipart request. These IDs are
     * replaced with the new upload IDs created from the uploading each corresponding multipart file.
     *
     * @param doc The document to which the parts are to be uploaded.
     * @param multiParts The parts of the request.
     * @return The updated document with the new upload IDs.
     * @throws NuxeoException If there is an error reading a part.
     */
    private DocumentDTO uploadMultiParts(DocumentDTO doc, Collection<Part> multiParts) {
        // Filter out parts that are not files.
        List<Part> fileParts = multiParts.stream().skip(1).toList();

        // Create a new upload for each file part in the multipart and update the document
        for (Part part : fileParts) {
            try (InputStream inputStream = part.getInputStream()) {
                String uploadId = getUpload(part.getSubmittedFileName(), BlobUtils.getMimeType(part.getContentType()),
                        BlobUtils.getCharset(part.getContentType()), inputStream);
                // update the document with the new upload ID, replacing those that match the original multipart name.
                findAndReplaceUploadId(doc.getProperties(), part.getName(), uploadId);
            } catch (IOException e) {
                throw new NuxeoException("Error reading part", BAD_REQUEST.getStatusCode());
            }
        }
        return doc;
    }

    private void applyGovernance(CoreSession session, DocumentModel docModel, DocumentDTO doc){
        if (!docModel.isGovernable()) {
            return;
        }
        if (shouldMarkAsRecord(doc)!=null) {
            session.setRecord(docModel.getRef(), shouldMarkAsRecord(doc));
        }
        if (docModel.isRecord()) {
            if (TRUE.equals(getLegalHold(doc))) {
                session.setLegalHold(docModel.getRef(), true, null);
            }
            if (retainUntil(doc) != null) {
                session.setRetainUntil(docModel.getRef(), retainUntil(doc), null);
            }
        }
    }

    private Boolean shouldMarkAsRecord(DocumentDTO doc) {
        return (Boolean) getProperty(doc, SYSGOV_IS_RECORD, BooleanType.INSTANCE);
    }

    private Boolean getLegalHold(DocumentDTO doc) {
        return (Boolean) getProperty(doc, SYSGOV_HAS_LEGAL_HOLD, BooleanType.INSTANCE);
    }

    private Instant retainUntil(DocumentDTO doc) {
        return (Instant) getProperty(doc, SYSGOV_RETAIN_UNTIL, DateType.INSTANCE);
    }

    // mimic conversion happening in DBSDocument.convert
    private Object getProperty(DocumentDTO doc, String key, Type type) {
        Object value = doc.getProperties().get(key);
        if (value == null) {
            return null;
        }
        try {
            return type.convert(value);
        } catch (TypeException e) {
            throw new NuxeoException("Invalid %s value for property: %s".formatted(type.getName(), key),
                    BAD_REQUEST.getStatusCode());
        }
    }

    public String getUpload(String fileName, String mimeType, String fileEncoding, InputStream inputStream)
            throws IOException {
        // create UploadDTO for single step and send for process upload.
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeType, fileEncoding, null, false, 0, 0);
        return uploadService.processUpload(uploadHandler, mimeType, uploadDTO, inputStream).getId();
    }

    @SuppressWarnings("unchecked")
    private void findAndReplaceUploadId(Object value, String uploadId, String newUploadId) {
        // If the value is an array, list or map, iterate over its elements and recursively call this method.
        if (value instanceof Object[] array) {
            for (Object o : array) {
                findAndReplaceUploadId(o, uploadId, newUploadId);
            }
        } else if (value instanceof List<?> list) {
            for (Object listItem : list) {
                findAndReplaceUploadId(listItem, uploadId, newUploadId);
            }
        } else if (value instanceof Map<?, ?> map) {
            if (isBlobUploadIdMap((Map<Object, Object>) map)) {
                if (map.get(UPLOAD_ID_MARKER).equals(uploadId)) {
                    ((Map<Object, Object>) map).put(UPLOAD_ID_MARKER, newUploadId);
                }
            } else {
                for (Object mapValue : map.values()) {
                    findAndReplaceUploadId(mapValue, uploadId, newUploadId);
                }
            }
        }
    }

    private boolean isBlobUploadIdMap(Map<Object, Object> map) {
        return map.size() == 1 && map.containsKey(UPLOAD_ID_MARKER);
    }

    private void fireViewAuditEvent(CoreSession session, DocumentModel source) {
        NuxeoPrincipal principal = session.getPrincipal();
        DocumentEventContext ctx = new DocumentEventContext(session, principal, source);
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, session.getRepositoryName());
        Event event = ctx.newEvent(DOCUMENT_VIEWED);
        EventService eventService = Framework.getService(EventService.class);
        eventService.fireEvent(event);
    }

    public DocumentDTO importDocument(DocumentDTO dto, String repoId, DocumentRef docRef, BulkImportUploadDTO uploadDTO,
            InputStream inputStream) throws IOException {
        String uploadId = getUpload(uploadDTO.filename(), uploadDTO.mimeType(), uploadDTO.encoding(), inputStream);
        log.debug("Upload ID: {}", uploadId);
        Map<String, String> uploadMap = Map.of(UPLOAD_ID_MARKER, uploadId);
        dto.getProperties().put(SYSFILE_BLOB, uploadMap);
        return createDocument(repoId, docRef, dto, false);
    }
}
