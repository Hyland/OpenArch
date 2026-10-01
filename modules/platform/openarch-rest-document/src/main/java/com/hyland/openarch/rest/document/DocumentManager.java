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

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static com.hyland.openarch.ecm.core.api.CoreSession.RETAIN_UNTIL_INDETERMINATE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_VIEWED;
import static com.hyland.openarch.rest.document.DocumentMapper.UPLOAD_ID_MARKER;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.api.event.CoreEventConstants;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.TypeException;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import com.hyland.openarch.ecm.core.utils.BlobUtils;
import com.hyland.openarch.rest.bulkimport.BulkImportUploadDTO;
import com.hyland.openarch.rest.document.utils.ACLChangesHandler;
import com.hyland.openarch.rest.upload.UploadDTO;
import com.hyland.openarch.rest.upload.UploadService;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
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
            throw new RepositoryException("Missing document type for document creation: primaryType",
                    Response.Status.BAD_REQUEST.getStatusCode());
        }
        CoreSession session = getSession(repoId);
        DocumentModel document = documentMapper.createDocumentModel(session, parentRef, doc, enforceSysName);
        if (doc.getAcl() != null && !doc.getAcl().isEmpty()) {
            aclChangesHandler.applyACLChange(doc, document);
        }
        applyGovernance(session, document, doc);
        session.save();
        document.refresh();
        return documentMapper.documentModelToDocumentDTO(document);
    }

    public DocumentDTO updateDocument(String repoId, DocumentRef docRef, DocumentDTO doc, UpdateCondition condition,
            boolean userChange) {
        CoreSession session = getSession(repoId);
        DocumentDTO currentDocument = getDocument(repoId, docRef);
        DocumentModel docModel = session.getDocument(docRef);
        if (userChange) {
            docModel.markUserChange();
        }
        if (aclChangesHandler.isChangeInACL(currentDocument, doc)) {
            aclChangesHandler.applyACLChange(doc, docModel);
        }
        // remove legal hold first, to allow properties updates
        if (docModel.isRecord() && FALSE.equals(getLegalHold(doc))) {
            session.setLegalHold(docRef, false);
        }
        Instant currentTime = Instant.now();
        if (docModel.isRecord() && RETAIN_UNTIL_INDETERMINATE.equals(docModel.getRetainUntil())
                && retainUntil(doc) != null && currentTime.isBefore(retainUntil(doc))) {
            // Retention is currently indeterminate and an actual retention date is requested.
            // In order to allow properties updates, first remove all retention,
            // then updateDocumentModel will set properties,
            // then applyGovernance will set the new retention date.
            session.setRetainUntil(docRef, currentTime);
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

    /**
     * Atomically creates-or-updates a document, reusing the standard create and update paths so that all their
     * semantics apply (validation, enrichers, fulltext, denormalization, events).
     * <p>
     * The target is matched by its id (when {@code matchById}) or by parent id and name. It is created if absent or
     * updated (merge) if present. A concurrent create is arbitrated by the unique index: the loser gets a
     * {@link ConcurrentUpdateException} and is retried as an update. Records and documents under retention or legal
     * hold cannot be updated this way, and re-parenting (by id) is rejected.
     * <p>
     * Must be called with an ambient transaction, which the retry replaces with a fresh one; the caller finalizes
     * whatever transaction is active when this returns (which is what the request transaction filter does).
     */
    public UpsertDocumentResult upsertDocument(String repoId, DocumentRef parentRef, DocumentDTO doc,
            boolean matchById, boolean userChange) {
        try {
            return upsertDocumentOnce(repoId, parentRef, doc, matchById, userChange);
        } catch (ConcurrentUpdateException e) {
            // a concurrent create won the race and doomed this transaction
            // restart it and retry: the document now exists, so this resolves to an update
            TransactionHelper.setTransactionRollbackOnly();
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
            return upsertDocumentOnce(repoId, parentRef, doc, matchById, userChange);
        }
    }

    // runs within the upsertDocument transaction (self-invoked createDocument/updateDocument are not re-wrapped)
    private UpsertDocumentResult upsertDocumentOnce(String repoId, DocumentRef parentRef, DocumentDTO doc,
            boolean matchById, boolean userChange) {
        if (doc == null || StringUtils.isBlank(doc.getPrimaryType())) {
            throw new RepositoryException("Missing sys_primaryType for upsert", Response.Status.BAD_REQUEST.getStatusCode());
        }
        DocumentRef targetRef;
        if (matchById) {
            if (StringUtils.isBlank(doc.getId())) {
                throw new RepositoryException("Missing sys_id for upsert by id",
                        Response.Status.BAD_REQUEST.getStatusCode());
            }
            if (!documentMapper.allowSetIdOnCreation) {
                // otherwise a create would assign a server-generated id, so the next upsert with the same client id
                // would not find the document and would create a duplicate; refuse rather than silently misbehave
                throw new RepositoryException("Upsert by id is not enabled (requires "
                        + DocumentMapper.ALLOW_SET_ID_ON_CREATION + ")", Response.Status.BAD_REQUEST.getStatusCode());
            }
            targetRef = new IdRef(doc.getId());
        } else {
            if (StringUtils.isBlank(doc.getName())) {
                throw new RepositoryException("Missing document name for upsert by path",
                        Response.Status.BAD_REQUEST.getStatusCode());
            }
            targetRef = childRef(parentRef, doc.getName());
        }
        DocumentModel existing = getIfExists(repoId, targetRef);
        if (existing != null) {
            // re-parenting must be rejected explicitly: the update path updates in place and would silently
            // ignore a changed parent in the request; an unmentioned parent is not a re-parenting request
            if (matchById && StringUtils.isNotBlank(doc.getParentId())) {
                String existingParentId = existing.getParentRef() == null ? null : existing.getParentRef().toString();
                if (!doc.getParentId().equals(existingParentId)) {
                    throw new RepositoryException("Cannot re-parent via upsert: " + existing.getId(),
                            Response.Status.CONFLICT.getStatusCode());
                }
            }
            return new UpsertDocumentResult(updateDocument(repoId, targetRef, doc, null, userChange), false);
        } else {
            if (matchById) {
                // unlike the update, the create needs to know where to put the document and how to name it
                if (StringUtils.isBlank(doc.getParentId())) {
                    throw new RepositoryException("Missing sys_parentId for upsert by id creating a document",
                            Response.Status.BAD_REQUEST.getStatusCode());
                }
                if (StringUtils.isBlank(doc.getName())) {
                    throw new RepositoryException("Missing sys_name for upsert by id creating a document",
                            Response.Status.BAD_REQUEST.getStatusCode());
                }
            }
            // create: enforce the requested name so a concurrent create surfaces as a ConcurrentUpdateException
            return new UpsertDocumentResult(createDocument(repoId, parentRef, doc, true), true);
        }
    }

    /**
     * Gets the document, or {@code null} if there is none. Unlike {@link CoreSession#exists}, a document that exists
     * but is not readable by the current user is not reported as absent, which would send the upsert into a create
     * that can only fail on the unique index.
     */
    protected DocumentModel getIfExists(String repoId, DocumentRef docRef) {
        try {
            return getSession(repoId).getDocument(docRef);
        } catch (DocumentNotFoundException e) {
            return null;
        }
    }

    /** Builds the reference of the child named {@code name} under the given parent path. */
    protected DocumentRef childRef(DocumentRef parentRef, String name) {
        if (parentRef instanceof PathRef pathRef) {
            // the two-argument constructor deals with the slash and refuses a name that contains one
            return new PathRef(StringUtils.stripEnd(pathRef.value, "/"), name);
        }
        throw new IllegalStateException("Upsert by path requires a PathRef parent, got: " + parentRef);
    }

    public DocumentDTO patchDocument(String repoId, DocumentRef docRef, String patchString, UpdateCondition condition,
            boolean userChange) throws JacksonException {
        // TODO transform patch "test" statements into conditional updates, for better atomicity
        JsonNode patch = objectMapper.readTree(patchString);
        // compute a DocumentDTO with explicit empty lists, so that patch can add to them using mylist/-
        DocumentDTO document = getDocument(repoId, docRef, false, true); // with empty lists
        JsonNode patched = Jackson3JsonPatch.apply(patch, objectMapper.convertValue(document, JsonNode.class));
        document = objectMapper.treeToValue(patched, DocumentDTO.class);
        return updateDocument(repoId, docRef, document, condition, userChange);
    }

    public DocumentDTO copyDocument(String repoId, DocumentRef src, DocumentRef dst, String name) {
        CoreSession session = getSession(repoId);
        DocumentModel copy = session.copy(src, dst, name);
        session.save();
        copy.refresh();
        return documentMapper.documentModelToDocumentDTO(copy);
    }

    public DocumentDTO moveDocument(String repoId, DocumentRef docRef, DocumentRef parentRef,
            UpdateCondition condition, boolean userChange) {
        CoreSession session = getSession(repoId);
        if (userChange) {
            session.getDocument(docRef).markUserChange();
        }
        DocumentModel moved = session.move(docRef, parentRef, null, condition);
        session.save();
        moved.refresh();
        return documentMapper.documentModelToDocumentDTO(moved);
    }

    public DocumentDTO checkinDocument(String repoId, DocumentRef docRef, boolean minor, boolean userChange) {
        CoreSession session = getSession(repoId);
        if (userChange) {
            session.getDocument(docRef).markUserChange();
        }
        DocumentRef checkedInDoc = session.checkIn(docRef, minor);
        session.save();
        return getDocument(repoId, checkedInDoc);
    }

    public DocumentDTO setLock(String repoId, DocumentRef docRef) {
        CoreSession session = getSession(repoId);
        DocumentModel doc = session.getDocument(docRef);
        doc.setLock();
        session.save();
        return getDocument(repoId, docRef);
    }

    public DocumentDTO removeLock(String repoId, DocumentRef docRef) {
        CoreSession session = getSession(repoId);
        DocumentModel doc = session.getDocument(docRef);
        doc.removeLock();
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

    public DocumentDTO setLegalHold(String repoId, DocumentRef docRef, Boolean hold) {
        CoreSession session = getSession(repoId);
        session.setLegalHold(docRef, hold);
        session.save();
        return documentMapper.documentModelToDocumentDTO(session.getDocument(docRef));
    }

    public DocumentDTO setRetainUntil(String repoId, DocumentRef docRef, Instant until) {
        CoreSession session = getSession(repoId);
        session.setRetainUntil(docRef, until);
        session.save();
        return documentMapper.documentModelToDocumentDTO(session.getDocument(docRef));
    }

    public DocumentDTO restoreVersion(String repoId, DocumentRef versionRef, boolean userChange) {
        CoreSession session = getSession(repoId);
        DocumentModel doc = session.restoreVersion(versionRef);
        if (userChange) {
            doc.markUserChange();
        }
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
            Collection<Part> multiParts, boolean userChange) {
        uploadMultiParts(doc, multiParts);
        return updateDocument(repoId, docRef, doc, null, userChange);
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
     * @throws RepositoryException If there is an error reading a part.
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
                throw new RepositoryException("Error reading part", BAD_REQUEST.getStatusCode());
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
                session.setLegalHold(docModel.getRef(), true);
            }
            if (retainUntil(doc) != null) {
                session.setRetainUntil(docModel.getRef(), retainUntil(doc));
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
            throw new RepositoryException("Invalid %s value for property: %s".formatted(type.getName(), key),
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
        RepositoryPrincipal principal = session.getPrincipal();
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
