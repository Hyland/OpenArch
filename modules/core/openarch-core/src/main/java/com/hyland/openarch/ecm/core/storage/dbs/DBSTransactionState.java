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
package com.hyland.openarch.ecm.core.storage.dbs;

import static java.lang.Boolean.TRUE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.FULLTEXT_EXTRACTOR_TASK_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_GENERATION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_JOB_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_POS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_USER_GENERATION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_CHUNKS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY_ON_PREM;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.DIRTY_BLOB_XPATHS;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.DIRTY_XPATHS;
import static com.hyland.openarch.ecm.core.api.event.CoreEventConstants.DISABLE_AUTO_INDEXING;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static com.hyland.openarch.ecm.core.storage.dbs.DBSDocument.INITIAL_GENERATION;
import static com.hyland.openarch.ecm.core.storage.dbs.DBSDocument.INITIAL_USER_GENERATION;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.ConcurrentUpdateException;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentContext;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentService;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PartialList;
import com.hyland.openarch.ecm.core.api.ScrollResult;
import com.hyland.openarch.ecm.core.api.ValuesVisitor;
import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.api.model.DeltaLong;
import com.hyland.openarch.ecm.core.api.repository.FulltextConfiguration;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationException;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationReport;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationService;
import com.hyland.openarch.ecm.core.event.raiser.DocumentEventRaiser;
import com.hyland.openarch.ecm.core.event.raiser.DocumentEventRaiserContext;
import com.hyland.openarch.ecm.core.event.raiser.DocumentEventRaiserService;
import com.hyland.openarch.ecm.core.event.raiser.DocumentEventSenderService;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByClause;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.UnknownDocumentType;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.security.SecurityPolicyService;
import com.hyland.openarch.ecm.core.security.SecurityService;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.ListDiff;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.StateHelper;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession.ChildrenSelector;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession.ChildrenSelectorTestContext;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.DocumentTask;

/**
 * Transactional state for a session.
 * <p>
 * Until {@code save()} is called, data lives in the transient map.
 * <p>
 * Upon save, data is written to the repository connection.
 * <p>
 * If the connection is transactional, usual behavior occurs.
 * <p>
 * If the connection is not transactional, then at this means that other sessions can read uncommitted data. To allow
 * rollback, save data is also kept in an undo log in order for rollback to be possible. On commit, the undo log is
 * forgotten. On rollback, the undo log is replayed.
 *
 * @since 5.9.4
 */
public class DBSTransactionState implements LockManager, AutoCloseable, Synchronization, StateAccessor {

    private static final Logger log = LogManager.getLogger(DBSTransactionState.class);

    private static final String KEY_UNDOLOG_CREATE = "__UNDOLOG_CREATE__\0\0";

    protected final DBSRepository repository;

    protected final DBSConnection connection;

    protected final DBSSession session;

    /** Retrieved and created document state. */
    protected Map<String, DBSDocumentState> transientStates = new HashMap<>();

    /** Ids of documents created but not yet saved. */
    protected Set<String> transientCreated = new LinkedHashSet<>();

    /**
     * Document ids modified as "user changes", which means that a change token should be checked.
     *
     * @since 9.2
     */
    protected final Set<Object> userChangeIds = new HashSet<>();

    /**
     * Map of doc id to condition predicates for updates to the document with that id.
     */
    private final Map<String, Predicate> updateConditionPredicates = new HashMap<>();

    /**
     * Undo log.
     * <p>
     * A map of document ids to null or State. The value is null when the document has to be deleted when applying the
     * undo log. Otherwise the value is a State. If the State contains the key {@link #KEY_UNDOLOG_CREATE} then the
     * state must be re-created completely when applying the undo log, otherwise just applied as an update.
     * <p>
     * Null when there is no active transaction.
     */
    protected Map<String, State> undoLog;

    private final SecurityPolicyService securityPolicyService;

    protected static final ThreadLocal<List<DocumentTask>> TASKS = new ThreadLocal<>();

    public DBSTransactionState(DBSRepository repository, DBSSession session) {
        this.repository = repository;
        this.connection = repository.getConnection();
        this.session = session;
        securityPolicyService = Framework.getService(SecurityService.class)
                .getSecurityPolicyService(repository.getName());
    }

    public SchemaManager getSchemaManager() {
        return connection.getSchemaManager();
    }

    /**
     * {@inheritDoc}
     *
     * @since 11.1
     */
    @Override
    public void close() {
        connection.close();
    }

    /** @since 11.1 */
    public String getRootId() {
        return connection.getRootId();
    }

    /**
     * New transient state for something just read from the repository.
     */
    protected DBSDocumentState newTransientState(State state) {
        if (state == null) {
            return null;
        }
        String id = (String) state.get(SYS_ID);
        if (transientStates.containsKey(id)) {
            throw new IllegalStateException("Already transient: " + id);
        }
        DBSDocumentState docState = new DBSDocumentState(state); // copy
        transientStates.put(id, docState);
        return docState;
    }

    @Override
    public DBSDocumentState getStateForUpdate(String id) {
        // check transient state
        DBSDocumentState docState = transientStates.get(id);
        if (docState != null) {
            return docState;
        }
        // fetch from repository
        State state = connection.readState(id);
        return newTransientState(state);
    }

    @Override
    public State getStateForRead(String id) {
        // check transient state
        DBSDocumentState docState = transientStates.get(id);
        if (docState != null) {
            return docState.getState();
        }
        // fetch from repository
        return connection.readState(id);
    }

    @Override
    public State readPartialStateNoCache(String id, Collection<String> keys) {
        return connection.readPartialState(id, keys);
    }

    @Override
    public void updateStateNoCache(String id, StateDiff diff) {
        connection.updateState(id, diff, null);
    }

