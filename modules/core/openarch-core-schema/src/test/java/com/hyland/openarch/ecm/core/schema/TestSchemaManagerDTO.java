/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.schema.validation.SchemaValidationException;
import com.hyland.openarch.ecm.core.schema.validation.ValidationReport;

import tools.jackson.databind.ObjectMapper;

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
        Map<String, Object> map = MAPPER.readValue(modelJson, Map.class);
        try {
            new SchemaManagerDTO(map, null, false);
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
        Map<String, Object> map = MAPPER.readValue(modelJson, Map.class);
        new SchemaManagerDTO(map, null, false);
    }

    @Test
    public void testManagedCharacteristicsAreReservedForSystemSchemas() throws Exception {
        String modelJson = """
                {
                  "types": {
                    "customType": {
                      "fields": {
                        "value": {
                          "extends": "string",
                          "managed": {}
                        }
                      }
                    }
                  },
                  "schemas": {
                    "custom": {
                      "fields": {
                        "custom_value": {
                          "extends": "string",
                          "managed": {
                            "systemOwned": true
                          }
                        }
                      }
                    },
                    "system": {
                      "projectId": "project",
                      "fields": {
                        "sys_value": {
                          "extends": "string",
                          "managed": {}
                        }
                      }
                    }
                  },
                  "mixinTypes": {},
                  "primaryTypes": {}
                }
                """;
        Map<String, Object> map = MAPPER.readValue(modelJson, Map.class);

        SchemaValidationException e = assertThrows(SchemaValidationException.class,
                () -> new SchemaManagerDTO(map, null, false));
        assertEquals(Set.of("types/customType/fields/value/managed", "schemas/custom/fields/custom_value/managed",
                "schemas/system/fields/sys_value/managed"),
                e.getReport().asList().stream().map(v -> v.getPath()).collect(java.util.stream.Collectors.toSet()));
        e.getReport()
         .asList()
         .forEach(v -> assertEquals(
                 "Managed field characteristics are reserved for core system schemas (at \"%s\").".formatted(
                         v.getPath()),
                 v.getMessage(Locale.ENGLISH)));
    }

    @Test
    public void testManagedCharacteristicsAreAllowedForSystemSchemas() throws Exception {
        String modelJson = """
                {
                  "types": {},
                  "schemas": {
                    "system": {
                      "fields": {
                        "sys_value": {
                          "extends": "string",
                          "managed": {
                            "systemOwned": true
                          }
                        }
                      }
                    }
                  },
                  "mixinTypes": {},
                  "primaryTypes": {}
                }
                """;
        Map<String, Object> map = MAPPER.readValue(modelJson, Map.class);

        SchemaManager manager = new SchemaManagerDTO(map, null, false);
        assertTrue(manager.getField("sys_value").getCharacteristics().systemOwned());
    }

    @Test
    public void testMissingPrimaryTypeParentMeansSysContent() throws Exception {
        String modelJson = """
                {
                  "types": {},
                  "schemas": {},
                  "mixinTypes": {},
                  "primaryTypes": {
                    "Custom": {},
                    "SysContent": {}
                  }
                }
                """;
        Map<String, Object> map = MAPPER.readValue(modelJson, Map.class);

        SchemaManager manager = new SchemaManagerDTO(map);

        assertEquals("SysContent", manager.getDocumentType("Custom").getSuperType().getName());
        assertEquals("SysContent", manager.toContentModel().primaryTypes().get("Custom").extend());
    }

    /**
     * A type that belongs to a project must still say so once it has been through the schema layer
     * and back out again.
     * <p>
     * List and complex types always did; a <em>simple</em> type (one that is a supertype plus
     * constraints) did not, because its DTO was built from its supertype's and only the constraints
     * were lifted across, so a project-scoped simple type lost its {@code projectId} on every save.
     * All three kinds are covered here so the asymmetry cannot come back.
     */
    @Test
    public void testProjectScopedTypesKeepTheirProjectId() throws Exception {
        String modelJson = """
                {
                  "types": {
                    "myproject#boundedString": {
                      "extends": "string",
                      "constraints": {
                        "maxLength": 10
                      },
                      "projectId": "P123"
                    },
                    "myproject#stringList": {
                      "list": "string",
                      "projectId": "P123"
                    },
                    "myproject#pair": {
                      "fields": {
                        "first": "string",
                        "second": "string"
                      },
                      "projectId": "P123"
                    }
                  },
                  "schemas": {},
                  "mixinTypes": {},
                  "primaryTypes": {}
                }
                """;
        Map<String, Object> map = MAPPER.readValue(modelJson, Map.class);
        SchemaManager schemaManager = new SchemaManagerDTO(map);

        Map<String, Object> types = (Map<String, Object>) schemaManager.toMap().get("types");
        types.forEach((name, type) -> assertEquals("P123", ((Map<String, Object>) type).get("projectId"),
                "projectId lost for type " + name));
    }

}
