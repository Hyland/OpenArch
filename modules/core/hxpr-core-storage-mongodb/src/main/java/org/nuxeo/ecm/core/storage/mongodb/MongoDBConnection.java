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
package org.nuxeo.ecm.core.storage.mongodb;

import static com.mongodb.ErrorCategory.DUPLICATE_KEY;
import static com.mongodb.ErrorCategory.fromErrorCode;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_MINOR_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_JOB_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBConverter.DEFAULT_CONVERTER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.KEY_ACE_PRINCIPAL;
import static org.nuxeo.ecm.core.api.DocumentConstants.KEY_ACE_STATUS;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_SET;
import static org.nuxeo.ecm.core.storage.mongodb.MongoDBRepository.ONE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.ConditionFailedException;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.lock.LockManager;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;
import org.nuxeo.ecm.core.storage.dbs.DBSConnection;
import org.nuxeo.ecm.core.storage.dbs.DBSConnectionBase;
import org.nuxeo.ecm.core.storage.dbs.DBSExpressionEvaluator;
import org.nuxeo.ecm.core.storage.dbs.DBSStateFlattener;
import org.nuxeo.ecm.core.storage.dbs.DBSTransactionState.ConditionalUpdates;
import org.nuxeo.ecm.core.storage.mongodb.MongoDBConverter.ConditionsAndUpdates;
import com.hyland.openarch.runtime.mongodb.MongoDBOperators;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import com.hyland.openarch.runtime.transaction.TransactionRuntimeException;

import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoQueryException;
import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.MongoWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

/**
 * MongoDB implementation of a {@link DBSConnection}.
 *
 * @since 11.1 (introduce in 5.9.4 as MongoDBRepository)
 */
public class MongoDBConnection extends DBSConnectionBase {

    private static final Logger log = LogManager.getLogger(MongoDBConnection.class);

    // MongoDB error code 86
    private static final String MONGODB_INDEX_KEY_SPECS_CONFLICT = "IndexKeySpecsConflict";

    protected final MongoDBRepository mongoDBRepository;

    protected final MongoCollection<Document> coll;

    /**
     * Recomputed whenever the schema manager is, as it depends on it; the schema-less default outside a transaction.
     */
    protected MongoDBConverter converter = DEFAULT_CONVERTER;

    protected ClientSession clientSession;

    protected boolean transactionStarted;

    public MongoDBConnection(MongoDBRepository repository, boolean initialize) {
        super(repository);
        mongoDBRepository = repository;
        coll = repository.getCollection();
        if (repository.supportsSessions()) {
            clientSession = repository.getClient().startSession();
        } else {
            clientSession = null;
        }
        if (initialize) {
            initRepository();
        }
    }

    @Override
    public void close() {
        if (clientSession != null) {
            clientSession.close();
            clientSession = null;
        }
    }

    // the schema manager is kept invariant for the duration of the transaction
    private SchemaManager schemaManager;

    @Override
    public SchemaManager getSchemaManager() {
        if (schemaManager == null) {
            throw new TransactionRuntimeException("Transaction not started");
        }
        return schemaManager;
    }

    /** Sets the schema manager, and recomputes the converter which depends on it. */
    private void setSchemaManager(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
        converter = schemaManager == null ? DEFAULT_CONVERTER : new MongoDBConverter(schemaManager);
    }

    @Override
    public void begin() {
        setSchemaManager(repository.getSchemaManager());
        if (clientSession != null) {
            clientSession.startTransaction();
            transactionStarted = true;
        }
    }

    @Override
    public void commit() {
        setSchemaManager(null);
        if (clientSession != null) {
            try {
                clientSession.commitTransaction();
            } finally {
                transactionStarted = false;
            }
        }
    }

    @Override
    public void rollback() {
        setSchemaManager(null);
        if (clientSession != null) {
            try {
                clientSession.abortTransaction();
            } finally {
                transactionStarted = false;
            }
        }
    }