    /**
     * Returns states and marks them transient, because they're about to be returned to user code (where they may be
     * modified).
     */
    public List<DBSDocumentState> getStatesForUpdate(Collection<String> ids) {
        // check which ones we have to fetch from repository
        List<String> idsToFetch = new LinkedList<>();
        for (String id : ids) {
            // check transient state
            DBSDocumentState docState = transientStates.get(id);
            if (docState != null) {
                continue;
            }
            // will have to fetch it
            idsToFetch.add(id);
        }
        if (!idsToFetch.isEmpty()) {
            List<State> states = connection.readStates(idsToFetch);
            for (State state : states) {
                newTransientState(state);
            }
        }
        // everything now fetched in transient
        List<DBSDocumentState> docStates = new ArrayList<>(ids.size());
        for (String id : ids) {
            DBSDocumentState docState = transientStates.get(id);
            if (docState == null) {
                if (log.isTraceEnabled()) {
                    log.trace("Cannot fetch document with id: {}", id, new Throwable("debug stack trace"));
                }
                continue;
            }
            docStates.add(docState);
        }
        return docStates;
    }

    // XXX TODO for update or for read?
    public DBSDocumentState getChildState(String parentId, String name) {
        // check transient state
        for (DBSDocumentState docState : transientStates.values()) {
            if (!parentId.equals(docState.getParentId())) {
                continue;
            }
            if (!name.equals(docState.getName())) {
                continue;
            }
            return docState;
        }
        // fetch from repository
        State state = connection.readChildState(parentId, name, Collections.emptySet());
        if (state == null) {
            return null;
        }
        String id = (String) state.get(SYS_ID);
        if (transientStates.containsKey(id)) {
            // found transient, even though we already checked
            // that means that in-memory it's not a child, but in-database it's a child (was moved)
            // -> ignore the database state
            return null;
        }
        return newTransientState(state);
    }

    public boolean hasChild(String parentId, String name) {
        // check transient state
        for (DBSDocumentState docState : transientStates.values()) {
            if (!parentId.equals(docState.getParentId())) {
                continue;
            }
            if (!name.equals(docState.getName())) {
                continue;
            }
            return true;
        }
        // check repository
        return connection.hasChild(parentId, name, Collections.emptySet());
    }

    public List<DBSDocumentState> getChildrenStates(String parentId) {
        return getChildrenStates(parentId, ChildrenSelector.ALL);
    }

    public List<DBSDocumentState> getChildrenStates(String parentId, ChildrenSelector childrenSelector) {
        return getChildren(parentId, childrenSelector, docState -> docState);
    }

    private <T> List<T> getChildren(String parentId, ChildrenSelector childrenSelector,
                                    Function<DBSDocumentState, T> converter) {
        List<T> children = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ChildrenSelectorTestContext context = new ChildrenSelectorTestContext(getSchemaManager());
        // check transient state
        for (DBSDocumentState docState : transientStates.values()) {
            if (!parentId.equals(docState.getParentId())) {
                continue;
            }
            if (childrenSelector.test(docState.state, context)) {
                children.add(converter.apply(docState));
                seen.add(docState.getId());
            }
        }
        List<State> states = connection.queryKeyValue(SYS_PARENT_ID, parentId, seen);
        for (State state : states) {
            String id = (String) state.get(SYS_ID);
            if (transientStates.containsKey(id)) {
                // found transient, even though we passed an exclusion list for known children
                // that means that in-memory it's not a child, but in-database it's a child (was moved)
                // -> ignore the database state
                continue;
            }
            if (childrenSelector.test(state, context)) {
                DBSDocumentState docState = newTransientState(state);
                children.add(converter.apply(docState));
            }
        }
        return children;
    }

    public List<String> getChildrenIds(String parentId) {
        return getChildrenIds(parentId, ChildrenSelector.ALL);
    }

    public List<String> getChildrenIds(String parentId, ChildrenSelector childrenSelector) {
        return getChildren(parentId, childrenSelector, DBSDocumentState::getId);
    }

    public boolean hasChildren(String parentId) {
        // check transient state
        for (DBSDocumentState docState : transientStates.values()) {
            if (!parentId.equals(docState.getParentId())) {
                continue;
            }
            return true;
        }
        // check repository
        return connection.queryKeyValuePresence(SYS_PARENT_ID, parentId, Collections.emptySet());
    }

    public DBSDocumentState newState() {
        return new DBSDocumentState();
    }

    public void create(DBSDocumentState docState) {
        // sys_id
        String id = docState.getId();
        if (id == null) {
            // (may be non-null for import)
            id = connection.generateNewId();
            docState.put(SYS_ID, id);
        }
        if (transientStates.containsKey(id)) {
            throw new ConcurrentUpdateException(id);
        }
        transientStates.put(id, docState);
        transientCreated.add(id);

        // properties managed by system

        // sys_primaryType
        String primaryType = docState.getPrimaryType();
        if (primaryType == null) {
            throw new RepositoryException("Missing " + SYS_PRIMARY_TYPE);
        }

        // sys_name
        String name = docState.getName();
        if (name == null) {
            throw new RepositoryException("Missing " + SYS_NAME);
        }
        name = session.normalize(name);
        docState.put(SYS_NAME, name);

        // sys_ancestorIds
        String parentId = docState.getParentId();
        docState.put(SYS_ANCESTOR_IDS, getAncestorIds(parentId));

        // sys_pos
        if (parentId != null) {
            Long pos = session.getNextPos(parentId);
            docState.put(SYS_POS, pos);
        }

        // sys_isFolderish
        DocumentType type = getSchemaManager().getDocumentType(primaryType);
        if (type != null && type.isFolder()) {
            // note that we don't check sys_mixinTypes to determine folderish
            docState.put(SYS_IS_FOLDERISH, TRUE);
        }

        // sys_gen and sys_ugen
        docState.put(SYS_GENERATION, INITIAL_GENERATION);
        docState.put(SYS_USER_GENERATION, INITIAL_USER_GENERATION);

        // sys_racl
        securityPolicyService.updateDenormalizedProperties(id, this);
    }

