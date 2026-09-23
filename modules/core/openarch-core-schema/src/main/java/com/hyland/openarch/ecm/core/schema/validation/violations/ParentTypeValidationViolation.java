/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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

public class ParentTypeValidationViolation extends AbstractValidationViolation {
    private static final Logger log = LogManager.getLogger(ParentTypeValidationViolation.class);

    private static final String MESSAGE_KEY = "%s.%s".formatted(APPLICATION_KEY_BASE,
            ParentTypeValidationViolation.class.getSimpleName());

    private static final String DEFAULT_MSG_FORMAT = "%s parent type defined for primary type %s at path %s";

    private final transient List<Object> messageParameters;

    public ParentTypeValidationViolation(DTOPath node, String primaryType, boolean noValidParent, String... keyArgs) {
        super(node, MESSAGE_KEY, Type.ERROR, keyArgs);
        this.messageParameters = List.of(noValidParent ? "No" : "Invalid", primaryType, getPath());
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        return messageParameters;
    }

    @Override
    protected Logger getLog() {
        return log;
    }

    @Override
    protected String getDefaultMessage(Locale locale) {
        return DEFAULT_MSG_FORMAT.formatted(getMessageParameters(locale).toArray());
    }
}
