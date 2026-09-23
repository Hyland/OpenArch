/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.schema.types.resolver;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * External references are document field with a simple type whose value refers to an external business entity. Objects
 * implementing this interface are able to resolve the entity using the reference.
 *
 * @since 7.1
 */
public interface ObjectResolver {

    String NAME_KEY = "name";

    /**
     * The validation parameter key to enable/disable validation on resolver. Enabled by default.
     *
     * @since 10.2
     */
    String VALIDATION_PARAMETER_KEY = "validation";

    String MESSAGE_KEY_ITEM = "resolver";

    /**
     * Configure this resolver.
     *
     * @param parameters A map of parameter whose keys are parameter names and map value are corresponding values.
     * @throws IllegalArgumentException If some parameter are not compatible with this resolver.
     * @throws IllegalStateException If this resolver is already configured.
     * @since 7.1
     */
    void configure(Map<String, String> parameters) throws IllegalArgumentException, IllegalStateException;

    /**
     * Returns the resolved object types.
     *
     * @since 7.2
     */
    List<Class<?>> getManagedClasses();

    /**
     * Provides this resolver name.
     *
     * @return The resolver name.
     * @since 7.1
     */
    String getName();

    /**
     * Provides this resolver parameters.
     *
     * @return A map containing associating parameter name to its value
     * @since 7.1
     */
    Map<String, Object> getParameters();

    /**
     * Validates some value references an existing entity.
     *
     * @param value The reference.
     * @return true if value could be resolved as an existing external reference, false otherwise.
     * @throws IllegalStateException If this resolver has not been configured.
     * @since 7.1
     */
    boolean validate(Object value);

    /**
     * Validates some value references an existing entity, in the given context
     *
     * @param value The reference.
     * @param context A resolver-specific context allowing resolution of the value.
     * @return true if value could be resolved as an existing external reference, false otherwise.
     * @throws IllegalStateException If this resolver has not been configured.
     * @since 10.2
     */
    default boolean validate(Object value, Object context) {
        return validate(value);
    }

    /**
     * Provides the entity referenced by a value.
     *
     * @param value The reference.
     * @return The referenced entity, null if no entity matches the value.
     * @throws IllegalStateException If this resolver has not been configured.
     * @since 7.1
     */
    Object fetch(Object value);

    /**
     * Provides the entity referenced by a value, in the given context.
     *
     * @param value The reference.
     * @param context A resolver-specific context allowing resolution of the value.
     * @return The referenced entity, null if no entity matches the value.
     * @throws IllegalStateException If this resolver has not been configured.
     * @since 10.2
     */
    default Object fetch(Object value, Object context) {
        return fetch(value);
    }

    /**
     * Provides the entity referenced by a value, return the entity as expected type.
     *
     * @param value The reference.
     * @return The referenced entity, null if no entity matches the value or if this entity cannot be converted as type.
     * @throws IllegalStateException If this resolver has not been configured.
     * @since 7.1
     */
    <T> T fetch(Class<T> type, Object value);

    /**
     * Generates a reference to an entity.
     *
     * @param object The entity.
     * @return A reference to the entity or null if its not a managed entity type.
     * @throws IllegalStateException If this resolver has not been configured.
     * @since 7.1
     */
    Object getReference(Object object);

    /**
     * Returns the key used for the error message translation.
     */
    String getConstraintMessageKey();

    /**
     * Returns the parameters used in the error message.
     */
    List<Object> getConstraintErrorParameters(Object invalidValue, Locale locale);

    /**
     * Provides an error message to display when some invalid value does not match existing entity.
     *
     * @param invalidValue The invalid value that don't match any entity.
     * @param locale The language in which the message should be generated.
     * @return A message in the specified language or
     * @since 7.1
     */
    String getConstraintErrorMessage(Object invalidValue, Locale locale);

    Map<String, Object> toMap();

}
