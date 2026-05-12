/*
 * (C) Copyright 2023-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Supriti Paul
 */
package org.nuxeo.ecm.core.schema.validation.violations;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.schema.validation.SchemaDTONode;

/**
 * Violation uses when an item that you wish to remove Document or Root under primary type.
 * This violation will produce an Error.
 * <p>
 * MessageKey: {@code label.schema.application.violation.PrimaryTypeValidationViolation.removed}
 */

public class PrimaryTypeValidationViolation extends AbstractValidationViolation {
    private static final Logger log = LogManager.getLogger(PrimaryTypeValidationViolation.class);

    private static final String MESSAGE_KEY = "%s.%s".formatted(APPLICATION_KEY_BASE,
            PrimaryTypeValidationViolation.class.getSimpleName());

    private static final String DEFAULT_MSG_FORMAT = "The value \"%s\" already exists at \"%s\" and cannot be removed as primary types are not allowed to be removed.";

    private final transient List<Object> messageParameters;

    /**
     * Creates an {@link PrimaryTypeValidationViolation} where the parameter output is:
     * <pre>
     *[
     *  invalidValue,
     *  path
     *]
     * </pre>
     * For use with keyArg requiring 2 or less parameters, e.g.:
     * <ul>
     *  <li>label.schema.application.violation.PrimaryTypeValidationViolation.removed</li>
     * </ul>
     */
    public PrimaryTypeValidationViolation(SchemaDTONode node, String... keyArgs) {
        super(node, MESSAGE_KEY, ValidationViolation.Type.ERROR, keyArgs);
        this.messageParameters = List.of(getInvalidValue(), getPath());
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
