/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.trash;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.event.async.TrashBatchTaskProcessor.PARAM_NAME;
import static org.nuxeo.ecm.core.event.async.TrashBatchTaskProcessor.BATCH_TRASH_TASK_NAME;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

public class TrashServiceImpl extends DefaultComponent implements TrashService {

    private static final Logger log = LogManager.getLogger(TrashServiceImpl.class);

    private static final String TRASHED_QUERY = "SELECT * FROM SysContent WHERE sysver_isVersion = FALSE AND sys_isTrashed = TRUE AND sys_parentId = '%s'";

    private static final String PATH_SEPARATOR = "/";

    /**
     * Matches names of documents in the trash, created by {@link #trashDocuments(List)}.
     */
    private static final Pattern TRASHED_PATTERN = Pattern.compile("(.*)\\._\\d{13,}_\\.trashed");

    /**
     * Matches names resulting from a collision, suffixed with a time in milliseconds, created by
     * DocumentImpl.handleName. We also attempt to remove this when getting a doc out of the trash.
     */
    private static final Pattern COLLISION_PATTERN = Pattern.compile("(.*)\\.\\d{13,}");

    @Override
    public boolean isTrashed(CoreSession session, DocumentRef docRef) {
        Boolean isTrashed = (Boolean) session.getDocumentSystemProp(docRef, SYS_IS_TRASHED, Boolean.class);
        return Boolean.TRUE.equals(isTrashed);
    }

    @Override
    public void trashDocuments(List<DocumentModel> docs) {
        docs.forEach(this::doTrashDocument);
        docs.stream().map(DocumentModel::getCoreSession).findFirst().ifPresent(CoreSession::save);
    }

    private void doTrashDocument(DocumentModel doc) {
        CoreSession session = doc.getCoreSession();
        DocumentRef docRef = doc.getRef();
        if (!session.canRemoveDocument(docRef)) {
            NuxeoPrincipal pal = session.getPrincipal();
            throw new DocumentSecurityException(
                    "User %s does not have the permission to remove the document %s (%s)".formatted( //
                            pal.getId(), doc.getId(), doc.getPath()));
        } else if (session.isTrashed(docRef)) {
            log.warn("Document {} of type {} is already in the trash, nothing to do", doc.getId(), doc.getType());
        } else {
            if (doc.getParentRef() == null) {
                // handle placeless document
                session.removeDocument(doc.getRef());
            } else {
                DocumentModel docForEvent = doc;
                if (!Boolean.parseBoolean(String.valueOf(doc.getContextData(DISABLE_TRASH_RENAMING)))) {
                    String newName = mangleName(doc);
                    session.move(docRef, doc.getParentRef(), newName);
                    docForEvent = session.getDocument(docRef);
                    docForEvent.copyContextData(doc);
                }
                session.setDocumentSystemProp(docRef, SYS_IS_TRASHED, Boolean.TRUE);
                notifyEvent(session, DOCUMENT_TRASHED, docForEvent);
                if (session.hasChildren(doc.getRef())) {
                    trashDescendants(doc, Boolean.TRUE);
                }
            }
        }
    }

    @Override
    public void untrashDocuments(List<DocumentModel> docs) {
        for (DocumentModel doc : docs) {
            doUntrashDocument(doc, true);
        }
        docs.stream().map(DocumentModel::getCoreSession).findFirst().ifPresent(CoreSession::save);
    }

    private Set<DocumentRef> doUntrashDocument(DocumentModel doc, boolean processChildren) {
        CoreSession session = doc.getCoreSession();
        DocumentRef docRef = doc.getRef();

        DocumentModel docForEvent = doc;
        // move only non placeless document
        // currently we don't trash placeless document
        DocumentRef parentRef = doc.getParentRef();
        if (!Boolean.TRUE.equals(doc.getContextData(DISABLE_TRASH_RENAMING)) && parentRef != null) {
            String newName = unmangleName(doc);
            if (!newName.equals(doc.getName())) {
                session.move(docRef, parentRef, newName);
                docForEvent = session.getDocument(docRef);
                docForEvent.copyContextData(doc);
            }
        }
        session.setDocumentSystemProp(docRef, SYS_IS_TRASHED, Boolean.FALSE);
        notifyEvent(session, DOCUMENT_UNTRASHED, docForEvent);
        if (processChildren && session.hasChildren(doc.getRef())) {
            trashDescendants(doc, Boolean.FALSE);
        }

        Set<DocumentRef> docRefs = new HashSet<>();
        docRefs.add(docRef);

        // now untrash the parent if needed
        if (parentRef != null && session.isTrashed(parentRef)) {
            DocumentModel parent = session.getDocument(parentRef);
            Set<DocumentRef> ancestorDocRefs = doUntrashDocument(parent, false);
            docRefs.addAll(ancestorDocRefs);
        }
        return docRefs;
    }