    /** Gets ancestors including id itself. */
    protected List<Object> getAncestorIds(String id) {
        if (id == null) {
            return null;
        }
        State state = getStateForRead(id);
        if (state == null) {
            throw new RuntimeException("No such id: " + id);
        }
        @SuppressWarnings("unchecked")
        List<Object> ancestors = (List<Object>) state.get(SYS_ANCESTOR_IDS);
        if (ancestors == null) {
            return List.of(id);
        } else {
            List<Object> newAncestors = new ArrayList<>(ancestors.size() + 1);
            newAncestors.addAll(ancestors);
            newAncestors.add(id);
            return newAncestors;
        }
    }

    /**
     * Copies the document into a newly-created object.
     * <p>
     * The copy is automatically saved.
     */
    public DBSDocumentState copy(String id) {
        DBSDocumentState copyState = new DBSDocumentState(getStateForRead(id));
        String copyId = connection.generateNewId();
        copyState.put(SYS_ID, copyId);
        // other fields updated by the caller
        transientStates.put(copyId, copyState);
        transientCreated.add(copyId);
        return copyState;
    }

    /**
     * Updates ancestors recursively after a move.
     * <p>
     * Recursing from given doc, replace the first ndel ancestors with those passed.
     * <p>
     * Doesn't check transient (assumes save is done). The modifications are automatically saved.
     */
    public void updateAncestors(String id, int ndel, List<Object> ancestorIds) {
        Set<String> ids = new HashSet<>();
        ids.add(id);
        try (Stream<State> states = getDescendants(id, Collections.emptySet(), 0)) {
            states.forEach(state -> ids.add((String) state.get(SYS_ID)));
        }
        // we collect all ids first to avoid reentrancy to the repository
        for (String cid : ids) {
            // XXX TODO oneShot update, don't pollute transient space
            DBSDocumentState docState = getStateForUpdate(cid);
            @SuppressWarnings("unchecked")
            List<Object> ancestors = (List<Object>) docState.get(SYS_ANCESTOR_IDS);
            List<Object> newAncestors;
            if (ancestors == null) {
                newAncestors = ancestorIds;
            } else {
                newAncestors = new ArrayList<>(ancestorIds.size() + ancestors.size() - ndel);
                newAncestors.addAll(ancestorIds);
                newAncestors.addAll(ancestors.subList(ndel, ancestors.size()));
            }
            docState.put(SYS_ANCESTOR_IDS, newAncestors);
        }
    }

    /**
     * Updates the denormalized properties on a document recursively.
     */
    public void updateDenormalizedPropertiesWithDescendants(String id, RepositoryPrincipal principal) {
        // versions too XXX TODO
        save(principal); // flush everything to the database as getting descendants needs queries
        securityPolicyService.updateDenormalizedPropertiesWithDescendants(id, this, session);
    }

    /**
     * Updates the denormalized state on some documents (not recursively), bypassing caches.
     */
    public void updateDenormalizedPropertiesNoCache(Collection<String> ids) {
        securityPolicyService.updateDenormalizedPropertiesNoCache(ids, this);
    }

    public Stream<State> getDescendants(String id, Set<String> keys, int limit) {
        return connection.getDescendants(id, keys, limit);
    }

    public List<DBSDocumentState> getKeyValuedStates(String key, Object value) {
        List<DBSDocumentState> docStates = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        // check transient state
        for (DBSDocumentState docState : transientStates.values()) {
            if (!value.equals(docState.get(key))) {
                continue;
            }
            docStates.add(docState);
            seen.add(docState.getId());
        }
        // fetch from repository
        List<State> states = connection.queryKeyValue(key, value, seen);
        for (State state : states) {
            docStates.add(newTransientState(state));
        }
        return docStates;
    }

    public List<DBSDocumentState> getKeyValuedStates(String key1, Object value1, String key2, Object value2) {
        List<DBSDocumentState> docStates = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        // check transient state
        for (DBSDocumentState docState : transientStates.values()) {
            seen.add(docState.getId());
            if (!(value1.equals(docState.get(key1)) && value2.equals(docState.get(key2)))) {
                continue;
            }
            docStates.add(docState);
        }
        // fetch from repository
        List<State> states = connection.queryKeyValue(key1, value1, key2, value2, seen);
        for (State state : states) {
            docStates.add(newTransientState(state));
        }
        return docStates;
    }

    /** @since 11.1 */
    public PartialList<Map<String, Object>> queryAndFetch(DBSExpressionEvaluator evaluator,
                                                          OrderByClause orderByClause, boolean distinctDocuments, int limit, int offset, int countUpTo) {
        return connection.queryAndFetch(evaluator, orderByClause, distinctDocuments, limit, offset, countUpTo);
    }

    /** @since 11.1 */
    public ScrollResult<String> scroll(DBSExpressionEvaluator evaluator, int batchSize, int keepAliveSeconds) {
        return connection.scroll(evaluator, batchSize, keepAliveSeconds);
    }

    /** @since 11.1 */
    public ScrollResult<String> scroll(String scrollId) {
        return connection.scroll(scrollId);
    }

    /** @since 11.1 */
    @Override
    public Lock getLock(String id) {
        return connection.getLock(id);
    }

    /** @since 11.1 */
    @Override
    public Lock setLock(String id, Lock lock) {
        return connection.setLock(id, lock);
    }

    /** @since 11.1 */
    @Override
    public Lock removeLock(String id, String owner) {
        return connection.removeLock(id, owner);
    }

    /**
     * Removes a list of documents.
     * <p>
     * Called after a {@link #save} has been done.
     */
    public void removeStates(Set<String> ids) {
        if (undoLog != null) {
            for (String id : ids) {
                if (undoLog.containsKey(id)) {
                    // there's already a create or an update in the undo log
                    State oldUndo = undoLog.get(id);
                    if (oldUndo == null) {
                        // create + delete -> forget
                        undoLog.remove(id);
                    } else {
                        // update + delete -> original old state to re-create
                        oldUndo.put(KEY_UNDOLOG_CREATE, TRUE);
                    }
                } else {
                    // just delete -> store old state to re-create
                    State oldState = StateHelper.deepCopy(getStateForRead(id));
                    oldState.put(KEY_UNDOLOG_CREATE, TRUE);
                    undoLog.put(id, oldState);
                }
            }
        }
        for (String id : ids) {
            transientStates.remove(id);
        }
        connection.deleteStates(ids);
    }