    /**
     * Initializes the MongoDB repository.
     */
    protected void initRepository() {
        if (coll.countDocuments(DEFAULT_CONVERTER.filterEq(SYS_ID, getRootId())) == 0) {
            initRoot();
        }
        // always init indexes even if repository still exists, to allow migrations
        initIndexes();
    }

    // create required indexes
    private void initIndexes() {
        // code does explicit queries on those
        createIndex(Indexes.ascending(SYS_PARENT_ID));
        createIndex(Indexes.ascending(SYS_ANCESTOR_IDS));
        createIndex(Indexes.ascending(SYS_READ_ACL));
        createIndex(Indexes.ascending(SYS_PARENT_ID, SYS_NAME), //
                new IndexOptions().unique(true) //
                                  // no unicity check for placeless documents
                                  .partialFilterExpression(Filters.exists(SYS_PARENT_ID)));
        createIndex(Indexes.ascending(SYS_PARENT_ID, SYSVER_VERSION, SYSVER_MINOR_VERSION), //
                new IndexOptions().unique(true) //
                                  .partialFilterExpression(Filters.eq(SYSVER_IS_VERSION, TRUE)));
        // often used in user-generated queries
        createIndex(Indexes.ascending(SYS_PRIMARY_TYPE));
        createIndex(Indexes.ascending(SYS_IS_TRASHED));
        createIndex(Indexes.ascending(SYSGOV_RETAIN_UNTIL));
        createIndex(Indexes.ascending(SYS_BLOB_KEYS));
        if (!repository.isFulltextDisabled()) {
            createIndex(Indexes.ascending(SYS_FULLTEXT_JOB_ID));
        }
        createIndex(Indexes.ascending(SYS_ACL + "." + KEY_ACE_PRINCIPAL));
        createIndex(Indexes.ascending(SYS_ACL + "." + KEY_ACE_STATUS));
        createIndex(Indexes.descending(SYS_MODIFIED));
    }

    private void createIndex(Bson keys) {
        createIndex(keys, new IndexOptions());
    }

    private void createIndex(Bson keys, IndexOptions indexOptions) {
        try {
            coll.createIndex(keys, indexOptions);
            return;
        } catch (MongoCommandException e) {
            if (!MONGODB_INDEX_KEY_SPECS_CONFLICT.equals(e.getErrorCodeName())) {
                throw e;
            }
            // fall through
        }
        // index creation failed due to old incompatible definition; drop it and recreate it
        coll.dropIndex(keys);
        coll.createIndex(keys, indexOptions);
    }

    @Override
    public String generateNewId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void createState(State state) {
        Document doc = converter.stateToBson(state);
        log.trace("MongoDB: CREATE {}: {}", doc.get(MONGODB_ID), doc);
        try {
            insertOne(doc);
        } catch (DuplicateKeyException dke) {
            log.trace("MongoDB:    -> DUPLICATE KEY: {}", doc.get(MONGODB_ID));
            throw new ConcurrentUpdateException(dke);
        }
    }

    @Override
    public void createStates(List<State> states) {
        List<Document> docs = states.stream().map(converter::stateToBson).toList();
        log.trace("MongoDB: CREATE [{}]: {}",
                () -> docs.stream().map(doc -> doc.get(MONGODB_ID).toString()).collect(Collectors.joining(", ")),
                () -> docs);
        try {
            insertMany(docs);
        } catch (MongoBulkWriteException mbwe) {
            List<String> duplicates = mbwe.getWriteErrors()
                                          .stream()
                                          .filter(wr -> DUPLICATE_KEY.equals(fromErrorCode(wr.getCode())))
                                          .map(BulkWriteError::getMessage)
                                          .toList();
            // Avoid hiding any others bulk errors
            if (duplicates.size() == mbwe.getWriteErrors().size()) {
                log.trace("MongoDB:    -> DUPLICATE KEY: {}", duplicates);
                var concurrentUpdateException = new ConcurrentUpdateException("Concurrent update");
                duplicates.forEach(concurrentUpdateException::addInfo);
                throw concurrentUpdateException;
            }

            throw mbwe;
        }
    }

