/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.validation.io;

import static org.nuxeo.ecm.core.schema.types.constraints.Constraint.MESSAGES_DEFAULT_LANG;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Locale;

import org.nuxeo.ecm.core.schema.validation.violations.AbstractBaseValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * @since TODO
 */
@Schema(name = "ValidationError", description = "Represents a validation error when updating a document or content model.")
public class ValidationErrorDTO extends AbstractBaseValidationViolation {

    private final String message;

    public ValidationErrorDTO(String message, String messageKey, List<Object> parameters, Object invalidValue,
            String path, ValidationViolation.Type type) {
        super(messageKey, parameters, invalidValue, path, type);
        this.message = message;
    }

    public ValidationErrorDTO(ValidationViolation v) {
        this(v.getMessage(MESSAGES_DEFAULT_LANG), v.getMessageKey(), v.getMessageParameters(MESSAGES_DEFAULT_LANG),
                v.getInvalidValue(), v.getPath(), v.getType());
    }

    public ValidationErrorDTO(String message, String messageKey, List<Object> parameters, Object invalidValue, String path) {
        this(message, messageKey, parameters, invalidValue, path, Type.ERROR);
    }

    @Override
    public String getMessage(Locale locale) {
        return getMessage();
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getMessage() {
        return message;
    }

    @JsonInclude(Include.NON_EMPTY)
    public List<Object> getParameters() {
        return super.getMessageParameters(null);
    }
}