    public void markUserChange(String id) {
        userChangeIds.add(id);
    }

    public void setUpdateConditionPredicate(String id, Predicate predicate) {
        updateConditionPredicates.put(id, predicate);
    }

    /**
     * Checks if there is anything to save.
     *
     * @return a String describing an unsaved document, or {@code null} if there is nothing to save
     */
    public String checkIfDirty() {
        if (!transientCreated.isEmpty()) {
            String id = transientCreated.iterator().next();
            DBSDocumentState docState = transientStates.get(id);
            return "created: " + docState;
        }
        for (DBSDocumentState docState : transientStates.values()) {
            // TODO add new StateHelper methods to just check if diff is not empty
            StateDiff diff = docState.getStateChange();
            if (diff != null) {
                return "modified: id=" + docState.getId() + " diff=" + diff;
            }
        }
        return null;
    }

    /**
     * Writes transient state to database.
     * <p>
     * An undo log is kept in order to rollback the transaction later if needed.
     */
    public void save(RepositoryPrincipal principal) {
        enrichUpdatedDocuments(principal);
        updateDenormalizedState();
        validation();

        List<DocumentTask> tasks = new ArrayList<>();
        if (!repository.isFulltextDisabled()) {
            // TODO getting fulltext already does a getStateChange
            tasks = getFulltextTasks();
        }
        if (!tasks.isEmpty()) {
            if (TASKS.get() == null) {
                TASKS.set(tasks);
                TransactionHelper.registerSynchronization(this);
            } else {
                TASKS.get().addAll(tasks);
            }
        }

        Map<String, Map<String, Serializable>> contextForDocsWithDirtyProps = new HashMap<>();
        Map<String, Map<String, Serializable>> contextForDocsWithDirtyBlobs = new HashMap<>();
        List<State> statesToCreate = new ArrayList<>();
        for (String id : transientCreated) { // ordered
            DBSDocumentState docState = transientStates.get(id);
            computeEventContext(id, docState, contextForDocsWithDirtyProps, contextForDocsWithDirtyBlobs);
            docState.setNotDirty();
            if (undoLog != null) {
                undoLog.put(id, null); // marker to denote create
            }
            State state = docState.getState();
            state.put(SYS_GENERATION, INITIAL_GENERATION);
            statesToCreate.add(state);
        }
        if (!statesToCreate.isEmpty()) {
            connection.createStates(statesToCreate);
            sendDocumentEvents(DOCUMENT_CREATED, transientCreated, principal, contextForDocsWithDirtyProps);
        }

        Set<String> updatedIds = new HashSet<>();
        Map<String, Object> diffsById = new HashMap<>();
        Map<String, Object> originalStatesById = new HashMap<>();
        Map<String, Object> currentStatesById = new HashMap<>();
        for (DBSDocumentState docState : transientStates.values()) {
            String id = docState.getId();
            if (transientCreated.contains(id)) {
                continue; // already done
            }
            StateDiff diff = docState.getStateChange();
            if (diff != null) {
                updatedIds.add(id);
                diffsById.put(id, diff);
                originalStatesById.put(id, docState.getOriginalState());
                currentStatesById.put(id, docState.getState());
                computeEventContext(id, docState, contextForDocsWithDirtyProps, contextForDocsWithDirtyBlobs);
                try {
                    if (undoLog != null && !undoLog.containsKey(id)) {
                        undoLog.put(id, StateHelper.deepCopy(docState.getOriginalState()));
                        // else there's already a create or an update in the undo log so original info is enough
                    }
                    ConditionalUpdates conditionalUpdates = new ConditionalUpdates();
                    // increment generation
                    Long base = (Long) docState.get(SYS_GENERATION);
                    docState.put(SYS_GENERATION, DeltaLong.valueOf(base, 1));
                    diff.put(SYS_GENERATION, DeltaLong.valueOf(base, 1));
                    // update user generation if applicable
                    if (userChangeIds.contains(docState.getId())) {
                        addConditionalUpdateForUserGeneration(conditionalUpdates, docState);
                    }
                    // user-defined condition predicate
                    addUpdateConditionPredicate(conditionalUpdates, id);

                    if (conditionalUpdates.isEmpty()) {
                        conditionalUpdates = null;
                    }
                    connection.updateState(id, diff, conditionalUpdates);
                } finally {
                    docState.setNotDirty();
                }
            }
        }
        transientCreated.clear();
        userChangeIds.clear();
        updateConditionPredicates.clear();
        // dispatch the document events through the registered DocumentEventRaisers
        raiseUpdatedDocumentEvents(updatedIds, contextForDocsWithDirtyProps, contextForDocsWithDirtyBlobs, principal,
                diffsById, originalStatesById, currentStatesById);
    }

    private void computeEventContext(String id, DBSDocumentState docState,
                                     Map<String, Map<String, Serializable>> contextForDocsWithDirtyProps,
                                     Map<String, Map<String, Serializable>> contextForDocsWithDirtyBlobs) {
        List<String> dirtyPaths = getDirtyPaths(docState);
        Map<String, Serializable> propsContext = new HashMap<>();
        if (!dirtyPaths.isEmpty()) {
            List<String> condensedPaths = condenseDirtyPaths(dirtyPaths);
            propsContext.put(DIRTY_XPATHS, (Serializable) condensedPaths);
        }
        if (isOnPremChunksCleanup(docState)) {
            propsContext.put(DISABLE_AUTO_INDEXING, true);
        }
        if (!propsContext.isEmpty()) {
            contextForDocsWithDirtyProps.put(id, propsContext);
        }
        List<String> dirtyBlobPaths = getDirtyBlobsPath(dirtyPaths);
        if (!dirtyBlobPaths.isEmpty()) {
            contextForDocsWithDirtyBlobs.put(id, Map.of(DIRTY_BLOB_XPATHS, (Serializable) dirtyBlobPaths));
        }
    }

