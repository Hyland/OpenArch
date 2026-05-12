/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.schema.types.constraints;

import static org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver.NAME_KEY;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolverService;
import org.nuxeo.runtime.api.Framework;

/**
 * External references are document field with a simple type whose value refers to an external business entity. This
 * constraints ensure some value is a reference of an existing external entity resolved by the underlying resolver :
 * {@link #getResolver()} .
 *
 * @since 7.1
 */
public final class ObjectResolverConstraint extends AbstractConstraint {

    private ObjectResolver resolver;

    public ObjectResolverConstraint(ObjectResolver resolver) {
        super();
        this.resolver = resolver;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static ObjectResolverConstraint fromDTO(Map<String, Object> dto) {
        if (!(dto.get("resolver") instanceof Map map)) {
            return null;
        }
        Map<String, String> parameters = new HashMap<>();
        map.forEach((k, v) -> parameters.put(Objects.toString(k, null), Objects.toString(v, null)));
        String name = parameters.remove(NAME_KEY);

        var objectResolverService = Framework.getService(ObjectResolverService.class);
        ObjectResolver resolver = objectResolverService.getResolver(name, parameters);
        return new ObjectResolverConstraint(resolver);
    }

    @Override
    public Map<String, Object> toDTO() {
        return Map.of("resolver", resolver.toDTO());
    }

    public ObjectResolver getResolver() {
        return resolver;
    }

    @Override
    public boolean validate(Object object) {
        if (object == null) {
            return true;
        }
        return resolver.validate(object);
    }

    @Override
    public Description getDescription() {
        Map<String, Object> parameters = Collections.unmodifiableMap(resolver.getParameters());
        return new Description(resolver.getName(), parameters);
    }

    @Override
    public String getMessageKey() {
        return resolver.getConstraintMessageKey();
    }

    @Override
    public List<Object> getMessageParameters(Object invalidValue, Locale locale) {
        return resolver.getConstraintErrorParameters(getComputedInvalidValue(invalidValue), getLocale(locale));
    }

    @Override
    public String getErrorMessage(Object invalidValue, Locale locale) {
        return resolver.getConstraintErrorMessage(getComputedInvalidValue(invalidValue), getLocale(locale));
    }

}
