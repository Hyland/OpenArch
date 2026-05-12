/*
 * (C) Copyright 2019-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.core.api.validation;

import java.util.List;
import java.util.Locale;

import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.validation.violations.AbstractBaseValidationViolation;

/**
 * Global validation violation simply handling a message key.
 *
 * @since 11.1
 */
public class GlobalViolation extends AbstractBaseValidationViolation {

    public GlobalViolation(String messageKey) {
        this(messageKey, null);
    }

    public GlobalViolation(String messageKey, List<Object> parameters) {
        super(messageKey, parameters, null, null, Type.ERROR);
    }

    @Override
    public String getMessage(Locale locale) {
        Locale computedLocale = locale != null ? locale : Constraint.MESSAGES_DEFAULT_LANG;
        List<Object> invalidValue = getMessageParameters(computedLocale);
        return getMessage(locale, getMessageKey(), invalidValue);
    }
}
