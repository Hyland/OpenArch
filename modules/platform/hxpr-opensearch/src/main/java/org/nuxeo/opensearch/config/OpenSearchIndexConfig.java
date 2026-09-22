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

package org.nuxeo.opensearch.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static org.nuxeo.opensearch.OpenSearchConstants.ALL_FIELDS;
import static org.nuxeo.opensearch.OpenSearchConstants.DOC_TYPE;
import static org.nuxeo.opensearch.OpenSearchConstants.EMBEDDINGS_TYPE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.common.Environment;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XNodeList;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XEnable;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;
import org.nuxeo.opensearch.core.IncrementalIndexNameGenerator;
import org.nuxeo.runtime.api.Framework;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * XMap descriptor for configuring an index
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
@XObject(value = "openSearchIndex")
@XRegistry(enable = false)
public class OpenSearchIndexConfig {

    public static final String DEFAULT_SETTING_FILE = "default-doc-settings.json";

    public static final String DEFAULT_DYNAMIC_SETTING_FILE = "default-doc-dynamic-settings.json";

    public static final String DEFAULT_MAPPING_FILE = "default-doc-mapping.json";

    protected static final String NUMBER_OF_SHARDS = "number_of_shards";

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final String DEFAULT_REPOSITORY_NAME = "default";

    protected static final String WRITE_SUFFIX = "-write";

    @XNode(value = XEnable.ENABLE, fallback = "@enabled")
    @XEnable
    protected boolean isEnabled = true;

    @XNode("@name")
    @XRegistryId
    protected String name;

    // @since 9.3
    @XNode("@manageAlias")
    protected boolean manageAlias;

    // @since 9.3
    @XNode("@writeAlias")
    protected String writeAlias;

    @XNode("@repository")
    protected String repositoryName;

    @XNode("@type")
    protected String type = DOC_TYPE;

    @XNode("@create")
    protected boolean create = true;

    /**
     * Number of primary shards for this index, overriding {@code number_of_shards} from the settings file.
     * <p>
     * Lets each index use a shard count matching its data volume while sharing a single settings file. When unset, the
     * value from the settings file applies.
     */
    @XNode("@numberOfShards")
    protected Integer numberOfShards;

    @XNode("settings")
    protected String settings;

    @XNode("settings@file")
    protected String settingsFile;

    @XNode("dynamicSettings")
    protected String dynamicSettings;

    @XNode("dynamicSettings@file")
    protected String dynamicSettingsFile;

    @XNode("mapping")
    protected String mapping;

    @XNode("mapping@file")
    protected String mappingFile;

    @XNodeList(value = "fetchFromSource/exclude", type = String[].class, componentType = String.class)
    protected String[] excludes;

    @XNodeList(value = "fetchFromSource/include", type = String[].class, componentType = String.class)
    protected String[] includes;

    @Override
    public String toString() {
        if (isEnabled()) {
            return String.format("OpenSearchIndexConfig(%s, %s, %s)", getName(), getRepositoryName(), getType());
        }
        return "OpenSearchIndexConfig disabled";
    }

    public String[] getExcludes() {
        if (excludes == null) {
            return new String[] { SYS_FULLTEXT_BINARY };
        }
        return excludes;
    }

