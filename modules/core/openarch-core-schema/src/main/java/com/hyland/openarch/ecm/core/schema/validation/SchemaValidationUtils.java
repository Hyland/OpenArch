/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema.validation;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.CONSTRAINTS;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MAX_EXCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MAX_INCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MAX_LENGTH;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIN_EXCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIN_INCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIN_LENGTH;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MODIFIED;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.PATH_DELIMITER;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.RESOLVER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_ROOT;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import com.hyland.openarch.ecm.core.schema.dto.DTOPath;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.LengthConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.RangeConstraint;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import com.hyland.openarch.ecm.core.schema.utils.MapDTOComparator;
import com.hyland.openarch.ecm.core.schema.validation.violations.AlreadyExistsViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.PrimaryTypeValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.RangeViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.UnknownObjectViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;

public class SchemaValidationUtils {

    private SchemaValidationUtils() {
        // Utility class
    }

    /**
     * Provides validation for the set of {@link Constraint}s at the provided {@link DTOPath}. <p>
     * The following classes are currently validated:
     * <ul>
     * <li>{@link LengthConstraint}</li>
     * <li>{@link NumericIntervalConstraint}</li>
     * <li>{@link ObjectResolverConstraint}</li>
     * </ul>
     * <p>
     * Any errors detected will be added to the provided {@link ValidationViolation} list
     * @param constraints to validate
     * @param node location of the constraints
     * @param violations list of violations to add errors to
     * @see SchemaValidationUtils#validateLengthConstraint
     * @see SchemaValidationUtils#validateNumericIntervalConstraint
     * @see SchemaValidationUtils#validateObjectResolverConstraint
     */
    public static void validateConstraints(Set<Constraint> constraints, DTOPath node,
            List<ValidationViolation> violations) {
        constraints.forEach(constraint -> validateConstraint(constraint, node, violations));
    }

    private static void validateConstraint(Constraint constraint, DTOPath node,
            List<ValidationViolation> violations) {
        if (constraint == null) {
            return;
        }
        if (constraint instanceof LengthConstraint lengthConstraint) {
            validateLengthConstraint(lengthConstraint, node, violations);
        }
        if (constraint instanceof NumericIntervalConstraint numericIntervalConstraint) {
            validateNumericIntervalConstraint(numericIntervalConstraint, node, violations);
        }
        if (constraint instanceof ObjectResolverConstraint resolverConstraint) {
            validateObjectResolverConstraint(resolverConstraint, node, violations);
        }
    }

    /**
     * Validates {@link RangeConstraint}.
     * Checks that the minLength < maxLength of the constraint.
     * If this is not the case a {@link RangeViolation} is added to {@code violations}
     * @param <T> {@link Constraint} implementing {@link RangeConstraint}
     * @param <E> {@link Number} implementing {@link Comparable} used by the {@link RangeConstraint}
     * @param constraint
     * @param node
     * @param minParameter
     * @param maxParameter
     * @param violations
     */
    private static <T extends Constraint & RangeConstraint<E>, E extends Number & Comparable<E>> void validateRangeConstraint(
            T constraint, DTOPath node, String minParameter, String maxParameter,
            List<ValidationViolation> violations) {
        if (!constraint.validRange()) {
            violations.add(new RangeViolation(constraint, node, minParameter, maxParameter));
        }
    }

    /**
     * Validates {@link LengthConstraint}.
     * Checks that the minLength < maxLength of the constraint.
     * If this is not the case a {@link RangeViolation} is added to {@code violations}
     * @param constraint The {@link LengthConstraint} to validate
     * @param node Location of the constraint
     * @param violations List of violations
     * @see {@link #validateRangeConstraint}
     */
    private static void validateLengthConstraint(LengthConstraint constraint, DTOPath node,
            List<ValidationViolation> violations) {
        validateRangeConstraint(constraint, node, MIN_LENGTH, MAX_LENGTH, violations);
    }

    /**
     * Validates {@link NumericIntervalConstraint}.
     * Checks that the min < max of the constraint. <p>
     * If this is not the case a {@link RangeViolation} is added to {@code violations}
     * @param constraint The {@link NumericIntervalConstraint} to validate
     * @param node Location of the constraint
     * @param violations List of violations
     * @see {@link #validateRangeConstraint}
     */
    private static void validateNumericIntervalConstraint(NumericIntervalConstraint constraint, DTOPath node,
            List<ValidationViolation> violations) {
        validateRangeConstraint(constraint, node, constraint.isIncludingMin() ? MIN_INCLUSIVE : MIN_EXCLUSIVE,
                constraint.isIncludingMax() ? MAX_INCLUSIVE : MAX_EXCLUSIVE, violations);
    }

    /**
     * Validates {@link ObjectResolverConstraint}.
     * Checks that the resolver exists. If the {@link ObjectResolverConstraint#getResolver()} == null
     * a violation is added to {@code violations}
     * @param constraint The {@link ObjectResolverConstraint} to validate
     * @param node Location of the constraint
     * @param violations List of violations
     */
    private static void validateObjectResolverConstraint(ObjectResolverConstraint resolverConstraint,
            DTOPath node, List<ValidationViolation> violations) {
        ObjectResolver resolver = resolverConstraint.getResolver();
        if (resolver == null) {
            violations.add(new UnknownObjectViolation(node, CONSTRAINTS, RESOLVER));
        }
    }

    public static void validateDTODiff(Map<String, Object> origDto, Map<String, Object> newDto,
            List<ValidationViolation> violations) {
        MapDTOComparator comparator = new MapDTOComparator(origDto, newDto);
        List<Pair<DTOPath, DTOPath>> modifiedNodes = comparator.getModifiedNodes();
        if (!modifiedNodes.isEmpty()) {
            modifiedNodes.forEach(nodePair -> violations.add(
                    new AlreadyExistsViolation(nodePair.getRight(), nodePair.getLeft(), MODIFIED)));
        }
        List<DTOPath> removedNodes = comparator.getRemovedNodes();
        if (!removedNodes.isEmpty()) {
            removedNodes.forEach(node -> {
                if (isRootPrimaryType(node)) {
                    violations.add(new PrimaryTypeValidationViolation(node, "removed"));
                } else {
                    violations.add(new AlreadyExistsViolation(node, "removed"));
                }
            });
        }
    }

    /** Whether the node is one of the primary types that may never be removed. */
    private static boolean isRootPrimaryType(DTOPath node) {
        String path = node.path();
        return path.equals(PRIMARY_TYPES + PATH_DELIMITER + SYS_CONTENT)
                || path.equals(PRIMARY_TYPES + PATH_DELIMITER + SYS_ROOT);
    }
}
