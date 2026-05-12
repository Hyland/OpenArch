/*
 * (C) Copyright 2023-2024 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.schema.validation.violations;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.schema.validation.SchemaDTONode;

/**
 * Violation uses when an item that you wish to remove or modified already exists.
 * This violation will produce a Warning, not an Error.
 * <p>
 * MessageKey: {@code label.schema.application.violation.AlreadyExistsViolation.*}
 */
public class AlreadyExistsViolation extends AbstractValidationViolation {

    private static final Logger log = LogManager.getLogger(AlreadyExistsViolation.class);

    private static final String MESSAGE_KEY = "%s.%s".formatted(APPLICATION_KEY_BASE,
            AlreadyExistsViolation.class.getSimpleName());

    private static final String DEFAULT_MSG_FORMAT = "The value \"%s\" cannot be applied as \"%s\" already exists.";

    private final transient List<Object> messageParameters;

    /**
     * Creates an {@link AlreadyExistsViolation} where the parameter output is:
     * <pre>
     *[
     *  invalidValue,
     *  path
     *]
     * </pre>
     * For use with keyArg requiring 2 or less parameters, e.g.:
     * <ul>
     *  <li>label.schema.application.violation.AlreadyExistsViolation</li>
     *  <li>label.schema.application.violation.AlreadyExistsViolation.removed</li>
     * </ul>
     * There may be other possible keys, not listed here.
     * @see #AlreadyExistsViolation(SchemaDTONode, SchemaDTONode, String...)
     */
    public AlreadyExistsViolation(SchemaDTONode node, String... keyArgs) {
        super(node, MESSAGE_KEY, ValidationViolation.Type.WARNING, keyArgs);
        this.messageParameters = List.of(getInvalidValue(), getPath());
    }

    /**
     * Creates an {@link AlreadyExistsViolation} where the parameter output is:
     * <pre>
     *[
     *  invalidValue,
     *  path,
     *  originalValue
     *]
     * </pre>
     * For use with keyArg requiring 3 or less parameters, .e.g: <p>
     * <ul>
     *  <li>label.schema.application.violation.AlreadyExistsViolation.modified</li>
     * </ul>
     * There may be other possible keys, not listed here.
     * @see #AlreadyExistsViolation(SchemaDTONode, String...)
     */
    public AlreadyExistsViolation(SchemaDTONode invalidNode, SchemaDTONode originalNode, String... keyArgs) {
        super(invalidNode, MESSAGE_KEY, ValidationViolation.Type.WARNING, keyArgs);
        this.messageParameters = List.of(getInvalidValue(), originalNode.getNode(), getPath());
    }

    public AlreadyExistsViolation(String messageKey, String... parameters) {
        super(messageKey, parameters);
        this.messageParameters = Arrays.asList(parameters);
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        return messageParameters;
    }

    @Override
    protected Logger getLog() {
        return log;
    }

    @Override
    protected String getDefaultMessage(Locale locale) {
        return DEFAULT_MSG_FORMAT.formatted(getMessageParameters(locale).toArray());
    }

}
