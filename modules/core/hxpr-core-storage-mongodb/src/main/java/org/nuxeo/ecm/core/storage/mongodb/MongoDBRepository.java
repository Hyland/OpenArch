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

import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSEMB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSGRAPH;
import static org.nuxeo.ecm.core.api.DocumentConstants.CINCONTEXT_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.CINREMOTE_INGEST_EVENT_VERSION;
import static org.nuxeo.ecm.core.api.DocumentConstants.CINREMOTE_INGEST_RAW;
import static org.nuxeo.ecm.core.api.DocumentConstants.CINREMOTE_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.CONTEXT_KG_PROCESSING_STATES;
import static org.nuxeo.ecm.core.api.DocumentConstants.CONTEXT_PROPERTIES;
import static org.nuxeo.ecm.core.api.DocumentConstants.CONTEXT_PROPERTY_NAMES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_CINCONTEXT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_CINREMOTE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGRAPH_GRAPH_DB_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGRAPH_ONTOLOGY_REFS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGRAPH_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_ADDITIONAL_DATA;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_CONTENT_ITEM_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_DATA;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_FILL_COLOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_FILLABLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_FIX_ROTATION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_HEIGHT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_MOVABLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_OPACITY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_RESIZABLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_SCALE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_STROKE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_WIDTH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_X;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_Y;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_BOOKMARK_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_ADDITIONAL_DATA;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_CONTENT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_DATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_EXTERNAL_AUTHOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_EXTERNAL_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_IS_EXTERNAL_COMMENT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_UPDATED_AT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_HIGHLIGHT_HIGHLIGHTS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_HIGHLIGHT_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_IMAGE_HREF;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_IMAGE_PADDING;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_IMAGE_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_IMAGE_RADIUS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_LINE_CAP;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_LINE_MARKER_END;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_LINE_MARKER_START;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_LINE_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_LINE_REVERSE_X;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_LINE_REVERSE_Y;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_PAGE_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATION_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATABLE_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_ANNOTATABLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_BOOKMARK_ADDITIONAL_DATA;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_BOOKMARK_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_BOOKMARK_PAGE_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_REDACTABLE_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_REDACTABLE_SOURCE_DOCUMENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_REDACTABLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_PATH_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_PATH_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_BOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_FONT_COLOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_FONT_FAMILY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_FONT_SIZE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_HORIZONTALLY_CENTER_BOX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_ITALIC;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_LINE_HEIGHT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_LINE_HEIGHT_MULTIPLIER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_OVERFLOW;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_PADDING;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_PREFIX;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_RADIUS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_STRIKE_THROUGH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_ALIGNMENT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_UNDERLINE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_TEXT_VERTICALLY_CENTER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_ANNOTATION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_ANNOTATABLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_BOOKMARK;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_COMMENT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_HIGHLIGHT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_IMAGE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_LINE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_REDACTABLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSMD_TEXT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSTEM;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.WRITE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_GRAPHABLE;
import static org.nuxeo.ecm.core.schema.MixinNames.CIN_CONTEXT;
import static org.nuxeo.ecm.core.schema.MixinNames.CIN_REMOTE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_HAS_EMBEDDINGS;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_VERSIONED_WITH_PARENT;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_ANNOTATABLE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_HL;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_IMAGE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_LINE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_PATH;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_REDACTABLE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_MD_TEXT;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.CONSTRAINTS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.EXTENDS;
import static org.nuxeo.ecm.core.schema.TypeConstants.BLOB_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_MD_ANNOTATION;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_MD_BOOKMARK;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_MD_COMMENT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FIELDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.LIST;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXINS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXIN_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PREFIX;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SCHEMAS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.TYPES;
import static org.nuxeo.ecm.core.schema.TypeServiceImpl.CONFIG_TYPES;
import static org.nuxeo.ecm.core.security.RetentionSecurityPolicy.RETENTION_ENGINE_NAME;
import static org.nuxeo.ecm.core.security.SecurityServiceImpl.CONFIG_SECURITY;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.schema.types.primitives.AnyType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryBase;
import org.nuxeo.ecm.core.storage.dbs.DBSSession;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mongodb.MongoDBConnectionService;

import com.hyland.hxpr.config.ConfigurationService;
import com.hyland.hxpr.config.ConfigurationServiceIO;
import com.mongodb.MongoClientException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;

/**
 * MongoDB implementation of a {@link Repository}.
 *
 * @since 5.9.4
 */
public class MongoDBRepository extends DBSRepositoryBase implements ConfigurationServiceIO {

    private static final Logger log = LogManager.getLogger(MongoDBRepository.class);

    public static final Long LONG_ZERO = Long.valueOf(0);

    public static final Double ZERO = Double.valueOf(0);

    public static final Double ONE = Double.valueOf(1);

    public static final String MONGODB_ID = "_id";

    public static final String MONGODB_INC = "$inc";

    public static final String MONGODB_SET = "$set";

    public static final String MONGODB_UNSET = "$unset";

    public static final String MONGODB_PUSH = "$push";

    /** @since 11.5 */
    public static final String MONGODB_PULLALL = "$pullAll";

