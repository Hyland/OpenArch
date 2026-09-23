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
package com.hyland.openarch.ecm.core.schema.validation.violations;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.PATH_DELIMITER;

import java.util.List;
import java.util.Locale;

import com.hyland.openarch.ecm.core.schema.types.constraints.LengthConstraint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.schema.dto.DTOPath;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.RangeConstraint;

/**
 * Violation designed to be used when a class or object has error with the values provided. <p>
 * For example:<p>
 * A {@link LengthConstraint} that has a min value larger than a max value.
 */
public class RangeViolation extends AbstractValidationViolation {

    private static final Logger log = LogManager.getLogger(RangeViolation.class);

    private static final String MESSAGE_KEY = "%s.%s".formatted(APPLICATION_KEY_BASE,
            RangeViolation.class.getSimpleName());

    private static final String MESSAGE_FORMAT = "The value \"%s\" must be less than the value \"%s\".";

    private final transient List<Object> messageParameters;

    private final transient String defaultMessage;

    public <T extends Constraint & RangeConstraint<E>, E extends Number & Comparable<E>> RangeViolation(T constraint,
            DTOPath node, String minParameter, String maxParameter) {
        super(constraint.getMin(), appendPath(minParameter, node.path()), MESSAGE_KEY, Type.ERROR);
        this.messageParameters = extractParameters(constraint, node.path(), minParameter, maxParameter);
        this.defaultMessage = MESSAGE_FORMAT.formatted(constraint.getMin(), constraint.getMax());
    }

    /**
     * Extracts the parameters from the provided {@link Constraint} and {@link DTOPath}.
     * <p>
     * Parameters extracted will take the format:
     * <pre>
     *parameters: [
     *    /path/to/minimumValue,
     *    minimumValue,
     *    /path/to/maximumValue,
     *    maximumValue
     *]
     * </pre>
     *
     * @param <T> {@link Constraint} implementing {@link RangeConstraint}
     * @param <E> {@link Number} implementing {@link Comparable} used by the {@link RangeConstraint}
     * @param constraint The constraint to extract values from
     * @param node The json node to extract locations/ paths from
     * @param minParameter The name of the parameter associated with {@link RangeConstraint#getMin}
     * @param maxParameter The name of the parameter associated with {@link RangeConstraint#getMax}
     * @return The list of parameters extracted from the node and constraint.
     */
    private <T extends Constraint & RangeConstraint<E>, E extends Number & Comparable<E>> List<Object> extractParameters(
            T constraint, String constraintPath, String minParameter, String maxParameter) {
        String minPath = appendPath(minParameter, constraintPath);
        String maxPath = appendPath(maxParameter, constraintPath);
        return List.of(minPath, constraint.getMin(), maxPath, constraint.getMax());
    }

    private static String appendPath(String additionalPath, String basePath) {
        return basePath + PATH_DELIMITER + additionalPath;
    }

    @Override
    public String getDefaultMessage(Locale locale) {
        return defaultMessage;
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        return messageParameters;
    }

    @Override
    protected Logger getLog() {
        return log;
    }
}
