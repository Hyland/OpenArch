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
package org.nuxeo.ecm.core.schema.validation.violations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.schema.SchemaManagerConstants;
import org.nuxeo.ecm.core.schema.dto.DTOPath;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

public abstract class AbstractValidationViolation extends AbstractBaseValidationViolation {

    protected static final String APPLICATION_KEY_BASE = "label.schema.application.violation";

    protected AbstractValidationViolation(Object invalidValue) {
        this(invalidValue, null, null, Type.ERROR);
    }

    protected AbstractValidationViolation(String messageKey, String... parameters) {
        super(messageKey, Arrays.asList(parameters), null, null, Type.ERROR);
    }

    protected AbstractValidationViolation(Object invalidValue, String path, String messageKey, Type type) {
        super(messageKey, null, invalidValue, path, type);
    }

    protected AbstractValidationViolation(Object invalidValue, String path, String messageKey, String... keyArgs) {
        this(invalidValue, path, appendMessageKey(messageKey, keyArgs), Type.ERROR);
    }

    protected AbstractValidationViolation(DTOPath node, String messageKey, Type type, String... keyArgs) {
        this(node.invalidValue(), node.path(), appendMessageKey(messageKey, keyArgs), type);
    }

    protected abstract Logger getLog();

    /**
     * The name of the messages bundle used to localize the message. <p>
     * For example: `schema_messages`
     * @return The name of the messages bundle used to localize the message, for example `schema_messages`.
     * @see {@link Constraint#MESSAGES_BUNDLE}
     */
    protected String getMessagesBundleName() {
        return Constraint.MESSAGES_BUNDLE;
    }

    /**
     * Message to be returned if there is no entry in the messages bundle
     * @param locale
     * @return The default message to use if there is no entry in the messages bundle.
     * @see {@link #getMessage(Locale)}
     */
    protected abstract String getDefaultMessage(Locale locale);

    /**
     * The message if it's found in message bundles, a generic message otherwise.
     * @param locale
     * @return The localized message if it's found in message bundles, a generic message otherwise.
     */
    @Override
    public String getMessage(Locale locale) {
        String key = getMessageKey();
        Locale computedLocale = locale != null ? locale : Constraint.MESSAGES_DEFAULT_LANG;
        List<Object> sanitizedParameters = sanitizeParameters(getMessageParameters(computedLocale));
        String message;
        try {
            message = I18NUtils.getMessageString(getMessagesBundleName(), key, sanitizedParameters, computedLocale);
        } catch (MissingResourceException e) {
            getLog().debug("No bundle found", e);
            message = null;
        }

        if (message != null && !message.trim().isEmpty() && !key.equals(message)) {
            // use the message if there's one
            return message;
        } else {
            if (locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
                return getDefaultMessage(locale);
            } else {
                return getMessage(Locale.ENGLISH);
            }
        }
    }

    private List<Object> sanitizeParameters(List<Object> parameters) {
        return parameters.stream().map(obj -> (Object) escapeMessageFormat(obj.toString())).toList();
    }

    protected static String appendMessageKey(String messageKey, String... keyArgs) {
        ArrayList<String> keyParts = new ArrayList<>();
        keyParts.add(messageKey);
        keyParts.addAll(Arrays.asList(keyArgs));
        return StringUtils.join(keyParts, SchemaManagerConstants.MESSAGE_KEY_SEPARATOR);
    }
}