    @Override
    public State readState(String id) {
        return findOne(converter.filterEq(SYS_ID, id));
    }

    @Override
    public State readPartialState(String id, Collection<String> keys) {
        Document fields = new Document();
        keys.forEach(key -> fields.put(converter.keyToBson(key), ONE));
        return findOne(converter.filterEq(SYS_ID, id), fields);
    }

    @Override
    public List<State> readStates(List<String> ids) {
        return findAll(converter.filterIn(SYS_ID, ids));
    }

    @Override
    public void updateState(String id, StateDiff diff, ConditionalUpdates conditionalUpdates) {
        ConditionsAndUpdates conditionsAndUpdates = converter.diffToBson(diff);
        for (Document update : conditionsAndUpdates.updates) {
            // filter on doc id we want to update
            Document filter = new Document();
            converter.putToBson(filter, SYS_ID, id);
            // filter on array updates (see MongoDBConverter.UpdateBuilder.processListDiff)
            converter.addToFilter(filter, conditionsAndUpdates.conditions);
            if (conditionalUpdates != null) {
                // filter on change token
                // assume bson is identical to dbs internals
                // condition works even if value is null
                String key = conditionalUpdates.getConditionKey();
                if (key != null) {
                    Object value = conditionalUpdates.getConditionValue();
                    Document doc = new Document();
                    converter.putToBson(doc, key, value);
                    converter.addToFilter(filter, doc);
                }
                // filter on conditional update predicate
                Predicate conditionPredicate = conditionalUpdates.getConditionPredicate();
                if (conditionPredicate != null) {
                    converter.addToFilter(filter, predicateToDocument(conditionPredicate));
                }
                Document set = (Document) update.computeIfAbsent(MONGODB_SET, k -> new Document());
                set.putAll(conditionalUpdates.getUpdates());
                conditionalUpdates.finish();
            }
            if (filter.size() == 1) {
                log.trace("MongoDB: UPDATE {}: {}", id, update);
            } else {
                log.trace("MongoDB: UPDATE {}: IF {} THEN {}", id, filter, update);
            }
            try {
                UpdateResult w = updateMany(filter, update);
                if (w.getModifiedCount() != 1) {
                    if (conditionalUpdates != null && conditionalUpdates.getConditionPredicate() != null) {
                        // if there's a conditional update requested, assume that the update failure is due to that
                        // (in theory not always true but a good heuristic)
                        log.trace("MongoDB:    -> CONDITION FAILED: {}", id);
                        throw new ConditionFailedException(id);
                    } else {
                        log.trace("MongoDB:    -> CONCURRENT UPDATE: {}", id);
                        throw new ConcurrentUpdateException(id);
                    }
                }
            } catch (MongoWriteException mwe) {
                if (DUPLICATE_KEY.equals(fromErrorCode(mwe.getCode()))) {
                    log.trace("MongoDB:    -> DUPLICATE KEY: {}", id);
                    throw new ConcurrentUpdateException(mwe.getError().getMessage(), mwe);
                }
                throw mwe;
            }
        }
    }

    private Document predicateToDocument(Predicate predicate) {
        MongoDBRepositoryQueryBuilder builder = new MongoDBRepositoryQueryBuilder(converter,
                predicate, null, null, null, getSchemaManager());
        builder.walkTopLevelExpression();
        return builder.getQuery();
    }

    @Override
    public void deleteStates(Set<String> ids) {
        Bson filter = converter.filterIn(SYS_ID, ids);
        log.trace("MongoDB: REMOVE {}", ids);
        DeleteResult w = deleteMany(filter);
        if (w.getDeletedCount() != ids.size()) {
            log.debug("Removed {} docs for {} ids: {}", w::getDeletedCount, ids::size, () -> ids);
        }
    }

