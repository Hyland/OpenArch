/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document;

import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.nuxeo.rest.utils.QueryParameters.CONDITION;
import static org.nuxeo.rest.utils.QueryParameters.IF;
import static org.nuxeo.rest.utils.QueryParameters.UPSERT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.UpdateCondition;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse201;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.ApiResponse422;
import org.nuxeo.rest.swagger.annotation.SwaggerRequestBody;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;

public abstract class AbstractDocumentAPI {

    public static final String PATH = DocumentManager.BASE_DOCUMENT_PATH;

    public static final String DOC_PATH = "/path"; // NOSONAR

    public static final io.swagger.v3.oas.models.parameters.Parameter DOCPATH_PARAMETER = new io.swagger.v3.oas.models.parameters.Parameter() //
        .in("path") //
        .name("docPath") //
        .description("The document path.") //
        .required(true) //
        .schema(new io.swagger.v3.oas.models.media.Schema<String>().pattern("(/)?(.)*").type("string")); //

    protected DocumentManager documentManager;

    protected AbstractDocumentAPI(DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    /**
     * Computes the update condition from the mutually exclusive {@code condition} and {@code if} parameters.
     *
     * @param hxql the value of the {@code condition} parameter (an HXQL predicate)
     * @param expr the value of the {@code if} parameter (an expression)
     * @return the condition, or {@code null} if none was provided
     */
    protected static UpdateCondition toUpdateCondition(String hxql, String expr) {
        boolean hasHxql = StringUtils.isNotBlank(hxql);
        boolean hasExpr = StringUtils.isNotBlank(expr);
        if (hasHxql && hasExpr) {
            throw new NuxeoException("Cannot use both " + CONDITION + " and " + IF + " parameters",
                    BAD_REQUEST.value());
        }
        if (hasHxql) {
            return UpdateCondition.hxql(hxql);
        } else if (hasExpr) {
            return UpdateCondition.expr(expr);
        } else {
            return null;
        }
    }

    /** A condition (optimistic concurrency) is not supported together with upsert; reject it rather than ignore it. */
    protected void rejectConditionWithUpsert(String hxql, String expr) {
        if (StringUtils.isNotBlank(hxql) || StringUtils.isNotBlank(expr)) {
            throw new NuxeoException("Cannot use " + CONDITION + " or " + IF + " parameters with " + UPSERT,
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse200()
    public @interface ApiResponse200Document {
        @AliasFor(annotation = ApiResponse200.class, attribute = "description")
        String description() default "Returns the updated document.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse200()
    public @interface ApiResponse200DocumentPost {
        @AliasFor(annotation = ApiResponse200.class, attribute = "description")
        String description() default "Returns the created document.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse201(useReturnTypeSchema = true)
    public @interface ApiResponse201Document {
        @AliasFor(annotation = ApiResponse201.class, attribute = "description")
        String description() default "Returns the created document.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse204()
    protected @interface ApiResponse204DocumentDeleted {
        @AliasFor(annotation = ApiResponse204.class, attribute = "description")
        String description() default "Document deleted.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse400()
    protected @interface ApiResponse400SingleAcePerPrincipal {
        @AliasFor(annotation = ApiResponse400.class, attribute = "description")
        String description() default "Only one principal per ACE.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse400()
    protected @interface ApiResponse400PatchMissingField {
        @AliasFor(annotation = ApiResponse400.class, attribute = "description")
        String description() default "Patch operation references a non-existent field.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse404()
    protected @interface ApiResponse404ParentDocumentNotFound {
        @AliasFor(annotation = ApiResponse404.class, attribute = "description")
        String description() default "Parent Document not found.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse409()
    protected @interface ApiResponse409DuplicateDocumentName {
        @AliasFor(annotation = ApiResponse409.class, attribute = "description")
        String description() default "A document with the requested sys_name already exists under the parent"
                + " and sys_name enforcement is enabled (enforceSysName=true).";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse422()
    protected @interface ApiResponse422ValidationError {
        @AliasFor(annotation = ApiResponse422.class, attribute = "description")
        String description() default "A property value failed validation: it could not be converted to the property"
                + " type, violated a schema constraint, or referenced an unknown upload id or blob key.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @SwaggerRequestBody(content = {
            @Content(mediaType = MULTIPART_FORM_DATA, schema = @Schema(type = "object", requiredProperties = { "main",
                    "main1234" }), schemaProperties = {
                            @SchemaProperty(name = "main", schema = @Schema(implementation = DocumentDTO.class, example = """
                                      {
                                        "sys_primaryType":"SysFile",
                                        "sys_title": "My File",
                                        "sysfile_blob": {
                                              "uploadId" : "main1234"
                                        }
                                      }
                                    """, description = "Represents a Document.\n Note: Don't change uploadId as it needs to match with the file's name property")),
                            @SchemaProperty(name = "main1234", schema = @Schema(type = "string", format = "binary")) }) })
    protected @interface RequestBodyMultiPart {
    }
}