    public static final String MONGODB_EACH = "$each";

    public static final String MONGODB_META = "$meta";

    public static final String MONGODB_TYPE = "$type";

    public static final String MONGODB_TEXT_SCORE = "textScore";

    public static final String FULLTEXT_INDEX_NAME = "fulltext";

    public static final String LANGUAGE_FIELD = "__language";

    public static final String SYSGRAPH_STRING_LIST_TYPE = "sysgraph#stringList";

    // Content Intelligence composite type names — see migrateCinModel() below.
    static final String CINREMOTE_STRING_LIST_TYPE = "cinremote#stringList";

    static final String CINREMOTE_CIN_BLOB_TYPE = "cinremote#cinBlob";

    static final String CINREMOTE_CIN_BLOB_LIST_TYPE = "cinremote#cinBlobList";

    static final String CINREMOTE_CONTENT_DIGEST_TYPE = "cinremote#contentDigest";

    static final String CINREMOTE_CONTENT_DIGEST_LIST_TYPE = "cinremote#contentDigestList";

    static final String CINCONTEXT_STRING_LIST_TYPE = "cincontext#stringList";

    static final String CINCONTEXT_KG_PROCESSING_STATE_TYPE = "cincontext#kgProcessingState";

    static final String CINCONTEXT_KG_PROCESSING_STATE_LIST_TYPE = "cincontext#kgProcessingStateList";

    // KG-config migration constants — see migrateKgConfig() below.

    static final String KGCONFIG_STORE_NAME = "kgconfig";

    static final String KG_DOMAINS_LEGACY = "domains";

    static final String KG_GRAPH_DBS = "graphDBs";

    static final String KG_ONTOLOGIES = "ontologies";

    static final String KG_DOMAIN_ID_LEGACY = "domainId";

    static final String KG_DOMAIN_NAME_LEGACY = "domain";

    static final String KG_GRAPH_DB_ID = "graphDBId";

    static final String KG_GRAPH_DB_NAME = "graphDBName";

    static final String KG_VERSION = "version";

    static final String KG_VERSION_DEFAULT_LEGACY = "v1";

    static final String KG_CONNECTION_CONFIG = "connectionConfig";

    static final String KG_ONTOLOGY_ROUTES = "ontologyRoutes";

    /**
     * Default maximum execution time for a query.
     *
     * @since 11.1
     */
    protected static final Duration MAX_TIME_DEFAULT = Duration.ofHours(1);

    protected final MongoDBRepositoryDescriptor descriptor;

    protected final MongoClient mongoClient;

    protected final MongoDBConverter converter;

    protected final MongoDBCursorService cursorService;

    protected final MongoCollection<Document> coll;

    protected final MongoCollection<Document> configColl;

    protected final boolean supportsSessions;

    protected final boolean supportsTransactions;

    /**
     * Maximum execution time for a query when outside of a transaction.
     *
     * @since 11.1
     */
    protected final long maxTimeMS;

    public MongoDBRepository(MongoDBRepositoryDescriptor descriptor) {
        super(descriptor.name, descriptor);
        this.descriptor = descriptor;

        MongoDBConnectionService mongoService = Framework.getService(MongoDBConnectionService.class);
        mongoClient = mongoService.getClient();
        MongoDatabase database = mongoService.getDatabase();
        coll = database.getCollection(repositoryName);
        configColl = database.getCollection(repositoryName + ".config");
        maxTimeMS = MAX_TIME_DEFAULT.toMillis();

        converter = new MongoDBConverter(DBSSession.TRUE_OR_NULL_BOOLEAN_KEYS);
        cursorService = new MongoDBCursorService(converter);

        registerConfigurationServiceIO();
        initialize(descriptor);

        // check session and transaction support
        boolean hasSessions;
        boolean hasTransactions;
        try (ClientSession session = mongoClient.startSession()) {
            hasSessions = true;
            try {
                session.startTransaction();
                session.abortTransaction();
                hasTransactions = true;
            } catch (MongoClientException e) {
                hasTransactions = false;
            }
        } catch (MongoClientException ee) {
            // startSession may throw
            // "Sessions are not supported by the MongoDB cluster to which this client is connected"
            // startTransaction may throw
            hasSessions = false;
            hasTransactions = false;
        }
        // TODO: reactivate sessions/transactions when they can be better tested
        hasSessions = false;
        hasTransactions = false;
        supportsSessions = hasSessions;
        supportsTransactions = hasTransactions;
        initRepository();
    }

    @Override
    public void shutdown() {
        unregisterConfigurationServiceIO();
        cursorService.clear();
        super.shutdown();
    }