    /**
     * Whether this update only clears {@code sysemb_chunks} on an {@code onPrem} SysEmbeddings document, as done after
     * a confirmed EV Store write. Such an update must not re-drive embeddings indexing: the vectors are unchanged and
     * onPrem indexing never reads the chunks blob. An update that also touches {@code sysemb_embeddings} still indexes.
     */
    private static boolean isOnPremChunksCleanup(DBSDocumentState docState) {
        if (!SYSEMB_RESIDENCY_ON_PREM.equals(docState.get(SYSEMB_RESIDENCY))) {
            return false;
        }
        StateDiff diff = docState.getStateChange();
        return diff != null && diff.containsKey(SYSEMB_CHUNKS)
                && StateHelper.equalsLoose(diff.get(SYSEMB_CHUNKS), null)
                && !diff.containsKey(SYSEMB_EMBEDDINGS);
    }

    protected void addConditionalUpdateForUserGeneration(ConditionalUpdates conditionalUpdates,
            DBSDocumentState docState) {
        // condition and update
        Long oldGen = (Long) docState.getOriginalState().get(SYS_USER_GENERATION);
        Long newGen = incrementUserGeneration(oldGen);
        conditionalUpdates.setCondition(SYS_USER_GENERATION, oldGen);
        conditionalUpdates.addUpdate(SYS_USER_GENERATION, newGen);
        // callback
        Runnable callback = () -> {
            // also store the new generation in the state (without marking dirty)
            Long og = (Long) conditionalUpdates.getUpdates().get(SYS_USER_GENERATION);
            docState.getState().put(SYS_USER_GENERATION, og);
            // new conditional update
            Long ng = incrementUserGeneration(og);
            conditionalUpdates.setCondition(SYS_USER_GENERATION, og);
            conditionalUpdates.addUpdate(SYS_USER_GENERATION, ng);
        };
        conditionalUpdates.addCallback(callback);
    }

    protected static Long incrementUserGeneration(Long userGeneration) {
        if (userGeneration == null) {
            // for old documents created before this was defined and set at creation time
            userGeneration = INITIAL_USER_GENERATION;
        }
        return Long.valueOf(userGeneration.longValue() + 1);
    }

    protected void addUpdateConditionPredicate(ConditionalUpdates conditionalUpdates, String id) {
        Predicate predicate = updateConditionPredicates.get(id);
        if (predicate == null) {
            return;
        }
        conditionalUpdates.setConditionPredicate(predicate);
    }

    @Override
    public void beforeCompletion() {
        // Nothing to do
    }

    @Override
    public void afterCompletion(int status) {
        var listTasks = TASKS.get();
        TASKS.remove();
        if (!listTasks.isEmpty() && status == Status.STATUS_COMMITTED) {
            scheduleTasks(listTasks);
        }
    }

    /**
     * Logic to get the conditions to use to match and update a change token.
     * <p>
     * This may be called several times for a single DBS document update, because the low-level storage may need several
     * database updates for a single high-level update in some cases.
     *
     * @since 9.1
     */
    public static class ConditionalUpdates {

        private Predicate conditionPredicate;

        private String conditionKey;

        private Object conditionValue;

        protected final Map<String, Object> updates = new HashMap<>();

        protected final List<Runnable> callbacks = new ArrayList<>();

        public void setConditionPredicate(Predicate predicate) {
            conditionPredicate = predicate;
        }

        public void setCondition(String key, Object value) {
            conditionKey = key;
            conditionValue = value;
        }

        public void addUpdate(String key, Object value) {
            updates.put(key, value);
        }

        /**
         * Adds a callback.
         * <p>
         * The callback is useful to 1. finish updating internal in-memory document state after an update is done, and
         * 2. prepare a new condition/update if the conditional update is used again.
         */
        public void addCallback(Runnable callback) {
            callbacks.add(callback);
        }

        public boolean isEmpty() {
            return conditionPredicate == null && conditionKey == null;
        }

        public Predicate getConditionPredicate() {
            return conditionPredicate;
        }

        public String getConditionKey() {
            return conditionKey;
        }

        public Object getConditionValue() {
            return conditionValue;
        }

        /**
         * Gets the updates to make to write the updated change token.
         */
        public Map<String, Object> getUpdates() {
            return updates;
        }

        /**
         * To be called by the processor after each update.
         */
        public void finish() {
            callbacks.forEach(Runnable::run);
        }
    }

    protected void applyUndoLog() {
        Set<String> deletes = new HashSet<>();
        for (Entry<String, State> es : undoLog.entrySet()) {
            String id = es.getKey();
            State state = es.getValue();
            if (state == null) {
                deletes.add(id);
            } else {
                boolean recreate = state.remove(KEY_UNDOLOG_CREATE) != null;
                if (recreate) {
                    connection.createState(state);
                } else {
                    // undo update
                    State currentState = connection.readState(id);
                    if (currentState != null) {
                        StateDiff diff = StateHelper.diff(currentState, state);
                        if (!diff.isEmpty()) {
                            connection.updateState(id, diff, null);
                        }
                    }
                    // else we expected to read a current state but it was concurrently deleted...
                    // in that case leave it deleted
                }
            }
        }
        if (!deletes.isEmpty()) {
            connection.deleteStates(deletes);
        }
    }

    /**
     * Runs the document enrichers (including the system-field stamping) on every genuinely modified,
     * non-version document. Created documents are enriched on the create path instead.
     */
    private void enrichUpdatedDocuments(RepositoryPrincipal principal) {
        if (principal == null) {
            return;
        }
        Instant now = Instant.now();
        // modification
        for (DBSDocumentState docState : transientStates.values()) {
            if (!docState.isDirty()) {
                continue;
            }
            if (transientCreated.contains(docState.getId())) {
                // already done
                continue;
            }
            if (isVersion(docState)) {
                continue;
            }
            StateDiff diff = docState.getStateChange();
            if (diff == null) {
                // no real change
                continue;
            }
            enrichOnUpdate(docState, principal, now);
        }
    }

