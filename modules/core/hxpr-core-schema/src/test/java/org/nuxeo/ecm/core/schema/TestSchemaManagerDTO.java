/*
 * (C) Copyright 2025 Hyland (https://hyland.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationException;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestSchemaManagerDTO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testDuplicateProperty() throws Exception {
        String modelJson = """
                    {
                      "schemas": {
                        "s1": {
                          "prefix": "pref",
                          "fields": {
                            "pref_validator": "string"
                          }
                        },
                        "s2": {
                          "prefix": "pref",
                          "fields": {
                            "pref_validator": "long"
                          }
                        }
                      },
                      "types": {},
                      "mixinTypes": {},
                      "primaryTypes": {}
                    }
                """;
        Map<String, Object> dto = MAPPER.readValue(modelJson, Map.class);
        try {
            new SchemaManagerDTO(dto, null, false);
            // fail("should have detected duplicate");
            // for now an error is logged
        } catch (SchemaValidationException e) {
            ValidationReport report = e.getReport();
            assertEquals(1, report.numberOfViolations());
            String msg = report.asList().get(0).getMessage(Locale.ENGLISH);
            assertEquals("Duplicate property \"pref_validator\" in schemas \"s1\" (string) and \"s2\" (long).", msg);
        }
    }

    @Test
    public void testDateWithoutTime() throws Exception {
        String modelJson = """
                {
                  "schemas": {
                    "myschema": {
                      "prefix": "my",
                      "fields": {
                        "my_prop": {
                          "extends": "date",
                          "default": "2025-08-01",
                          "constraints": {
                            "minInclusive": "2025-01-01",
                            "maxInclusive": "2025-12-31"
                          }
                        }
                      }
                    }
                  },
                  "types": {},
                  "mixinTypes": {},
                  "primaryTypes": {}
                }
                """;
        Map<String, Object> dto = MAPPER.readValue(modelJson, Map.class);
        new SchemaManagerDTO(dto, null, false);
    }

}
