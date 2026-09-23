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

import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.SchemaManagerDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The whole content model of a repository.
 * <p>
 * This is the single in-memory representation of a content model. It has exactly two
 * serializations, and no other shape should be introduced:
 * <ul>
 * <li>the REST shape, produced by Jackson from the annotations on these records;</li>
 * <li>the persisted shape, produced by {@link ModelMapCodec}.</li>
 * </ul>
 * {@link SchemaManagerDTO} reads it into the {@link SchemaManager} object graph;
 * {@link SchemaManager#toContentModel()} produces it from one.
 * <p>
 * {@link #version} is the optimistic-locking counter of the v2 REST API. It is <em>not</em> part of the content: it is
 * {@code null} everywhere except on a v2 request or response, so it is omitted from both serializations above (and thus
 * absent from the persisted shape, where the version is document metadata). Use {@link #withVersion} to attach it at
 * the REST boundary and {@link #withoutVersion} to drop it before the model is validated or persisted.
 */
@Schema(name = "Model", description = "Represents a model.")
public record ContentModelDTO( //
        Map<String, SchemaDTO> schemas, //
        Map<String, TypeDTO> types, //
        Map<String, MixinTypeDTO> mixinTypes, //
        Map<String, PrimaryTypeDTO> primaryTypes, //
        @Schema(description = "Optimistic-locking version, present on the v2 API only.") //
        @JsonInclude(Include.NON_NULL) Long version) {

    public ContentModelDTO {
        // iteration order is part of the persisted shape, so keep it
        schemas = immutableCopy(schemas);
        types = immutableCopy(types);
        mixinTypes = immutableCopy(mixinTypes);
        primaryTypes = immutableCopy(primaryTypes);
    }

    /**
     * Constructor without a version, for the content model itself: the version is left {@code null} so it stays out of
     * both serializations.
     */
    public ContentModelDTO(Map<String, SchemaDTO> schemas, Map<String, TypeDTO> types,
            Map<String, MixinTypeDTO> mixinTypes, Map<String, PrimaryTypeDTO> primaryTypes) {
        this(schemas, types, mixinTypes, primaryTypes, null);
    }

    /** Returns a copy of this model carrying the given optimistic-locking {@code version}. */
    public ContentModelDTO withVersion(Long version) {
        return new ContentModelDTO(schemas, types, mixinTypes, primaryTypes, version);
    }

    /** Returns a copy of this model without any optimistic-locking version, i.e. the content alone. */
    public ContentModelDTO withoutVersion() {
        return version == null ? this : withVersion(null);
    }

    static <V> Map<String, V> immutableCopy(Map<String, V> map) {
        return map == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(map));
    }

    public static ContentModelDTO empty() {
        return new ContentModelDTO(null, null, null, null);
    }

    /** Keeps only the items belonging to {@code projectId}. */
    public ContentModelDTO filterByProject(String projectId) {
        return new ContentModelDTO( //
                filter(schemas, s -> projectId.equals(s.projectId())), //
                filter(types, t -> projectId.equals(t.projectId())), //
                filter(mixinTypes, m -> projectId.equals(m.projectId())), //
                filter(primaryTypes, p -> projectId.equals(p.projectId())));
    }

    /**
     * Returns this model with every item belonging to {@code projectId} replaced by those of
     * {@code contributed}, which are all stamped with {@code projectId}.
     */
    public ContentModelDTO withProjectItems(String projectId, ContentModelDTO contributed) {
        return new ContentModelDTO( //
                replaceProjectItems(schemas, contributed.schemas(), projectId, SchemaDTO::projectId,
                        s -> s.withProjectId(projectId)), //
                replaceProjectItems(types, contributed.types(), projectId, TypeDTO::projectId,
                        t -> t.withProjectId(projectId)), //
                replaceProjectItems(mixinTypes, contributed.mixinTypes(), projectId, MixinTypeDTO::projectId,
                        m -> m.withProjectId(projectId)), //
                replaceProjectItems(primaryTypes, contributed.primaryTypes(), projectId, PrimaryTypeDTO::projectId,
                        p -> p.withProjectId(projectId)));
    }

    private static <V> Map<String, V> filter(Map<String, V> map, Predicate<V> keep) {
        Map<String, V> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (keep.test(value)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static <V> Map<String, V> replaceProjectItems(Map<String, V> current, Map<String, V> contributed,
            String projectId, Function<V, String> projectIdOf, Function<V, V> stamp) {
        Map<String, V> result = filter(current, value -> !projectId.equals(projectIdOf.apply(value)));
        contributed.forEach((key, value) -> result.put(key, stamp.apply(value)));
        return result;
    }
}
