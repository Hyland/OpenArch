/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.SYSTEM_SCHEMAS;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.DefaultComponent;

import com.hyland.openarch.config.Configurable;
import com.hyland.openarch.config.ConfigurationService;
import com.hyland.openarch.config.ConfigurationWithVersion;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.ecm.core.schema.dto.ModelMapCodec;
import com.hyland.openarch.ecm.core.schema.dto.SchemaDTO;
import com.hyland.openarch.ecm.core.schema.dto.TypeDTO;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * The TypeService is the component dealing with registration of schemas and document types (and mixins).
 * <p>
 * The implementation is delegated to the SchemaManager.
 */
public class TypeServiceImpl extends DefaultComponent
        implements TypeService, Configurable<SchemaManager> {

    private static final Logger log = LogManager.getLogger(TypeServiceImpl.class);

    /** @since 11.5 */
    public static final String COMPONENT_NAME = "com.hyland.openarch.ecm.core.schema.TypeService";

    /** @since 11.5 */
    public static final String XP_DOCTYPE = "doctype";

    protected static final String XP_SCHEMA = "schema";

    public static final String CONFIG_TYPES = "types";

    // the default (bootstrap) schema manager
    private SchemaManagerBase defaultSchemaManager;

    private final Map<String, SchemaManager> schemaManagers = new ConcurrentHashMap<>();

    @Override
    public int getApplicationStartedOrder() {
        return -100;
    }

    @Override
    public void start() {
        SchemaRegistry schemaReg = getExtensionPointRegistry(XP_SCHEMA);
        DocTypeRegistry docTypeReg = getExtensionPointRegistry(XP_DOCTYPE);
        defaultSchemaManager = new SchemaManagerImpl(schemaReg, docTypeReg);
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            configService.registerConfigurable(this);
        }
    }

    @Override
    public void stop() {
        ConfigurationService configService = Framework.getService(ConfigurationService.class);
        if (configService != null) {
            configService.unregisterConfigurable(this);
        }
        defaultSchemaManager = null;
        schemaManagers.clear();
    }

    @Override
    public SchemaManager getDefaultSchemaManager() {
        return defaultSchemaManager;
    }

    @Override
    public SchemaManager getSchemaManager(String repositoryName) {
        if (repositoryName == null) {
            // unit tests
            return defaultSchemaManager;
        }
        return schemaManagers.getOrDefault(repositoryName, defaultSchemaManager);
    }

    @Override
    public void setSchemaManager(String repositoryName, SchemaManager schemaManager) {
        // set locally and persist (sending cluster invalidations) as one operation, so that concurrent writers cannot
        // set the local schema manager in one order and persist in the other; the configuration service skips the write
        // if nothing changed
        setConfigurationGlobally(repositoryName, withCoreSystemDefinitions(schemaManager));
    }

    @Override
    public void setSchemaManager(String repositoryName, SchemaManager schemaManager, long expectedVersion) {
        // Persist with optimistic locking, then set locally, as one operation: if the persisted version has moved the
        // write is rejected and the local schema manager is left untouched, and two concurrent writers set the local
        // schema manager in the order they persisted, so this node cannot end up serving an older model than it stored.
        // An unchanged configuration is not written at all and keeps its version, so applying the same content model
        // repeatedly is free.
        setConfigurationGloballyIfVersionMatches(repositoryName, withCoreSystemDefinitions(schemaManager),
                expectedVersion);
    }

    @Override
    public ModelWithVersion getModelWithVersion(String repositoryName) {
        ConfigurationWithVersion snapshot = Framework.getService(ConfigurationService.class)
                                                    .readConfigurationForUpdate(repositoryName, this);
        // shape violations are not reported here: this is a read of already-persisted configuration, and the caller
        // validates the update it computes from it
        ContentModelDTO model = ModelMapCodec.read(snapshot.config(), new ArrayList<>());
        return new ModelWithVersion(model, snapshot.version());
    }

    // Configurable
    @Override
    public SchemaManager getLocalState(String repositoryName) {
        return getSchemaManager(repositoryName);
    }

    // Configurable
    @Override
    public void setLocalState(String repositoryName, SchemaManager schemaManager) {
        schemaManagers.put(repositoryName, schemaManager);
    }

    // Configurable
    @Override
    public String getConfigurableConfigName() {
        return CONFIG_TYPES;
    }

    // Configurable
    @Override
    public Map<String, Object> toConfigMap(SchemaManager schemaManager) {
        return schemaManager.toMap();
    }

    // Configurable
    @Override
    public SchemaManager fromConfigMap(String repositoryName, Map<String, Object> config) {
        List<ValidationViolation> violations = new ArrayList<>();
        AtomicBoolean legacyShape = new AtomicBoolean();
        ContentModelDTO model = withCoreSystemDefinitions(ModelMapCodec.read(config, violations, legacyShape));
        if (legacyShape.get()) {
            log.warn("Content model for repository {} is stored in the legacy shape; "
                    + "it will be rewritten in the current shape the next time the model is updated", repositoryName);
        }
        return new SchemaManagerDTO(model, violations, false);
    }

    /**
     * The same schema manager with the core system schemas and types reset to their current bootstrap definitions, so
     * that no write can redefine them. Every write goes through this.
     * <p>
     * The violations of {@code schemaManager} are not carried over: the model it holds has already been validated by
     * the time it gets here, and the rebuilt one is validated again on construction.
     */
    private SchemaManager withCoreSystemDefinitions(SchemaManager schemaManager) {
        return new SchemaManagerDTO(withCoreSystemDefinitions(schemaManager.toContentModel()), List.of(), false);
    }

    /**
     * Replaces persisted core system schemas and types with their current bootstrap definitions.
     */
    private ContentModelDTO withCoreSystemDefinitions(ContentModelDTO model) {
        return withCoreSystemDefinitions(model, defaultSchemaManager.toContentModel());
    }

    /** Same merge with explicit bootstrap definitions, for tests. */
    static ContentModelDTO withCoreSystemDefinitions(ContentModelDTO model, ContentModelDTO defaults) {
        Map<String, SchemaDTO> schemas = new LinkedHashMap<>(model.schemas());
        SYSTEM_SCHEMAS.forEach(name -> {
            if (defaults.schemas().containsKey(name)) {
                schemas.put(name, defaults.schemas().get(name));
            }
        });
        Map<String, TypeDTO> types = new LinkedHashMap<>(model.types());
        types.keySet().removeIf(name -> name.startsWith("system#"));
        defaults.types()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith("system#"))
                .forEach(entry -> types.put(entry.getKey(), entry.getValue()));
        return new ContentModelDTO(schemas, types, model.mixinTypes(), model.primaryTypes());
    }

}