    private void enrichOnUpdate(DBSDocumentState docState, RepositoryPrincipal principal, Instant now) {
        DocumentEnrichmentService service = Framework.getService(DocumentEnrichmentService.class);
        if (service != null) {
            var context = new DocumentEnrichmentContext(principal, now, session.getDocument(docState, false, true));
            service.enrichOnUpdate(context);
        }
    }

    private boolean isVersion(DBSDocumentState docState) {
        return TRUE.equals(docState.get(SYSVER_IS_VERSION));
    }

    protected void updateDenormalizedState() {
        BlobKeysFinder blobKeysFinder = new BlobKeysFinder(connection.getBlobKeysPaths());
        for (DBSDocumentState docState : transientStates.values()) {
            computeBlobKeys(docState, blobKeysFinder);
            // denormalization of sys_isFolderish done in DBSDocument
        }
    }

    protected void computeBlobKeys(DBSDocumentState docState, BlobKeysFinder blobKeysFinder) {
        Set<String> blobKeysSet = blobKeysFinder.findBlobKeys(docState.getState());
        List<Object> blobKeys;
        if (blobKeysSet.isEmpty()) {
            blobKeys = null;
        } else {
            blobKeys = new ArrayList<>(blobKeysSet);
            blobKeys.sort(null);
        }
        docState.put(SYS_BLOB_KEYS, blobKeys);
    }

    /**
     * Iterates on a {@link State} to find the blob keys.
     */
    protected static class BlobKeysFinder {

        protected final List<List<String>> blobKeysPaths;

        protected final Set<String> blobKeys = new HashSet<>();

        public BlobKeysFinder(List<List<String>> blobKeysPaths) {
            this.blobKeysPaths = blobKeysPaths;
        }

        public Set<String> findBlobKeys(State state) {
            blobKeys.clear();
            blobKeysPaths.forEach(path -> findBlobKeys(state, path, 0));
            return blobKeys;
        }

        protected void findBlobKeys(State state, List<String> path, int start) {
            int size = path.size();
            for (int i = start; i < size; i++) {
                Object value = state.get(path.get(i));
                if (value == null) {
                    // fast path when the property doesn't exist
                    return;
                }
                if (value instanceof State vState) {
                    state = vState;
                    continue; // process next path element
                }
                if (i == size - 1) {
                    // end of path
                    if (value instanceof String vStr) {
                        blobKeys.add(vStr);
                    } else if (value instanceof List list) {
                        // list of naked blob keys (no current use case)
                        for (Object v : list) {
                            if (v instanceof String str) {
                                blobKeys.add(str);
                            }
                        }
                    }
                } else if (value instanceof List) {
                    // process all complex list elements
                    for (Object v : (List<?>) value) {
                        if (v instanceof State vState) {
                            findBlobKeys(vState, path, i + 1);
                        }
                    }
                }
                return;
            }
        }
    }

    private static final Set<String> SET_SYS_READ_ACL_SYS_MODIFIED = Set.of(SYS_READ_ACL, SYS_MODIFIED);

    private void validation() {
        DocumentValidationService validationService = Framework.getService(DocumentValidationService.class);
        if (validationService == null) {
            return;
        }
        List<ValidationViolation> violations = new ArrayList<>();
        for (DBSDocumentState docState : transientStates.values()) {
            if (isVersion(docState)) {
                // skip versions until the need arises
                continue;
            }
            StateDiff diff = docState.getStateChange();
            if (diff == null) {
                // not dirty
                continue;
            }
            if (diff.keySet().equals(SET_SYS_READ_ACL_SYS_MODIFIED)) {
                // all that changed is sys_racl and sys_modified, don't do validation
                continue;
            }
            ValuesVisitor visitor = validationService.getValidationVisitor(violations);
            DBSDocument doc = session.getDocument(docState, false, true);
            if (doc.getType() instanceof UnknownDocumentType) {
                // old doc, don't validate
                continue;
            }
            doc.visitFields(visitor);
        }
        if (!violations.isEmpty()) {
            // do not leave the session in an un-saveable state
            clearSavedState();
            throw new DocumentValidationException(new DocumentValidationReport(violations));
        }
    }

    /**
     * Called when created in a transaction.
     *
     * @since 7.4
     */
    public void begin() {
        if (!repository.supportsTransactions()) {
            if (undoLog != null) {
                throw new RepositoryException("Transaction already started");
            }
            undoLog = new HashMap<>();
        }
        connection.begin();
    }

    /**
     * Checks that commit can proceed.
     */
    public void end() {
        if (TransactionHelper.isTransactionActive()) {
            String err = checkIfDirty();
            if (err != null) {
                throw new RepositoryException("Cannot commit transaction with unsaved session data: " + err);
            }
        }
    }

    /**
     * Commits to database.
     * <p>
     * Data must have been explicitly saved before, so here it's just cleanup.
     */
    public void commit() {
        // clear transient, this means that after this references to states will be stale
        // TODO mark states as invalid
        clearTransient();
        // the transaction ended, the proxied DBSSession will disappear and cannot be reused anyway
        if (undoLog != null) {
            undoLog = null;
        }
        connection.commit();
    }

    /**
     * Rolls back the save state by applying the undo log.
     */
    public void rollback() {
        clearTransient();
        if (undoLog != null) {
            applyUndoLog();
            // the transaction ended, the proxied DBSSession will disappear and cannot be reused anyway
            undoLog = null;
        }
        connection.rollback();
    }

    protected void clearTransient() {
        transientStates.clear();
        transientCreated.clear();
    }

    private void clearSavedState() {
        clearTransient();
        userChangeIds.clear();
        updateConditionPredicates.clear();
        if (undoLog != null) {
            undoLog.clear();
        }
    }

