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
package org.nuxeo.ecm.core.schema.dto;

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.ENUMERATION;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MAX_EXCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MAX_INCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MAX_LENGTH;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIN_EXCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIN_INCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIN_LENGTH;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.NULLABLE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PATTERN;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.RESOLVER;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintUtils;
import org.nuxeo.ecm.core.schema.types.constraints.LengthConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The set of constraints attached to a type, a field or a list item.
 * <p>
 * This is the single representation of a constraint set. It converts to and from the flat map form
 * the schema layer already speaks — {@link Constraint#toMap()} one way,
 * {@link ConstraintUtils#constraintsFromMap} the other — which is also the persisted shape.
 * <p>
 * {@code nullable} defaults to {@code true} and only ever means "there is no
 * {@link NotNullConstraint}". Absence and {@code true} are the same thing, which is why merging goes through the
 * flat map, where absence is representable.
 */
@Schema(name = "Constraint", description = "Represents a constraint.")
public record ConstraintsDTO( //
        @JsonInclude(Include.NON_NULL) Number minLength, //
        @JsonInclude(Include.NON_NULL) Number maxLength, //
        @JsonInclude(Include.NON_NULL) ResolverDTO resolver, //
        boolean nullable, //
        @JsonInclude(Include.NON_NULL) Object minInclusive, //
        @JsonInclude(Include.NON_NULL) Object minExclusive, //
        @JsonInclude(Include.NON_NULL) Object maxInclusive, //
        @JsonInclude(Include.NON_NULL) Object maxExclusive, //
        @JsonInclude(Include.NON_NULL) String pattern, //
        @JsonInclude(Include.NON_NULL) List<Object> enumeration) {

    public ConstraintsDTO {
        // interval bounds arrive as Integer from JSON, as Long from NumericIntervalConstraint and
        // as an ISO-8601 String from DateIntervalConstraint; normalize the numeric ones so that
        // equal constraints compare equal whatever they came from
        minInclusive = normalizeBound(minInclusive);
        minExclusive = normalizeBound(minExclusive);
        maxInclusive = normalizeBound(maxInclusive);
        maxExclusive = normalizeBound(maxExclusive);
    }

    private static Object normalizeBound(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /*
     * ===== flat map form =====
     */

    /**
     * Reads a constraint set from the flat map form shared by the persisted shape and
     * {@link Constraint#toMap()}. Returns {@code null} for a {@code null} or empty map.
     * <p>
     * This is also the JSON creator, so that an omitted {@code nullable} means "no NotNull
     * constraint" rather than the {@code false} a primitive boolean would otherwise default to.
     */
    @JsonCreator
    @SuppressWarnings("unchecked")
    public static ConstraintsDTO fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new ConstraintsDTO( //
                asNumber(map.get(MIN_LENGTH)), //
                asNumber(map.get(MAX_LENGTH)), //
                ResolverDTO.fromMap((Map<String, Object>) map.get(RESOLVER)), //
                !Boolean.FALSE.equals(map.get(NULLABLE)), //
                map.get(MIN_INCLUSIVE), //
                map.get(MIN_EXCLUSIVE), //
                map.get(MAX_INCLUSIVE), //
                map.get(MAX_EXCLUSIVE), //
                (String) map.get(PATTERN), //
                (List<Object>) map.get(ENUMERATION));
    }

    /**
     * Kept as the exact {@link Number} it arrived as. {@link LengthConstraint} passes its bounds
     * through unconverted, so widening an {@link Integer} to a {@link Long} here would change the
     * numeric type stored in the persisted document.
     */
    private static Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }

    /**
     * The flat map form: the persisted shape, and the input of {@link ConstraintUtils#constraintsFromMap}. Only
     * constraints that are actually set appear, so that maps can be merged with "later wins"
     * semantics.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, MIN_LENGTH, minLength);
        putIfNotNull(map, MAX_LENGTH, maxLength);
        putIfNotNull(map, MIN_INCLUSIVE, minInclusive);
        putIfNotNull(map, MIN_EXCLUSIVE, minExclusive);
        putIfNotNull(map, MAX_INCLUSIVE, maxInclusive);
        putIfNotNull(map, MAX_EXCLUSIVE, maxExclusive);
        putIfNotNull(map, PATTERN, pattern);
        putIfNotNull(map, ENUMERATION, enumeration);
        if (resolver != null) {
            map.put(RESOLVER, resolver.toStorageMap());
        }
        if (!nullable) {
            map.put(NULLABLE, Boolean.FALSE);
        }
        return map;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * Merges two constraint sets, {@code overriding} winning key by key. Mirrors the {@link Map#putAll}
     * the map-based code used to do, which is why it goes through {@link #toMap()}: only keys that
     * are actually present override.
     */
    public static ConstraintsDTO merge(ConstraintsDTO base, ConstraintsDTO overriding) {
        if (base == null) {
            return overriding;
        }
        if (overriding == null) {
            return base;
        }
        Map<String, Object> map = new LinkedHashMap<>(base.toMap());
        map.putAll(overriding.toMap());
        return fromMap(map);
    }

    /*
     * ===== Constraint objects =====
     */

    /** Builds the constraint set from the schema layer's {@link Constraint} objects. */
    public static ConstraintsDTO of(Set<Constraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        constraints.forEach(constraint -> map.putAll(constraint.toMap()));
        return fromMap(map);
    }

    /** Builds the schema layer's {@link Constraint} objects from this constraint set. */
    public Set<Constraint> toConstraints() {
        return ConstraintUtils.constraintsFromMap(toMap());
    }
}
