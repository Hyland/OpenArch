/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 */
package org.nuxeo.ecm.core.schema.validation.violations;

import java.util.List;
import java.util.Locale;

import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

import com.fasterxml.jackson.annotation.JsonInclude;

public abstract class AbstractBaseValidationViolation implements ValidationViolation {

    private final transient String messageKey;

    private final transient List<Object> parameters;

    private final transient Object invalidValue;

    private final transient String path;

    private final transient ValidationViolation.Type type;

    protected AbstractBaseValidationViolation(String messageKey, List<Object> parameters, Object invalidValue,
            String path, Type type) {
        this.messageKey = messageKey;
        this.parameters = parameters;
        this.invalidValue = invalidValue;
        this.path = path;
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Object getInvalidValue() {
        return invalidValue;
    }

    @Override
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getMessageKey() {
        return messageKey;
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        return parameters;
    }

    @Override
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getPath() {
        return path;
    }

    @Override
    public String getMessage(Locale locale) {
        return getMessage(locale, messageKey, invalidValue == null ? List.of() : List.of(invalidValue));
    }

    protected String getMessage(Locale locale, String messageKey, List<Object> invalidValueList) {
        Locale computedLocale = locale != null ? locale : Constraint.MESSAGES_DEFAULT_LANG;
        return I18NUtils.getMessageString(Constraint.MESSAGES_BUNDLE, escapeMessageFormat(messageKey),
                invalidValueList, computedLocale);
    }

    /**
     * When using {@link I18NUtils#getMessageString(String, String, List, Locale)} the string may include formatting.
     * As we do not need any string formatting any special characters should be escaped.
     * @see java.text.MessageFormat
     */
    protected String escapeMessageFormat(String message) {
        return message.replace("{", "'{'").replace("}", "'}'");
    }
}
