/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.schema.types.resolver;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.common.function.FunctionUtils.sortByKeyAndMapValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

/**
 * Basic implementation of {@link ObjectResolver} which provides {@link #parameters} and {@link #validation} support.
 *
 * @since 10.2
 */
public abstract class AbstractObjectResolver implements ObjectResolver {

    protected Map<String, Object> parameters;

    protected boolean validation;

    @Override
    public void configure(Map<String, String> parameters) throws IllegalArgumentException, IllegalStateException {
        if (this.parameters != null) {
            throw new IllegalStateException("cannot change configuration, may be already in use somewhere");
        }
        this.validation = Boolean.parseBoolean(parameters.getOrDefault(VALIDATION_PARAMETER_KEY, TRUE.toString()));
        this.parameters = new HashMap<>();
        this.parameters.put(NAME_KEY, parameters.get(NAME_KEY));
        this.parameters.put(VALIDATION_PARAMETER_KEY, validation);
    }

    @Override
    public Map<String, Object> getParameters() {
        checkConfig();
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public String getName() {
        checkConfig();
        return (String) parameters.get(NAME_KEY);
    }

    @Override
    public boolean validate(Object value) throws IllegalStateException {
        checkConfig();
        return !validation || fetch(value) != null;
    }

    @Override
    public boolean validate(Object value, Object context) throws IllegalStateException {
        checkConfig();
        return !validation || fetch(value, context) != null;
    }

    protected void checkConfig() throws IllegalStateException {
        if (parameters == null) {
            throw new IllegalStateException(
                    "you should call #configure(Map<String, String>) before. Please get this resolver "
                            + "through ExternalReferenceService which is in charge of resolver configuration.");
        }
    }

    @Override
    public String getConstraintMessageKey() {
        List<String> pathTokens = new ArrayList<>();
        pathTokens.add(Constraint.MESSAGES_KEY);
        pathTokens.add(MESSAGE_KEY_ITEM);
        pathTokens.add(getName());
        return StringUtils.join(pathTokens, '.');
    }

    @Override
    public List<Object> getConstraintErrorParameters(Object invalidValue, Locale locale) {
        return List.of(invalidValue);
    }

    protected boolean checkMessage(String message, String key) {
        return message != null && !message.trim().isEmpty() && !key.equals(message);
    }

    protected String getDefaultConstrainErrorMessage(Object invalidValue) {
        return "%s cannot resolve reference %s".formatted(getName(), invalidValue);
    }

    /**
     * Use a default translation key : label.schema.constraint.resolver.[Resolver.getName()]
     *
     * @param resolver The requesting resolver.
     * @param suffixCase This field is a which allow to define alternative translation.
     * @param invalidValue The invalid value that don't match any entity.
     * @param locale The language in which the message should be generated.
     * @param additionnalParameters Relayed elements to build the message.
     * @return A message in the specified language
     * @since 7.1
     */
    protected String getConstraintErrorMessage(String bundle, String suffixCase, Object invalidValue, Locale locale,
            String... additionnalParameters) {
        String keyConstraint;
        if (suffixCase != null) {
            keyConstraint = StringUtils.join(List.of(getConstraintMessageKey(), suffixCase), '.');
        } else {
            keyConstraint = getConstraintMessageKey();
        }

        List<Object> params = new ArrayList<>();
        params.add(invalidValue);
        if (additionnalParameters != null) {
            params.addAll(Arrays.asList(additionnalParameters));
        }

        String message = I18NUtils.getMessageString(bundle != null ? bundle : Constraint.MESSAGES_BUNDLE, keyConstraint,
                params, locale);
        if (checkMessage(message, keyConstraint)) {
            return message;
        }
        return getDefaultConstrainErrorMessage(invalidValue);
    }

    @Override
    public Map<String, Object> toDTO() {
        Map<String, Object> params = new HashMap<>(parameters);
        params.remove(NAME_KEY);
        params.remove(VALIDATION_PARAMETER_KEY);
        if (!validation) {
            params.put(VALIDATION_PARAMETER_KEY, "false");
        }
        params = sortByKeyAndMapValue(params, Entry::getValue);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put(NAME_KEY, getName()); // put name first
        dto.putAll(params);
        return dto;
    }

}
