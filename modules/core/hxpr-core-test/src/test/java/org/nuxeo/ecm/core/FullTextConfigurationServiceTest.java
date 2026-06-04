/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package org.nuxeo.ecm.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.storage.FullTextConfigurationService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonPatch;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
public class FullTextConfigurationServiceTest {

    private static final String TEST_REPO = "test";

    private static final int FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT = 128 * 1024; // 128 K

    @Inject
    private FullTextConfigurationService fullTextConfigurationService;

    @Inject
    private TypeService typeService;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void fulltextConfigurationFactoryExtractsFulltextIndexedFieldsFromModel() throws Exception {
        // Given a Model defined with a few document types with the fullTextIndexed flag
        var schemaManager = typeService.getSchemaManager(TEST_REPO);
        var schemaManagerDTO = new SchemaManagerDTO(createModelDTO(schemaManager), null, false);
        typeService.setSchemaManager(TEST_REPO, schemaManagerDTO);

        // When extracting the fulltext configuration
        schemaManager = typeService.getSchemaManager(TEST_REPO);
        FulltextConfiguration config = fullTextConfigurationService.makeConfiguration(schemaManager,
                FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT, false);

        // Then the fields to indexed are properly defined
        assertThat(config.getFulltextFieldSizeLimit()).isEqualTo(FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT);
        assertThat(config.getIncludedTypes()).containsExactly("DocTest");
        assertThat(config.getPropPathBlobProperties()).containsExactlyEntriesOf(
                Map.of("DocTest", Set.of("test_attachedFile/key")));
        assertThat(config.getPropPathStringProperties()).containsExactlyEntriesOf(
                Map.of("DocTest", Set.of("test_filename")));
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
        var patched = JsonPatch.apply(jsonPatch, mapper.valueToTree(schemaManager.toDTO()));
        return mapper.convertValue(patched, Map.class);
    }
}
