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
 */
package com.hyland.hxpr.springboot.server.config;

import static org.nuxeo.ecm.core.api.DocumentConstants.SCHEMA_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.nuxeo.runtime.springboot.actuator.ApplicationInfo;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Configuration
public class OpenApiConfiguration {

    @Bean
    @Order(10)
    public OpenApiCustomizer sortSchemasAlphabetically() {
        return openApi -> {
            @SuppressWarnings("rawtypes")
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            openApi.getComponents().setSchemas(new TreeMap<>(schemas));
        };
    }

    /*
     * Customizer to reorder parameters in the order path, query, header and set requestBody required to false
     * Order is kept higher as this is going to generate immutable parameter list
     */
    @Bean
    @Order(20)
    public OpenApiCustomizer orderParametersAndSetRequestBody() {
        return openApi ->
            openApi.getPaths().forEach((path, pathItem) -> {
                for (Operation operation : pathItem.readOperations()) {
                    if (operation.getRequestBody() != null) {
                        operation.getRequestBody().setRequired(false);
                    }
                    if (operation.getParameters() != null) {
                        // Reorder parameters if necessary
                        var list = getSortedParameterList(operation);
                        operation.setParameters(list);
                    }
                }
            });
    }

    @Bean
    @Order(13)
    public OpenApiCustomizer setAllowReservedToTrue() {
        return openApi ->
            openApi.getPaths().forEach((path, pathItem) -> {
                for (Operation operation : pathItem.readOperations()) {
                    if (operation.getParameters() != null) {
                        // allow reserved characters in path parameters without encoding
                        operation.getParameters().forEach(parameter -> {
                            if (isPathParameter(parameter)) {
                                parameter.allowReserved(true);
                            }
                        });
                    }
                }
            });
    }

    @Bean
    @Order(11)
    public OpenApiCustomizer addDocumentGovernanceProperties() {
        return openApi ->
            openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
                if (schemaName.equals(SCHEMA_NAME)) {
                    var hasLegalHoldSchema = new BooleanSchema();
                    hasLegalHoldSchema.setDescription("True/false indicator of whether the document contains the legal hold schema.");
                    schema.addProperty(SYSGOV_HAS_LEGAL_HOLD, hasLegalHoldSchema);
                    var isRecordSchema = new BooleanSchema();
                    isRecordSchema.setDescription("True/false indicator of whether the document is a record.");
                    schema.addProperty(SYSGOV_IS_RECORD, isRecordSchema);
                    var retainUntilSchema = new DateTimeSchema();
                    retainUntilSchema.setDescription("The date until which the document should be retained.");
                    schema.addProperty(SYSGOV_RETAIN_UNTIL, retainUntilSchema);
                }
            });
    }

    @Bean
    @Order(12)
    public OpenApiCustomizer enableAdditionalPropertiesInSchema() {
        return openApi ->
            openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
                if (schemaName.equals(SCHEMA_NAME)) {
                    schema.setAdditionalProperties(true);
                }
                if (schemaName.equals("NamedQuery") &&
                    schema.getProperties().get("parameters") instanceof Schema<?> namedQuerySchema) {
                            namedQuerySchema.setAdditionalProperties(true);
                }
            });
    }

    // Sort parameters by type in the order path, query, header
    private static List<Parameter> getSortedParameterList(Operation operation) {
        return operation.getParameters().stream().sorted((p1, p2) -> {
            if (isPathParameter(p1) && !isPathParameter(p2)) {
                return -1;  // p1 is a path parameter and should come before p2
            }
            if (isPathParameter(p2) && !isPathParameter(p1)) {
                return 1;   // p2 is a path parameter and should come before p1
            }
            if (isQueryParameter(p1) && !isQueryParameter(p2)) {
                return -1;  // p1 is a query parameter and should come before p2
            }
            if (isQueryParameter(p2) && !isQueryParameter(p1)) {
                return 1;   // p2 is a query parameter and should come before p1
            }
            if (p1.getIn().equals(p2.getIn())) {
                return p1.getName().compareTo(p2.getName());  // Both are of the same type, so keep their order
            }
            return 0;       // Both are of the same type, so keep their order
        }).toList();
    }

    private static boolean isQueryParameter(Parameter p1) {
        return p1.getIn().equals("query");
    }

    private static boolean isPathParameter(Parameter p1) {
        return p1.getIn().equals("path");
    }

    @Bean
    public OpenApiCustomizer setInfo(ApplicationInfo applicationInfo) {
        Info info = new Info();
        info.setTitle("hxpr-app API");
        info.setVersion(applicationInfo.version());
        return openApi -> openApi.setInfo(info);
    }

    @Bean
    public OpenAPI addSecuritySchemes() {
        final String securitySchemeName = "SecurityScheme";
        return new OpenAPI()
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components().addSecuritySchemes(securitySchemeName,
                new SecurityScheme().bearerFormat("JWT")
                                    .description("Authentication")
                                    .scheme("bearer")
                                    .type(SecurityScheme.Type.HTTP)
                                    ));
    }
}
