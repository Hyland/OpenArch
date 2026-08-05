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

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.SwaggerRequestBody;
import org.springframework.core.annotation.AliasFor;

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
