/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package org.nuxeo.rest.model.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.rest.model.dto.ModelDTO;
import org.nuxeo.rest.model.exception.ModelValidationException;
import org.nuxeo.rest.model.validation.ModelValidator;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Features(CoreFeature.class)
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-repo-core-types-contrib.xml")
@SpringBootTest(classes = { ModelValidator.class })
public class ModelValidatorTest extends RuntimeSpringBootTest {

    private static final String REPO_ID = "test";

    @Autowired
    ModelValidator modelValidator;

    @RuntimeInject
    TypeService typeService;

    private static final ObjectMapper mapper = new ObjectMapper();

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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001")).getReport();
        assertEquals(1, report.numberOfViolations());
        assertEquals(
                "The default value test cannot be added to the field test_date from schema test_schema as it is not a valid ISO 8601 format [yyyy-MM-dd[THH:mm:ss[.SSS][+tz]]]",
                report.toString());
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }
    @Test
    void validatorThrowsExceptionIfSystemNotPresentForSchemaInPrimaryType() throws Exception {
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(modelToValidateDTO)).getReport();
        assertEquals(1, report.numberOfViolations());
        assertEquals(
                "The system schema must be present in the following content type SysFile",
                report.toString());
    }

    @Test
    void validatorThrowsExceptionIfSchemaNotPresentInPrimaryType() throws Exception {
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
        var report = assertThrows(ModelValidationException.class,
                () -> modelValidator.validate(modelToValidateDTO)).getReport();
        assertEquals(1, report.numberOfViolations());
        assertEquals(
                "The system schema must be present in the following content type SysFile",
                report.toString());
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
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
        var modelToValidateDTO = mapper.convertValue(jsonNode, ModelDTO.class);
        assertDoesNotThrow(() -> modelValidator.validate(currentModel, modelToValidateDTO, "HxP001"));
    }
}
