/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static org.nuxeo.rest.config.RestDocumentAutoConfiguration.USER_SCHEMA_REF;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Unit tests for {@link RestDocumentAutoConfiguration} OpenAPI customizers.
 * <p>
 * Tests are intentionally pure unit tests (no Spring context) – each customizer lambda is obtained directly from the
 * configuration bean method and applied to a hand-built {@link OpenAPI} instance.
 */
class TestRestDocumentAutoConfiguration {

    private RestDocumentAutoConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new RestDocumentAutoConfiguration();
    }

    // -------------------------------------------------------------------------
    // documentCreatorDescriptionCustomizer (Order 14) – the new bean
    // -------------------------------------------------------------------------

    @Nested
    class DocumentCreatorDescriptionCustomizerTest {

        private OpenApiCustomizer customizer;

        @BeforeEach
        void setUp() {
            customizer = configuration.documentCreatorDescriptionCustomizer();
        }

        @Test
        void customise_withNullComponents_doesNotThrow() {
            OpenAPI openApi = new OpenAPI();
            // components is null by default on a fresh OpenAPI object
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_withNullSchemas_doesNotThrow() {
            OpenAPI openApi = new OpenAPI().components(new Components());
            // schemas map is null on a freshly created Components
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_withMissingDocumentSchema_doesNotThrow() {
            OpenAPI openApi = openApiWithEmptyDocumentSchemas();
            // No "Document" schema registered
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_withDocumentSchemaHavingNullProperties_doesNotThrow() {
            OpenAPI openApi = openApiWithDocumentSchema(null);
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_withBothUserPropertiesPresent_wrapsThemInAllOf() {
            Map<String, Schema> properties = new HashMap<>();
            properties.put(SYS_CREATOR, new Schema<>().$ref(USER_SCHEMA_REF));
            properties.put(SYS_LAST_CONTRIBUTOR, new Schema<>().$ref(USER_SCHEMA_REF));
            OpenAPI openApi = openApiWithDocumentSchema(properties);

            customizer.customise(openApi);

            Schema<?> creatorSchema = getDocumentProperty(openApi, SYS_CREATOR);
            assertAllOfWithDescription(creatorSchema, "Represents a user who created the document.");

            Schema<?> lastContributorSchema = getDocumentProperty(openApi, SYS_LAST_CONTRIBUTOR);
            assertAllOfWithDescription(lastContributorSchema, "Represents a user who has last modified the document.");
        }

        @Test
        void customise_withOnlySysCreatorPresent_wrapsSysCreatorOnly() {
            Map<String, Schema> properties = new HashMap<>();
            properties.put(SYS_CREATOR, new Schema<>().$ref(USER_SCHEMA_REF));
            OpenAPI openApi = openApiWithDocumentSchema(properties);

            customizer.customise(openApi);

            Schema<?> creatorSchema = getDocumentProperty(openApi, SYS_CREATOR);
            assertAllOfWithDescription(creatorSchema, "Represents a user who created the document.");
            // sys_lastContributor was absent – it must not have been added
            assertThat(getDocumentProperties(openApi)).doesNotContainKey(SYS_LAST_CONTRIBUTOR);
        }

        @Test
        void customise_withOnlySysLastContributorPresent_wrapsSysLastContributorOnly() {
            Map<String, Schema> properties = new HashMap<>();
            properties.put(SYS_LAST_CONTRIBUTOR, new Schema<>().$ref(USER_SCHEMA_REF));
            OpenAPI openApi = openApiWithDocumentSchema(properties);

            customizer.customise(openApi);

            Schema<?> lastContributorSchema = getDocumentProperty(openApi, SYS_LAST_CONTRIBUTOR);
            assertAllOfWithDescription(lastContributorSchema, "Represents a user who has last modified the document.");
            assertThat(getDocumentProperties(openApi)).doesNotContainKey(SYS_CREATOR);
        }

        @Test
        void customise_withOnlySysLockOwnerPresent_wrapsSysLockOwnerOnly() {
            Map<String, Schema> properties = new HashMap<>();
            properties.put(SYS_LOCK_OWNER, new Schema<>().$ref(USER_SCHEMA_REF));
            OpenAPI openApi = openApiWithDocumentSchema(properties);

            customizer.customise(openApi);

            Schema<?> lockOwnerSchema = getDocumentProperty(openApi, SYS_LOCK_OWNER);
            assertAllOfWithDescription(lockOwnerSchema, "Represents a user who locked the document.");
            assertThat(getDocumentProperties(openApi)).doesNotContainKey(SYS_CREATOR);
        }

        @Test
        void customise_withNeitherUserPropertyPresent_leavesPropertiesUnchanged() {
            Map<String, Schema> properties = new HashMap<>();
            properties.put("sys_title", new Schema<>());
            OpenAPI openApi = openApiWithDocumentSchema(properties);

            customizer.customise(openApi);

            assertThat(getDocumentProperties(openApi)).containsOnlyKeys("sys_title");
        }

        @Test
        void customise_wrappedSchema_hasAllOfRefPointingToUserComponent() {
            Map<String, Schema> properties = new HashMap<>();
            properties.put(SYS_CREATOR, new Schema<>().$ref(USER_SCHEMA_REF));
            OpenAPI openApi = openApiWithDocumentSchema(properties);

            customizer.customise(openApi);

            Schema<?> creatorSchema = getDocumentProperty(openApi, SYS_CREATOR);
            assertThat(creatorSchema.getAllOf()).hasSize(1);
            assertThat(creatorSchema.getAllOf().get(0).get$ref()).isEqualTo(USER_SCHEMA_REF);
        }

        // helper assertions

        @SuppressWarnings("rawtypes")
        private void assertAllOfWithDescription(Schema<?> schema, String expectedDescription) {
            assertThat(schema).as("Schema must not be null").isNotNull();
            assertThat(schema.getDescription()).isEqualTo(expectedDescription);
            List<Schema> allOf = schema.getAllOf();
            assertThat(allOf).as("allOf list must contain exactly one entry").hasSize(1);
            assertThat(allOf.get(0).get$ref()).isEqualTo(USER_SCHEMA_REF);
        }
    }

    // -------------------------------------------------------------------------
    // restDocumentOperationNameCustomizer (Order 2)
    // -------------------------------------------------------------------------

    @Nested
    class RestDocumentOperationNameCustomizerTest {

        private OpenApiCustomizer customizer;

        @BeforeEach
        void setUp() {
            customizer = configuration.restDocumentOperationNameCustomizer();
        }

        @ParameterizedTest
        @CsvSource({
                "getDocument_1, getDocument",
                "getDocument_1_1, getDocument",
                "getDocument, getDocument",
                "getDocument_2, getDocument_2"})
        void customise_removesUnderscoreOneSuffix(String operationId, String expectedOperationId) {
            OpenAPI openApi = openApiWithOperation(operationId);
            customizer.customise(openApi);
            assertThat(getFirstOperationId(openApi)).isEqualTo(expectedOperationId);
        }

        @Test
        void customise_handlesOperationWithNullOperationId() {
            OpenAPI openApi = openApiWithOperation(null);
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }
    }

    // -------------------------------------------------------------------------
    // docPathMultipartPutCustomiser (Order 3)
    // -------------------------------------------------------------------------

    @Nested
    class DocPathMultipartPutCustomiserTest {

        private OpenApiCustomizer customizer;

        @BeforeEach
        void setUp() {
            customizer = configuration.docPathMultipartPutCustomiser();
        }

        @Test
        void customise_withNoDocIdPath_doesNotThrow() {
            OpenAPI openApi = new OpenAPI().path("/api/doc/{docPath}", new PathItem());
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_withNoDocPathPath_doesNotThrow() {
            OpenAPI openApi = new OpenAPI().path("/api/doc/{docId}", new PathItem());
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_withEmptyPaths_doesNotThrow() {
            OpenAPI openApi = new OpenAPI();
            // Paths map exists but contains no entries matching docId or docPath
            openApi.path("/api/health", new PathItem());
            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
        }

        @Test
        void customise_mergesDocIdContentIntoDocPath() {
            Content docIdContent = new Content().addMediaType("application/json", new MediaType());
            Content docPathContent = new Content();

            OpenAPI openApi = buildDocIdDocPathOpenApi(docIdContent, docPathContent);

            customizer.customise(openApi);

            Content mergedDocPathContent = openApi.getPaths()
                                                  .get("/api/doc/{docPath}")
                                                  .getPut()
                                                  .getRequestBody()
                                                  .getContent();
            assertThat(mergedDocPathContent).containsKey("application/json");
        }

        @Test
        void customise_doesNotOverwriteExistingDocPathContent() {
            MediaType existingMediaType = new MediaType();
            Content docIdContent = new Content().addMediaType("application/json", new MediaType());
            Content docPathContent = new Content().addMediaType("application/json", existingMediaType);

            OpenAPI openApi = buildDocIdDocPathOpenApi(docIdContent, docPathContent);

            customizer.customise(openApi);

            MediaType resultMediaType = openApi.getPaths()
                                               .get("/api/doc/{docPath}")
                                               .getPut()
                                               .getRequestBody()
                                               .getContent()
                                               .get("application/json");
            assertThat(resultMediaType).isSameAs(existingMediaType);
        }

        @Test
        void customise_withNullDocIdRequestBody_doesNotMerge() {
            Content docPathContent = new Content();
            Operation docIdPut = new Operation().operationId("putDocId");
            // requestBody intentionally left null on docId
            PathItem docIdPath = new PathItem().put(docIdPut);

            Operation docPathPut = new Operation().operationId("putDocPath")
                                                  .requestBody(new RequestBody().content(docPathContent));
            PathItem docPathPathItem = new PathItem().put(docPathPut);

            OpenAPI openApi = new OpenAPI().path("/api/doc/{docId}", docIdPath)
                                           .path("/api/doc/{docPath}", docPathPathItem);

            assertThatNoException().isThrownBy(() -> customizer.customise(openApi));
            assertThat(docPathContent).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private OpenAPI openApiWithEmptyDocumentSchemas() {
        return new OpenAPI().components(new Components().schemas(new HashMap<>()));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private OpenAPI openApiWithDocumentSchema(Map<String, Schema> properties) {
        Schema documentSchema = new Schema<>();
        if (properties != null) {
            documentSchema.setProperties(properties);
        }
        Map<String, Schema> schemas = new HashMap<>();
        schemas.put(SCHEMA_NAME, documentSchema);
        return new OpenAPI().components(new Components().schemas(schemas));
    }

    @SuppressWarnings("rawtypes")
    private Map<String, Schema> getDocumentProperties(OpenAPI openApi) {
        return openApi.getComponents().getSchemas().get(SCHEMA_NAME).getProperties();
    }

    @SuppressWarnings("rawtypes")
    private Schema<?> getDocumentProperty(OpenAPI openApi, String propertyName) {
        return (Schema<?>) getDocumentProperties(openApi).get(propertyName);
    }

    private OpenAPI openApiWithOperation(String operationId) {
        Operation operation = new Operation().operationId(operationId);
        PathItem pathItem = new PathItem().get(operation);
        return new OpenAPI().path("/api/doc/{docId}", pathItem);
    }

    private String getFirstOperationId(OpenAPI openApi) {
        return openApi.getPaths().values().iterator().next().readOperations().get(0).getOperationId();
    }

    private OpenAPI buildDocIdDocPathOpenApi(Content docIdContent, Content docPathContent) {
        Operation docIdPut = new Operation().operationId("putDocId")
                                            .requestBody(new RequestBody().content(docIdContent));
        PathItem docIdPathItem = new PathItem().put(docIdPut);

        Operation docPathPut = new Operation().operationId("putDocPath")
                                              .requestBody(new RequestBody().content(docPathContent));
        PathItem docPathPathItem = new PathItem().put(docPathPut);

        return new OpenAPI().path("/api/doc/{docId}", docIdPathItem)
                            .path("/api/doc/{docPath}", docPathPathItem);
    }
}