    @Override
    public State readChildState(String parentId, String name, Set<String> ignored) {
        Bson filter = getChildQuery(parentId, name, ignored);
        return findOne(filter);
    }

    protected NuxeoException newQueryException(String message, MongoException cause, Bson filter) {
        NuxeoException exc = new NuxeoException(message, cause);
        if (filter != null) {
            String msg;
            if (filter instanceof Document document) {
                msg = document.toJson();
            } else {
                msg = filter.toString();
            }
            exc.addInfo("Filter: " + msg);
        }
        return exc;
    }

    protected NuxeoException newQueryTimeout(MongoException cause, Bson filter) {
        return newQueryException("Query timed out as a result of the maximum operation time being exceeded", cause,
                filter);
    }

    protected NuxeoException newQueryTimeoutClient(MongoException cause, Bson filter) {
        return newQueryException("Query timed out on client side after socketTimeout exceeded", cause, filter);
    }

    protected NuxeoException newQueryFailure(MongoException cause, Bson filter) {
        return newQueryException("Query operation failed on the server", cause, filter);
    }

    protected void logQuery(String id, Bson fields) {
        logQuery(converter.filterEq(SYS_ID, id), fields);
    }

    protected void logQuery(Bson filter, Bson fields) {
        if (fields == null) {
            log.trace("MongoDB: QUERY {}", filter);
        } else {
            log.trace("MongoDB: QUERY {} KEYS {}", filter, fields);
        }
    }

    protected void logQuery(Bson query, Bson fields, Bson orderBy, int limit, int offset) {
        if (orderBy == null) {
            log.trace("MongoDB: QUERY {} KEYS {} OFFSET {} LIMIT {}", query, fields, offset, limit);
        } else {
            log.trace("MongoDB: QUERY {} KEYS {} ORDER BY {} OFFSET {} LIMIT {}", query, fields, orderBy, offset,
                    limit);
        }
    }

    @Override
    public boolean hasChild(String parentId, String name, Set<String> ignored) {
        Document filter = getChildQuery(parentId, name, ignored);
        return exists(filter);
    }

    protected Document getChildQuery(String parentId, String name, Set<String> ignored) {
        Document filter = new Document();
        converter.putToBson(filter, SYS_PARENT_ID, parentId);
        converter.putToBson(filter, SYS_NAME, name);
        addIgnoredIds(filter, ignored);
        return filter;
    }

    protected void addIgnoredIds(Document filter, Set<String> ignored) {
        if (!ignored.isEmpty()) {
            Document notInIds = new Document(MongoDBOperators.NIN, converter.listToBson(SYS_ID, ignored));
            filter.put(MONGODB_ID, notInIds);
        }
    }

    @Override
    public List<State> queryKeyValue(String key, Object value, Set<String> ignored) {
        Document filter = new Document();
        converter.putToBson(filter, key, value);
        addIgnoredIds(filter, ignored);
        return findAll(filter);
    }

    @Override
    public List<State> queryKeyValue(String key1, Object value1, String key2, Object value2, Set<String> ignored) {
        Document filter = new Document();
        converter.putToBson(filter, key1, value1);
        converter.putToBson(filter, key2, value2);
        addIgnoredIds(filter, ignored);
        return findAll(filter);
    }

    @Override
    public Stream<State> getDescendants(String rootId, Set<String> keys) {
        return getDescendants(rootId, keys, 0);
    }

    @Override
    public Stream<State> getDescendants(String rootId, Set<String> keys, int limit) {
        Bson filter = converter.filterEq(SYS_ANCESTOR_IDS, rootId);
        Document fields = new Document();
        fields.put(MONGODB_ID, ONE);
        keys.forEach(key -> fields.put(converter.keyToBson(key), ONE));
        return stream(filter, fields, limit);
    }

