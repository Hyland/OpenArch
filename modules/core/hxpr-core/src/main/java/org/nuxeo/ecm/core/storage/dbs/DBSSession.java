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

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.api.DocumentConstants.KEY_ACE_PERMISSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_POS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static org.nuxeo.ecm.core.async.tasks.DeleteBatchTaskProcessor.BATCH_DELETE_TASK_NAME;
import static org.nuxeo.ecm.core.query.sql.HXQL.KEY_ACE_PERMISSION_COMPAT;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_VERSIONED_WITH_PARENT;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PATH_INTERNAL;
import static org.nuxeo.ecm.core.storage.dbs.DBSSession.ChildrenSelector.ALL;
import static org.nuxeo.ecm.core.storage.dbs.DBSSession.ChildrenSelector.FOR_CHECK_IN;
import static org.nuxeo.ecm.core.storage.dbs.DBSSession.ChildrenSelector.FOR_COPY;
import static org.nuxeo.ecm.core.storage.dbs.DBSSession.ChildrenSelector.FOR_RESTORE_CLEAR;
import static org.nuxeo.ecm.core.storage.dbs.DBSSession.ChildrenSelector.FOR_RESTORE_COPY;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.utils.IdUtils;
import org.nuxeo.ecm.core.DocumentImpl;
import org.nuxeo.ecm.core.api.ConditionFailedException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DeletedChildDocumentRecord;
import org.nuxeo.ecm.core.api.DocumentExistsException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.UpdateCondition;
import org.nuxeo.ecm.core.api.lock.LockManager;
import org.nuxeo.ecm.core.api.query.QueryParameters;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.async.tasks.DeleteBatchTaskProcessor;
import org.nuxeo.ecm.core.blob.DocumentBlobService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.ecm.core.event.impl.GovernanceEventContext;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.HXQLParser;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.Statement;
import org.nuxeo.ecm.core.query.sql.model.StatementValidator;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.MixinNames;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.UnknownDocumentType;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityService;
import com.hyland.hxpr.expr.ExprPredicates;
import org.nuxeo.ecm.core.storage.QueryOptimizer;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.StateHelper;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.metrics.MetricsService;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

/**
 * Implementation of a {@link InternalSession} for Document-Based Storage.
 *
 * @since 5.9.4
 */
public class DBSSession implements InternalSession {

    private static final Logger log = LogManager.getLogger(DBSSession.class);

    private static final Set<String> KEYS_USEFUL_DURING_REMOVE = Set.of( //
            SYS_PARENT_ID, //
            SYSGOV_RETAIN_UNTIL, //
            SYSGOV_HAS_LEGAL_HOLD);

    private static final String VERSION_NAME_PREFIX = "_v_";

    private final Repository repository;

    protected final DBSTransactionState transaction;

    protected final boolean fulltextStoredInBlob;

    protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    private final Timer saveTimer;

    private final Timer queryTimer;

    private static final String LOG_MIN_DURATION_KEY = "org.nuxeo.dbs.query.log_min_duration_ms";

    public static final String CANNOT_REMOVE_ROOT_DIRECTORY = "Cannot remove %s, it is the root directory";

    public static final String CANNOT_REMOVE_UNDER_RETENTION = "Cannot remove %s, it is under retention / hold";

    private final long logMinDurationNs;

    private final SecurityPolicyService securityPolicyService;

    public static final String GOVERNANCE_CHILD_DELETION_NOTIFY_FEATURE_FLAG = "gov-child-delete-notify";

    private static final int BATCH_SIZE = 500;

    private static final int DELETE_ASYNC_THRESHOLD = 20;

    public DBSSession(DBSRepository repository) {
        this.repository = repository;
        transaction = new DBSTransactionState(repository, this);
        FulltextConfiguration fulltextConfiguration = repository.getFulltextConfiguration();
        fulltextStoredInBlob = fulltextConfiguration != null && fulltextConfiguration.isFulltextStoredInBlob();

        saveTimer = registry.timer(MetricName.build("nuxeo", "repositories", "repository", "save")
                                             .tagged("repository", repository.getName()));
        queryTimer = registry.timer(MetricName.build("nuxeo", "repositories", "repository", "query")
                                              .tagged("repository", repository.getName()));
        logMinDurationNs = Long.parseLong(Framework.getProperty(LOG_MIN_DURATION_KEY, "-1")) * 1000000;
        securityPolicyService = Framework.getService(SecurityService.class)
                                         .getSecurityPolicyService(repository.getName());
    }

    protected DocumentBlobService getDocumentBlobService() {
        return Framework.getService(DocumentBlobService.class);
    }

    protected void notifyAfterCopy(InternalDocument doc) {
        getDocumentBlobService().notifyAfterCopy(doc);
    }

    @Override
    public String getRepositoryName() {
        return repository.getName();
    }

    @Override
    public void destroy() {
        transaction.close();
    }

    @Override
    public SchemaManager getSchemaManager() {
        return transaction.getSchemaManager();
    }

    @SuppressWarnings("resource") // timerContext closed by stop() in finally
    @Override
    public void save(NuxeoPrincipal principal) {
        final Timer.Context timerContext = saveTimer.time();
        try {
            transaction.save(principal);
            if (!TransactionHelper.isTransactionActiveOrMarkedRollback()) {
                transaction.commit();
            }
        } finally {
            timerContext.stop();
        }
    }

    protected String getRootId() {
        return transaction.getRootId();
    }

