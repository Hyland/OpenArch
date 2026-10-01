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

import static jakarta.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static java.util.Objects.requireNonNull;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.CREATE_VERSION;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.DELETE_CHILD;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.GENERIC_DOCUMENT_SECURITY_ERROR;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;
import static com.hyland.openarch.ecm.core.event.impl.DocumentEventContext.REQUESTED_PERMISSION_PROPERTY_KEY;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracing;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModel.DocumentModelRefresh;
import com.hyland.openarch.ecm.core.api.DocumentModelList;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.api.event.CoreEventConstants;
import com.hyland.openarch.ecm.core.api.event.DocumentEventCategories;
import com.hyland.openarch.ecm.core.api.event.DocumentEventTypes;
import com.hyland.openarch.ecm.core.api.impl.DocumentModelListImpl;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.api.trash.TrashService;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.ecm.core.model.DocumentEventSpec;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.ecm.core.model.PathComparator;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.storage.dbs.DBSDocument;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.metrics.MetricsService;

public class SessionImpl implements CoreSession {

    private static final Logger log = LogManager.getLogger(SessionImpl.class);

    private static final Comparator<? super InternalDocument> PATH_COMPARATOR = new PathComparator();

    private static final MetricRegistry METRIC_REGISTRY = SharedMetricRegistries.getOrCreate(
            MetricsService.class.getName());

    // static caches shared by all threads
    private static final Map<String, Counter> DELETE_DOC_COUNT = new ConcurrentHashMap<>();

    private final String repositoryName;

    private final RepositoryPrincipal principal;

    // lazily initialized counters
    private Counter deleteDocumentCount;

    public SessionImpl(String repositoryName, RepositoryPrincipal principal) {
        this.repositoryName = repositoryName;
        this.principal = principal;
    }

    public InternalSession getSession() {
        RepositoryService repositoryService = Framework.getService(RepositoryService.class);
        return repositoryService.getSession(repositoryName);
    }

    @Override
    public SchemaManager getSchemaManager() {
        return getSession().getSchemaManager();
    }

    @Override
    public String getRepositoryName() {
        return repositoryName;
    }

    @Override
    public RepositoryPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String toString() {
        return repositoryName + "/" + principal;
    }

    private void deleteDocumentCountInc() {
        if (deleteDocumentCount == null) {
            deleteDocumentCount = DELETE_DOC_COUNT.computeIfAbsent(repositoryName,
                    r -> METRIC_REGISTRY.counter(
                            MetricName.build("nuxeo", "repositories", "repository", "documents", "delete")
                                      .tagged("repository", r)));
        }
        deleteDocumentCount.inc();
    }

    private DocumentImpl readModel(InternalDocument doc) {
        return new DocumentImpl(doc, principal);
    }

    private final void checkPermission(InternalDocument doc, String permission) {
        if (!hasPermission(doc, permission)) {
            log.debug("Permission '{}' is not granted to '{}' on document {} ({} - {})", permission, principal.getId(),
                    doc.getPath(), doc.getId(), doc.getType().getName());
            DocumentModel docModel = readModel(doc);
            Map<String, Serializable> options = new HashMap<>();
            options.put(REQUESTED_PERMISSION_PROPERTY_KEY, permission);
            notifyEvent(DocumentEventTypes.DOCUMENT_PERMISSION_DENIED, docModel, options, null, null, false);
            throw new DocumentSecurityException(
                    "Privilege '" + permission + "' is not granted to '" + principal.getId() + "'" + " on document '" + doc.getId() + "'");
        }
    }

    private boolean isAdministrator() {
        return principal.isAdministrator();
    }

    private InternalDocument resolveReference(DocumentRef docRef, String permission) throws DocumentNotFoundException {
        requireNonNull(docRef, "null docRef");
        String value = (String) docRef.reference();
        requireNonNull(value, "null reference");
        InternalDocument doc;
        if (docRef instanceof PathRef) {
            doc = getSession().resolvePath(value);
        } else if (docRef instanceof IdRef) {
            doc = getSession().getDocumentByUUID(value);
        } else {
            throw new UnsupportedOperationException(docRef.getClass().getName());
        }
        if (permission != null) {
            checkPermission(doc, permission);
        }
        return doc;
    }

