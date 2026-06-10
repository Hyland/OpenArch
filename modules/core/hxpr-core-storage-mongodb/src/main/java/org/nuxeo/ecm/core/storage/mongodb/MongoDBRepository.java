/*
 * (C) Copyright 2014-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.mongodb;

import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSEMB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_SYSGRAPH;
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
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_CONTENT;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSMD_COMMENT_PREFIX;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
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
        default -> false;
        };
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

        return needsMigration;
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

        migrateMetadataSchemas(schemas);
        migrateMetadataMixins(mixins);
        migrateMetadataPrimaryTypes(primaryTypes);

        int configSizeAfterMigration = schemas.size() + mixins.size() + primaryTypes.size();
        return configSizeAfterMigration > configSizeBeforeMigration;
    }

    /**
     * Adds document metadata schemas if they don't exist.
     */
    private void migrateMetadataSchemas(Map<String, Object> schemas) {
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

        schemas.computeIfAbsent(SCHEMA_SYSMD_COMMENT, k -> Map.of(
                PREFIX, SYSMD_COMMENT_PREFIX,
                FIELDS, Map.of(SYSMD_COMMENT_CONTENT, StringType.ID)));

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
     * Adds document metadata primary types if they don't exist.
     */
    private void migrateMetadataPrimaryTypes(Map<String, Object> primaryTypes) {
        // Add metadata primary types
        primaryTypes.computeIfAbsent(SYS_MD_BOOKMARK, k -> Map.of(
                "extends", SYS_CONTENT,
                SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSMD_BOOKMARK)));

        primaryTypes.computeIfAbsent(SYS_MD_ANNOTATION, k -> Map.of(
                "extends", SYS_CONTENT,
                SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSMD_ANNOTATION)));

        primaryTypes.computeIfAbsent(SYS_MD_COMMENT, k -> Map.of(
                "extends", SYS_CONTENT,
                SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSMD_COMMENT)));

        // Embeddings
        primaryTypes.computeIfAbsent(SYS_EMBEDDINGS, k -> Map.of(
                        "extends", SYS_CONTENT,
                        SCHEMAS, List.of(SCHEMA_SYSTEM, SCHEMA_SYSEMB)));
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
