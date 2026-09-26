/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.springboot.server.config;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_ACCEPTABLE;
import static org.apache.http.HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SCHEMA_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import com.hyland.openarch.runtime.springboot.actuator.ApplicationInfo;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiConfiguration implements WebMvcConfigurer {

    // The generated document keys its responses by the status as a string, while the platform names the codes
    // as ints, so each is converted once here rather than spelled out again as a literal.
    private static final String BAD_REQUEST = String.valueOf(SC_BAD_REQUEST);

    private static final String NOT_ACCEPTABLE = String.valueOf(SC_NOT_ACCEPTABLE);

    private static final String UNSUPPORTED_MEDIA_TYPE = String.valueOf(SC_UNSUPPORTED_MEDIA_TYPE);

    /** A tag of the form {@code "<prefix> v<number>"} names a version of the API the prefix names. */
    private static final Pattern VERSIONED_TAG = Pattern.compile("(?<prefix>.+) (?<version>v\\d+)");

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

    /*
     * springdoc 3.x endpoints (/v3/api-docs, /v3/api-docs.yaml) return byte[]. Without a byte[] converter that is
     * ordered ahead of Jackson and claims application/json, Spring MVC writes the array through Jackson, which
     * base64-encodes it. Inserting a byte[] converter at the front of the list makes Spring write the raw bytes.
     * Uses extendMessageConverters (not the Spring 7 configureMessageConverters(ServerBuilder)) because Spring Boot
     * 4.1 does not invoke the new builder hook in this setup; this is the same hook MultipartJsonConverterConfig uses.
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ByteArrayHttpMessageConverter converter = new ByteArrayHttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM,
                MediaType.parseMediaType("application/vnd.oai.openapi"),
                MediaType.parseMediaType("application/vnd.oai.openapi+json")));
        converters.add(0, converter);
    }

    @Bean
    @Order(10)
    public OpenApiCustomizer sortSchemasAlphabetically() {
        return openApi -> {
            @SuppressWarnings("rawtypes") Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            openApi.getComponents().setSchemas(new TreeMap<>(schemas));
        };
    }

    /**
     * Keeps a single tag on each operation that declares one of its own, the one it declares.
     * <p>
     * springdoc adds the tag a controller declares at class level to every operation it holds, on top of any tag the
     * operation declares itself, and puts the operation's own tags first. An operation carrying two tags is generated
     * once per tag by the SDK generator, so the v2 endpoints, which live in the same controller as their v1
     * counterparts and name their own tag, would also be generated into the v1 API class.
     */
    @Bean
    @Order(14)
    public OpenApiCustomizer keepOnlyTheTagAnOperationDeclares() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            for (Operation operation : pathItem.readOperations()) {
                List<String> tags = operation.getTags();
                if (tags != null && tags.size() > 1) {
                    operation.setTags(List.of(tags.get(0)));
                }
            }
        });
    }

    /**
     * Declares the versioned tags that only the operations name, so they are documented like the ones a controller
     * declares.
     * <p>
     * A controller serving several versions of an API declares the tag of the first at class level, and each operation
     * of a later one names its own tag (see {@link #keepOnlyTheTagAnOperationDeclares}). Only the first is a
     * declaration, so the later ones would be listed with no description. A {@code "<prefix> v<number>"} tag covers
     * the same API as {@code prefix}, so it is described as that API, at that version. Any other tag is left as it
     * is.
     */
    @Bean
    @Order(15)
    public OpenApiCustomizer declareVersionedTags() {
        return openApi -> {
            List<Tag> tags = openApi.getTags() == null ? new ArrayList<>() : new ArrayList<>(openApi.getTags());
            Map<String, String> descriptions = tags.stream()
                                                   .filter(tag -> tag.getDescription() != null)
                                                   .collect(toMap(Tag::getName, Tag::getDescription));
            Set<String> declared = tags.stream().map(Tag::getName).collect(toSet());
            openApi.getPaths()
                   .values()
                   .stream()
                   .flatMap(pathItem -> pathItem.readOperations().stream())
                   .map(Operation::getTags)
                   .filter(Objects::nonNull)
                   .flatMap(List::stream)
                   .distinct()
                   .filter(name -> !declared.contains(name))
                   .forEach(name -> describeVersionedTag(name, descriptions).ifPresent(
                           description -> tags.add(new Tag().name(name).description(description))));
            openApi.setTags(tags);
        };
    }

    /**
     * The description of a versioned tag: the one of the API it is a version of, marked with that version. Empty for a
     * tag that is not a version, or whose API has no description of its own; there is then nothing to declare and the
     * tag is left as it is.
     */
    private static Optional<String> describeVersionedTag(String name, Map<String, String> descriptions) {
        Matcher matcher = VERSIONED_TAG.matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptions.get(matcher.group("prefix")))
                       .map(described -> described + ", " + matcher.group("version"));
    }

    /*
     * Customizer to reorder parameters in the order path, query, header and set requestBody required to false
     * Order is kept higher as this is going to generate immutable parameter list
     */
    @Bean
    @Order(20)
    public OpenApiCustomizer orderParametersAndSetRequestBody() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
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
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
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

    /*
     * A request body that has to be parsed can fail to parse, and Spring answers that with 400 before the
     * handler runs — nothing is read, validated or written. Verified against a running application: all 42
     * operations with a JSON or JSON Patch body answered 400 to a malformed body, and a create against a
     * real parent left the repository unchanged.
     *
     * Only parseable bodies qualify. An operation accepting the wildcard type has nothing to parse and
     * answers 201, and a multipart body fails earlier, in the dispatcher, not here.
     *
     * Operations that already declare 400 keep their own description: those describe a domain cause, such as
     * an invalid identifier, and are more specific than anything this rule could add.
     */
    @Bean
    @Order(23)
    public OpenApiCustomizer declareBadRequestOnParseableRequestBodies() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            for (Operation operation : pathItem.readOperations()) {
                RequestBody requestBody = operation.getRequestBody();
                if (requestBody == null || requestBody.getContent() == null
                        || operation.getResponses() == null
                        || operation.getResponses().containsKey(BAD_REQUEST)
                        || requestBody.getContent().keySet().stream().noneMatch(t -> t.contains("json"))) {
                    continue;
                }
                operation.getResponses()
                         .addApiResponse(BAD_REQUEST, new ApiResponse().description(
                                 "The request body could not be parsed."));
            }
        });
    }

    /*
     * Response content negotiation fails with 406 when the Accept header asks for something no message
     * converter can write, and Spring decides that while matching the handler, before any handler body runs
     * — so it reaches every operation, including the ones that answer 204 with no content at all. Verified
     * against a running application: all 108 generated operations answered 406 to Accept: application/xml,
     * and a mutating call rejected this way never reached its handler.
     *
     * There is deliberately no condition here. Declaring it from a per-operation rule would be wrong twice
     * over: the generated response content is not the negotiation input (the download operations publish the
     * wildcard media type and still answer 406), and an operation that constrains nothing still answers 406
     * because no converter writes the requested type.
     */
    @Bean
    @Order(22)
    public OpenApiCustomizer declareNotAcceptableOnEveryOperation() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            for (Operation operation : pathItem.readOperations()) {
                if (operation.getResponses() == null || operation.getResponses().containsKey(NOT_ACCEPTABLE)) {
                    continue;
                }
                operation.getResponses()
                         .addApiResponse(NOT_ACCEPTABLE, new ApiResponse().description(
                                 "No representation matches the Accept header. This API carries its version in the"
                                         + " media type, so asking for a version it no longer serves — for example"
                                         + " application/vnd.hxcs.v0+json — is answered here."));
            }
        });
    }

    /*
     * An operation whose request body is restricted to specific media types answers any other Content-Type
     * with 415 before the handler runs. That is Spring's content negotiation, identical for every such
     * operation, so it is declared from the request body itself rather than annotated one handler at a time:
     * a new endpoint with a constrained body is then documented correctly without anyone remembering to.
     * Operations that accept the wildcard type cannot produce it and are left alone. Runs after
     * orderParametersAndSetRequestBody so the request bodies are final.
     */
    @Bean
    @Order(21)
    public OpenApiCustomizer declareUnsupportedMediaTypeOnConstrainedRequestBodies() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            for (Operation operation : pathItem.readOperations()) {
                RequestBody requestBody = operation.getRequestBody();
                if (requestBody == null || requestBody.getContent() == null
                        || requestBody.getContent().containsKey(MediaType.ALL_VALUE)
                        || operation.getResponses() == null
                        || operation.getResponses().containsKey(UNSUPPORTED_MEDIA_TYPE)) {
                    continue;
                }
                // sorted so the generated description is stable across builds
                String accepted = requestBody.getContent().keySet().stream().sorted().collect(joining(", "));
                operation.getResponses()
                         .addApiResponse(UNSUPPORTED_MEDIA_TYPE,
                                 new ApiResponse().description(
                                         "Unsupported request body media type. Accepted: " + accepted + "."));
            }
        });
    }

    @Bean
    public OpenApiCustomizer setInfo(ApplicationInfo applicationInfo) {
        Info info = new Info();
        info.setTitle("openarch-app API");
        info.setVersion(applicationInfo.version());
        return openApi -> openApi.setInfo(info);
    }

    @Bean
    public OpenAPI addSecuritySchemes() {
        final String securitySchemeName = "SecurityScheme";
        return new OpenAPI().addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                            .components(new Components().addSecuritySchemes("SecurityScheme",
                                    new SecurityScheme().bearerFormat("JWT")
                                                        .description("Authentication")
                                                        .scheme("bearer")
                                                        .type(SecurityScheme.Type.HTTP)));
    }
}
