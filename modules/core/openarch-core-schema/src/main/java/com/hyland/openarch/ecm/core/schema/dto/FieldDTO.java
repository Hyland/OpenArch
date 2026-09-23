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
package com.hyland.openarch.ecm.core.schema.dto;

import java.util.Map;

import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * A field of a complex type or schema, or the item of a list type.
 * <p>
 * A field is either a reference to a named type ({@link #typeRef}) or an anonymous type declared
 * inline ({@link #fields} for a complex type, {@link #list} for a list type), plus its own
 * constraints, default value, full-text flag and optional repository-managed characteristics.
 * <p>
 * <b>On {@code typeRef}, {@code type} and {@code extends}:</b> the wire formats spell a type
 * reference two ways, and the choice is purely about whether the field carries anything else:
 * <ul>
 * <li>a reference on its own is <em>compact</em>: the bare string {@code "string"} when persisted,
 * {@code {"type": "string"}} over REST;</li>
 * <li>a reference alongside constraints, a default or a nested declaration is <em>expanded</em>:
 * {@code {"extends": "string", "constraints": {…}}} in both formats.</li>
 * </ul>
 * They are one concept, so this record holds one component and lets the two serializations decide
 * how to spell it. That is also why constraints can no longer be silently dropped by writing
 * {@code type} where {@code extends} was expected.
 */
@Schema(name = "Field", description = "Represents a field.")
@JsonPropertyOrder({ "type", "extends", "fields", "list", "constraints", "default", "fullTextIndexed",
        "managed", "projectId" })
@JsonDeserialize(using = FieldDTODeserializer.class)
public record FieldDTO( //
        @JsonIgnore String typeRef, //
        @JsonInclude(Include.NON_NULL) Map<String, FieldDTO> fields, //
        @JsonInclude(Include.NON_NULL) FieldDTO list, //
        @JsonInclude(Include.NON_NULL) ConstraintsDTO constraints, //
        @Schema( //
                deprecated = true, //
                description = "Deprecated: default values are not supported and this property will be removed in a future version.", //
                extensions = @Extension(properties = @ExtensionProperty(name = "setter-extra-annotation", value = "@Deprecated"))) //
        @JsonProperty("default") @JsonInclude(Include.NON_NULL) String defaultValue, //
        @JsonInclude(Include.NON_DEFAULT) boolean fullTextIndexed, //
        @Schema( //
                description = "Internal repository-managed behavior. Read-only and reserved for core system schemas.", //
                accessMode = Schema.AccessMode.READ_ONLY) //
        @JsonProperty("managed") @JsonInclude(Include.NON_NULL) FieldCharacteristics characteristics, //
        @JsonInclude(Include.NON_NULL) String projectId) {

    /** A bare reference to a named type. */
    public static FieldDTO ofType(String typeRef) {
        return new FieldDTO(typeRef, null, null, null, null, false, null, null);
    }

    /**
     * Whether this field is nothing more than a type reference, and can therefore use the compact
     * spelling.
     */
    @JsonIgnore
    public boolean isCompact() {
        return typeRef != null && fields == null && list == null && constraints == null && defaultValue == null
                && !fullTextIndexed && characteristics == null && projectId == null;
    }

    /** The type reference, when compact. See the class javadoc. */
    @Schema(description = "Name of the referenced type, when the field declares nothing else.")
    @JsonProperty("type")
    @JsonInclude(Include.NON_NULL)
    public String type() {
        return isCompact() ? typeRef : null;
    }

    /** The type reference, when expanded. See the class javadoc. */
    @Schema(description = "Name of the referenced type, when the field also declares constraints, a default value or a nested type.")
    @JsonProperty("extends")
    @JsonInclude(Include.NON_NULL)
    public String extend() {
        return isCompact() ? null : typeRef;
    }

}
