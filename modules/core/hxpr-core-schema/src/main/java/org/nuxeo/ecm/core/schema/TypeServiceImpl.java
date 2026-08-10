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
package org.nuxeo.ecm.core.schema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.hyland.hxpr.config.Configurable;
import com.hyland.hxpr.config.ConfigurationService;

/**
 * The TypeService is the component dealing with registration of schemas and document types (and mixins).
 * <p>
 * The implementation is delegated to the SchemaManager.
 */
public class TypeServiceImpl extends DefaultComponent implements TypeService, Configurable, ApplicationContextAware {

    private static final Logger log = LogManager.getLogger(TypeServiceImpl.class);

    /** @since 11.5 */
    public static final String COMPONENT_NAME = "org.nuxeo.ecm.core.schema.TypeService";

    /** @since 11.5 */
    public static final String XP_DOCTYPE = "doctype";

    protected static final String XP_SCHEMA = "schema";

    public static final String CONFIG_TYPES = "types";

    // the default (bootstrap) schema manager
    private SchemaManagerBase defaultSchemaManager;

    private final Map<String, SchemaManager> schemaManagers = new ConcurrentHashMap<>();

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

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
        // set locally
        setSchemaManagerLocally(repositoryName, schemaManager);
        // persist it (sends cluster invalidations)
        writeConfigurationGlobally(repositoryName);
    }

    private void setSchemaManagerLocally(String repositoryName, SchemaManager schemaManager) {
        schemaManagers.put(repositoryName, schemaManager);
        // send local invalidation
        if (applicationContext != null) {
            applicationContext.publishEvent(new SchemaManagerUpdatedEvent(repositoryName));
        } else {
            log.debug("No application context available to publish event for repository: {}", repositoryName);
        }
    }

    // Configurable
    @Override
    public String getConfigurableConfigName() {
        return CONFIG_TYPES;
    }

    // Configurable
    @Override
    public Map<String, Object> getLocalConfiguration(String repositoryName) {
        return getSchemaManager(repositoryName).toDTO();
    }

    // Configurable
    @Override
    public void setLocalConfiguration(String repositoryName, Map<String, Object> config) {
        SchemaManager schemaManager;
        try {
            schemaManager = new SchemaManagerDTO(config);
        } catch (NullPointerException | ClassCastException | RuntimeServiceException e) {
            log.error("Failed to read types for repository: {}", repositoryName, e);
            return;
        }
        setSchemaManagerLocally(repositoryName, schemaManager);
    }

}
