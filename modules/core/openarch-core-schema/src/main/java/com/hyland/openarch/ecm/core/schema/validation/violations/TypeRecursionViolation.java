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
package com.hyland.openarch.ecm.core.schema.validation.violations;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.schema.dto.DTOPath;

/**
 * Violation designed to be used when an object has error when a provided type causes a Type Recursion. <p>
 * For example:<p>
 * A type is missing all of the following expected fields:
 * <ul>
 *  <li>extends</li>
 *  <li>fields</li>
 *  <li>list</li>
 * </ul>
 */
public class TypeRecursionViolation extends AbstractValidationViolation {

    private static final Logger log = LogManager.getLogger(TypeRecursionViolation.class);

    private static final String MESSAGE_KEY = "%s.%s".formatted(APPLICATION_KEY_BASE,
            TypeRecursionViolation.class.getSimpleName());

    private static final String DEFAULT_MSG_FORMAT = "The value \"%s\" is a type recursion.";

    public TypeRecursionViolation(DTOPath node, String... keyArgs) {
        super(node.key(), node.path(), MESSAGE_KEY, keyArgs);
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        return List.of(getInvalidValue());
    }

    @Override
    protected Logger getLog() {
        return log;
    }

    @Override
    protected String getDefaultMessage(Locale locale) {
        return DEFAULT_MSG_FORMAT.formatted(getInvalidValue());
    }

}