    @Override
    public boolean queryKeyValuePresence(String key, String value, Set<String> ignored) {
        Document filter = new Document();
        converter.putToBson(filter, key, value);
        addIgnoredIds(filter, ignored);
        return exists(filter);
    }

    protected boolean exists(Bson filter) {
        return exists(filter, justPresenceField());
    }

    protected boolean exists(Bson filter, Bson projection) {
        logQuery(filter, projection);
        try {
            return find(filter).projection(projection).first() != null;
        } catch (MongoExecutionTimeoutException e) {
            throw newQueryTimeout(e, filter);
        } catch (MongoSocketReadTimeoutException e) {
            throw newQueryTimeoutClient(e, filter);
        } catch (MongoQueryException e) {
            throw newQueryFailure(e, filter);
        }
    }

    protected State findOne(Bson filter) {
        return findOne(filter, null);
    }

    protected State findOne(Bson filter, Bson projection) {
        try (Stream<State> stream = stream(filter, projection)) {
            return stream.findAny().orElse(null);
        }
    }

    protected List<State> findAll(Bson filter) {
        try (Stream<State> stream = stream(filter)) {
            return stream.collect(Collectors.toList()); // must be mutable
        } catch (MongoExecutionTimeoutException e) {
            throw newQueryTimeout(e, filter);
        } catch (MongoSocketReadTimeoutException e) {
            throw newQueryTimeoutClient(e, filter);
        } catch (MongoQueryException e) {
            throw newQueryFailure(e, filter);
        }
    }

    protected Stream<State> stream(Bson filter) {
        return stream(filter, null, 0);
    }

    protected Stream<State> stream(Bson filter, Bson projection) {
        return stream(filter, projection, 0);
    }

