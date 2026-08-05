/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api.model;

import org.apache.commons.lang3.StringUtils;

/**
 * Exception thrown when trying to convert a property value to an incompatible type during read or write.
 */
public class PropertyConversionException extends InvalidPropertyValueException {

    private static final long serialVersionUID = 1L;

    public PropertyConversionException() {
        super();
    }

    public PropertyConversionException(String message) {
        super(message);
    }

    public PropertyConversionException(String message, Throwable cause) {
        super(message, cause);
    }

    public PropertyConversionException(Throwable cause) {
        super(cause);
    }

    public PropertyConversionException(Class<?> fromClass, Class<?> toClass) {
        this(getMessage(fromClass, toClass, null, null));
    }

    public PropertyConversionException(Class<?> fromClass, Class<?> toClass, String xpath) {
        this(fromClass, toClass, xpath, null);
    }

    public PropertyConversionException(Class<?> fromClass, Class<?> toClass, String xpath, Throwable cause) {
        this(getMessage(fromClass, toClass, xpath, cause), cause);
    }

    protected static String getMessage(Class<?> fromClass, Class<?> toClass, String xpath, Throwable cause) {
        String res = "Property Conversion failed from %s to %s".formatted(fromClass, toClass);
        if (StringUtils.isNotBlank(xpath)) {
            res += ": %s".formatted(xpath);
        }
        if (cause != null) {
            res += " (%s)".formatted(cause.getMessage());
        }
        return res;
    }

}
