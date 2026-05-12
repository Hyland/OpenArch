/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     David Edwards
 */
package org.nuxeo.ecm.core.schema.validation;

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.CONSTRAINTS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MODIFIED;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.RESOLVER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_ROOT;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.LengthConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.RangeConstraint;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.core.schema.utils.MapDTOComparator;
import org.nuxeo.ecm.core.schema.validation.violations.AlreadyExistsViolation;
import org.nuxeo.ecm.core.schema.validation.violations.PrimaryTypeValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.RangeViolation;
import org.nuxeo.ecm.core.schema.validation.violations.UnknownObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

public class SchemaValidationUtils {

    private static final String MAX_LENGTH = "maxLength";

    private static final String MIN_LENGTH = "minLength";

    private SchemaValidationUtils() {
        // Utility class
    }

    /**
     * Provides validation for the set of {@link Constraint}s at the provided {@link SchemaDTONode}. <p>
     * The following classes are currently validated:
     * <ul>
     * <li>{@link LengthConstraint}</li>
     * <li>{@link NumericIntervalConstraint}</li>
     * <li>{@link ObjectResolverConstraint}</li>
     * </ul>
     * <p>
     * Any errors detected will be added to the provided {@code List<SchemaValidationViolation>}
     * @param constraints to validate
     * @param node location of the constraints
     * @param violations list of violations to add errors to
     * @see SchemaValidationUtils#validateLengthConstraint
     * @see SchemaValidationUtils#validateNumericIntervalConstraint
     * @see SchemaValidationUtils#validateObjectResolverConstraint
     */
    public static void validateConstraints(Set<Constraint> constraints, SchemaDTONode node,
            List<ValidationViolation> violations) {
        constraints.forEach(constraint -> validateConstraint(constraint, node, violations));
    }

    private static void validateConstraint(Constraint constraint, SchemaDTONode node,
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
     * If this is not the case a {@Link SchemaDTOViolation} is added to {@code violations}
     * @param <T> {@link Constraint} implementing {@link RangeConstraint}
     * @param <E> {@link Number} implementing {@link Comparable} used by the {@link RangeConstraint}
     * @param constraint
     * @param node
     * @param minParameter
     * @param maxParameter
     * @param violations
     */
    private static <T extends Constraint & RangeConstraint<E>, E extends Number & Comparable<E>> void validateRangeConstraint(
            T constraint, SchemaDTONode node, String minParameter, String maxParameter,
            List<ValidationViolation> violations) {
        if (!constraint.validRange()) {
            violations.add(new RangeViolation(constraint, node, minParameter, maxParameter));
        }
    }

    /**
     * Validates {@link LengthConstraint}.
     * Checks that the minLength < maxLength of the constraint.
     * If this is not the case a {@Link SchemaDTOViolation} is added to {@code violations}
     * @param constraint The {@link LengthConstraint} to validate
     * @param node Location of the constraint
     * @param violations List of violations
     * @see {@link #validateRangeConstraint}
     */
    private static void validateLengthConstraint(LengthConstraint constraint, SchemaDTONode node,
            List<ValidationViolation> violations) {
        validateRangeConstraint(constraint, node, MIN_LENGTH, MAX_LENGTH, violations);
    }

    /**
     * Validates {@link NumericIntervalConstraint}.
     * Checks that the min < max of the constraint. <p>
     * If this is not the case a {@Link SchemaDTOViolation} is added to {@code violations}
     * @param constraint The {@link NumericIntervalConstraint} to validate
     * @param node Location of the constraint
     * @param violations List of violations
     * @see {@link #validateRangeConstraint}
     */
    private static void validateNumericIntervalConstraint(NumericIntervalConstraint constraint, SchemaDTONode node,
            List<ValidationViolation> violations) {
        validateRangeConstraint(constraint, node, constraint.isIncludingMin() ? "minInclusive" : "minExclusive",
                constraint.isIncludingMax() ? "maxInclusive" : "maxExclusive", violations);
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
            SchemaDTONode node, List<ValidationViolation> violations) {
        ObjectResolver resolver = resolverConstraint.getResolver();
        if (resolver == null) {
            violations.add(new UnknownObjectViolation(node, CONSTRAINTS, RESOLVER));
        }
    }

    public static void validateDTODiff(Map<String, Object> origDto, Map<String, Object> newDto,
            List<ValidationViolation> violations) {
        MapDTOComparator comparator = new MapDTOComparator(origDto, newDto);
        List<Pair<SchemaDTONode, SchemaDTONode>> modifiedNodes = comparator.getModifiedNodes();
        if (!modifiedNodes.isEmpty()) {
            modifiedNodes.forEach(nodePair -> violations.add(
                    new AlreadyExistsViolation(nodePair.getRight(), nodePair.getLeft(), MODIFIED)));
        }
        List<SchemaDTONode> removedNodes = comparator.getRemovedNodes();
        if (!removedNodes.isEmpty()) {
            removedNodes.forEach(node -> {
                if (PRIMARY_TYPES.equals(node.getParentNode().getKey())
                        && (SYS_CONTENT.equals(node.getKey()) || SYS_ROOT.equals(node.getKey()))) {
                    violations.add(new PrimaryTypeValidationViolation(node, "removed"));
                } else {
                    violations.add(new AlreadyExistsViolation(node, "removed"));
                }
            });
        }
    }
}
