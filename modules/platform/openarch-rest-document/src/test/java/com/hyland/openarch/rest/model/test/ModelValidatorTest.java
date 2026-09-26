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
package com.hyland.openarch.rest.model.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.rest.document.ObjectMapperConfiguration;
import com.hyland.openarch.rest.model.exception.ModelValidationException;
import com.hyland.openarch.rest.model.validation.ModelValidator;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.DatabindException;

@Features(CoreFeature.class)
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-repo-core-types-contrib.xml")
@SpringBootTest(classes = { ModelValidator.class, ObjectMapperConfiguration.class })
public class ModelValidatorTest extends RuntimeSpringBootTest {

    private static final String REPO_ID = "test";

    @Autowired
    ModelValidator modelValidator;

    @RuntimeInject
    TypeService typeService;

    @Test
    public void validatorDoNotThrowsExceptionIfFullTextFlagOnValidFields() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                  "schemas": {
                    "testX": {
                      "fields": {
                        "test_string": {
                          "type": "string",
                          "fullTextIndexed": true
                        },
                        "test_binary": {
                          "type": "binary",
                          "fullTextIndexed": true
                        },
                        "test_blob": {
                          "extends": "blob",
                          "fullTextIndexed": true
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }

    @Test
    public void validatorThrowsExceptionIfFullTextFlagOnInvalidFields() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                  "schemas": {
                    "testX": {
                      "fields": {
                        "test_boolean": {
                          "type": "boolean",
                          "fullTextIndexed": true
                        },
                        "test_long": {
                          "type": "long",
                          "fullTextIndexed": true
                        },
                        "test_string": {
                          "type": "string",
                          "fullTextIndexed": true
                        },
                        "test_blob": {
                          "extends": "blob",
                          "fullTextIndexed": true
                        }
                      },
                      "prefix": "test"
                    }
                  }
                }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001")).getReport();
        assertEquals(2, report.numberOfViolations());
        assertEquals(
                "The flag fullTextIndexed cannot be added to the field test_boolean from schema testX\n"
                        + "The flag fullTextIndexed cannot be added to the field test_long from schema testX",
                report.toString());
    }

    @Test
    public void validatorThrowsExceptionIfDateNotInISO8601() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                     "schemas": {
                       "test": {
                         "fields": {
                           "test_desc": {
                             "type": "string"
                             }
                         },
                            "prefix": "test"
                       },
                        "test_schema": {
                          "fields": {
                            "test_date": {
                              "default": "test",
                              "extends": "date"
                            }
                          }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001")).getReport();
        assertEquals(1, report.numberOfViolations());
        assertEquals(
                "The default value test cannot be added to the field test_date from schema test_schema as it is not a valid ISO 8601 format [yyyy-MM-dd[THH:mm:ss[.SSS][+tz]]]",
                report.toString());
    }

    @Test
    public void validatorThrowsExceptionIfLengthBoundIsNegative() throws Exception {
        // a negative bound is dropped by the constraint layer, where a missing bound means "unbounded": the field would
        // silently end up with no length limit at all. A length is held as a Long, so this is caught as the model is
        // read rather than by the validator, which never sees the dropped bound.
        var modelToValidate = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_name": {
                              "extends": "string",
                              "constraints": { "maxLength": -5 }
                            }
                          }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var exception = assertThrows(DatabindException.class,
                () -> mapper.convertValue(jsonNode, ContentModelDTO.class));
        assertTrue(exception.getMessage().contains("-5"), exception.getMessage());
    }