    private void trashDescendants(DocumentModel model, Boolean value) {
        CoreSession session = model.getCoreSession();
        TaskService service = Framework.getService(TaskService.class);
        String nxql = "SELECT * FROM SysContent where sys_ancestorIds='%s'".formatted(model.getId());
        String user = session.getPrincipal().getId();
        service.submitTask(
                new ScrollerTask.Builder(BATCH_TRASH_TASK_NAME, nxql, session.getRepositoryName(), user)
                        .param(PARAM_NAME,
                                value)
                        .withTimestamp(null)
                        .build());
    }

    private void notifyEvent(CoreSession session, String eventId, DocumentModel doc) {
        notifyEvent(session, eventId, doc, false);
    }

    private void notifyEvent(CoreSession session, String eventId, DocumentModel doc, boolean immediate) {
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        ctx.setProperties(new HashMap<>(doc.getContextData()));
        ctx.setCategory(DocumentEventCategories.EVENT_DOCUMENT_CATEGORY);
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, session.getRepositoryName());
        Event event = ctx.newEvent(eventId);
        event.setInline(false);
        event.setImmediate(immediate);
        EventService eventService = Framework.getService(EventService.class);
        eventService.fireEvent(event);
    }

    private static class TrashInfo {

        /** Docs found ok. */
        public List<DocumentModel> docs;

        /** Refs of common tree roots of docs found ok. */
        public List<DocumentRef> rootRefs;

        /** Paths of common tree roots of docs found ok. */
        public Set<Path> rootPaths;

        /** Refs of parents of common tree roots of docs found ok. */
        public Set<DocumentRef> rootParentRefs;

        /** Number of docs not ok due to permissions. */
        public int forbidden;

        /** Number of docs not ok due to lock status. */
        public int locked;

    }

    @Override
    public boolean canPurgeOrUntrash(List<DocumentModel> docs, NuxeoPrincipal principal) {
        if (docs.isEmpty()) {
            return false;
        }
        // used to do only check on parent perm
        TrashInfo info = getInfo(docs, principal, true);
        return info.docs.size() == docs.size();
    }

    private TrashInfo getInfo(List<DocumentModel> docs, NuxeoPrincipal principal, boolean checkDeleted) {
        TrashInfo info = new TrashInfo();
        info.docs = new ArrayList<>(docs.size());
        if (docs.isEmpty()) {
            return info;
        }
        CoreSession session = docs.get(0).getCoreSession();
        for (DocumentModel doc : docs) {
            if (checkDeleted && !doc.isTrashed()) {
                info.forbidden++;
                continue;
            }
            if (doc.getParentRef() != null
                    && !session.hasPermission(doc.getParentRef(), SecurityConstants.DELETE_CHILD)) {
                info.forbidden++;
                continue;
            }
            if (!session.hasPermission(doc.getRef(), SecurityConstants.DELETE)) {
                info.forbidden++;
                continue;
            }
            if (doc.isLocked()) {
                String locker = getDocumentLocker(doc);
                if (principal == null || principal.isAdministrator() || principal.getId().equals(locker)) {
                    info.docs.add(doc);
                } else {
                    info.locked++;
                }
            } else {
                info.docs.add(doc);
            }
        }
        return info;
    }

    private static String getDocumentLocker(DocumentModel doc) {
        Lock lock = doc.getLockInfo();
        return lock == null ? null : lock.getOwner();
    }

    /**
     * Path-based comparator used to put folders before their children.
     */
    private static class PathComparator implements Comparator<DocumentModel>, Serializable {

        private static final long serialVersionUID = 1L;

        public static final PathComparator INSTANCE = new PathComparator();

        @Override
        public int compare(DocumentModel doc1, DocumentModel doc2) {
            return doc1.getPathAsString()
                       .replace(PATH_SEPARATOR, "\u0000")
                       .compareTo(doc2.getPathAsString().replace(PATH_SEPARATOR, "\u0000"));
        }

    }

    private TrashInfo getTrashInfo(List<DocumentModel> docs, NuxeoPrincipal principal, boolean checkDeleted) {
        TrashInfo info = getInfo(docs, principal, checkDeleted);
        // Keep only common tree roots (see NXP-1411)
        // This is not strictly necessary with Nuxeo Core >= 1.3.2
        info.docs.sort(PathComparator.INSTANCE);
        info.rootPaths = new HashSet<>();
        info.rootRefs = new LinkedList<>();
        info.rootParentRefs = new HashSet<>();
        Path previousPath = null;
        for (DocumentModel doc : info.docs) {
            if (previousPath == null || !previousPath.isPrefixOf(doc.getPath())) {
                Path path = doc.getPath();
                info.rootPaths.add(path);
                info.rootRefs.add(doc.getRef());
                if (doc.getParentRef() != null) {
                    info.rootParentRefs.add(doc.getParentRef());
                }
                previousPath = path;
            }
        }
        return info;
    }

    @Override
    public DocumentModel getAboveDocument(DocumentModel doc, NuxeoPrincipal principal) {
        TrashInfo info = getTrashInfo(List.of(doc), principal, false);
        return getAboveDocument(doc, info.rootPaths);
    }

    private DocumentModel getAboveDocument(DocumentModel doc, Set<Path> rootPaths) {
        CoreSession session = doc.getCoreSession();
        while (underOneOf(doc.getPath(), rootPaths)) {
            doc = session.getParentDocument(doc.getRef());
            if (doc == null) {
                // handle placeless document
                break;
            }
        }
        return doc;
    }

    private static boolean underOneOf(Path testedPath, Set<Path> paths) {
        for (Path path : paths) {
            if (path != null && path.isPrefixOf(testedPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void purgeDocuments(CoreSession session, List<DocumentRef> docRefs) {
        if (docRefs.isEmpty()) {
            return;
        }
        session.removeDocuments(docRefs.toArray(new DocumentRef[docRefs.size()]));
        session.save();
    }

    @Override
    public void purgeDocumentsUnder(DocumentModel parent) {
        if (parent == null || !parent.isFolder()) {
            throw new UnsupportedOperationException("Empty trash can only be performed on a SysFolderish document");
        }
        CoreSession session = parent.getCoreSession();
        if (!session.hasPermission(parent.getRef(), SecurityConstants.DELETE_CHILD)) {
            return;
        }
        try (IterableQueryResult result = session.queryAndFetch(String.format(TRASHED_QUERY, parent.getId()),
                NXQL.NXQL)) {
            NuxeoPrincipal principal = session.getPrincipal();
            StreamSupport.stream(result.spliterator(), false)
                         .map(map -> map.get(SYS_ID).toString())
                         .map(IdRef::new)
                         // check user has permission to remove document
                         .filter(ref -> session.hasPermission(ref, SecurityConstants.DELETE))
                         // check user has permission to remove a locked document
                         .filter(ref -> {
                             if (principal == null || principal.isAdministrator()) {
                                 // administrator can remove anything
                                 return true;
                             } else {
                                 // only lock owner can remove locked document
                                 DocumentModel doc = session.getDocument(ref);
                                 return !doc.isLocked() || principal.getId().equals(getDocumentLocker(doc));
                             }
                         })
                         .forEach(session::removeDocument);
        }
        session.save();
    }

    @Override
    public boolean isMangledName(String docName) {
        return TRASHED_PATTERN.matcher(docName).matches();
    }

    @Override
    public String mangleName(DocumentModel doc) {
        return doc.getName() + "._" + System.currentTimeMillis() + "_.trashed";
    }

    @Override
    public String unmangleName(DocumentModel doc) {
        return unmangleName(doc.getCoreSession(), doc.getParentRef(), doc.getName());
    }

    @Override
    public String unmangleName(CoreSession session, DocumentRef parentRef, String docName) {
        if (session == null) {
            return docName;
        }
        // remove trashed part
        String name = getFirstGroup(TRASHED_PATTERN, docName);
        // remove collision avoidance
        String orig = getFirstGroup(COLLISION_PATTERN, name);
        if (!docName.equals(name)) {
            // check if orig (the supposed name without collision) is available
            String parentPath = session.getDocument(parentRef).getPathAsString();
            if (parentPath.equals(PATH_SEPARATOR)) {
                parentPath = ""; // root
            }
            String newPath = parentPath + PATH_SEPARATOR + orig;
            if (!session.exists(new PathRef(newPath))) {
                name = orig;
            }
        }
        return name;
    }

    private String getFirstGroup(Pattern pattern, String name) {
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches() && StringUtils.isNotEmpty(matcher.group(1))) {
            return matcher.group(1);
        }

        return name;
    }

}