    /**
     * Logs, runs request and constructs a closeable {@link Stream} on top of {@link MongoCursor}.
     * <p>
     * We should rely on this method, because it correctly handles cursor closed state.
     * <p>
     * Note: Looping on {@link FindIterable} or {@link MongoIterable} could lead to cursor leaks. This is also the case
     * on some call to {@link MongoIterable#first()}.
     *
     * @return a closeable {@link Stream} instance linked to {@link MongoCursor}
     */
    protected Stream<State> stream(Bson filter, Bson projection, int limit) {
        if (filter == null) {
            // empty filter
            filter = new Document();
        }
        // it's ok if projection is null
        logQuery(filter, projection);

        boolean completedAbruptly = true;
        MongoCursor<Document> cursor = find(filter).limit(limit).projection(projection).iterator();
        try {
            Set<Object> seen = new HashSet<>();
            Stream<State> stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(cursor, 0), false) //
                                                .onClose(cursor::close)
                                                .filter(doc -> seen.add(doc.get(MONGODB_ID)))
                                                // MongoDB cursors may return the same
                                                // object several times
                                                .map(converter::bsonToState);
            // the stream takes responsibility for closing the session
            completedAbruptly = false;
            return stream;
        } catch (MongoExecutionTimeoutException e) {
            throw newQueryTimeout(e, filter); // NOSONAR (cursor is not leaked)
        } catch (MongoSocketReadTimeoutException e) {
            throw newQueryTimeoutClient(e, filter); // NOSONAR (cursor is not leaked)
        } catch (MongoQueryException e) {
            throw newQueryFailure(e, filter); // NOSONAR (cursor is not leaked)
        } finally {
            if (completedAbruptly) {
                cursor.close();
            }
        }
    }

    protected Document justPresenceField() {
        return new Document(MONGODB_ID, ONE);
    }

    @Override
    public PartialList<Map<String, Object>> queryAndFetch(DBSExpressionEvaluator evaluator,
            OrderByClause orderByClause, boolean distinctDocuments, int limit, int offset, int countUpTo) {
        // orderByClause may be null and different from evaluator.getOrderByClause() in case we want to post-filter
        MongoDBRepositoryQueryBuilder builder = new MongoDBRepositoryQueryBuilder(converter,
                evaluator.getExpression(), evaluator.getSelectClause(), orderByClause, evaluator.pathResolver,
                getSchemaManager(), evaluator.getAnnotations());
        builder.walk();
        Document filter = builder.getQuery();
        Bson orderBy = builder.getOrderBy();
        Bson keys = builder.getProjection();
        // Don't do manual projection if there are no projection wildcards, as this brings no new
        // information and is costly. The only difference is several identical rows instead of one.
        boolean manualProjection = !distinctDocuments && builder.hasProjectionWildcard();
        if (manualProjection) {
            // we'll do post-treatment to re-evaluate the query to get proper wildcard projections
            // so we need the full state from the database
            keys = null;
            evaluator.parse(getSchemaManager());
        }

        logQuery(filter, keys, orderBy, limit, offset);

        List<Map<String, Object>> projections;
        long totalSize;
        try (MongoCursor<Document> cursor = find(filter).projection(keys)
                                                        .skip(offset)
                                                        .limit(limit)
                                                        .sort(orderBy)
                                                        .iterator()) {
            projections = new ArrayList<>();
            DBSStateFlattener flattener = new DBSStateFlattener(builder.propertyKeys, getSchemaManager());
            Iterable<Document> docs = () -> cursor;
            for (Document doc : docs) {
                State state = converter.bsonToState(doc);
                if (manualProjection) {
                    projections.addAll(evaluator.matches(state));
                } else {
                    projections.add(flattener.flatten(state));
                }
            }
        } catch (MongoExecutionTimeoutException e) {
            throw newQueryTimeout(e, filter);
        } catch (MongoSocketReadTimeoutException e) {
            throw newQueryTimeoutClient(e, filter);
        } catch (MongoQueryException e) {
            throw newQueryFailure(e, filter);
        }
        if (countUpTo == -1) {
            // count full size
            if (limit == 0) {
                int psize = projections.size();
                if (psize == 0) {
                    totalSize = countDocuments(filter);
                } else {
                    totalSize = psize + (long) offset;
                }
            } else if (manualProjection) {
                totalSize = -1; // unknown due to manual projection
            } else {
                totalSize = countDocuments(filter); // will return -2 if time out during count (e.g. too many results)
            }
        } else if (countUpTo == 0) {
            // no count
            totalSize = -1; // not counted
        } else {
            // count only if less than countUpTo
            if (limit == 0) {
                int psize = projections.size();
                if (psize == 0) {
                    totalSize = countDocuments(filter);
                } else {
                    totalSize = psize + (long) offset;
                }
            } else if (manualProjection) {
                totalSize = -1; // unknown due to manual projection
            } else {
                totalSize = countDocuments(filter, new CountOptions().limit(countUpTo + 1));
                // will return -2 if time out during count (e.g. too many results)
            }
            if (totalSize > countUpTo) {
                totalSize = -2; // truncated
            }
        }
        if (!projections.isEmpty()) {
            log.trace("MongoDB:    -> {}", projections::size);
        }
        return new PartialList<>(projections, totalSize);
    }

    @SuppressWarnings("resource") // cursor is being registered, must not be closed
    @Override
    public ScrollResult<String> scroll(DBSExpressionEvaluator evaluator, int batchSize, int keepAliveSeconds) {
        MongoDBCursorService cursorService = mongoDBRepository.getCursorService();
        cursorService.checkForTimedOutScroll();
        MongoDBRepositoryQueryBuilder builder = new MongoDBRepositoryQueryBuilder(converter,
                evaluator.getExpression(), evaluator.getSelectClause(), null, evaluator.pathResolver,
                getSchemaManager(), evaluator.getAnnotations());
        builder.walk();
        Document filter = builder.getQuery();
        Bson keys = builder.getProjection();
        logQuery(filter, keys, null, 0, 0);

        MongoCursor<Document> cursor;
        try {
            cursor = find(filter).projection(keys).batchSize(batchSize).iterator();
            cursor.hasNext(); // check timeout asap - NOSONAR
        } catch (MongoExecutionTimeoutException e) {
            throw newQueryTimeout(e, filter);
        } catch (MongoSocketReadTimeoutException e) {
            throw newQueryTimeoutClient(e, filter);
        } catch (MongoQueryException e) {
            throw newQueryFailure(e, filter);
        }
        String scrollId = cursorService.registerCursor(cursor, batchSize, keepAliveSeconds);
        return scroll(scrollId);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        MongoDBCursorService cursorService = mongoDBRepository.getCursorService();
        try {
            return cursorService.scroll(scrollId);
        } catch (MongoExecutionTimeoutException e) {
            throw newQueryTimeout(e, null);
        } catch (MongoSocketReadTimeoutException e) {
            throw newQueryTimeoutClient(e, null);
        } catch (MongoQueryException e) {
            throw newQueryFailure(e, null);
        }
    }

    protected static final Bson LOCK_FIELDS = Projections.include(SYS_LOCK_OWNER, SYS_LOCK_CREATED);

    protected static final Bson UNSET_LOCK_UPDATE = Updates.combine(Updates.unset(SYS_LOCK_OWNER),
            Updates.unset(SYS_LOCK_CREATED));

    @Override
    public Lock getLock(String id) {
        logQuery(id, LOCK_FIELDS);
        // we do NOT want to use clientSession here because locks must be non-transactional
        Document res = coll.find(converter.filterEq(SYS_ID, id)).projection(LOCK_FIELDS).first();
        if (res == null) {
            // document not found
            throw new DocumentNotFoundException(id);
        }
        String owner = res.getString(SYS_LOCK_OWNER);
        if (owner == null) {
            // not locked
            return null;
        }
        Instant created = (Instant) converter.bsonToScalar(SYS_LOCK_CREATED, res.get(SYS_LOCK_CREATED));
        return new Lock(owner, created);
    }

    @Override
    public Lock setLock(String id, Lock lock) {
        Bson filter = Filters.and( //
                converter.filterEq(SYS_ID, id), //
                Filters.exists(SYS_LOCK_OWNER, false) // select doc if no lock is set
        );
        Bson setLock = Updates.combine( //
                Updates.set(SYS_LOCK_OWNER, lock.getOwner()), //
                Updates.set(SYS_LOCK_CREATED, converter.valueToBson(SYS_LOCK_CREATED, lock.getCreated())) //
        );
        log.trace("MongoDB: FINDANDMODIFY {} UPDATE {}", filter, setLock);
        // we do NOT want to use clientSession here because locks must be non-transactional
        Document res = coll.findOneAndUpdate(filter, setLock);
        if (res != null) {
            // found a doc to lock
            return null;
        } else {
            // doc not found, or lock owner already set
            // get the old lock
            logQuery(id, LOCK_FIELDS);
            // we do NOT want to use clientSession here because locks must be non-transactional
            Document old = coll.find(converter.filterEq(SYS_ID, id)).projection(LOCK_FIELDS).first();
            if (old == null) {
                // document not found
                throw new DocumentNotFoundException(id);
            }
            String oldOwner = (String) old.get(SYS_LOCK_OWNER);
            Instant oldCreated = (Instant) converter.bsonToScalar(SYS_LOCK_CREATED, old.get(SYS_LOCK_CREATED));
            if (oldOwner != null) {
                return new Lock(oldOwner, oldCreated);
            }
            // no lock -- there was a race condition
            // TODO do better
            throw new ConcurrentUpdateException("Lock " + id);
        }
    }

    @Override
    public Lock removeLock(String id, String owner) {
        Document filter = new Document();
        converter.putToBson(filter, SYS_ID, id);
        if (owner != null) {
            // remove if owner matches or null
            // implements LockManager.canLockBeRemoved inside MongoDB
            Object ownerOrNull = Arrays.asList(owner, null);
            filter.put(SYS_LOCK_OWNER, new Document(MongoDBOperators.IN, ownerOrNull));
        }
        // else unconditional remove
        // remove the lock
        log.trace("MongoDB: FINDANDMODIFY {} UPDATE {}", filter, UNSET_LOCK_UPDATE);
        // we do NOT want to use clientSession here because locks must be non-transactional
        Document old = coll.findOneAndUpdate(filter, UNSET_LOCK_UPDATE);
        if (old != null) {
            // found a doc and removed the lock, return previous lock
            String oldOwner = (String) old.get(SYS_LOCK_OWNER);
            if (oldOwner == null) {
                // was not locked
                return null;
            } else {
                // return previous lock
                var oldCreated = (Instant) converter.bsonToScalar(SYS_LOCK_CREATED, old.get(SYS_LOCK_CREATED));
                return new Lock(oldOwner, oldCreated);
            }
        } else {
            // doc not found, or lock owner didn't match
            // get the old lock
            logQuery(id, LOCK_FIELDS);
            // we do NOT want to use clientSession here because locks must be non-transactional
            old = coll.find(converter.filterEq(SYS_ID, id)).projection(LOCK_FIELDS).first();
            if (old == null) {
                // document not found
                throw new DocumentNotFoundException(id);
            }
            String oldOwner = (String) old.get(SYS_LOCK_OWNER);
            Instant oldCreated = (Instant) converter.bsonToScalar(SYS_LOCK_CREATED, old.get(SYS_LOCK_CREATED));
            if (oldOwner != null) {
                if (!LockManager.canLockBeRemoved(oldOwner, owner)) {
                    // existing mismatched lock, flag failure
                    return new Lock(oldOwner, oldCreated, true);
                }
                // old owner should have matched -- there was a race condition
                // TODO do better
                throw new ConcurrentUpdateException("Unlock " + id);
            }
            // old owner null, should have matched -- there was a race condition
            // TODO do better
            throw new ConcurrentUpdateException("Unlock " + id);
        }
    }

    protected void insertOne(Document document) {
        if (transactionStarted) {
            coll.insertOne(clientSession, document);
        } else {
            coll.insertOne(document);
        }
    }

    protected void insertMany(List<Document> documents) {
        if (transactionStarted) {
            coll.insertMany(clientSession, documents);
        } else {
            coll.insertMany(documents);
        }
    }

    protected UpdateResult updateMany(Bson filter, Bson update) {
        if (transactionStarted) {
            return coll.updateMany(clientSession, filter, update);
        } else {
            return coll.updateMany(filter, update);
        }
    }

    protected DeleteResult deleteMany(Bson filter) {
        if (transactionStarted) {
            return coll.deleteMany(clientSession, filter);
        } else {
            return coll.deleteMany(filter);
        }
    }

    protected FindIterable<Document> find(Bson filter) {
        FindIterable<Document> it;
        if (transactionStarted) {
            it = coll.find(clientSession, filter);
        } else {
            it = coll.find(filter);
        }
        it.maxTime(getMaxTimeMs(), MILLISECONDS);
        return it;
    }

    protected long getMaxTimeMs() {
        long ttl = TransactionHelper.getTransactionTimeToLive();
        // add some extra millis because 0 maxTime is not taken in account
        return ttl < 0 ? mongoDBRepository.maxTimeMS : ttl * 1000 + 250;
    }

    protected long countDocuments(Bson filter) {
        return countDocuments(filter, new CountOptions());
    }

    protected long countDocuments(Bson filter, CountOptions options) {
        options.maxTime(getMaxTimeMs(), MILLISECONDS);
        try {
            if (transactionStarted) {
                return coll.countDocuments(clientSession, filter, options);
            } else {
                return coll.countDocuments(filter, options);
            }

        } catch (MongoExecutionTimeoutException e) {
            log.warn("MongoDB timed out when computing total count with filters {}", filter::toString);
            return -2;
        }
    }

}