    private InternalDocument resolveParentReference(DocumentRef docRef) {
        // first let's try to handle a path reference
        if (docRef != null && docRef.type() == DocumentRef.PATH && docRef.reference() != null) {
            String docPath = (String) docRef.reference();
            if ("/".equals(docPath)) {
                return null;
            }
            String parentPath = docPath.substring(0, docPath.lastIndexOf('/'));
            if (parentPath.isEmpty()) {
                parentPath = "/";
            }
            return resolveReference(new PathRef(parentPath), null);
        }
        return resolveReference(docRef, null).getParent();
    }

    @Override
    public void save() {
        getSession().save(principal);
    }

    @Override
    public List<DocumentModel> getAncestorDocuments(final DocumentRef docRef) {
        final List<DocumentModel> docsList = new ArrayList<>();
        InternalDocument doc = resolveReference(docRef, null);
        if (!hasPermission(doc, READ)) {
            // doesn't have read permission on the target document, return empty list
            return docsList;
        }
        while (doc != null && !"/".equals(doc.getPath())) {
            docsList.add(hasPermission(doc, READ) ? readModel(doc) : null);
            doc = doc.getParent();
        }
        Collections.reverse(docsList);
        return docsList;
    }

    @Override
    public DocumentModel getRootDocument() {
        InternalDocument doc = getSession().getRootDocument();
        return readModel(doc);
    }

    private final boolean hasPermission(InternalDocument doc, String permission) {
        return doc.hasPermission(principal, permission);
    }

    @Override
    public boolean hasPermission(DocumentRef docRef, String permission) {
        InternalDocument doc = resolveReference(docRef, null);
        return hasPermission(doc, permission);
    }

    @Override
    public boolean exists(DocumentRef docRef) {
        try {
            resolveReference(docRef, READ);
            return true;
        } catch (DocumentNotFoundException | DocumentSecurityException e) {
            return false;
        }
    }

    @Override
    public DocumentModel getDocument(DocumentRef docRef) throws DocumentNotFoundException {
        InternalDocument doc = resolveReference(docRef, READ);
        return readModel(doc);
    }

    @Override
    public DocumentModelList getDocuments(DocumentRef[] docRefs) throws DocumentNotFoundException {
        List<DocumentModel> docs = new ArrayList<>(docRefs.length);
        for (DocumentRef docRef : docRefs) {
            InternalDocument doc;
            try {
                doc = resolveReference(docRef, READ);
            } catch (DocumentSecurityException e) {
                // no permission
                continue;
            }
            docs.add(readModel(doc));
        }
        return new DocumentModelListImpl(docs);
    }

    @Override
    public DocumentModel getChild(DocumentRef parentRef, String name) {
        InternalDocument parent = resolveReference(parentRef, READ);
        InternalDocument child = parent.getChild(name);
        checkPermission(child, READ);
        return readModel(child);
    }

    @Override
    public boolean hasChild(DocumentRef parentRef, String name) {
        InternalDocument doc = resolveReference(parentRef, READ);
        return doc.hasChild(name);
    }

    @Override
    public DocumentModelList getChildren(DocumentRef parentRef) {
        InternalDocument parent = resolveReference(parentRef, READ);
        DocumentModelList docs = new DocumentModelListImpl();
        for (InternalDocument child : parent.getChildren()) {
            if (hasPermission(child, READ)) {
                docs.add(readModel(child));
            }
        }
        return docs;
    }

    @Override
    public DocumentModel getParentDocument(DocumentRef parentRef) {
        InternalDocument parent = resolveParentReference(parentRef);
        if (parent == null) {
            return null;
        }
        if (!hasPermission(parent, READ)) {
            throw new DocumentSecurityException("Privilege READ is not granted to " + principal.getId() + " on document '" + parent.getId() + "'");
        }
        return readModel(parent);
    }

    @Override
    public boolean hasChildren(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        return doc.hasChildren();
    }