    @Test
    public void validatorThrowsExceptionIfIntervalBoundIsNeitherNumberNorDate() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_when": {
                              "extends": "date",
                              "constraints": { "minInclusive": "not-a-date" }
                            }
                          }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001")).getReport();
        assertEquals(1, report.numberOfViolations());
        assertEquals(
                "The minInclusive value not-a-date cannot be added to the field test_when from schema test_schema as it is neither a number nor a valid ISO 8601 format [yyyy-MM-dd[THH:mm:ss[.SSS][+tz]]]",
                report.toString());
    }

    @Test
    public void validatorThrowsExceptionIfNestedFieldBoundIsInvalid() throws Exception {
        // a bad length bound is rejected wherever it sits, as the model is read: on a sub-field of a complex field, and
        // on the item type of a list. A length is held as a Long, so a non-integer such as 1.5 cannot survive
        // deserialization to reach the validator and must be caught here instead.
        var complexModel = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_complex": {
                              "fields": {
                                "inner": {
                                  "extends": "string",
                                  "constraints": { "maxLength": -1 }
                                }
                              }
                            }
                          }
                        }
                     }
                 }
                """;
        var complexException = assertThrows(DatabindException.class,
                () -> mapper.convertValue(mapper.readTree(complexModel), ContentModelDTO.class));
        assertTrue(complexException.getMessage().contains("-1"), complexException.getMessage());

        var listModel = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_list": {
                              "list": {
                                "extends": "string",
                                "constraints": { "minLength": 1.5 }
                              }
                            }
                          }
                        }
                     }
                 }
                """;
        var listException = assertThrows(DatabindException.class,
                () -> mapper.convertValue(mapper.readTree(listModel), ContentModelDTO.class));
        assertTrue(listException.getMessage().contains("1.5"), listException.getMessage());
    }

    @Test
    public void validatorAcceptsValidConstraintBounds() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        // a length as a plain integer, and interval bounds as a number and as an ISO 8601 date, are all readable
        var modelToValidate = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_name": {
                              "extends": "string",
                              "constraints": { "minLength": 0, "maxLength": 42 }
                            },
                            "test_when": {
                              "extends": "date",
                              "constraints": { "minInclusive": "2025-01-01" }
                            },
                            "test_size": {
                              "extends": "long",
                              "constraints": { "maxInclusive": 100 }
                            }
                          }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        modelValidator.validate(currentModel, modelToValidateDTO, "HxP001");
    }

    @Test
    public void validatorWillNotThrowExceptionIfDateNotInISO8601() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_date": {
                              "default": "1600-02-29T08:19:51+09:43",
                              "extends": "date"
                            }
                          }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }

    @Test
    public void validatorWillNotThrowExceptionIfDateWithoutTime() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_date": {
                              "extends": "date",
                              "default": "2025-10-01",
                              "constraints": {
                                "minInclusive": "2025-01-01",
                                "maxInclusive": "2025-12-31"
                              }
                            }
                          }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }

    @Test
    public void validatorThrowsExceptionIfDefaultValueIsNotBoolean() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                     "schemas": {
                        "test": {
                          "fields": {
                            "test_desc": {
                              "type": "string"
                            }
                          },
                          "prefix": "test"
                       },
                        "test_bool": {
                          "fields": {
                            "test_boolean": {
                              "default": "test",
                              "extends": "boolean"
                            }
                          }
                        }
                     }
                }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001")).getReport();
        assertEquals(1, report.numberOfViolations());
        assertEquals(
                "The default value test cannot be added to the field test_boolean from schema test_bool as it is not a valid boolean type",
                report.toString());
    }

    @Test
    public void validatorWillNotThrowExceptionIfDefaultValueIsBoolean() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                {
                     "schemas": {
                        "test_schema": {
                          "fields": {
                            "test_bool": {
                              "default": "true",
                              "extends": "boolean"
                            }
                          }
                        }
                     }
                }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }
    @Test
    void validatorAllowsImplicitSystemSchema() throws Exception {
        var modelToValidate = """
                 {
                     "primaryTypes": {
                        "SysFile": {
                          "extends": "SysContent",
                          "mixins": [
                             "SysFilish",
                              "SysVersionable"
                          ],
                          "schemas": [
                             "sysfile",
                             "sysfiles"
                          ]
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(modelToValidateDTO));
    }

    @Test
    void validatorAllowsPrimaryTypeWithoutExplicitSchema() throws Exception {
        var modelToValidate = """
                 {
                     "primaryTypes": {
                        "SysFile": {
                           "extends": "SysContent",
                           "mixins": [
                              "SysFilish",
                              "SysVersionable"
                           ]
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(modelToValidateDTO));
    }

    @Test
    public void validatorWillNotThrowExceptionIfPrimaryTypeInheritsSystemSchema() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                 {
                     "primaryTypes": {
                        "File1":  {
                           "extends": "SysContent",
                           "mixins": [
                              "SysVersionable"
                           ],
                           "schemas": [
                              "system",
                              "sysfile"
                           ]
                        },
                        "File2":  {
                           "extends": "File1"
                        }
                    }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }

    @Test
    public void validatorWillNotThrowExceptionIfPrimaryTypeInheritsSystemSchemaMixin() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                 {
                    "mixinTypes": {
                      "NewMixin": {
                        "schemas": [
                          "system"
                        ]
                      }
                    },
                     "primaryTypes": {
                        "File1": {
                           "mixins": [
                              "NewMixin"
                           ]
                        },
                        "File2":  {
                           "extends": "File1"
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }

    @Test
    public void validatorWillNotThrowExceptionIfPrimaryTypeHasItsOwnSchemaWithSystem() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                 {
                     "primaryTypes": {
                        "File1":  {
                           "extends": "Document",
                           "schemas": [
                              "system",
                              "sysfile"
                           ]
                        }
                    }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }

    @Test
    public void validatorWillNotThrowExceptionIfPrimaryTypeInheritsSystemSchemaOfValidDocumentTypes() throws Exception {
        var currentModel = typeService.getSchemaManager(REPO_ID);
        var modelToValidate = """
                 {
                     "primaryTypes": {
                        "File1":  {
                           "extends": "SysFile"
                        }
                     },
                     "schemas": {
                        "testdesc1": {
                           "fields": {
                              "description1": {
                                 "type": "string"
                              }
                            }
                        },
                        "testdesc2": {
                           "fields": {
                              "description2": {
                                 "type": "string"
                              }
                            }
                        }
                     }
                 }
                """;
        var jsonNode = mapper.readTree(modelToValidate);
        var modelToValidateDTO = mapper.convertValue(jsonNode, ContentModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }
}
