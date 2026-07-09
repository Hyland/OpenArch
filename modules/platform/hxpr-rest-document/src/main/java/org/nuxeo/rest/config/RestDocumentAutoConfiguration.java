/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.rest.config;

import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;

@AutoConfiguration
@ComponentScan(basePackages = { "org.nuxeo.rest" })
public class RestDocumentAutoConfiguration {

    static final String USER_SCHEMA_REF = "#/components/schemas/User";

    static final String GROUP_SCHEMA_REF = "#/components/schemas/Group";

    static final String ACE_SCHEMA_NAME = "ACE";

    static final String USER_PROPERTY_NAME = "user";

    static final String GROUP_PROPERTY_NAME = "group";

    /**
     * Customizer to override the description of the {@code sys_creator} property in the Document schema.
     * <p>
     * The class-level {@code @Schema} annotation on {@code UserDTO} defines a default description ("Represents a
     * user.") that is used wherever a {@code UserDTO}-typed property is referenced. In OpenAPI 3.0, a property that is
     * a {@code $ref} cannot carry sibling attributes such as {@code description} (they are silently ignored by
     * parsers and Swagger UI), so a field-level {@code @Schema(description = ...)} annotation on the DTO field has no
     * effect. The only OpenAPI 3.0-compliant way to attach a description to a referenced schema is to wrap the
     * {@code $ref} in an {@code allOf}, which is what this customizer does.
     */
    @Bean
    @Order(14)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public OpenApiCustomizer documentCreatorDescriptionCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema documentSchema = openApi.getComponents().getSchemas().get(SCHEMA_NAME);
            if (documentSchema == null || documentSchema.getProperties() == null) {
                return;
            }
            Map<String, Schema<?>> properties = documentSchema.getProperties();
            updateDescriptionForUserProperties(properties, SYS_CREATOR, "Represents a user who created the document.", USER_SCHEMA_REF);
            updateDescriptionForUserProperties(properties, SYS_LAST_CONTRIBUTOR, "Represents a user who has last modified the document.", USER_SCHEMA_REF);
            updateDescriptionForUserProperties(properties, SYS_LOCK_OWNER, "Represents a user who locked the document.", USER_SCHEMA_REF);

            Schema aceSchema = openApi.getComponents().getSchemas().get(ACE_SCHEMA_NAME);
            if (aceSchema == null) {
                return;
            }
            properties = aceSchema.getProperties();
            updateDescriptionForUserProperties(properties, USER_PROPERTY_NAME, "Represents a user within the ACL.", USER_SCHEMA_REF);
            updateDescriptionForUserProperties(properties, GROUP_PROPERTY_NAME, "Represents a group within the ACL.", GROUP_SCHEMA_REF);
        };
    }

    private void updateDescriptionForUserProperties(Map<String, Schema<?>> properties, String propName, String newDescription, String schemaRef) {
        if (!properties.containsKey(propName)) {
            return;
        }
        Schema<?> wrapper = new Schema<>()
                .description(newDescription);
        if (StringUtils.isNotEmpty(schemaRef)) {
            wrapper.addAllOfItem(new Schema<>().$ref(schemaRef));
        }
        properties.put(propName, wrapper);
    }

    /**
     * Customizer to remove the "_1" suffix from the operationId of all operations.
     * <p>
     * This is necessary because the operationId is used as the method name in the generated client code and the "_1"
     * suffix is added to the operationId when there are multiple operations with the same name. This can include
     * multiple "_1" suffixes, so we need to recursively remove them.
     */
    @Bean
    @Order(2)
    public OpenApiCustomizer restDocumentOperationNameCustomizer() {
        return openApi -> openApi.getPaths()
                                 .values()
                                 .forEach(pathItem -> trimSuffixOperationId(pathItem.readOperations(), "_1"));
    }

    /**
     * Adds missing requestBody content for multipart PUT requests for docPath.
     * <p>
     * There appears to be a bug that causes the response to be missing for multipart PUT requests for docPath. This
     * method retrieves the requestBody content from the docId operation and adds it to the docPath operation.
     * <p>
     * This works on the basis that docPath operation will contain the same content as docId operation.
     */
    @Bean
    @Order(3)
    public OpenApiCustomizer docPathMultipartPutCustomiser() {
        return openApi -> {
            Map<String, PathItem> paths = openApi.getPaths();
            String docIdPath = paths.keySet()
                                    .stream()
                                    .filter(path -> path.endsWith("{docId}"))
                                    .findFirst()
                                    .orElse(null);
            String docPath = paths.keySet()
                                  .stream()
                                  .filter(path -> path.endsWith("{docPath}"))
                                  .findFirst()
                                  .orElse(null);
            if (docIdPath == null || docPath == null) {
                return;
            }
            paths.get(docIdPath).readOperationsMap().forEach((method, operation) -> {
                Content docIdContent = operation.getRequestBody() != null ? operation.getRequestBody().getContent()
                        : null;
                Operation docPathOperation = paths.get(docPath).readOperationsMap().get(method);
                Content docPathContent = docPathOperation.getRequestBody() != null
                        ? docPathOperation.getRequestBody().getContent()
                        : null;
                if (docIdContent == null || docPathContent == null) {
                    return;
                }
                docIdContent.forEach(docPathContent::putIfAbsent);
            });
        };
    }

    private void trimSuffixOperationId(List<Operation> operations, String suffixToTrim) {
        operations.stream()
                  .filter(operation -> operation.getOperationId() != null
                          && operation.getOperationId().endsWith(suffixToTrim))
                  .forEach(operation -> {
                      String operationId = operation.getOperationId();
                      if (operationId != null && operationId.endsWith(suffixToTrim)) {
                          operation.setOperationId(
                                  operationId.substring(0, operationId.length() - suffixToTrim.length()));
                      }
                  });
        if (operations.stream()
                      .anyMatch(operation -> operation.getOperationId() != null
                              && operation.getOperationId().endsWith(suffixToTrim))) {
            trimSuffixOperationId(operations, suffixToTrim);
        }
    }
}