    /**
     * Gets the fulltext updates to do. Called at save() time.
     *
     * @return a list of {@link DocumentTask} instances to schedule post-commit.
     */
    protected List<DocumentTask> getFulltextTasks() {
        Map<String, Boolean> dirtyIds = new HashMap<>();
        findDirtyBinariesDocumentsForFullText(dirtyIds);
        if (dirtyIds.isEmpty()) {
            return Collections.emptyList();
        }
        markIndexingInProgress(dirtyIds.keySet());
        List<DocumentTask> tasks = new ArrayList<>(dirtyIds.size());
        for (Entry<String, Boolean> entry : dirtyIds.entrySet()) {
            DocumentTask task;
            if (TRUE.equals(entry.getValue())) {
                // created so we force the timestamp to be null to avoid linking the fulltext task to a cache invalidation
                task = new DocumentTask(FULLTEXT_EXTRACTOR_TASK_NAME, repository.getName(), SYSTEM_USERNAME, entry.getKey(), null);
            } else {
                task = new DocumentTask(FULLTEXT_EXTRACTOR_TASK_NAME, repository.getName(), SYSTEM_USERNAME, entry.getKey());
            }
            tasks.add(task);
        }
        return tasks;
    }

    protected void markIndexingInProgress(Set<String> ids) {
        FulltextConfiguration fulltextConfiguration = repository.getFulltextConfiguration();
        for (DBSDocumentState docState : getStatesForUpdate(ids)) {
            if (!fulltextConfiguration.isFulltextIndexable(docState.getPrimaryType())) {
                continue;
            }
            docState.put(SYS_FULLTEXT_JOB_ID, docState.getId());
        }
    }

    /**
     * Finds if the given document has dirty paths.
     * @param docState The document to check.
     * @return the list of dirty property paths. The dirty path are the real path including the index in the case of
     *         list, for example 'foo/0/bar/key' or 'foo/bar/key' for single property.
     */
    protected List<String> getDirtyPaths(DBSDocumentState docState) {
        State originalState = docState.getOriginalState();
        State state = docState.getState();
        if (originalState == state) {
            return List.of();
        }
        StateDiff diff = StateHelper.diff(originalState, state);
        if (diff.isEmpty()) {
            return List.of();
        }
        StateDiff reversedDiff = StateHelper.diff(state, originalState);
        // we do diffs in both directions to capture removal of complex list elements,
        // for instance for {foo: [{bar: baz}] -> {foo: []}
        // diff paths = foo and rdiff paths = foo/*/bar
        Set<String> paths = new HashSet<>();
        DirtyPathsFinder dirtyPathsFinder = new DirtyPathsFinder(paths);
        dirtyPathsFinder.findDirtyPaths(diff);
        dirtyPathsFinder.findDirtyPaths(reversedDiff);

        // return sorted list for consistency and ease testing
        return paths.stream().sorted().toList();
    }

    /**
     * Condenses dirty paths by replacing indexed paths with wildcard syntax.
     * For example, paths "foo/0/bar" and "foo/1/bar" become a single entry with a wildcard index "*".
     *
     * @param paths The original list of dirty paths with numeric indices.
     * @return A sorted list of condensed paths with wildcards.
     */
    protected List<String> condenseDirtyPaths(List<String> paths) {
        Set<String> result = new HashSet<>();
        for (String path : paths) {
            // Use lookahead (?=/|$) to match indices without consuming any trailing slash
            String template = path.replaceAll("/\\d+(?=/|$)", "/*");
            result.add(template);
        }
        return result.stream().sorted().toList();
    }

    /**
     * Finds the documents having dirty binaries that have to be reindexed as fulltext.
     *
     * @param docsWithDirtyBinaries set of ids, updated by this method
     */
    protected void findDirtyBinariesDocumentsForFullText(Map<String, Boolean> docsWithDirtyBinaries) {
        for (DBSDocumentState docState : transientStates.values()) {
            if (!getDirtyBinariesPaths(docState, true).isEmpty()) {
                // Mark if the document is created or updated, this is useful for the fulltext extractor task later,
                // the flag is true when the document is created.
                docsWithDirtyBinaries.put(docState.getId(), transientCreated.contains(docState.getId()));
            }
        }
    }

    public List<String> getDirtyBinariesPaths(String id) {
        DBSDocumentState docState = transientStates.get(id);
        if (docState == null) {
            // The document has not been updated or created in the current transaction
            return List.of();
        }
        return getDirtyBinariesPaths(docState, false);
    }

    /**
     * Finds if the given document has dirty binaries.
     *
     * @param docState The document to check.
     * @return the list of dirty binaries paths. The dirty path are the real path including the index in the case of
     *         list of blobs, for example 'foo/0/blob/key' or 'foo/blob/key' for single binary.
     */
    protected List<String> getDirtyBinariesPaths(DBSDocumentState docState, boolean onlyFullTextBinaries) {
        List<String> paths = getDirtyPaths(docState);

        if (onlyFullTextBinaries) {
            FulltextConfiguration fulltextConfiguration = repository.getFulltextConfiguration();
            Set<String> indexedBinaryFields = fulltextConfiguration.getPropPathBlobProperties().get(docState.getPrimaryType());
            List<String> dirtyBlobPaths = new ArrayList<>();
            for (String path : paths) {
                if (indexedBinaryFields != null && indexedBinaryFields.contains(path)) {
                    dirtyBlobPaths.add(path);
                }
            }
            return dirtyBlobPaths;
        } else {
            return getDirtyBlobsPath(paths);
        }
    }

