/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.schema.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a {@link FieldDTO} from JSON.
 * <p>
 * A field can be written three ways — a bare type name, {@code {"type": …}} or
 * {@code {"extends": …, …}} — and all three mean the same thing, so this delegates to the shared
 * lenient reader in {@link ModelMapCodec#readField}. Doing it here rather than with a properties
 * creator is what lets {@code {"type": "string", "constraints": {…}}} keep its constraints instead
 * of silently dropping them.
 */
public class FieldDTODeserializer extends ValueDeserializer<FieldDTO> {

    @Override
    public FieldDTO deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        List<ValidationViolation> violations = new ArrayList<>();
        FieldDTO field = ModelMapCodec.readField(context.readValue(parser, Object.class), violations);
        if (!violations.isEmpty()) {
            // a value of the wrong shape is a bad request, not a field to quietly drop
            return context.reportInputMismatch(FieldDTO.class, violations.get(0).getMessage(Locale.ENGLISH));
        }
        return field;
    }
}