    /*
     * Normalize using NFC to avoid decomposed characters (like 'e' + COMBINING ACUTE ACCENT instead of LATIN SMALL
     * LETTER E WITH ACUTE). NFKC (normalization using compatibility decomposition) is not used, because compatibility
     * decomposition turns some characters (LATIN SMALL LIGATURE FFI, TRADE MARK SIGN, FULLWIDTH SOLIDUS) into a series
     * of characters ('f'+'f'+'i', 'T'+'M', '/') that cannot be re-composed into the original, and therefore loses
     * information.
     */
    public static String normalize(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC);
    }

    @Override
    public InternalDocument resolvePath(String path) {
        // TODO move checks and normalize higher in call stack
        if (path == null) {
            throw new DocumentNotFoundException("Null path");
        }
        int len = path.length();
        if (len == 0) {
            throw new DocumentNotFoundException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new DocumentNotFoundException("Relative path: " + path);
        }
        if (len > 1 && path.charAt(len - 1) == '/') {
            // remove final slash
            path = path.substring(0, len - 1);
            len--;
        }
        path = normalize(path);

        if (len == 1) {
            return getRootDocument();
        }
        DBSDocumentState docState = null;
        String parentId = getRootId();
        String[] names = path.split("/", -1);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new DocumentNotFoundException("Path with empty component: " + path);
            }
            docState = transaction.getChildState(parentId, name);
            if (docState == null) {
                throw new DocumentNotFoundException(path);
            }
            parentId = docState.getId();
        }
        return getDocument(docState);
    }

    protected String getDocumentIdByPath(String path) {
        // TODO move checks and normalize higher in call stack
        if (path == null) {
            throw new DocumentNotFoundException("Null path");
        }
        int len = path.length();
        if (len == 0) {
            throw new DocumentNotFoundException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new DocumentNotFoundException("Relative path: " + path);
        }
        if (len > 1 && path.charAt(len - 1) == '/') {
            // remove final slash
            path = path.substring(0, len - 1);
            len--;
        }
        path = normalize(path);

        if (len == 1) {
            return getRootId();
        }
        DBSDocumentState docState = null;
        String parentId = getRootId();
        String[] names = path.split("/", -1);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new DocumentNotFoundException("Path with empty component: " + path);
            }
            // TODO XXX add getChildId method
            docState = transaction.getChildState(parentId, name);
            if (docState == null) {
                return null;
            }
            parentId = docState.getId();
        }
        return docState.getId(); // NOSONAR
    }

    protected InternalDocument getChild(String parentId, String name) {
        name = normalize(name);
        DBSDocumentState docState = transaction.getChildState(parentId, name);
        DBSDocument doc = getDocument(docState);
        if (doc == null) {
            throw new DocumentNotFoundException(name);
        }
        return doc;
    }

    protected List<InternalDocument> getChildren(String parentId) {
        List<DBSDocumentState> docStates = transaction.getChildrenStates(parentId);
        if (isOrderable(parentId)) {
            // sort children in order
            docStates.sort(POS_COMPARATOR);
        }
        List<InternalDocument> children = new ArrayList<>(docStates.size());
        for (DBSDocumentState docState : docStates) {
            try {
                children.add(getDocument(docState));
            } catch (DocumentNotFoundException e) {
                // ignore error retrieving one of the children
                // (Unknown document type)
                continue;
            }
        }
        return children;
    }

    public enum ChildrenSelector {
        ALL((state, context) -> true), //
        FOR_COPY(ChildrenSelector::testForCopy), //
        FOR_CHECK_IN(ChildrenSelector::testForCheckIn), //
        FOR_RESTORE_CLEAR(ChildrenSelector::testForCheckIn), //
        FOR_RESTORE_COPY(ChildrenSelector::testForCopy);

        private final BiPredicate<State, ChildrenSelectorTestContext> predicate;

        private ChildrenSelector(BiPredicate<State, ChildrenSelectorTestContext> predicate) {
            this.predicate = predicate;
        }

        public boolean test(State state, ChildrenSelectorTestContext context) {
            return predicate.test(state, context);
        }

        /**
         * Copy includes only children that aren't versions.
         */
        private static boolean testForCopy(State state, ChildrenSelectorTestContext context) {
            boolean isVersion = TRUE.equals(state.get(SYSVER_IS_VERSION));
            return !isVersion;
        }

        /**
         * Check in includes only children that aren't versions and have mixin SysVersionedWithParent.
         */
        private static boolean testForCheckIn(State state, ChildrenSelectorTestContext context) {
            boolean isVersion = TRUE.equals(state.get(SYSVER_IS_VERSION));
            if (isVersion) {
                return false;
            }
            String primaryType = (String) state.get(SYS_PRIMARY_TYPE);
            @SuppressWarnings("unchecked")
            List<String> mixinTypes = (List<String>) state.get(SYS_MIXIN_TYPES);
            if (mixinTypes == null) {
                mixinTypes = List.of();
            }
            return context.isVersionedWithParent(primaryType, mixinTypes);
        }
    }

    // separate context object to hold precomputed set of document types for SysVersionedWithParent
    public static class ChildrenSelectorTestContext {

        private final Set<String> primaryTypesVersionedWithParent;

        public ChildrenSelectorTestContext(SchemaManager schemaManager) {
            Set<String> set = schemaManager.getDocumentTypeNamesForMixin(SYS_VERSIONED_WITH_PARENT);
            primaryTypesVersionedWithParent = set == null ? Set.of() : set;
        }

        public boolean isVersionedWithParent(String primaryType, List<String> mixinTypes) {
            return primaryTypesVersionedWithParent.contains(primaryType)
                    || mixinTypes.contains(SYS_VERSIONED_WITH_PARENT);
        }
    }

    protected List<String> getChildrenIds(String parentId) {
        return getChildrenIds(parentId, ALL);
    }

    protected List<String> getChildrenIds(String parentId, ChildrenSelector childrenSelector) {
        if (isOrderable(parentId)) {
            // TODO get only id and pos, not full state
            // TODO state not for update
            List<DBSDocumentState> docStates = transaction.getChildrenStates(parentId, childrenSelector);
            docStates.sort(POS_COMPARATOR);
            List<String> children = new ArrayList<>(docStates.size());
            for (DBSDocumentState docState : docStates) {
                children.add(docState.getId());
            }
            return children;
        } else {
            return transaction.getChildrenIds(parentId, childrenSelector);
        }
    }

    protected boolean hasChildren(String parentId) {
        return transaction.hasChildren(parentId);

    }

    @Override
    public InternalDocument getDocumentByUUID(String id) {
        InternalDocument doc = getDocument(id);
        if (doc != null) {
            return doc;
        }
        // exception required by API
        throw new DocumentNotFoundException(id);
    }

    @Override
    public InternalDocument getRootDocument() {
        return getDocument(getRootId());
    }

    @Override
    public InternalDocument getNullDocument() {
        return new DBSDocument(null, null, this, true);
    }

    protected DBSDocument getDocument(String id) {
        DBSDocumentState docState = transaction.getStateForUpdate(id);
        return getDocument(docState);
    }

    protected DBSDocument getDocument(String id, boolean allowUnknownType) {
        DBSDocumentState docState = transaction.getStateForUpdate(id);
        return getDocument(docState, true, allowUnknownType);
    }

    protected List<InternalDocument> getDocuments(List<String> ids) {
        List<DBSDocumentState> docStates = transaction.getStatesForUpdate(ids);
        List<InternalDocument> docs = new ArrayList<>(ids.size());
        for (DBSDocumentState docState : docStates) {
            try {
                docs.add(getDocument(docState));
            } catch (DocumentNotFoundException e) {
                // unknown type in db, ignore
                continue;
            }
        }
        return docs;
    }

    protected DBSDocument getDocument(DBSDocumentState docState) {
        return getDocument(docState, true, false);
    }

    protected DBSDocument getDocument(DBSDocumentState docState, boolean readonly, boolean allowUnknownType) {
        if (docState == null) {
            return null;
        }
        boolean isVersion = TRUE.equals(docState.get(SYSVER_IS_VERSION));

        String typeName = docState.getPrimaryType();
        DocumentType type = getSchemaManager().getDocumentType(typeName);
        if (type == null) {
            if (allowUnknownType) {
                type = new UnknownDocumentType(typeName);
            } else {
                throw new DocumentNotFoundException("Unknown document type: " + typeName);
            }
        }

        if (isVersion) {
            return new DBSDocument(docState, type, this, readonly);
        } else {
            return new DBSDocument(docState, type, this, false);
        }
    }

    protected boolean hasChild(String parentId, String name) {
        name = normalize(name);
        return transaction.hasChild(parentId, name);
    }

    protected DBSDocumentState newState() {
        return transaction.newState();
    }

    protected void create(DBSDocumentState docState) {
        transaction.create(docState);
    }

    protected boolean isOrderable(String id) {
        State state = transaction.getStateForRead(id);
        String typeName = (String) state.get(SYS_PRIMARY_TYPE);
        return getSchemaManager().getDocumentType(typeName).getMixins().contains(MixinNames.SYS_ORDERABLE);
    }

    protected Long getNextPos(String parentId) {
        if (!isOrderable(parentId)) {
            return null;
        }
        long max = -1;
        for (DBSDocumentState docState : transaction.getChildrenStates(parentId)) {
            Long pos = (Long) docState.get(SYS_POS);
            if (pos != null && pos > max) {
                max = pos;
            }
        }
        return max + 1;
    }

    protected void orderBefore(String parentId, String sourceId, String destId) {
        if (!isOrderable(parentId)) {
            // TODO throw exception?
            return;
        }
        if (sourceId.equals(destId)) {
            return;
        }
        // This is optimized by assuming the number of children is small enough
        // to be manageable in-memory.
        // fetch children
        List<DBSDocumentState> docStates = transaction.getChildrenStates(parentId);
        // sort children in order
        docStates.sort(POS_COMPARATOR);
        // renumber
        int i = 0;
        DBSDocumentState source = null; // source if seen
        Long destPos = null;
        for (DBSDocumentState docState : docStates) {
            Object id = docState.getId();
            if (id.equals(destId)) {
                destPos = Long.valueOf(i);
                i++;
                if (source != null) {
                    source.put(SYS_POS, destPos);
                }
            }
            Long setPos;
            if (id.equals(sourceId)) {
                i--;
                source = docState;
                setPos = destPos;
            } else {
                setPos = Long.valueOf(i);
            }
            if (setPos != null && !setPos.equals(docState.get(SYS_POS))) {
                docState.put(SYS_POS, setPos);
            }
            i++;
        }
        if (destId == null) {
            Long setPos = Long.valueOf(i);
            if (!setPos.equals(source.get(SYS_POS))) { // NOSONAR
                source.put(SYS_POS, setPos);
            }
        }
    }

    protected void checkOut(DBSDocument doc) {
        if (doc.isCheckedOut()) {
            throw new NuxeoException("Already checked out");
        }
        doc.docState.put(SYSVER_IS_CHECKED_IN, null);
    }

    protected InternalDocument checkIn(DBSDocument doc, NuxeoPrincipal principal) {
        if (!doc.isCheckedOut()) {
            throw new NuxeoException("Already checked in", SC_CONFLICT);
        }
        String name = VERSION_NAME_PREFIX + IdUtils.generateStringId();
        return copy(doc, doc, name, principal, true);
    }

    protected void updateDenormalizedPropertiesWithDescendants(String id, NuxeoPrincipal principal) {
        transaction.updateDenormalizedPropertiesWithDescendants(id, principal);
    }

    @Override
    public void restoreVersion(InternalDocument version, NuxeoPrincipal principal) {
        if (!version.isVersion()) {
            throw new NuxeoException("Not a version: " + version.getId());
        }
        InternalDocument doc = version.getWorkingCopy();
        if (!doc.isCheckedOut()) {
            // we always restore on top of a checked out document
            doc.checkOut();
        }
        String docId = doc.getId();
        String versionId = version.getId();

        DBSDocumentState docState = transaction.getStateForUpdate(docId);

        if (TRUE.equals(docState.get(SYSGOV_IS_RECORD))) {
            getDocumentBlobService().notifyBeforeRemove(doc);
        }

        State versionState = transaction.getStateForRead(versionId);

        // clear all data
        for (String key : docState.state.keyArray()) {
            if (!keepWhenRestore(key)) {
                docState.put(key, null);
            }
        }
        // update from version
        for (Entry<String, Object> en : versionState.entrySet()) {
            String key = en.getKey();
            if (!keepWhenRestore(key)) {
                docState.put(key, StateHelper.deepCopy(en.getValue()));
            }
        }
        docState.put(SYSVER_IS_VERSION, null);
        docState.put(SYSGOV_IS_RECORD, null);
        docState.put(SYSGOV_RETAIN_UNTIL, null);
        docState.put(SYSGOV_HAS_LEGAL_HOLD, null);

        // delete children of the working copy that we want to get rid of during restore
        transaction.getChildrenIds(docId, FOR_RESTORE_CLEAR).forEach(id -> remove(id, principal));

        // copy children of the version we want to restore
        transaction.getChildrenIds(versionId, FOR_RESTORE_COPY).forEach(id -> copy(getDocument(id), doc, null, principal, false));

        if (TRUE.equals(versionState.get(SYSGOV_IS_RECORD))) {
            notifyAfterCopy(doc);
        }

        // TODO must copy children that are COPY_WITH_VERSION (FOR_RESTORE?) _and_ remove old ones
    }

    // keys we don't copy from version when restoring
    protected boolean keepWhenRestore(String key) {
        switch (key) {
        // these are placeful stuff
        case SYS_ID:
        case SYS_PARENT_ID:
        case SYS_ANCESTOR_IDS:
        case SYS_NAME:
        case SYS_POS:
        case SYS_PRIMARY_TYPE:
        case SYS_ACL:
        case SYS_READ_ACL:
            // these are version-specific
        case SYSVER_CREATED:
        case SYSVER_CREATOR:
        case SYSVER_DESCRIPTION:
        case SYSVER_IS_CHECKED_IN:
        case SYSVER_MINOR_VERSION:
        case SYSVER_TITLE:
        case SYSVER_VERSION:
            // these will be updated after restore
        case SYSVER_IS_VERSION:
            // record
        case SYSGOV_IS_RECORD:
        case SYSGOV_RETAIN_UNTIL:
        case SYSGOV_HAS_LEGAL_HOLD:
            return true;
        default:
            return false;
        }
    }

    /**
     * Returns a stream of descendants from a given base document, in no particular order. This does not include
     * information about the base document itself.
     * <p>
     * THE STREAM MUST BE CLOSED WHEN DONE to release resources.
     *
     * @param id the base document id
     * @param keys what to collect about the descendants in addition to their ids
     * @param limit the maximum number of descendants to return
     * @return a stream of {@link State}s; THE STREAM MUST BE CLOSED WHEN DONE
     */
    // can't be in InternalSession due to State not being available from this interface
    public Stream<State> getDescendants(String id, Set<String> keys, int limit) {
        return transaction.getDescendants(id, keys, limit);
    }

    @Override
    public InternalDocument copy(InternalDocument source, InternalDocument parent, String name,
            NuxeoPrincipal principal) {
        return copy(source, parent, name, principal, false);
    }

    private InternalDocument copy(InternalDocument source, InternalDocument parent, String name,
            NuxeoPrincipal principal, boolean isCheckIn) {
        transaction.save(principal);
        if (name == null) {
            name = source.getName();
        }
        // Note: enforcement=false is intentional - business rule validation handled at SessionImpl level
        // future enhancement may expose an enforceSysName flag for copy/move operations (non-blocking).
        name = DocumentImpl.handleName(source, parent, name, false);
        String sourceId = source.getId();
        String parentId = parent.getId();
        DBSDocumentState sourceState = transaction.getStateForUpdate(sourceId);
        State parentState = transaction.getStateForRead(parentId);
        String oldParentId = (String) sourceState.get(SYS_PARENT_ID);
        @SuppressWarnings("unchecked")
        List<String> parentAncestorIds = (List<String>) parentState.get(SYS_ANCESTOR_IDS);
        Deque<String> ancestorIds = new ArrayDeque<>();
        if (parentAncestorIds != null) {
            ancestorIds.addAll(parentAncestorIds);
        }
        ancestorIds.add(parentId);
        if (!isCheckIn && oldParentId != null && !oldParentId.equals(parentId) && ancestorIds.contains(sourceId)) {
            throw new DocumentExistsException(
                    "Cannot copy a node under itself: " + parentId + " is under " + sourceId);
        }
        Long pos = getNextPos(parentId);
        // do the copy
        ChildrenSelector childrenSelector = isCheckIn ? FOR_CHECK_IN : FOR_COPY;
        String copyId = copyRecurse(sourceId, parentId, ancestorIds, name, childrenSelector);
        DBSDocumentState copyState = transaction.getStateForUpdate(copyId);
        // pos fixup
        copyState.put(SYS_POS, pos);
        if (isCheckIn) {
            // checkIn fixup
            copyState.put(SYSVER_IS_VERSION, TRUE);
            copyState.put(SYSVER_VERSION, sourceState.get(SYSVER_VERSION)); // was reset by copy
            copyState.put(SYSVER_MINOR_VERSION, sourceState.get(SYSVER_MINOR_VERSION)); // was reset by copy
            copyState.put(SYSVER_CREATED, Instant.now());
            copyState.put(SYSVER_CREATOR, principal.getActingId());
            // we don't copy the working copy's ACL when creating a version
            copyState.put(SYS_ACL, null);

            // update the working copy to mark it checked in
            sourceState.put(SYSVER_IS_CHECKED_IN, TRUE);
        } else {
            // sys_created and sys_modified fixup
            var now = Instant.now();
            copyState.put(SYS_CREATED, now);
            copyState.put(SYS_MODIFIED, now);
            // version copy fixup
            copyState.put(SYSVER_IS_VERSION, null);
        }

        // update read acls
        updateDenormalizedPropertiesWithDescendants(copyId, principal);

        return getDocument(copyState);
    }

    private String copyRecurse(String sourceId, String parentId, Deque<String> ancestorIds, String name,
            ChildrenSelector childrenSelector) {
        String copyId = copy(sourceId, parentId, ancestorIds, name);
        ancestorIds.addLast(copyId);
        for (String childId : transaction.getChildrenIds(sourceId, childrenSelector)) {
            copyRecurse(childId, copyId, ancestorIds, null, FOR_COPY);
        }
        ancestorIds.removeLast();
        return copyId;
    }

    /**
     * Copy source under parent, and set its ancestors.
     */
    protected String copy(String sourceId, String parentId, Collection<String> ancestorIds, String name) {
        DBSDocumentState copy = transaction.copy(sourceId);
        copy.put(SYS_PARENT_ID, parentId);
        copy.put(SYS_ANCESTOR_IDS, new ArrayList<>(ancestorIds));
        if (name != null) {
            copy.put(SYS_NAME, name);
        }
        copy.put(SYSVER_IS_CHECKED_IN, null);
        copy.put(SYSVER_VERSION, null);
        copy.put(SYSVER_MINOR_VERSION, null);
        if (TRUE.equals(copy.get(SYSGOV_IS_RECORD))) {
            // unset record on the copy
            copy.put(SYSGOV_IS_RECORD, null);
            copy.put(SYSGOV_RETAIN_UNTIL, null);
            copy.put(SYSGOV_HAS_LEGAL_HOLD, null);
            DBSDocument doc = getDocument(copy);
            notifyAfterCopy(doc);
        }
        return copy.getId();
    }

    @Override
    public InternalDocument move(InternalDocument source, InternalDocument parent, String name,
            UpdateCondition condition, NuxeoPrincipal principal) {
        String oldName = source.getName();
        if (name == null) {
            name = oldName;
        }
        // Note: enforcement=false is intentional - business rule validation handled at SessionImpl level
        // future enhancement may expose an enforceSysName flag for copy/move operations (non-blocking).
        name = DocumentImpl.handleName(source, parent, name, false);
        String sourceId = source.getId();
        String parentId = parent.getId();
        DBSDocumentState sourceState = transaction.getStateForUpdate(sourceId);
        String oldParentId = (String) sourceState.get(SYS_PARENT_ID);

        // simple case of a rename
        if (Objects.equals(oldParentId, parentId)) {
            if (!oldName.equals(name)) {
                // do the move
                sourceState.put(SYS_NAME, name);
                // no ancestors to change
            }
            return source;
        }

        // prepare new ancestor ids
        transaction.save(principal);
        State parentState = transaction.getStateForRead(parentId);
        @SuppressWarnings("unchecked")
        List<Object> parentAncestorIds = (List<Object>) parentState.get(SYS_ANCESTOR_IDS);
        List<Object> ancestorIds;
        if (parentAncestorIds == null) {
            ancestorIds = List.of(parentId);
        } else {
            ancestorIds = new ArrayList<>(parentAncestorIds.size() + 1);
            ancestorIds.addAll(parentAncestorIds);
            ancestorIds.add(parentId);
        }

        if (ancestorIds.contains(sourceId)) {
            throw new DocumentExistsException("Cannot move a node under itself: " + parentId + " is under " + sourceId);
        }

        // do the move
        sourceState.put(SYS_NAME, name);
        sourceState.put(SYS_PARENT_ID, parentId);
        if (condition != null) {
            setUpdateCondition(sourceId, condition, principal);
        }

        // update ancestors on all sub-children
        @SuppressWarnings("unchecked")
        List<Object> oldAncestorIds = (List<Object>) sourceState.get(SYS_ANCESTOR_IDS);
        int ndel = oldAncestorIds == null ? 0 : oldAncestorIds.size();
        transaction.updateAncestors(sourceId, ndel, ancestorIds);

        updateDenormalizedPropertiesWithDescendants(sourceId, principal);

        return source;
    }

    /**
     * Removes a document.
     * <p>
     * We also have to update everything impacted by "relations":
     * <ul>
     * <li>parent-child relations: delete all subchildren recursively,
     * </ul>
     * Threshold for number of documents to delete in a batch before switching to async deletion is defined by {@link #DELETE_ASYNC_THRESHOLD}.
     */
    protected void remove(String rootId, NuxeoPrincipal principal) {
        transaction.save(principal);

        // if rootId is from the root directory, removal fails
        if (getRootId().equals(rootId)) {
            throw new DocumentSecurityException(CANNOT_REMOVE_ROOT_DIRECTORY.formatted(rootId));
        }

        State rootState = transaction.getStateForRead(rootId);

        // find all sub-docs
        Set<String> firstLevelIds = new HashSet<>();
        Set<String> removedIds = new HashSet<>();
        Set<String> undeletableIds = new HashSet<>();
        Instant now = Instant.now();

        Consumer<State> collector = state -> {
            String id = (String) state.get(SYS_ID);
            removedIds.add(id);
            if (rootId.equals(state.get(SYS_PARENT_ID))) {
                firstLevelIds.add(id);
            }
            if (TRUE.equals(state.get(SYSGOV_HAS_LEGAL_HOLD))) {
                undeletableIds.add(id);
            } else {
                Instant retainUntil = (Instant) state.get(SYSGOV_RETAIN_UNTIL);
                if (retainUntil != null && now.isBefore(retainUntil)) {
                    undeletableIds.add(id);
                }
            }
        };

        collector.accept(rootState); // add the root node too
        try (Stream<State> states = transaction.getDescendants(rootId, KEYS_USEFUL_DURING_REMOVE, 0)) {
            states.forEach(collector);
        }

        // if a subdocument is under retention / hold, removal fails
        if (!undeletableIds.isEmpty()) {
            if (!InternalSession.canDeleteUndeletable(principal)) {
                if (undeletableIds.contains(rootId)) {
                    throw new DocumentSecurityException(CANNOT_REMOVE_UNDER_RETENTION.formatted(rootId));
                } else {
                    throw new DocumentSecurityException("Cannot remove " + rootId + ", subdocument "
                            + undeletableIds.iterator().next() + " is under retention / hold");
                }
            }
        }

        //notify first level ids removal
        sendDocumentEvents(firstLevelIds, principal);

        // notify blob manager before removal for all documents being removed
        for (String id : removedIds) {
            try {
                DBSDocument doc = getDocument(id);
                getDocumentBlobService().notifyBeforeRemove(doc);
            } catch (DocumentNotFoundException e) {
                // unknown type in db
                // ignore blob manager notification
            }
        }

        // remove root and first level synchronously
        firstLevelIds.add(rootId);
        transaction.removeStates(firstLevelIds);

        // if we have more than one level, remove asynchronously
        if (removedIds.size() > firstLevelIds.size()) {
            //notify other level documents removal
            sendDocumentEvents(removedIds, principal);

            if (removedIds.size() < DELETE_ASYNC_THRESHOLD) {
                DeleteBatchTaskProcessor processor = new DeleteBatchTaskProcessor();
                processor.process(null, List.copyOf(removedIds), CoreInstance.getCoreSessionSystem(repository.getName()));
            } else {
                String nxql = String.format("SELECT * FROM SysContent, SysRelation WHERE sys_ancestorIds = '%s'", rootId);
                ScrollerTask command = new ScrollerTask.Builder(BATCH_DELETE_TASK_NAME, nxql, getRepositoryName(), SYSTEM_USERNAME).build();
                Framework.getService(TaskService.class).submitTask(command);
            }
        }
    }

    private static final Comparator<DBSDocumentState> POS_COMPARATOR = (s1, s2) -> {
        Long p1 = (Long) s1.get(SYS_POS);
        Long p2 = (Long) s2.get(SYS_POS);
        if (p1 == null && p2 == null) {
            // coherent sort
            return s1.hashCode() - s2.hashCode();
        }
        if (p1 == null) {
            return 1;
        }
        if (p2 == null) {
            return -1;
        }
        return p1.compareTo(p2);
    };

    @Override
    public void updateDenormalizedPropertiesNoCache(Collection<String> ids) {
        transaction.updateDenormalizedPropertiesNoCache(ids);
    }

    @Override
    public void removeDocument(String id, NuxeoPrincipal principal) {
        transaction.save(principal);

        if (getRootId().equals(id)) {
            throw new DocumentSecurityException(CANNOT_REMOVE_ROOT_DIRECTORY.formatted(id));
        }

        DBSDocumentState docState = transaction.getStateForUpdate(id);
        if (!InternalSession.canDeleteUndeletable(principal)) {
            Instant retainUntil = (Instant) docState.get(SYSGOV_RETAIN_UNTIL);
            if (retainUntil != null && Instant.now().isBefore(retainUntil)) {
                throw new DocumentSecurityException(CANNOT_REMOVE_UNDER_RETENTION.formatted(id));
            }
            if (TRUE.equals(docState.get(SYSGOV_HAS_LEGAL_HOLD))) {
                throw new DocumentSecurityException(CANNOT_REMOVE_UNDER_RETENTION.formatted(id));
            }
        }

        // notify blob manager before removal
        try {
            DBSDocument doc = getDocument(docState);
            getDocumentBlobService().notifyBeforeRemove(doc);
        } catch (DocumentNotFoundException e) {
            // unknown type in db
            // ignore blob manager notification
        }

        // remove doc
        transaction.removeStates(Collections.singleton(id));

    }

    @Override
    public PartialList<InternalDocument> query(Statement statement, QueryParameters queryParams, long countUpTo) {
        // query
        PartialList<String> pl = doQuery(statement, queryParams, (int) countUpTo);

        // get Documents in bulk
        List<InternalDocument> docs = getDocuments(pl);

        return new PartialList<>(docs, pl.totalSize());
    }

    @SuppressWarnings("resource") // Time.Context closed by stop()
    protected PartialList<String> doQuery(Statement statement, QueryParameters queryParams, int countUpTo) {
        final Timer.Context timerContext = queryTimer.time();
        try {
            Mutable<String> idKeyHolder = new MutableObject<>();
            PartialList<Map<String, Object>> pl = doQueryAndFetch(statement, queryParams, false, countUpTo,
                    idKeyHolder);
            @SuppressWarnings("deprecation") // legacy Mutable API used in existing query path
            String idKey = idKeyHolder.getValue();
            List<String> ids = new ArrayList<>(pl.size());
            for (Map<String, Object> map : pl) {
                String id = (String) map.get(idKey);
                ids.add(id);
            }
            return new PartialList<>(ids, pl.totalSize());
        } finally {
            long duration = timerContext.stop();
            if (logMinDurationNs >= 0 && duration > logMinDurationNs) {
                String msg = String.format("duration_ms:\t%.2f\t%s %s\tquery\t%s", duration / 1000000.0, queryParams,
                        countUpToAsString(countUpTo), statement);
                if (log.isTraceEnabled()) {
                    log.info(msg, new Throwable("Slow query stack trace"));
                } else {
                    log.info(msg);
                }
            }
        }
    }

    protected PartialList<Map<String, Object>> doQueryAndFetch(Statement statement, QueryParameters queryParams,
            boolean distinctDocuments, int countUpTo, Mutable<String> idKeyHolder) {
        StatementValidator.rejectSecurityProperties(statement, queryParams.principal(), getSchemaManager());
        List<Operand> projection = new ArrayList<>(statement.getSelectClause().elements());
        if (projection.isEmpty()) {
            // turned into SELECT sys_id
            projection.add(new Reference(SYS_ID));
        }
        boolean selectStar = projection.size() == 1 && projection.contains(new Reference(SYS_ID));
        if (selectStar) {
            distinctDocuments = true;
        }
        if (idKeyHolder != null) {
            Operand operand = projection.get(0);
            String idKey = operand instanceof Reference ref ? ref.name() : SYS_ID;
            idKeyHolder.setValue(idKey);
        }

        // Add useful select clauses, used for order by path
        for (String name : List.of(SYS_ID, SYS_PARENT_ID, SYS_NAME)) {
            Reference reference = new Reference(name);
            if (!projection.contains(reference)) {
                projection.add(reference);
            }
        }
        statement = statement.withSelectClause(new SelectClause(projection));

        QueryOptimizer optimizer = new DBSQueryOptimizer(getSchemaManager());
        statement = optimizer.optimize(statement);

        statement = securityPolicyService.addSecurityPolicies(statement, queryParams);
        // if the where clause is false, no need to execute it, return an empty list
        WhereClause whereClause = statement.getWhereClause();
        if (whereClause != null && Predicates.FALSE.equals(whereClause.predicate())) {
            return new PartialList<>(List.of(), 0);
        }
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(this, statement, optimizer.getAnnotations());

        int limit = (int) queryParams.limit();
        int offset = (int) queryParams.offset();
        if (offset < 0) {
            offset = 0;
        }
        if (limit < 0) {
            limit = 0;
        }

        int repoLimit;
        int repoOffset;
        OrderByClause repoOrderByClause;
        OrderByClause orderByClause = statement.getOrderByClause();
        boolean postFilter = isOrderByPath(orderByClause);
        if (postFilter) {
            // we have to merge ordering and batching between memory and
            // repository
            repoLimit = 0;
            repoOffset = 0;
            repoOrderByClause = null;
        } else {
            // fast case, we can use the repository query directly
            repoLimit = limit;
            repoOffset = offset;
            repoOrderByClause = orderByClause;
        }

        // query the repository
        PartialList<Map<String, Object>> projections = transaction.queryAndFetch(evaluator, repoOrderByClause,
                distinctDocuments, repoLimit, repoOffset, countUpTo);

        if (postFilter) {
            // ORDER BY
            if (orderByClause != null) {
                doOrderBy(projections, orderByClause);
            }
            // LIMIT / OFFSET
            if (limit != 0) {
                int size = projections.size();
                int fromIndex = offset > size ? size : offset;
                int toIndex = fromIndex + limit > size ? size : fromIndex + limit;
                projections = projections.subList(fromIndex, toIndex);
            }
        }
        materializePathProjection(projections, statement.getSelectClause());

        return projections;
    }

    protected void materializePathProjection(List<Map<String, Object>> projections, SelectClause selectClause) {
        boolean selectPath = selectClause.elements()
                                         .stream()
                                         .anyMatch(operand -> operand instanceof Reference ref
                                                 && SYS_PATH.equals(ref.name()));
        if (!selectPath) {
            return;
        }
        Set<String> selected = selectClause.elements()
                                           .stream()
                                           .filter(Reference.class::isInstance)
                                           .map(Reference.class::cast)
                                           .map(Reference::name)
                                           .collect(Collectors.toSet());
        for (Map<String, Object> projection : projections) {
            projection.put(SYS_PATH, getPath(projection));
            Stream.of(SYS_NAME, SYS_ID, SYS_PARENT_ID)
                  .filter(name -> !selected.contains(name))
                  .forEach(projection::remove);
        }
    }

    /** Does an ORDER BY clause include sys_path */
    protected boolean isOrderByPath(OrderByClause orderByClause) {
        if (orderByClause == null) {
            return false;
        }
        for (OrderByExpr ob : orderByClause.elements()) {
            if (ob.reference().name().equals(SYS_PATH)) {
                return true;
            }
        }
        return false;
    }

    protected String getPath(Map<String, Object> projection) {
        String name = (String) projection.get(SYS_NAME);
        String parentId = (String) projection.get(SYS_PARENT_ID);
        State state;
        if (parentId == null || (state = transaction.getStateForRead(parentId)) == null) {
            if ("".equals(name)) {
                return "/"; // root
            } else {
                return name; // placeless, no slash
            }
        }
        Deque<String> list = new ArrayDeque<>();
        list.addFirst(name);
        for (;;) {
            name = (String) state.get(SYS_NAME);
            parentId = (String) state.get(SYS_PARENT_ID);
            list.addFirst(name);
            if (parentId == null || (state = transaction.getStateForRead(parentId)) == null) {
                return StringUtils.join(list, '/');
            }
        }
    }

    protected void doOrderBy(List<Map<String, Object>> projections, OrderByClause orderByClause) {
        if (isOrderByPath(orderByClause)) {
            // add path info to do the sort
            for (Map<String, Object> projection : projections) {
                projection.put(KEY_PATH_INTERNAL, getPath(projection));
            }
        }
        projections.sort(new OrderByComparator(orderByClause));
    }

    public static class OrderByComparator implements Comparator<Map<String, Object>> {

        protected final OrderByClause orderByClause;

        public OrderByComparator(OrderByClause orderByClause) {
            // replace sys_path with sys___path for evaluation
            // (we don't want to allow sys_path to be usable anywhere else
            // and resolve to a null value)
            List<OrderByExpr> obl = new ArrayList<>();
            for (OrderByExpr ob : orderByClause.elements()) {
                if (ob.reference().name().equals(SYS_PATH)) {
                    ob = new OrderByExpr(new Reference(KEY_PATH_INTERNAL), ob.isDescending());
                }
                obl.add(ob);
            }
            this.orderByClause = new OrderByClause(obl);
        }

        @Override
        @SuppressWarnings("unchecked")
        public int compare(Map<String, Object> map1, Map<String, Object> map2) {
            for (OrderByExpr ob : orderByClause.elements()) {
                Reference ref = ob.reference();
                boolean desc = ob.isDescending();
                Object v1 = map1.get(ref.name());
                Object v2 = map2.get(ref.name());
                int cmp;
                if (v1 == null) {
                    cmp = v2 == null ? 0 : -1;
                } else if (v2 == null) {
                    cmp = 1;
                } else {
                    if (!(v1 instanceof Comparable)) {
                        throw new QueryParseException("Not a comparable: " + v1);
                    }
                    cmp = ((Comparable<Object>) v1).compareTo(v2);
                }
                if (desc) {
                    cmp = -cmp;
                }
                if (cmp != 0) {
                    return cmp;
                }
                // loop for lexicographical comparison
            }
            return 0;
        }
    }

    @Override
    public IterableQueryResult queryAndFetch(Statement statement, QueryParameters queryParams) {
        final Timer.Context timerContext = queryTimer.time();
        try { // NOSONAR
            PartialList<Map<String, Object>> pl = doQueryAndFetch(statement, queryParams, false, -1, null);
            return new DBSQueryResult(pl);
        } finally {
            long duration = timerContext.stop();
            if (logMinDurationNs >= 0 && duration > logMinDurationNs) {
                String msg = String.format("duration_ms:\t%.2f\t%s\tqueryAndFetch\t%s", duration / 1000000.0,
                        queryParams, statement);
                if (log.isTraceEnabled()) {
                    log.info(msg, new Throwable("Slow query stack trace"));
                } else {
                    log.info(msg);
                }
            }

        }
    }

    @SuppressWarnings("resource") // Time.Context closed by stop()
    @Override
    public PartialList<Map<String, Object>> queryProjection(Statement statement, QueryParameters queryParams,
            boolean distinctDocuments, long countUpTo) {
        final Timer.Context timerContext = queryTimer.time();
        try {
            return doQueryAndFetch(statement, queryParams, distinctDocuments, (int) countUpTo, null);
        } finally {
            long duration = timerContext.stop();
            if (logMinDurationNs >= 0 && duration > logMinDurationNs) {
                String msg = String.format("duration_ms:\t%.2f\t%s\tqueryProjection\t%s", duration / 1000000.0,
                        queryParams, statement);
                if (log.isTraceEnabled()) {
                    log.info(msg, new Throwable("Slow query stack trace"));
                } else {
                    log.info(msg);
                }
            }

        }
    }

    @Override
    public ScrollResult<String> scroll(Statement statement, QueryParameters queryParams, int batchSize,
            int keepAliveSeconds) {
        StatementValidator.rejectSecurityProperties(statement, queryParams.principal(), getSchemaManager());
        List<Operand> projection = new ArrayList<>(statement.getSelectClause().elements());
        projection.add(new Reference(SYS_ID));
        statement = statement.withSelectClause(new SelectClause(projection));
        QueryOptimizer optimizer = new DBSQueryOptimizer(getSchemaManager());
        statement = optimizer.optimize(statement);
        statement = securityPolicyService.addSecurityPolicies(statement, queryParams);
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(this, statement, optimizer.getAnnotations());
        return transaction.scroll(evaluator, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        return transaction.scroll(scrollId);
    }

    private String countUpToAsString(long countUpTo) {
        if (countUpTo > 0) {
            return String.format("count total results up to %d", countUpTo);
        }
        return countUpTo == -1 ? "count total results UNLIMITED" : "";
    }

    protected static class DBSQueryResult implements IterableQueryResult, Iterator<Map<String, Object>> {

        boolean closed;

        protected List<Map<String, Object>> maps;

        protected long totalSize;

        protected long pos;

        protected DBSQueryResult(PartialList<Map<String, Object>> pl) {
            this.maps = pl;
            this.totalSize = pl.totalSize();
        }

        @Override
        public Iterator<Map<String, Object>> iterator() {
            return this; // NOSONAR (this iterable does not support multiple traversals)
        }

        @Override
        public void close() {
            closed = true;
            pos = -1;
        }

        @Override
        public boolean mustBeClosed() {
            return false; // holds no resources
        }

        @Override
        public long size() {
            return totalSize;
        }

        @Override
        public long pos() {
            return pos;
        }

        @Override
        public void skipTo(long pos) {
            if (pos < 0) {
                pos = 0;
            } else if (pos > totalSize) {
                pos = totalSize;
            }
            this.pos = pos;
        }

        @Override
        public boolean hasNext() {
            return pos < totalSize;
        }

        @Override
        public Map<String, Object> next() {
            if (closed || pos == totalSize) {
                throw new NoSuchElementException();
            }
            Map<String, Object> map = maps.get((int) pos);
            pos++;
            return map;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /** Rewrites the deprecated ACL permission suffix in a full property path. */
    public static String compatibilityName(String name) {
        if (!name.startsWith(SYS_ACL + "/")) {
            return name;
        }
        int slash = name.lastIndexOf('/');
        String leaf = name.substring(slash + 1);
        return KEY_ACE_PERMISSION_COMPAT.equals(leaf) ? name.substring(0, slash + 1) + KEY_ACE_PERMISSION : name;
    }

    @Override
    public LockManager getLockManager() {
        return (DBSRepository) repository;
    }

    /**
     * Marks this document id as belonging to a user change.
     *
     * @since 9.2
     */
    public void markUserChange(String id) {
        transaction.markUserChange(id);
    }

    public void setUpdateCondition(String id, UpdateCondition condition, NuxeoPrincipal principal) {
        Operand operand = switch (condition.syntax()) {
            case HXQL -> parseHxqlCondition(condition.condition());
            case EXPR -> parseExprCondition(condition.condition(), principal);
        };
        switch (operand) {
            case Predicate predicate -> transaction.setUpdateConditionPredicate(id, predicate);
            // an EXPR condition may be short-circuited by the evaluator into a constant
            case BooleanLiteral(var value) -> {
                if (!value) {
                    // the condition can never be true, no need to attempt the update
                    throw new ConditionFailedException(id);
                }
                // the condition is always true, so clear any condition previously set for this document
                transaction.setUpdateConditionPredicate(id, null);
            }
            case null, default -> throw new QueryParseException(
                    "Condition is not a predicate: " + condition.condition());
        }
    }

    protected Operand parseHxqlCondition(String hxql) {
        return HXQLParser.parsePredicate(hxql);
    }

    protected Operand parseExprCondition(String expr, NuxeoPrincipal principal) {
        return ExprPredicates.toOperand(expr, principal, getSchemaManager());
    }

    /*
     * ----- Transaction management -----
     */

    @Override
    public void start() {
        transaction.begin();
    }

    @Override
    public void end() {
        transaction.end();
    }

    @Override
    public void commit() {
        transaction.commit();
    }

    @Override
    public void rollback() {
        transaction.rollback();
    }

    @Override
    public List<String> getDirtyBinariesPaths(String id) {
        return transaction.getDirtyBinariesPaths(id);
    }

    private void sendDocumentEvents(Set<String> docIds, NuxeoPrincipal principal) {
        if (docIds.isEmpty()) {
            return;
        }

        // send a core event for all sub/child documents
        long time = System.currentTimeMillis(); // make sure all events have the same time
        EventService eventService = Framework.getService(EventService.class);
        CoreSession coreSession = CoreInstance.getCoreSessionSystem(repository.getName());
        List<String> docIdsList = new ArrayList<>(docIds);
        for (List<String> batch : ListUtils.partition(docIdsList, BATCH_SIZE)){
            List<DeletedChildDocumentRecord> docsData = new ArrayList<>();
            for (String id : batch) {
                IdRef docId = new IdRef(id);
                if (!coreSession.exists(docId) || !coreSession.getDocument(docId).isGovernable()){
                    continue; // skip documents that do not exist or not document model type or not governable
                }

                DocumentModel doc = coreSession.getDocument(docId);
                String contentId = doc.getRepositoryName() + ":" + doc.getId();
                String fileName = doc.getName() != null ? doc.getName().split("\\.")[0] : "";
                DeletedChildDocumentRecord docData = new DeletedChildDocumentRecord(contentId, fileName, doc.getType());
                docsData.add(docData);
            }

            if (!docsData.isEmpty()){
                GovernanceEventContext context = new GovernanceEventContext(coreSession, principal);
                context.setDeletedChildDocumentsData(docsData);
                Event event = new EventImpl(DOCUMENT_REMOVED, context, Event.FLAG_NONE, time);
                eventService.fireEvent(event);
            }
        }
    }
}
