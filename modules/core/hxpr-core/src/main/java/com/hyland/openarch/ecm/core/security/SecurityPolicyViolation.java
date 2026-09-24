/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.security;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.hyland.openarch.common.utils.i18n.I18NUtils;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.validation.violations.AbstractBaseValidationViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;

public class SecurityPolicyViolation extends AbstractBaseValidationViolation {

    public SecurityPolicyViolation(Type type, String messageKey, Object... parameters) {
        super(messageKey, Arrays.asList(parameters), null, null, type);
    }

    public static void add(List<ValidationViolation> violations, Type type, String messageKey, Object... parameters) {
        violations.add(new SecurityPolicyViolation(type, messageKey, parameters));
    }

    @Override
    protected String getMessage(Locale locale, String messageKey, List<Object> invalidValueList) {
        // Overrides to use multiple parameters with the messageKey instead of a single invalidValue
        Locale computedLocale = locale != null ? locale : Constraint.MESSAGES_DEFAULT_LANG;
        return I18NUtils.getMessageString(Constraint.MESSAGES_BUNDLE, messageKey,
                getMessageParameters(computedLocale), computedLocale);
    }
}