    /**
     * Registers this as {@link ConfigurationService} persistence engine which will callback to
     * {@link ConfigurationServiceIO} methods ({@link #readConfiguration} and {@link #writeConfiguration})
     */
    private void registerConfigurationServiceIO() {
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            configService.registerConfigurationServiceIO(repositoryName, this);
        }
    }

    private void unregisterConfigurationServiceIO() {
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            configService.unregisterConfigurationServiceIO(repositoryName);
        }
    }

    // ConfigurationServiceIO
    @Override
    public Map<String, Object> readConfiguration(String configName) {
        Bson filter = Filters.eq(MONGODB_ID, configName);
        Document doc = configColl.find(filter).first();
        if (doc != null) {
            doc.remove(MONGODB_ID);
            if (migrateConfiguration(configName, doc)) {
                // it's been fixed up, write it back so that migration is persisted
                writeConfiguration(configName, doc);
                log.info("Old configuration has been migrated for: " + configName);
            }
        }
        return doc;
    }

    // ConfigurationServiceIO
    @Override
    public void writeConfiguration(String configName, Map<String, Object> config) {
        Objects.requireNonNull(config);
        Bson filter = Filters.eq(MONGODB_ID, configName);
        configColl.replaceOne(filter, new Document(config), new ReplaceOptions().upsert(true));
    }

    private boolean migrateConfiguration(String configName, Map<String, Object> config) {
        return switch (configName) {
        case CONFIG_TYPES -> migrateTypes(config);
        case CONFIG_SECURITY -> migrateSecurity(config);
        case KGCONFIG_STORE_NAME -> migrateKgConfig(config);
        default -> false;
        };
    }

    // KG-config migration

    /**
     * Migrates the {@code kgconfig} document from the legacy {@code domains[]} shape:
     * <pre>{ domains: [{ domainId, domain, version?, connectionConfig,
     *     active_ontology, namespaceId?, namespaceUserId? }] }</pre>
     * to the target shape:
     * <pre>{ graphDBs: [{ graphDBId, graphDBName, version, connectionConfig,
     *     ontologyRoutes }], ontologies: [...] }</pre>
     *
     * <p>Field mapping is straightforward; a few non-obvious choices:
     * <ul>
     * <li>{@code connectionConfig} is passed through unchanged — the consumer accepts both the
     *     legacy JSON-string form and a nested object.</li>
     * <li>{@code ontologyRoutes} is set to {@code null}, matching what a freshly-created
     *     graphDB stores (not {@code []}).</li>
     * <li>{@code active_ontology} is dropped — the new model keeps ontologies in a separate
     *     top-level array keyed by {@code ontologyId} which can't be reconstructed from the
     *     legacy URL alone.</li>
     * </ul>
     *
     * <p>Returns {@code true} iff the map was modified. Refuses to touch a document carrying
     * both shapes simultaneously, or whose {@code domains} value is non-null but not a
     * {@code List} — those cases are logged and require manual cleanup.
     */
    @SuppressWarnings("unchecked")
    static boolean migrateKgConfig(Map<String, Object> config) {
        boolean hasLegacy = config.containsKey(KG_DOMAINS_LEGACY);
        boolean hasGraphDBs = config.containsKey(KG_GRAPH_DBS);

        if (hasLegacy && hasGraphDBs) {
            log.error("kgconfig has both legacy '{}' and new '{}' keys — refusing to migrate. "
                    + "Manual cleanup required.", KG_DOMAINS_LEGACY, KG_GRAPH_DBS);
            return false;
        }

        Object legacyDomains = hasLegacy ? config.get(KG_DOMAINS_LEGACY) : null;
        if (legacyDomains != null && !(legacyDomains instanceof List)) {
            log.error("kgconfig '{}' is not a list (got {}) — refusing to migrate. "
                    + "Manual cleanup required.", KG_DOMAINS_LEGACY,
                    legacyDomains.getClass().getSimpleName());
            return false;
        }

        boolean changed = false;

        if (hasLegacy) {
            List<?> domains = legacyDomains instanceof List ? (List<?>) legacyDomains : List.of();
            List<Map<String, Object>> graphDBs = new ArrayList<>();
            int skipped = 0;
            for (Object element : domains) {
                if (!(element instanceof Map)) {
                    if (element != null) {
                        log.warn("Skipping malformed kgconfig domain entry of type {}",
                                element.getClass().getSimpleName());
                        skipped++;
                    }
                    continue;
                }
                Map<String, Object> domain = (Map<String, Object>) element;
                Map<String, Object> graphDB = new HashMap<>();
                graphDB.put(KG_GRAPH_DB_ID, domain.get(KG_DOMAIN_ID_LEGACY));
                graphDB.put(KG_GRAPH_DB_NAME, domain.get(KG_DOMAIN_NAME_LEGACY));
                Object version = domain.get(KG_VERSION);
                graphDB.put(KG_VERSION, version != null ? version : KG_VERSION_DEFAULT_LEGACY);
                graphDB.put(KG_CONNECTION_CONFIG, domain.get(KG_CONNECTION_CONFIG));
                graphDB.put(KG_ONTOLOGY_ROUTES, null);
                graphDBs.add(graphDB);
            }

            config.put(KG_GRAPH_DBS, graphDBs);
            config.remove(KG_DOMAINS_LEGACY);
            log.info("Migrated kgconfig document: {} legacy domain entries → graphDBs[] ({} skipped)",
                    graphDBs.size(), skipped);
            changed = true;
        } else {
            changed |= ensureMutableList(config, KG_GRAPH_DBS);
        }
        changed |= ensureMutableList(config, KG_ONTOLOGIES);

        return changed;
    }

    /** Replaces {@code config[key]} with a fresh empty list when it's absent, null, or not a List. */
    private static boolean ensureMutableList(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            return false;
        }
        config.put(key, new ArrayList<>());
        return true;
    }

    /**
     * Migrates an existing content model to a new one. See TestConfigTypesMigration for testing
     */
    @SuppressWarnings("unchecked")
    private boolean migrateTypes(Map<String, Object> config) {
        Map<String, Object> types = (Map<String, Object>) config.computeIfAbsent(TYPES, k -> new HashMap<>());
        Map<String, Object> schemas = (Map<String, Object>) config.computeIfAbsent(SCHEMAS, k -> new HashMap<>());
        Map<String, Object> mixins = (Map<String, Object>) config.computeIfAbsent(MIXIN_TYPES, k -> new HashMap<>());
        Map<String, Object> primaryTypes = (Map<String, Object>) config.computeIfAbsent(PRIMARY_TYPES,
                k -> new HashMap<>());

        boolean needsMigration = false;

        // ADD migration code here and set needsMigration to true if any changes were made
         needsMigration = migrateMetadataTypes(config);
         needsMigration |= migrateRenditionSchema(schemas);

        // Knowledge Graph stringList type
        if (types.putIfAbsent(SYSGRAPH_STRING_LIST_TYPE, Map.of(LIST, StringType.ID)) == null) {
            needsMigration = true;
        }

        // Content Intelligence model (CinRemote / CinContext) for existing repositories
        needsMigration |= migrateCinModel(types, schemas, mixins);

        return needsMigration;
    }

    /**
     * Seeds the Content Intelligence content model (the {@code cinremote} / {@code cincontext} schemas, their
     * composite types and the {@code CinRemote} / {@code CinContext} mixins) into an existing repository whose stored
     * content model predates them.
     * <p>
     * New repositories get this model natively from {@code CoreExtensions.xml} + the {@code cinremote.xsd} /
     * {@code cincontext.xsd} schemas; this migration only backfills repositories provisioned before the model shipped
     * in the product. It is idempotent: every entry is added with {@code putIfAbsent} / {@code computeIfAbsent}, so an
     * already-seeded model is left untouched and reports no change.
     *
     * @return {@code true} if any entry was added (a migration is needed), {@code false} otherwise
     */
    private boolean migrateCinModel(Map<String, Object> types, Map<String, Object> schemas,
            Map<String, Object> mixins) {
        boolean migrated = false;

        // Composite types referenced by the cinremote / cincontext schemas.
        migrated |= types.putIfAbsent(CINREMOTE_STRING_LIST_TYPE, Map.of(LIST, StringType.ID)) == null;
        migrated |= types.putIfAbsent(CINREMOTE_CIN_BLOB_TYPE, Map.of(FIELDS, Map.of(
                "blob", BLOB_TYPE,
                "label", StringType.ID,
                "originalMimeType", StringType.ID,
                "originalFilename", StringType.ID,
                "originalLength", LongType.ID,
                "originalDigest", StringType.ID,
                "uploadedMimeType", StringType.ID))) == null;
        migrated |= types.putIfAbsent(CINREMOTE_CIN_BLOB_LIST_TYPE,
                Map.of(LIST, CINREMOTE_CIN_BLOB_TYPE)) == null;
        migrated |= types.putIfAbsent(CINREMOTE_CONTENT_DIGEST_TYPE, Map.of(FIELDS, Map.of(
                "contentName", StringType.ID,
                "digestValue", StringType.ID))) == null;
        migrated |= types.putIfAbsent(CINREMOTE_CONTENT_DIGEST_LIST_TYPE,
                Map.of(LIST, CINREMOTE_CONTENT_DIGEST_TYPE)) == null;
        migrated |= types.putIfAbsent(CINCONTEXT_STRING_LIST_TYPE, Map.of(LIST, StringType.ID)) == null;
        migrated |= types.putIfAbsent(CINCONTEXT_KG_PROCESSING_STATE_TYPE, Map.of(FIELDS, Map.of(
                "ontologyId", StringType.ID,
                "state", BLOB_TYPE,
                "status", StringType.ID))) == null;
        migrated |= types.putIfAbsent(CINCONTEXT_KG_PROCESSING_STATE_LIST_TYPE,
                Map.of(LIST, CINCONTEXT_KG_PROCESSING_STATE_TYPE)) == null;

        // cinremote schema (prefix "cin").
        migrated |= schemas.putIfAbsent(SCHEMA_CINREMOTE, Map.of(PREFIX, CINREMOTE_PREFIX, FIELDS,
                cinRemoteFields())) == null;

        // cincontext schema (prefix "cinctx").
        migrated |= schemas.putIfAbsent(SCHEMA_CINCONTEXT, Map.of(PREFIX, CINCONTEXT_PREFIX, FIELDS, Map.of(
                CONTEXT_PROPERTIES, AnyType.ID,
                CONTEXT_PROPERTY_NAMES, CINCONTEXT_STRING_LIST_TYPE,
                CONTEXT_KG_PROCESSING_STATES, CINCONTEXT_KG_PROCESSING_STATE_LIST_TYPE))) == null;

        // CinRemote / CinContext mixins.
        migrated |= mixins.putIfAbsent(CIN_REMOTE, Map.of(SCHEMAS, List.of(SCHEMA_CINREMOTE))) == null;
        migrated |= mixins.putIfAbsent(CIN_CONTEXT, Map.of(SCHEMAS, List.of(SCHEMA_CINCONTEXT))) == null;

        return migrated;
    }

    /**
     * Builds the {@code cinremote} schema field-to-type map. Kept as a helper because the schema has more than the
     * {@link Map#of} 10-entry limit.
     */
    private static Map<String, Object> cinRemoteFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("cin_id", StringType.ID);
        fields.put("cin_paths", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_directAncestors", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_allAncestors", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_dateCreated", DateType.ID);
        fields.put("cin_createdBy", StringType.ID);
        fields.put("cin_dateModified", DateType.ID);
        fields.put("cin_modifiedBy", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_type", StringType.ID);
        fields.put("cin_sourceId", StringType.ID);
        fields.put("cin_aspects", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_read", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_deny", CINREMOTE_STRING_LIST_TYPE);
        fields.put("cin_orgUnit", StringType.ID);
        fields.put("cin_isOrgUnit", BooleanType.ID);
        fields.put("cin_ingest", AnyType.ID);
        fields.put("cin_digests", CINREMOTE_CONTENT_DIGEST_LIST_TYPE);
        fields.put("cin_ingestProperties", AnyType.ID);
        fields.put("cin_ingestPropertyNames", CINREMOTE_STRING_LIST_TYPE);
        fields.put(CINREMOTE_INGEST_EVENT_VERSION, LongType.ID);
        fields.put("cin_blobs", CINREMOTE_CIN_BLOB_LIST_TYPE);
        fields.put(CINREMOTE_INGEST_RAW, BLOB_TYPE);
        return fields;
    }

     /**
     * Migrates an old content model that doesn't contain document metadata schemas.
     */
    @SuppressWarnings("unchecked")
    private boolean migrateMetadataTypes(Map<String, Object> config) {
        Map<String, Object> schemas = (Map<String, Object>) config.computeIfAbsent(SCHEMAS, k -> new HashMap<>());
        Map<String, Object> mixins = (Map<String, Object>) config.computeIfAbsent(MIXIN_TYPES, k -> new HashMap<>());
        Map<String, Object> primaryTypes = (Map<String, Object>) config.computeIfAbsent(PRIMARY_TYPES, k -> new HashMap<>());

        int configSizeBeforeMigration = schemas.size() + mixins.size() + primaryTypes.size();

        boolean migrated = migrateMetadataSchemas(schemas);
        migrateMetadataMixins(mixins);
        migrated |= migrateMetadataPrimaryTypes(primaryTypes);

        int configSizeAfterMigration = schemas.size() + mixins.size() + primaryTypes.size();
        return configSizeAfterMigration > configSizeBeforeMigration || migrated;
    }

    /**
     * Adds document metadata schemas if they don't exist. Extends existing schemas with new fields if needed.
     */
    @SuppressWarnings("unchecked")
    private boolean migrateMetadataSchemas(Map<String, Object> schemas) {
        boolean migrated = false;

        // Add document metadata schemas:
        schemas.computeIfAbsent(SCHEMA_SYSMD_BOOKMARK, k -> Map.of(
                PREFIX, SYSMD_BOOKMARK_PREFIX,
                FIELDS, Map.of(
                        SYSMD_BOOKMARK_NAME, StringType.ID,
                        SYSMD_BOOKMARK_PAGE_ID, LongType.ID,
                        SYSMD_BOOKMARK_ADDITIONAL_DATA, StringType.ID)));

        schemas.computeIfAbsent(SCHEMA_SYSMD_ANNOTATION, k -> {
            Map<String, String> annotationFields = new HashMap<>();
            annotationFields.put(SYSMD_ANNOTATION_NAME, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_PAGE_ID, LongType.ID);
            annotationFields.put(SYSMD_ANNOTATION_TYPE, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_CONTENT_ITEM_ID, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_ID, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_X, DoubleType.ID);
            annotationFields.put(SYSMD_ANNOTATION_Y, DoubleType.ID);
            annotationFields.put(SYSMD_ANNOTATION_HEIGHT, DoubleType.ID);
            annotationFields.put(SYSMD_ANNOTATION_WIDTH, DoubleType.ID);
            annotationFields.put(SYSMD_ANNOTATION_FILL_COLOR, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_FILLABLE, BooleanType.ID);
            annotationFields.put(SYSMD_ANNOTATION_STROKE, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_OPACITY, DoubleType.ID);
            annotationFields.put(SYSMD_ANNOTATION_RESIZABLE, BooleanType.ID);
            annotationFields.put(SYSMD_ANNOTATION_MOVABLE, BooleanType.ID);
            annotationFields.put(SYSMD_ANNOTATION_DATA, StringType.ID);
            annotationFields.put(SYSMD_ANNOTATION_SCALE, DoubleType.ID);
            annotationFields.put(SYSMD_ANNOTATION_FIX_ROTATION, BooleanType.ID);
            annotationFields.put(SYSMD_ANNOTATION_ADDITIONAL_DATA, StringType.ID);
            return Map.of(
                    PREFIX, SYSMD_ANNOTATION_PREFIX,
                    FIELDS, annotationFields);
        });

        Object commentSchema = schemas.get(SCHEMA_SYSMD_COMMENT);
        if (commentSchema == null) {
            Map<String, Object> newCommentFields = new HashMap<>();
            newCommentFields.put(SYSMD_COMMENT_CONTENT, StringType.ID);
            newCommentFields.put(SYSMD_COMMENT_EXTERNAL_AUTHOR, StringType.ID);
            newCommentFields.put(SYSMD_COMMENT_STATE, StringType.ID);
            newCommentFields.put(SYSMD_COMMENT_ADDITIONAL_DATA, StringType.ID);
            newCommentFields.put(SYSMD_COMMENT_DATE, DateType.ID);
            newCommentFields.put(SYSMD_COMMENT_UPDATED_AT, DateType.ID);
            newCommentFields.put(SYSMD_COMMENT_IS_EXTERNAL_COMMENT, BooleanType.ID);
            newCommentFields.put(SYSMD_COMMENT_EXTERNAL_ID, StringType.ID);
            schemas.put(SCHEMA_SYSMD_COMMENT, Map.of(
                    PREFIX, SYSMD_COMMENT_PREFIX,
                    FIELDS, newCommentFields));
        } else {
            Map<String, Object> commentSchemaMap = (Map<String, Object>) commentSchema;
            if (!(commentSchemaMap instanceof HashMap)) {
                commentSchemaMap = new HashMap<>(commentSchemaMap);
                schemas.put(SCHEMA_SYSMD_COMMENT, commentSchemaMap);
            }
            Map<String, Object> fields = (Map<String, Object>) commentSchemaMap.get(FIELDS);
            if (fields == null) {
                fields = new HashMap<>();
                commentSchemaMap.put(FIELDS, fields);
            } else if (!(fields instanceof HashMap)) {
                fields = new HashMap<>(fields);
                commentSchemaMap.put(FIELDS, fields);
            }
            int sizeBefore = fields.size();
            fields.computeIfAbsent(SYSMD_COMMENT_CONTENT, k -> StringType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_EXTERNAL_AUTHOR, k -> StringType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_STATE, k -> StringType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_ADDITIONAL_DATA, k -> StringType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_DATE, k -> DateType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_UPDATED_AT, k -> DateType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_IS_EXTERNAL_COMMENT, k -> BooleanType.ID);
            fields.computeIfAbsent(SYSMD_COMMENT_EXTERNAL_ID, k -> StringType.ID);
            migrated = fields.size() > sizeBefore;
        }

        schemas.computeIfAbsent(SCHEMA_SYSMD_TEXT, k -> {
            Map<String, String> textFields = new HashMap<>();
            textFields.put(SYSMD_TEXT, StringType.ID);
            textFields.put(SYSMD_TEXT_FONT_FAMILY, StringType.ID);
            textFields.put(SYSMD_TEXT_FONT_SIZE, StringType.ID);
            textFields.put(SYSMD_TEXT_FONT_COLOR, StringType.ID);
            textFields.put(SYSMD_TEXT_ALIGNMENT, StringType.ID);
            textFields.put(SYSMD_TEXT_VERTICALLY_CENTER, BooleanType.ID);
            textFields.put(SYSMD_TEXT_HORIZONTALLY_CENTER_BOX, BooleanType.ID);
            textFields.put(SYSMD_TEXT_ITALIC, BooleanType.ID);
            textFields.put(SYSMD_TEXT_BOLD, BooleanType.ID);
            textFields.put(SYSMD_TEXT_UNDERLINE, BooleanType.ID);
            textFields.put(SYSMD_TEXT_STRIKE_THROUGH, BooleanType.ID);
            textFields.put(SYSMD_TEXT_OVERFLOW, BooleanType.ID);
            textFields.put(SYSMD_TEXT_PADDING, DoubleType.ID);
            textFields.put(SYSMD_TEXT_RADIUS, DoubleType.ID);
            textFields.put(SYSMD_TEXT_LINE_HEIGHT, DoubleType.ID);
            textFields.put(SYSMD_TEXT_LINE_HEIGHT_MULTIPLIER, DoubleType.ID);
            return Map.of(
                    PREFIX, SYSMD_TEXT_PREFIX,
                    FIELDS, textFields);
        });

        schemas.computeIfAbsent(SCHEMA_SYSMD_LINE, k -> Map.of(
                PREFIX, SYSMD_LINE_PREFIX,
                FIELDS, Map.of(
                        SYSMD_LINE_REVERSE_X, BooleanType.ID,
                        SYSMD_LINE_REVERSE_Y, BooleanType.ID,
                        SYSMD_LINE_MARKER_START, StringType.ID,
                        SYSMD_LINE_MARKER_END, StringType.ID,
                        SYSMD_LINE_CAP, StringType.ID)));

        schemas.computeIfAbsent(SCHEMA_SYSMD_IMAGE, k -> Map.of(
                PREFIX, SYSMD_IMAGE_PREFIX,
                FIELDS, Map.of(
                        SYSMD_IMAGE_HREF, StringType.ID,
                        SYSMD_IMAGE_PADDING, DoubleType.ID,
                        SYSMD_IMAGE_RADIUS, DoubleType.ID)));

        schemas.computeIfAbsent(SCHEMA_SYSMD_HIGHLIGHT, k -> Map.of(
                PREFIX, SYSMD_HIGHLIGHT_PREFIX,
                FIELDS, Map.of(
                        SYSMD_HIGHLIGHT_HIGHLIGHTS, StringType.ID)));

        schemas.computeIfAbsent(SCHEMA_SYSMD_PATH, k -> Map.of(
                PREFIX, SYSMD_PATH_PREFIX,
                FIELDS, Map.of(
                        SYSMD_PATH_PATH, StringType.ID)));

        schemas.computeIfAbsent(SCHEMA_SYSMD_ANNOTATABLE, k -> Map.of(
                PREFIX, SYSMD_ANNOTATABLE_PREFIX,
                FIELDS, Map.of(
                        SYSMD_ANNOTATABLE_STATE, StringType.ID)));

        schemas.computeIfAbsent(SCHEMA_SYSMD_REDACTABLE, k -> Map.of(
                PREFIX, SYSMD_REDACTABLE_PREFIX,
                FIELDS, Map.of(
                        SYSMD_REDACTABLE_SOURCE_DOCUMENT_ID, StringType.ID,
                        SYSMD_REDACTABLE_STATE, StringType.ID)));

        // Embeddings
        schemas.computeIfAbsent(SCHEMA_SYSEMB, k -> Map.of(
                PREFIX, SYSEMB_PREFIX,
                FIELDS, Map.of(
                        SYSEMB_EMBEDDINGS, Map.of(
                                EXTENDS, BLOB_TYPE,
                                CONSTRAINTS, Map.of("nullable", false)))));

        // Knowledge Graph
        schemas.computeIfAbsent(SCHEMA_SYSGRAPH, k -> Map.of(
                PREFIX, SYSGRAPH_PREFIX,
                FIELDS, Map.of(
                        SYSGRAPH_GRAPH_DB_IDS, SYSGRAPH_STRING_LIST_TYPE,
                        SYSGRAPH_ONTOLOGY_REFS, SYSGRAPH_STRING_LIST_TYPE)));

        return migrated;
    }

    /**
     * Adds document metadata mixins if they don't exist.
     */
    private void migrateMetadataMixins(Map<String, Object> mixins) {
        // Add SysVersionedWithParent mixin required by metadata containers
        mixins.computeIfAbsent(SYS_VERSIONED_WITH_PARENT, k -> Map.of());

        // Add metadata mixin types
        mixins.computeIfAbsent(SYS_MD_HL, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_HIGHLIGHT)));

        mixins.computeIfAbsent(SYS_MD_IMAGE, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_IMAGE)));

        mixins.computeIfAbsent(SYS_MD_LINE, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_LINE)));

        mixins.computeIfAbsent(SYS_MD_PATH, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_PATH)));

        mixins.computeIfAbsent(SYS_MD_TEXT, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_TEXT)));

        mixins.computeIfAbsent(SYS_MD_ANNOTATABLE, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_ANNOTATABLE)));

        mixins.computeIfAbsent(SYS_MD_REDACTABLE, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSMD_REDACTABLE)));

        // Embeddings
        mixins.computeIfAbsent(SYS_HAS_EMBEDDINGS, k -> Map.of());

        // Knowledge Graph
        mixins.computeIfAbsent(SYS_GRAPHABLE, k -> Map.of(SCHEMAS, List.of(SCHEMA_SYSGRAPH)));
    }

    /**
     * Adds document metadata primary types if they don't exist. Adds SysVersionedWithParent mixin if missing.
     */
    @SuppressWarnings("unchecked")
    private boolean migrateMetadataPrimaryTypes(Map<String, Object> primaryTypes) {
        // Add metadata primary types (with SysVersionedWithParent mixin for new installations)
        primaryTypes.computeIfAbsent(SYS_MD_BOOKMARK, k -> Map.of(
                "extends", SYS_CONTENT,
                SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSMD_BOOKMARK),
                MIXINS, List.of(SYS_VERSIONED_WITH_PARENT)));

        primaryTypes.computeIfAbsent(SYS_MD_ANNOTATION, k -> Map.of(
                "extends", SYS_CONTENT,
                SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSMD_ANNOTATION),
                MIXINS, List.of(SYS_VERSIONED_WITH_PARENT)));

        primaryTypes.computeIfAbsent(SYS_MD_COMMENT, k -> Map.of(
                "extends", SYS_CONTENT,
                SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSMD_COMMENT),
                MIXINS, List.of(SYS_VERSIONED_WITH_PARENT)));

        // Embeddings
        primaryTypes.computeIfAbsent(SYS_EMBEDDINGS, k -> Map.of(
                        "extends", SYS_CONTENT,
                        SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSEMB)));

        boolean migrated = false;
        migrated |= addMixinToPrimaryType(primaryTypes, SYS_MD_BOOKMARK, SYS_VERSIONED_WITH_PARENT);
        migrated |= addMixinToPrimaryType(primaryTypes, SYS_MD_ANNOTATION, SYS_VERSIONED_WITH_PARENT);
        migrated |= addMixinToPrimaryType(primaryTypes, SYS_MD_COMMENT, SYS_VERSIONED_WITH_PARENT);
        return migrated;
    }

    /**
     * Adds a mixin to an existing primary type if it's not already present.
     */
    @SuppressWarnings("unchecked")
    private boolean addMixinToPrimaryType(Map<String, Object> primaryTypes, String typeName, String mixinName) {
        Map<String, Object> type = (Map<String, Object>) primaryTypes.get(typeName);
        if (type == null) {
            return false;
        }
        if (!(type instanceof HashMap)) {
            type = new HashMap<>(type);
            primaryTypes.put(typeName, type);
        }
        List<String> mixins = (List<String>) type.get(MIXINS);
        if (mixins == null) {
            type.put(MIXINS, new ArrayList<>(List.of(mixinName)));
            return true;
        }
        if (!(mixins instanceof ArrayList)) {
            mixins = new ArrayList<>(mixins);
            type.put(MIXINS, mixins);
        }
        if (!mixins.contains(mixinName)) {
            mixins.add(mixinName);
            return true;
        }
        return false;
    }

    /**
     * Migrates existing sysrendition schema to add isExternal and blobPath fields if missing.
     */
    @SuppressWarnings("unchecked")
    private boolean migrateRenditionSchema(Map<String, Object> schemas) {
        Map<String, Object> renditionSchema = (Map<String, Object>) schemas.get("sysrendition");
        if (renditionSchema == null) {
            log.error("sysrendition schema not found during migration");
            return false;
        }
        Map<String, Object> fields = (Map<String, Object>) renditionSchema.get(FIELDS);
        if (fields == null) {
            log.error("sysrendition schema has no fields during migration");
            return false;
        }
        // Make fields mutable if needed
        if (!(fields instanceof HashMap)) {
            fields = new HashMap<>(fields);
            renditionSchema.put(FIELDS, fields);
        }
        int sizeBefore = fields.size();
        fields.computeIfAbsent("sysrendition_isExternal", k -> BooleanType.ID);
        fields.computeIfAbsent("sysrendition_blobPath", k -> StringType.ID);
        boolean migrated = fields.size() > sizeBefore;
        if (migrated) {
            log.info("Migrated sysrendition schema to include isExternal and blobPath fields");
        }
        return migrated;
    }

    /**
     * Migrates an old retention security configuration that doesn't contain "Write" permission checking.
     */
    @SuppressWarnings("unchecked")
    private boolean migrateSecurity(Map<String, Object> config) {
        Map<String, Object> retentionPolicy = (Map<String, Object>) config.computeIfAbsent(RETENTION_ENGINE_NAME, k -> new HashMap<>());
        List<String> permissions = (List<String>) retentionPolicy.computeIfAbsent("permissions", k -> new ArrayList<>());
        if (!permissions.contains(WRITE)) {
            permissions.add(WRITE);
            return true;
        }
        return false;
    }

    protected void initRepository() {
        // the first connection will init the repository
        getConnection(true).close();
    }

    @Override
    public MongoDBConnection getConnection() {
        return getConnection(false);
    }

    private MongoDBConnection getConnection(boolean initialize) {
        return new MongoDBConnection(this, initialize);
    }

    protected boolean supportsSessions() {
        return supportsSessions;
    }

    @Override
    public boolean supportsTransactions() {
        return supportsTransactions;
    }

    protected MongoClient getClient() {
        return mongoClient;
    }

    protected MongoDBCursorService getCursorService() {
        return cursorService;
    }

    protected MongoCollection<Document> getCollection() {
        return coll;
    }

    protected MongoDBConverter getConverter() {
        return converter;
    }

    @Override
    public void markReferencedBlobs(BiConsumer<String, String> markerCallback) {
        Bson filter = Filters.exists(SYS_BLOB_KEYS, true);
        Bson projection = Projections.fields(Projections.excludeId(), Projections.include(SYS_BLOB_KEYS));
        log.trace("MongoDB: QUERY {} KEYS {}", filter, projection);
        coll.find(filter).noCursorTimeout(true).projection(projection).forEach(doc -> {
            Object blobKeys = doc.get(SYS_BLOB_KEYS);
            if (blobKeys instanceof List list) {
                for (Object v : list) {
                    if (v instanceof String key) {
                        markerCallback.accept(key, repositoryName);
                    }
                }
            }
        });
    }

}