    /**
     * Returns the dirty blobs paths. The input is the list of dirty paths returned by the DirtyPathsFinder and, it is
     * compared with the list of blob keys defined in the repository configuration.
     * The format of the blob keys paths returned by the repository configuration are "foo/blob/key" for multiple binaries
     * or "foo/key" for single binary.
     *
     * @param dirtyPaths The format of the dirty paths are "foo/0/blob/key" or "foo/key" for single binary.
     * @return The list of dirty blob path. The format of the returned paths are "foo/0/blob/key" or "foo/key" for single
     * binary.
     */
    private List<String> getDirtyBlobsPath(List<String> dirtyPaths) {
        List<String> dirtyBlobPaths = new ArrayList<>();
        List<String> blobKeysPaths = connection.getBlobKeysPaths()
                .stream()
                .map(path -> String.join("/", path))
                .filter(path -> !SYS_FULLTEXT_BINARY.equals(path))
                .toList();

        dirtyPaths.forEach(path -> {
            // Remove the '/*' or '/d+' in the dirty path to compare them with the generic key blobs paths
            var cleanedPath = path.replaceAll("/\\d+", "").replaceAll("/\\*", "");
            if (blobKeysPaths.contains(cleanedPath)) {
                dirtyBlobPaths.add(removeBlobKeyPropertyFromPath(path));
            }
        });
        return dirtyBlobPaths;
    }

    /**
     * Remove the `key` path which is the sub-property of the blob that references the binary content.
     *
     * @param path The blob path to clean up.
     * @return The blob path without the "/key" sub-property.
     */
    private String removeBlobKeyPropertyFromPath(String path) {
        return path.substring(0, path.lastIndexOf("/" + KEY_BLOB_KEY));
    }

    /**
     * Iterates on a state diff to find the paths corresponding to dirty values.
     *
     * @since 7.10-HF04, 8.1
     */
    protected static class DirtyPathsFinder {

        protected Set<String> paths;

        public DirtyPathsFinder(Set<String> paths) {
            this.paths = paths;
        }

        public void findDirtyPaths(StateDiff value) {
            findDirtyPaths(value, null);
        }

        protected void findDirtyPaths(Object value, String path) {
            if (value instanceof List) {
                findDirtyPaths((List<?>) value, path);
            } else if (value instanceof ListDiff listDiff) {
                findDirtyPaths(listDiff, path);
            } else if (value instanceof State state) {
                findDirtyPaths(state, path);
            } else {
                paths.add(path);
            }
        }

        protected void findDirtyPaths(List<?> value, String path) {
            for (int i = 0; i < value.size(); i++) {
                findDirtyPaths(value.get(i), path + "/" + i);
            }
        }

        protected void findDirtyPaths(ListDiff value, String path) {
            String newPath = path;
            if (value.diff != null) {
                findDirtyPaths(value.diff, newPath);
            }
            if (value.rpush != null) {
                findDirtyPaths(value.rpush, newPath);
            }
            if (value.pull != null) {
                findDirtyPaths(value.pull, newPath);
            }
        }

        protected void findDirtyPaths(State value, String path) {
            for (Entry<String, Object> es : value.entrySet()) {
                String key = es.getKey();
                Object v = es.getValue();
                String newPath = path == null ? key : path + "/" + key;
                findDirtyPaths(v, newPath);
            }
        }
    }

    protected void scheduleTasks(List<DocumentTask> tasks) {
        // do async fulltext indexing only if high-level sessions are available
        RepositoryManager repositoryManager = Framework.getService(RepositoryManager.class);
        if (repositoryManager != null && !tasks.isEmpty()) {
            TaskService asyncTasksService = Framework.getService(TaskService.class);
            tasks.forEach(task -> asyncTasksService.submitTask(task));
        }
    }

    /**
     * Raises document update events for documents that were modified during the transaction.
     * Builds a {@link DocumentEventRaiserContext} carrying the ids, per-document context properties
     * and state diffs of documents with dirty properties and/or dirty blobs, then dispatches it to
     * the {@link DocumentEventRaiserService}, which delegates to every registered
     * {@link DocumentEventRaiser}. If the raiser service is not available, no event is raised.
     *
     * @param dirtyPropDocIds the ids of documents whose properties changed
     * @param contextForDocsWithDirtyProps per-document context properties for documents with dirty
     *            properties, keyed by document id
     * @param contextForDocsWithDirtyBlobs per-document context properties for documents with dirty
     *            blobs, keyed by document id
     * @param principal the principal performing the update, propagated to the raised events
     * @param diff the computed differences between the original and current document states,
     *            keyed by document id
     * @param originalDocument the original document states before the transaction, keyed by
     *            document id
     * @param currentDocument the current document states after the transaction, keyed by
     *            document id
     */
    private void raiseUpdatedDocumentEvents(Set<String> dirtyPropDocIds,
                                            Map<String, Map<String, Serializable>> contextForDocsWithDirtyProps,
                                            Map<String, Map<String, Serializable>> contextForDocsWithDirtyBlobs, RepositoryPrincipal principal,
                                            Map<String, Object> diff, Map<String, Object> originalDocument, Map<String, Object> currentDocument) {
        DocumentEventRaiserService raiserService = Framework.getService(DocumentEventRaiserService.class);
        CoreSession coreSession = CoreInstance.getCoreSessionSystem(repository.getName());
        if (raiserService != null) {
            DocumentEventRaiserContext context = new DocumentEventRaiserContext(coreSession, principal,
                    contextForDocsWithDirtyProps,contextForDocsWithDirtyBlobs, dirtyPropDocIds,
                    diff, originalDocument, currentDocument);
            // call matched registered raiser in a loop
            raiserService.raiseEvent(context);
        }
        else {
            log.debug("DocumentEventRaiserService is not registered; skipping update events for {} document(s) "
                            + "with dirty properties and {} document(s) with dirty blobs.",
                    dirtyPropDocIds.size(), contextForDocsWithDirtyBlobs.size());
        }
    }

    private void sendDocumentEvents(String eventName, Set<String> ids, RepositoryPrincipal principal,
                                    Map<String, Map<String, Serializable>> contextPropertiesById) {
        DocumentEventSenderService documentEventSender = Framework.getService(DocumentEventSenderService.class);
        documentEventSender.sendDocumentEvents(repository.getName(), eventName, ids, principal, contextPropertiesById);
    }
}