    public String[] getIncludes() {
        if (includes == null || includes.length == 0) {
            return new String[] { ALL_FIELDS };
        }
        return includes;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getSettings() {
        String content;
        if (settingsFile != null) {
            content = contentOfFile(settingsFile);
        } else if (settings != null && !settings.isEmpty()) {
            content = settings;
        } else {
            content = contentOfFile(DEFAULT_SETTING_FILE);
        }
        return overrideNumberOfShards(content);
    }

    public Integer getNumberOfShards() {
        return numberOfShards;
    }

    /**
     * Applies the {@code numberOfShards} descriptor attribute on top of the resolved settings, so that indexes sharing
     * a settings file can still use different shard counts.
     *
     * @return the settings with {@code number_of_shards} overridden, unchanged when no attribute is configured
     */
    protected String overrideNumberOfShards(String settingsJson) {
        if (numberOfShards == null) {
            return settingsJson;
        }
        if (numberOfShards < 1) {
            throw new IllegalArgumentException("Invalid " + NUMBER_OF_SHARDS + " for index: " + name + ", must be at"
                    + " least 1 but is: " + numberOfShards);
        }
        try {
            JsonNode tree = MAPPER.readTree(settingsJson);
            if (!(tree instanceof ObjectNode node)) {
                throw new IllegalArgumentException(
                        "Cannot override " + NUMBER_OF_SHARDS + " for index: " + name
                                + ", settings is not a JSON object: " + settingsJson);
            }
            node.put(NUMBER_OF_SHARDS, numberOfShards);
            return MAPPER.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot override " + NUMBER_OF_SHARDS + " for index: " + name, e);
        }
    }

    public String getDynamicSettings() {
        if (dynamicSettingsFile != null) {
            return contentOfFile(dynamicSettingsFile);
        } else if (dynamicSettings != null && !dynamicSettings.isEmpty()) {
            return dynamicSettings;
        }
        return contentOfFile(DEFAULT_DYNAMIC_SETTING_FILE);
    }

    protected String contentOfFile(String filename) {
        try (InputStream stream = getResourceStream(filename)) {
            String content = IOUtils.toString(stream, UTF_8);
            return Framework.expandVars(content);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load resource file: " + filename, e);
        }
    }

    @SuppressWarnings("resource") // closed by caller
    protected InputStream getResourceStream(String filename) {
        // First check if the resource is available on the config directory
        File file = new File(Environment.getDefault().getConfig(), filename);
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                // try another way
            }
        }

        // getResourceAsStream is needed getResource will not work when called from another module
        InputStream ret = this.getClass().getClassLoader().getResourceAsStream(filename);
        if (ret == null) {
            // Then try to get it from jar
            ret = this.getClass().getClassLoader().getResourceAsStream(filename);
        }
        if (ret == null) {
            throw new IllegalArgumentException(
                    String.format("Resource file cannot be found: %s or %s", file.getAbsolutePath(), filename));
        }
        return ret;
    }

    public String getMapping() {
        if (mappingFile != null) {
            return contentOfFile(mappingFile);
        } else if (mapping != null && !mapping.isEmpty()) {
            return mapping;
        }
        return contentOfFile(DEFAULT_MAPPING_FILE);
    }

    public boolean mustCreate() {
        return create;
    }

    public String getRepositoryName() {
        // Document and embeddings indices default to "default" repository if not specified
        if ((isDocumentIndex() || isEmbeddingsIndex()) && StringUtils.isBlank(repositoryName)) {
            repositoryName = DEFAULT_REPOSITORY_NAME;
        }
        return repositoryName;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    /**
     * Return true if the index/mapping is associated with a Nuxeo document repository
     *
     * @since 7.4
     */
    public boolean isDocumentIndex() {
        return DOC_TYPE.equals(getType());
    }

    /**
     * Return true if the index is an embeddings index
     */
    public boolean isEmbeddingsIndex() {
        return EMBEDDINGS_TYPE.equals(getType());
    }

    // @since 9.3
    public boolean hasExplicitWriteIndex() {
        return StringUtils.isNotBlank(writeAlias);
    }

    // @since 9.3
    public String writeIndexOrAlias() {
        // Custom alias managed outside of Nuxeo
        if (hasExplicitWriteIndex()) {
            return writeAlias;
        }
        // Nuxeo manages the write alias
        if (manageAlias) {
            return name + WRITE_SUFFIX;
        }
        // Simple index
        return name;
    }

    // @since 9.3
    public boolean manageAlias() {
        return manageAlias;
    }

    // @since 9.3
    public String newWriteIndexForAlias(String aliasName, String oldIndexName) {
        // TODO make the alias resolver configurable
        return new IncrementalIndexNameGenerator().getNextIndexName(aliasName, oldIndexName);
    }
}
