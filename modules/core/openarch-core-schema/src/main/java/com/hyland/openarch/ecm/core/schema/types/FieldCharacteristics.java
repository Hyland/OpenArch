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
package com.hyland.openarch.ecm.core.schema.types;

import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Declarative behavior attached to fields whose value is managed by the repository.
 *
 * @param systemOwned whether repository/system behavior owns writes instead of accepting generic user updates
 * @param source where the value comes from, or {@code null} if it is persisted in document state
 * @param notQueryable whether the field cannot be used by repository queries
 * @param notProjectable whether the field cannot be selected by a query
 * @param noAutoCheckout whether writing the field leaves a checked-in working copy checked in
 * @param falseAsNull representations where {@code false} is represented by {@code null}
 * @param notOutput external representations that exclude the field
 * @param readPermission additional permission required to read the field, or {@code null} for none
 * @param writePermission permission required instead of the normal {@code Write} permission,
 *            {@link #WRITE_MANAGED} when operation-specific authorization replaces the generic permission check, or
 *            {@code null} to require {@code Write}
 * @param versionWritePermission additional permission required to write the field on an immutable version,
 *            {@link #VERSION_WRITE_STANDARD} to apply standard write authorization without an additional
 *            version-specific permission, or {@code null} if the field is not writable on a version
 */
@Schema(name = "FieldCharacteristics", description = "Repository-managed access, query and output behavior for a field.")
public record FieldCharacteristics( //
        @JsonInclude(Include.NON_DEFAULT) boolean systemOwned, //
        @JsonInclude(Include.NON_NULL) ValueSource source, //
        @JsonInclude(Include.NON_DEFAULT) boolean notQueryable, //
        @JsonInclude(Include.NON_DEFAULT) boolean notProjectable, //
        @JsonInclude(Include.NON_DEFAULT) boolean noAutoCheckout, //
        @JsonInclude(Include.NON_EMPTY) Set<Representation> falseAsNull, //
        @JsonInclude(Include.NON_EMPTY) Set<Representation> notOutput, //
        @JsonInclude(Include.NON_NULL) String readPermission, //
        @JsonInclude(Include.NON_NULL) String writePermission, //
        @JsonInclude(Include.NON_NULL) String versionWritePermission) {

    /** Marker used when operation-specific authorization replaces the generic write permission check. */
    public static final String WRITE_MANAGED = "MANAGED";

    /** Marker used when a version write requires no additional version-specific permission. */
    public static final String VERSION_WRITE_STANDARD = "STANDARD";

    /** Default behavior for a repository-managed field. */
    public static final FieldCharacteristics DEFAULT = new FieldCharacteristics(false, null, false, false, false, null,
            null, null, null, null);

    /**
     * How the repository obtains a field's value.
     */
    public enum ValueSource {
        /** The repository computes the value when it is read or projected to an external representation. */
        COMPUTED,

        /** The field exists as a synthetic query operand or result. */
        QUERY_VIRTUAL
    }

    /** Representations of a field's value. */
    public enum Representation {
        /** The stored repository representation. */
        STORE,

        /** The REST document representation. */
        REST,

        /** The search-index document representation. */
        INDEX
    }

    public FieldCharacteristics {
        falseAsNull = falseAsNull == null || falseAsNull.isEmpty() ? Set.of() : EnumSet.copyOf(falseAsNull);
        notOutput = notOutput == null || notOutput.isEmpty() ? Set.of() : EnumSet.copyOf(notOutput);
        if (notQueryable && !notProjectable) {
            throw new IllegalArgumentException("A projectable field must be queryable");
        }
        if (source == ValueSource.QUERY_VIRTUAL && notQueryable) {
            throw new IllegalArgumentException("A query-virtual field must be queryable");
        }
        if (systemOwned && versionWritePermission != null) {
            throw new IllegalArgumentException("A system-owned field cannot have a version write permission");
        }
        if (systemOwned && writePermission != null) {
            throw new IllegalArgumentException("A system-owned field cannot have a write permission");
        }
        if (systemOwned && noAutoCheckout) {
            throw new IllegalArgumentException("A system-owned field cannot disable auto-checkout");
        }
        if (notOutput.contains(STORE)) {
            throw new IllegalArgumentException("Stored values cannot be excluded using notOutput");
        }
    }

}