    @Override
    public DocumentModel createDocumentModel(String parentPath, String name, String typeName) {
        PathRef.checkName(name);
        DocumentRef parentRef = parentPath == null ? null : new PathRef(parentPath);
        return createDocumentModelFromParentAndType(parentRef, name, typeName);
    }

    @Override
    public DocumentModel newDocumentModel(DocumentRef parentRef, String name, String typeName) {
        return createDocumentModelFromParentAndType(parentRef, name, typeName);
    }

    private DocumentModel createDocumentModelFromParentAndType(DocumentRef parentRef, String name, String typeName) {
        InternalDocument parent = parentRef == null ? null : resolveReference(parentRef, READ);
        DBSDocument doc = DBSDocument.newUnCreatedDocument(parent, name, typeName, getSession());
        return readModel(doc);
    }

    @Override
    public boolean canRemoveDocument(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, null);
        return canRemoveDocument(doc) == null;
    }

    /**
     * Checks if a document can be removed, and returns a failure reason if not.
     */
    private String canRemoveDocument(InternalDocument doc) {
        if (!hasPermission(doc, DELETE)) {
            return GENERIC_DOCUMENT_SECURITY_ERROR.formatted(DELETE, principal.getId(), doc.getId());
        }
        InternalDocument parent = doc.getParent();
        if (parent == null) {
            return null; // ok
        }
        return hasPermission(parent, DELETE_CHILD) ? null
                : "Missing permission '" + DELETE_CHILD + "' on parent document " + parent.getId();
    }

    @Override
    public void removeDocument(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, null);
        removeDocument(doc);
    }

    private void removeDocument(InternalDocument doc) {
        try {
            String reason = canRemoveDocument(doc);
            if (reason != null) {
                throw new DocumentSecurityException(reason);
            }
            removeNotifyOneDoc(doc);

        } catch (ConcurrentUpdateException e) {
            e.addInfo("Failed to remove document " + doc.getId());
            throw e;
        }
        deleteDocumentCountInc();
    }

    private void removeNotifyOneDoc(InternalDocument doc) {
        DocumentModel docModel = readModel(doc);
        Map<String, Serializable> options = new HashMap<>();
        options.put(CoreEventConstants.DOCUMENT_PARENT_ID, docModel.getParentRef());
        InternalDocument sourceDoc = doc.isVersion() ? doc.getParent() : null;
        doc.remove(principal);
        if (sourceDoc != null) {
            DocumentModel sourceDocModel = readModel(sourceDoc);
            notifyEvent(DocumentEventTypes.VERSION_REMOVED, sourceDocModel, options, null, null, false);
        }
        notifyEvent(DocumentEventTypes.DOCUMENT_REMOVED, docModel, options, null, null, false);
    }

    /**
     * Implementation uses the fact that the lexicographic ordering of paths is a refinement of the "contains" partial
     * ordering.
     */
    @Override
    public void removeDocuments(DocumentRef[] docRefs) {
        InternalDocument[] docs = new InternalDocument[docRefs.length];

        for (int i = 0; i < docs.length; i++) {
            docs[i] = resolveReference(docRefs[i], null);
        }
        // TODO OPTIM: it's not guaranteed that getPath is cheap and
        // we call it a lot. Should use an object for pairs (document, path)
        // to call it just once per doc.
        Arrays.sort(docs, PATH_COMPARATOR); // nulls first
        String[] paths = new String[docs.length];
        for (int i = 0; i < docs.length; i++) {
            paths[i] = docs[i].getPath();
        }
        String lastRemovedWithSlash = "\u0000";
        for (int i = 0; i < docs.length; i++) {
            String path = paths[i];
            if (i == 0 || path == null || !path.startsWith(lastRemovedWithSlash)) {
                removeDocument(docs[i]);
                if (path != null) {
                    lastRemovedWithSlash = path + "/";
                }
            }
        }
    }

    @Override
    public void removeChildren(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, DELETE_CHILD);
        for (InternalDocument child : doc.getChildren()) {
            if (hasPermission(child, DELETE)) {
                removeNotifyOneDoc(child);
            }
        }
    }

    @Override
    public DocumentModel copy(DocumentRef docRef, DocumentRef folderRef, String name) {
        InternalDocument doc = resolveReference(docRef, READ);
        InternalDocument folder = resolveReference(folderRef, CREATE_CHILD);
        if (name == null) {
            name = doc.getName();
        } else {
            PathRef.checkName(name);
        }

        Map<String, Serializable> options = new HashMap<>();

        // add the destination name, destination and source references in the options of the event
        options.put(CoreEventConstants.SOURCE_REF, docRef);
        DocumentModel docModel = readModel(doc);
        options.put(CoreEventConstants.SOURCE_PARENT_REF, docModel.getParentRef());
        options.put(CoreEventConstants.SOURCE_PATH, docModel.getPathAsString());
        String trimmedPath = docModel.getPathAsString().substring(0, docModel.getPathAsString().lastIndexOf("/"));
        String sourceParentPath = trimmedPath.isEmpty() ? "/" : trimmedPath;
        options.put(CoreEventConstants.SOURCE_PARENT_PATH, sourceParentPath);

        InternalDocument copiedDoc = getSession().copy(doc, folder, name, principal);
        // no need to clear lock, locks table is not copied

        // notify document created by copy
        DocumentModel copiedDocModel = readModel(copiedDoc);

        String comment = doc.getRepositoryName() + ':' + docRef.toString();

        notifyEvent(DocumentEventTypes.DOCUMENT_CREATED_BY_COPY, copiedDocModel, options, null, comment, false);

        // notify document copied
        comment = copiedDoc.getRepositoryName() + ':' + copiedDocModel.getRef().toString();

        notifyEvent(DocumentEventTypes.DOCUMENT_DUPLICATED, docModel, null, null, comment, false);

        return copiedDocModel;
    }

    @Override
    public List<DocumentModel> copy(List<DocumentRef> src, DocumentRef dst) {
        return src.stream().map(ref -> copy(ref, dst, null)).toList();
    }

    @Override
    public DocumentModel move(DocumentRef docRef, DocumentRef folderRef, String name) {
        return move(docRef, folderRef, name, null);
    }

    @Override
    public DocumentModel move(DocumentRef docRef, DocumentRef folderRef, String name, UpdateCondition condition) {
        InternalDocument doc = resolveReference(docRef, READ);

        if (doc.isVersion()) {
            throw new RepositoryException("Cannot move a version: " + doc.getId(), SC_UNPROCESSABLE_ENTITY);
        }

        InternalDocument folder;
        if (folderRef == null) {
            // rename
            folder = doc.getParent();
            checkPermission(folder, WRITE);
        } else {
            folder = resolveReference(folderRef, CREATE_CHILD);
            checkPermission(doc.getParent(), DELETE_CHILD);
            checkPermission(doc, DELETE);
        }

        DocumentModel docModel = readModel(doc);
        if (name == null) {
            name = docModel.getName();
        } else {
            PathRef.checkName(name);
        }

        // Validate embeddings naming policy at rename time (no-op for non-embeddings).
        // Note: we don't have access to the creation context here, so the "enforce sys name" flag
        // is NOT considered during rename. Invalid SysEmbeddings names are rejected instead of auto-prefixed.
        SysEmbeddingsPolicies.enforceEmbeddingsNameOrThrow(doc, name);

        // add the destination name, destination and source references in the options of the event
        Map<String, Serializable> options = new HashMap<>(docModel.getContextData());
        options.put(CoreEventConstants.SOURCE_REF, docRef);
        options.put(CoreEventConstants.SOURCE_PARENT_REF, docModel.getParentRef());
        options.put(CoreEventConstants.SOURCE_PATH, docModel.getPathAsString());
        String trimmedPath = docModel.getPathAsString().substring(0, docModel.getPathAsString().lastIndexOf("/"));
        String sourceParentPath = trimmedPath.isEmpty() ? "/" : trimmedPath;
        options.put(CoreEventConstants.SOURCE_PARENT_PATH, sourceParentPath);

        String comment = doc.getRepositoryName() + ':' + doc.getParentId();

        InternalDocument movedDoc = getSession().move(doc, folder, name, condition, principal);

        // notify document moved
        DocumentModel movedDocModel = readModel(movedDoc);
        notifyEvent(DocumentEventTypes.DOCUMENT_MOVED, movedDocModel, options, null, comment, false);

        return movedDocModel;
    }

    @Override
    public void move(List<DocumentRef> src, DocumentRef dst) {
        src.forEach(ref -> move(ref, dst, null));
    }

    @Override
    public void updateDenormalizedPropertiesNoCache(Collection<String> ids) {
        getSession().updateDenormalizedPropertiesNoCache(ids);
    }

    @Override
    public void cancel() {
        // nothing
    }

    @Override
    public DocumentModel restoreVersion(DocumentRef versionRef) {
        InternalDocument version = resolveReference(versionRef, READ);
        if (!version.isVersion()) {
            throw new RepositoryException("Not a version: " + version.getId(), SC_UNPROCESSABLE_ENTITY);
        }
        InternalDocument doc = version.getWorkingCopy();
        checkPermission(doc, WRITE);
        if (!doc.isVersionable()) {
            throw new RepositoryException("Not versionable: " + doc.getId(), SC_PRECONDITION_FAILED);
        }

        if (doc.isRecord()) {
            throw new PropertyException("Version cannot be restored on a record: " + doc.getId());
        }

        DocumentModel docModel = readModel(doc);

        // we're about to overwrite the document, make sure it's archived
        // TODO only if autoversioning
        // if (doc.isCheckedOut()) {
        // notifyEvent(DocumentEventTypes.ABOUT_TO_CHECKIN, docModel, null, null, null, true);
        // InternalDocument ver = doc.checkIn(principal);
        // notifyCheckedInVersion(docModel, new IdRef(ver.getId()), null);
        // }

        // restore itself works on a checked out document, do it here to be able to send an event
        if (!doc.isCheckedOut()) {
            doc.checkOut();
            notifyEvent(DocumentEventTypes.DOCUMENT_CHECKEDOUT, docModel, null, null, null, false);
        }

        getSession().restoreVersion(version, principal);
        notifyEvent(DocumentEventTypes.DOCUMENT_RESTORED, docModel, null, null, null, false);

        log.debug("Document restored to version:{}", version::getId);
        return docModel;
    }

    @Override
    public void checkOut(DocumentRef docRef) {
        // TODO: add a new permission names CHECKOUT and use it instead of WRITE
        InternalDocument doc = resolveReference(docRef, WRITE);
        if (!doc.isVersionable()) {
            throw new RepositoryException("Not versionable: " + docRef, SC_PRECONDITION_FAILED);
        }
        DocumentModel docModel = readModel(doc);
        doc.checkOut();
        notifyEvent(DocumentEventTypes.DOCUMENT_CHECKEDOUT, docModel, null, null, null, false);
    }

    @Override
    public DocumentRef checkIn(DocumentRef docRef) {
        return checkIn(docRef, false);
    }

    @Override
    public DocumentRef checkIn(DocumentRef docRef, boolean minor) {
        InternalDocument doc = resolveReference(docRef, CREATE_VERSION);
        if (!doc.isVersionable()) {
            throw new RepositoryException("Not versionable: " + docRef, SC_PRECONDITION_FAILED);
        }
        DocumentModel docModel = readModel(doc);
        notifyEvent(DocumentEventTypes.ABOUT_TO_CHECKIN, docModel, null, null, null, true);
        InternalDocument version = doc.checkIn(principal, minor);
        notifyCheckedInVersion(docModel, version);
        return new IdRef(version.getId());
    }

    /**
     * Sends an event for the creation of a new version.
     *
     * @param docModel working copy from which the version was created
     * @param version the internal representation of the version
     */
    private void notifyCheckedInVersion(DocumentModel docModel, InternalDocument version) {
        Map<String, Serializable> props = new HashMap<>();
        props.put(SYS_ID, version.getId());
        props.put(SYSVER_VERSION, version.getVersion());
        props.put(SYSVER_MINOR_VERSION, version.getMinorVersion());
        props.put(SYSVER_TITLE, (String) version.getPropertyValue(SYSVER_TITLE));
        props.put(SYSVER_DESCRIPTION, (String) version.getPropertyValue(SYSVER_DESCRIPTION));
        props.put(SYS_CREATED, ((Instant) version.getPropertyValue(SYS_CREATED)).toString());
        // notify checkin on live document
        notifyEvent(DocumentEventTypes.DOCUMENT_CHECKEDIN, docModel, props, null, null, false);
    }

    @Override
    public boolean isCheckedOut(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        return doc.isCheckedOut();
    }

    @Override
    public DocumentModel getWorkingCopy(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        InternalDocument pwc = doc.getWorkingCopy();
        if (pwc == null) {
            return null;
        }
        checkPermission(pwc, READ);
        return readModel(pwc);
    }

    /**
     * Whether a projection returns documents, which is what lets the backend do the filtering and the paging: an empty
     * projection is {@code SELECT *}, and the id alone is enough to read a document too.
     */
    protected static boolean isDocumentProjection(SelectClause select) {
        List<Operand> elements = select.elements();
        return elements.isEmpty() || (elements.size() == 1 && elements.contains(new Reference(SYS_ID)));
    }

    @Override
    public DocumentModelList query(String query) {
        return query(query, 0, 0, 0);
    }

    @Override
    public DocumentModelList query(String query, long limit, long offset, long countUpTo) {
        return query(query, Map.of(), limit, offset, countUpTo);
    }

    @Override
    public DocumentModelList query(String query, Map<String, Object> parameters, long limit, long offset,
            long countUpTo) {
        return query(parse(query, parameters), limit, offset, countUpTo);
    }

    @Override
    public DocumentModelList query(Statement statement, long limit, long offset, long countUpTo) {
        Span span = Tracing.getTracer().getCurrentSpan();
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("hxql", AttributeValue.stringAttributeValue(statement.toString()));
        map.put("limit", AttributeValue.longAttributeValue(limit));
        map.put("offset", AttributeValue.longAttributeValue(offset));
        map.put("countUpTo", AttributeValue.longAttributeValue(countUpTo));
        span.addAnnotation("AbstractSession#query", map);

        DocumentModelList ret = tracedQuery(statement, limit, offset, countUpTo);

        map.clear();
        map.put("totalSize", AttributeValue.longAttributeValue(ret.totalSize()));
        span.addAnnotation("AbstractSession#query.done", map);
        return ret;
    }

    /** Parses a query, reporting the text that could not be parsed. */
    protected static Statement parse(String query, Map<String, Object> parameters) {
        try {
            return HXQLParser.parse(query, parameters);
        } catch (QueryParseException e) {
            e.addInfo("Failed to parse query: " + query);
            throw e;
        }
    }

    private DocumentModelList tracedQuery(Statement statement, long limit, long offset, long countUpTo) {
        try {
            // force post-filtering if we SELECT something different than '*' or 'sys_id'
            // as the backend won't do the filtering (see NXP-30434)
            boolean postFilter = !isDocumentProjection(statement.getSelectClause());
            QueryParameters queryParams = new QueryParameters(principal, postFilter ? 0 : limit,
                    postFilter ? 0 : offset);

            // get document list with total size
            PartialList<InternalDocument> pl = getSession().query(statement, queryParams, postFilter ? -1 : countUpTo);
            // convert to DocumentModelList
            DocumentModelListImpl dms = new DocumentModelListImpl(pl.size());
            dms.setTotalSize(pl.totalSize());
            for (InternalDocument doc : pl) {
                dms.add(readModel(doc));
            }

            if (!postFilter) {
                // the backend has done all the needed filtering
                return dms;
            }

            // post-filter the results "by hand", the backend couldn't do it
            long start = limit == 0 || offset < 0 ? 0 : offset;
            long stop = start + (limit == 0 ? dms.size() : limit);
            int n = 0;
            DocumentModelListImpl docs = new DocumentModelListImpl();
            for (DocumentModel model : dms) {
                if (postFilter) {
                    if (!hasPermission(model.getRef(), READ)) {
                        continue;
                    }
                }
                if (n < start) {
                    n++;
                    continue;
                }
                if (n >= stop) {
                    if (countUpTo == 0) {
                        // can break early
                        break;
                    }
                    n++;
                    continue;
                }
                n++;
                docs.add(model);
            }
            if (countUpTo != 0) {
                docs.setTotalSize(n);
            }
            return docs;
        } catch (QueryParseException e) {
            e.addInfo("Failed to execute query: " + statement);
            throw e;
        }
    }

    @Override
    public IterableQueryResult queryAndFetch(String query) {
        return queryAndFetch(parse(query, Map.of()));
    }

    @Override
    public IterableQueryResult queryAndFetch(Statement statement) {
        Span span = Tracing.getTracer().getCurrentSpan();
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("hxql", AttributeValue.stringAttributeValue(statement.toString()));
        span.addAnnotation("AbstractSession#queryAndFetch", map);
        try {
            QueryParameters queryParams = new QueryParameters(principal, 0, 0);
            IterableQueryResult result = getSession().queryAndFetch(statement, queryParams);

            map.clear();
            map.put("totalSize", AttributeValue.longAttributeValue(result.size()));
            span.addAnnotation("AbstractSession#queryAndFetch.done", map);
            return result;
        } catch (QueryParseException e) {
            e.addInfo("Failed to execute query: " + statement);
            throw e;
        }
    }

    @Override
    public PartialList<Map<String, Object>> queryProjection(String query, boolean distinctDocuments, long limit,
            long offset, long countUpTo) {
        return queryProjection(parse(query, Map.of()), distinctDocuments, limit, offset, countUpTo);
    }

    @Override
    public PartialList<Map<String, Object>> queryProjection(Statement statement, boolean distinctDocuments,
            long limit, long offset, long countUpTo) {
        QueryParameters queryParams = new QueryParameters(principal, limit, offset);
        return getSession().queryProjection(statement, queryParams, distinctDocuments, countUpTo);
    }

    @Override
    public ScrollResult<String> scroll(String query, int batchSize, int keepAliveSeconds) {
        return scroll(parse(query, Map.of()), batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(Statement statement, int batchSize, int keepAliveSeconds) {
        Map<String, AttributeValue> map = new HashMap<>();
        Span span = Tracing.getTracer().getCurrentSpan();
        map.put("hxql", AttributeValue.stringAttributeValue(statement.toString()));
        map.put("batchSize", AttributeValue.longAttributeValue(batchSize));
        map.put("keepAliveSeconds", AttributeValue.longAttributeValue(keepAliveSeconds));
        QueryParameters queryParams = new QueryParameters(principal, 0, 0);
        map.put("queryParams", AttributeValue.stringAttributeValue(queryParams.toString()));
        span.addAnnotation("AbstractSession#scroll", map);
        return getSession().scroll(statement, queryParams, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        Span span = Tracing.getTracer().getCurrentSpan();
        span.addAnnotation("AbstractSession#scroll.next");
        return getSession().scroll(scrollId);
    }

    @Override
    public void setRecord(DocumentRef docRef, boolean makeRecord) {
        InternalDocument doc = resolveReference(docRef, READ);
        DocumentModel docModel = readModel(doc);
        docModel.setPropertyValue(SYSGOV_IS_RECORD, makeRecord);
    }

    @Override
    public boolean isRecord(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        return doc.isRecord();
    }

    @Override
    public void setRetainUntil(DocumentRef docRef, Instant retainUntil) throws PropertyException {
        InternalDocument doc = resolveReference(docRef, READ);
        DocumentModel docModel = readModel(doc);
        docModel.setPropertyValue(SYSGOV_RETAIN_UNTIL, retainUntil);
    }

    @Override
    public Instant getRetainUntil(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        return doc.getRetainUntil();
    }

    @Override
    public void setLegalHold(DocumentRef docRef, boolean hold) {
        InternalDocument doc = resolveReference(docRef, null);
        DocumentModel docModel = readModel(doc);
        docModel.setPropertyValue(SYSGOV_HAS_LEGAL_HOLD, hold);
    }

    @Override
    public boolean hasLegalHold(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        return doc.hasLegalHold();
    }

    @Override
    public boolean isUnderRetentionOrLegalHold(DocumentRef docRef) {
        InternalDocument doc = resolveReference(docRef, READ);
        return doc.isUnderRetentionOrLegalHold();
    }

    @Override
    public boolean isTrashed(DocumentRef docRef) {
        TrashService trashService = Framework.getService(TrashService.class);
        return trashService.isTrashed(this, docRef);
    }

    @Override
    public void orderBefore(DocumentRef parentRef, String src, String dest) {
        if (src == null || src.equals(dest)) {
            return;
        }
        InternalDocument doc = resolveReference(parentRef, null);
        // TODO XXX permissions
        doc.orderBefore(src, dest);
        // send event on container passing the reordered child as parameter
        DocumentModel docModel = readModel(doc);
        notifyEvent(DocumentEventTypes.DOCUMENT_CHILDREN_ORDER_CHANGED, docModel, null, null, src, false);
    }

    @Override
    public DocumentModelRefresh refreshDocument(DocumentRef docRef, int refreshFlags, String[] schemas) {
        throw new UnsupportedOperationException();
    }

    protected String computeKeyForAtomicCreation(DocumentModel docModel) {
        String repositoryName = docModel.getRepositoryName();
        if (repositoryName == null) {
            repositoryName = getRepositoryName();
        }
        String parentId = getDocument(docModel.getParentRef()).getId();
        String name = docModel.getName();
        return repositoryName + "-" + parentId + "-" + name;
    }

    @Override
    public String replaceBlobDigest(DocumentRef docRef, String key, String newKey, String newDigest) {
        if (!isAdministrator()) {
            throw new DocumentSecurityException("Only Administrators can replace a blob digest");
        }
        InternalDocument doc = resolveReference(docRef, null);
        String oldDigest = doc.replaceBlobDigest(key, newKey, newDigest);
        if (oldDigest != null) {
            DocumentModel docModel = readModel(doc);
            Map<String, Serializable> options = new HashMap<>();
            options.put(CoreEventConstants.BLOB_DIGEST_UPDATED_OLD_KEY, key);
            options.put(CoreEventConstants.BLOB_DIGEST_UPDATED_OLD_DIGEST, oldDigest);
            options.put(CoreEventConstants.BLOB_DIGEST_UPDATED_NEW_KEY, newKey);
            options.put(CoreEventConstants.BLOB_DIGEST_UPDATED_NEW_DIGEST, newDigest);
            notifyEvent(DocumentEventTypes.BLOB_DIGEST_UPDATED, docModel, options, null, null, false);
        }
        return oldDigest;
    }

    private void notifyEvent(String eventId, DocumentModel source, Map<String, Serializable> options, String category,
            String comment, boolean inline) {
        DocumentEventContext ctx = new DocumentEventContext(this, principal, source);
        if (options != null) {
            ctx.setProperties(options);
        }
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, repositoryName);
        if (comment != null) {
            ctx.setProperty("comment", comment);
        }
        ctx.setProperty("category", category == null ? DocumentEventCategories.EVENT_DOCUMENT_CATEGORY : category);
        Event event = ctx.newEvent(eventId);
        if (inline) {
            event.setInline(true);
        }
        EventService eventService = Framework.getService(EventService.class);
        eventService.fireEvent(event);
    }

    protected void publishWriteEvents(DocumentModel source, List<DocumentEventSpec> events) {
        for (DocumentEventSpec event : events) {
            Map<String, Serializable> properties = event.properties().isEmpty() ? null : event.properties();
            notifyEvent(event.name(), source, properties, null, null, event.inline());
        }
    }

    @Override
    public List<String> getDirtyBinariesPaths(DocumentRef ref) {
        InternalDocument doc = resolveReference(ref, READ);
        return getSession().getDirtyBinariesPaths(doc.getId());
    }
}
