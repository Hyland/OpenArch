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

import static com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver.NAME_KEY;
import static com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver.VALIDATION_PARAMETER_KEY;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.hyland.openarch.ecm.core.schema.types.resolver.AbstractObjectResolver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reference to an object resolver, as it appears inside a {@code constraints} block.
 * <p>
 * A resolver is identified by its {@code name} and configured by an open set of parameters, which
 * differ per resolver implementation ({@code vocabulary} and {@code repository} for the vocabulary
 * resolver, {@code type} for the user manager resolver, {@code store} for the document resolver,
 * …). Those parameters are therefore held in a map rather than as named components, so that a
 * resolver contributing an unforeseen parameter does not silently lose it.
 * <p>
 * {@code validation} is called out separately because it is the one parameter the schema layer
 * itself understands.
 */
@Schema(name = "Resolver", description = "Represents a resolver. Beyond \"name\" and \"validation\","
        + " the properties are resolver-specific parameters, e.g. \"vocabulary\", \"repository\","
        + " \"store\" or \"type\".", type = "object")
public record ResolverDTO(String name, boolean validation, Map<String, Object> parameters) {

    public ResolverDTO {
        parameters = parameters == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Reads a resolver from its map form. Used for both the REST and the persisted shape, which
     * differ only in how {@code validation} is spelled ({@code false} vs {@code "false"}).
     */
    @JsonCreator
    public static ResolverDTO fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> parameters = new LinkedHashMap<>(map);
        Object name = parameters.remove(NAME_KEY);
        Object validation = parameters.remove(VALIDATION_PARAMETER_KEY);
        return new ResolverDTO(Objects.toString(name, null), validation == null || !"false".equals(validation.toString()),
                parameters);
    }

    /**
     * The REST shape: {@code name} first, then an explicitly boolean {@code validation}, then the
     * resolver-specific parameters.
     */
    @JsonValue
    public Map<String, Object> toRestMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(NAME_KEY, name);
        map.put(VALIDATION_PARAMETER_KEY, validation);
        map.putAll(parameters);
        return map;
    }

    /**
     * The persisted shape, mirroring {@link AbstractObjectResolver#toMap()}: {@code name} first, then
     * the parameters sorted by key, with {@code validation} present only when it is off, and then
     * as the string {@code "false"}.
     */
    public Map<String, Object> toStorageMap() {
        Map<String, Object> params = new TreeMap<>(parameters);
        if (!validation) {
            params.put(VALIDATION_PARAMETER_KEY, "false");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(NAME_KEY, name); // put name first
        map.putAll(params);
        return map;
    }
}
