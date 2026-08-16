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

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.RESOLVER;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.schema.dto.DTOPath;

/**
 * Violation designed to be used when an object is not known. <p>
 * For example:<p>
 * Referencing an ObjectResolver that does not exist.
 */
public class UnknownObjectViolation extends AbstractValidationViolation {

    private static final Logger log = LogManager.getLogger(UnknownObjectViolation.class);

    private static final String MESSAGE_KEY = "%s.%s".formatted(APPLICATION_KEY_BASE,
            UnknownObjectViolation.class.getSimpleName());

    private static final String DEFAULT_MSG_FORMAT = "The value \"%s\" is unknown.";

    public UnknownObjectViolation(DTOPath node, String... keyArgs) {
        super(node, MESSAGE_KEY, Type.ERROR, keyArgs);
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        ArrayList<Object> result = new ArrayList<>();
        result.add(getInvalidValue());
        if (getInvalidValue() instanceof Map<?,?> dto && dto.get(RESOLVER) instanceof Map<?,?> resolver && resolver.get("name") != null) {
            result.add(resolver.get("name"));
        }
        result.add(getPath());
        return result;
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
