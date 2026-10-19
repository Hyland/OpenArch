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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.schema.FulltextConfiguration;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.SchemaManagerDTO;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import tools.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestFulltextConfiguration {

    private static final String TEST_REPO = "test";

    private static final int FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT = 128 * 1024; // 128 K

    @Inject
    private TypeService typeService;

    @Inject
    private RepositoryService repositoryService;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void canonicalConstructorUsesProvidedCollections() {
        var paths = new HashSet<>(Set.of("blob/key"));
        Map<String, Set<String>> properties = new HashMap<>(Map.of("DocTest", paths));
        var types = new HashSet<>(Set.of("DocTest"));
        var configuration = new FulltextConfiguration(properties, properties, types, 1024, true);
        assertThat(configuration.propPathBlobProperties()).isSameAs(properties);
        assertThat(configuration.propPathStringProperties()).isSameAs(properties);
        assertThat(configuration.includedTypes()).isSameAs(types);
        var updated = configuration.withFieldSizeLimitAndStoredInBlob(512, false);
        assertThat(updated.propPathBlobProperties()).isSameAs(properties);
        assertThat(updated.propPathStringProperties()).isSameAs(properties);
        assertThat(updated.includedTypes()).isSameAs(types);
    }

    @Test
    public void fulltextConfigurationExtractsFulltextIndexedFieldsFromModel() throws Exception {
        // Given a Model defined with a few document types with the fullTextIndexed flag
        var schemaManager = typeService.getSchemaManager(TEST_REPO);
        var schemaManagerDTO = new SchemaManagerDTO(createModelDTO(schemaManager), null, false);
        typeService.setSchemaManager(TEST_REPO, schemaManagerDTO);

        // When extracting the fulltext configuration
        schemaManager = typeService.getSchemaManager(TEST_REPO);
        FulltextConfiguration baseConfig = schemaManager.getFulltextConfiguration();
        FulltextConfiguration config = baseConfig.withFieldSizeLimitAndStoredInBlob(
                FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT, true);

        // Then the fields to indexed are properly defined
        assertThat(config.fieldSizeLimit()).isEqualTo(FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT);
        assertThat(config.storedInBlob()).isTrue();
        assertThat(baseConfig.fieldSizeLimit()).isZero();
        assertThat(baseConfig.storedInBlob()).isFalse();
        assertThat(config.propPathBlobProperties()).isSameAs(baseConfig.propPathBlobProperties());
        assertThat(config.propPathStringProperties()).isSameAs(baseConfig.propPathStringProperties());
        assertThat(config.includedTypes()).isSameAs(baseConfig.includedTypes());
        var otherConfig = config.withFieldSizeLimitAndStoredInBlob(512, false);
        assertThat(otherConfig.fieldSizeLimit()).isEqualTo(512);
        assertThat(otherConfig.storedInBlob()).isFalse();
        assertThat(config.fieldSizeLimit()).isEqualTo(FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT);
        assertThat(config.storedInBlob()).isTrue();
        assertThat(config.includedTypes()).containsExactly("DocTest");
        assertThat(config.propPathBlobProperties()).containsExactlyEntriesOf(
                Map.of("DocTest", Set.of("test_attachedFile/key")));
        assertThat(config.propPathStringProperties()).containsExactlyEntriesOf(
                Map.of("DocTest", Set.of("test_filename")));
        assertThat(config.isFulltextIndexable("SysRoot")).isFalse();
        assertThat(config.isFulltextIndexable("SysContent")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void repositoryConfigurationTracksModelWithoutEvents() throws Exception {
        var repository = repositoryService.getRepository(TEST_REPO);
        var originalManager = typeService.getSchemaManager(TEST_REPO);
        var originalConfig = repository.getFulltextConfiguration();
        assertThat(originalConfig.isFulltextIndexable("DocTest")).isFalse();

        typeService.setSchemaManager(TEST_REPO, new SchemaManagerDTO(createModelDTO(originalManager), null, false));
        var updatedConfig = repository.getFulltextConfiguration();
        assertThat(updatedConfig.isFulltextIndexable("DocTest")).isTrue();
        assertThat(updatedConfig.propPathBlobProperties().get("DocTest")).containsExactly("test_attachedFile/key");
        assertThat(updatedConfig.propPathStringProperties().get("DocTest")).containsExactly("test_filename");
        assertThat(updatedConfig.fieldSizeLimit()).isEqualTo(originalConfig.fieldSizeLimit());
        assertThat(updatedConfig.storedInBlob()).isEqualTo(originalConfig.storedInBlob());
        assertThat(originalConfig.isFulltextIndexable("DocTest")).isFalse();
        assertThat(originalManager.getFulltextConfiguration().isFulltextIndexable("DocTest")).isFalse();

        var patch = mapper.readTree("""
                [
                  {
                    "op": "replace",
                    "path": "/schemas/testschema/fields/test_attachedFile/fullTextIndexed",
                    "value": false
                  }
                ]
                """);
        var patched = Jackson3JsonPatch.apply(patch,
                mapper.valueToTree(typeService.getSchemaManager(TEST_REPO).toMap()));
        Map<String, Object> updatedModel = mapper.convertValue(patched, Map.class);
        typeService.setSchemaManager(TEST_REPO, new SchemaManagerDTO(updatedModel, null, false));
        assertThat(repository.getFulltextConfiguration().propPathBlobProperties()).doesNotContainKey("DocTest");
        assertThat(repository.getFulltextConfiguration().propPathStringProperties().get("DocTest"))
                .containsExactly("test_filename");
        assertThat(updatedConfig.propPathBlobProperties().get("DocTest")).containsExactly("test_attachedFile/key");

        typeService.setSchemaManager(TEST_REPO, originalManager);
        assertThat(repository.getFulltextConfiguration().isFulltextIndexable("DocTest")).isFalse();
        assertThat(updatedConfig.isFulltextIndexable("DocTest")).isTrue();
    }

    private Map<String, Object> createModelDTO(SchemaManager schemaManager) throws Exception {
        var patch = """
                [
                    {
                        "op": "add",
                        "path": "/schemas/testschema",
                        "value": {
                            "prefix": "ts",
                            "projectId": "HxP001",
                            "fields": {
                                "test_attachedFile": {
                                    "extends": "blob",
                                    "fullTextIndexed": true
                                },
                                "test_attachedFileNotIndexed": "blob",
                                "test_filename": {
                                    "extends": "string",
                                    "fullTextIndexed": true
                                },
                                "test_stringNotIndexed": "string"
                            }
                        }
                    },{
                        "op": "add",
                        "path": "/primaryTypes/DocTest",
                        "value": {
                            "extends": "SysContent",
                            "mixins": ["SysFullTextIndexable"],
                            "schemas": ["testschema"],
                            "projectId": "HxP001"
                        }
                    },{
                        "op": "add",
                        "path": "/mixinTypes/SysFullTextIndexable",
                        "value": { }
                    }
                ]
                """;

        var jsonPatch = mapper.readTree(patch);
        var patched = Jackson3JsonPatch.apply(jsonPatch, mapper.valueToTree(schemaManager.toMap()));
        return mapper.convertValue(patched, Map.class);
    }
}
