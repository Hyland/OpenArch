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
package com.hyland.openarch.rest.model;

public class ModelRequestConstants {

    private ModelRequestConstants() {
        // utility class
    }

    // Each v2 example is its v1 counterpart with the optimistic-locking "version" field added as the first property.
    // The shared "_BODY" holds only the model's properties (no surrounding braces); v1 wraps them in braces and v2 adds
    // "version" first. (Annotation values must be compile-time constants, so this uses constant string concatenation
    // rather than a runtime rewrite.) A write request example shows the version the client last read
    // (VERSION_EXAMPLE), while a response example shows the current, incremented value (VERSION_INC_EXAMPLE).

    private static final String VERSION_EXAMPLE = "\"version\": 42";

    private static final String VERSION_INC_EXAMPLE = "\"version\": 43";

    private static final String PUT_REQUEST_EXAMPLE_BODY = """
            "mixinTypes": {
              "SysFolderish": {
                "noPerDocumentQuery": true,
                "projectId": "HxP001"
              }
            },
            "primaryTypes": {
              "SysContent": {},
              "SysFile": {
                "extends": "SysContent",
                "mixins": [
                  "SysFilish",
                  "SysVersionable"
                ],
                "schemas": [
                  "system",
                  "sysfile",
                  "sysfiles",
                  "identifier",
                  "address"
                ]
              },
              "SysFolder": {
                "extends": "SysContent",
                "mixins": [
                  "SysFolderish"
                ],
                "schemas": [
                  "identifier"
                ],
                "subtypes": [
                  "SysFile"
                ]
              }
            },
            "schemas": {
              "identifier": {
                "fields": {
                  "nm_firstname": {
                    "type": "string"
                  },
                  "nm_lastname": {
                    "type": "string"
                  }
                },
                "prefix": "nm"
              },
              "address": {
                "fields": {
                  "adr_home": {
                    "type": "string"
                  },
                  "adr_office": {
                    "type": "string"
                  }
                },
                "prefix": "adr"
              }
            },
            "types": {
              "blob": {
                "fields": {
                  "digest": {
                    "type": "string"
                  },
                  "encoding": {
                    "type": "string"
                  },
                  "filename": {
                    "type": "string"
                  },
                  "key": {
                    "type": "binary"
                  },
                  "length": {
                    "type": "long"
                  },
                  "mimeType": {
                    "type": "string"
                  }
                }
              }
            }
            """;

    public static final String PUT_REQUEST_EXAMPLE = "{" + PUT_REQUEST_EXAMPLE_BODY + "}";

    public static final String PUT_REQUEST_EXAMPLE_V2 = "{" + VERSION_EXAMPLE + "," + PUT_REQUEST_EXAMPLE_BODY + "}";

    public static final String PUT_RESPONSE_EXAMPLE_V2 = "{" + VERSION_INC_EXAMPLE + ","
            + PUT_REQUEST_EXAMPLE_BODY + "}";

    private static final String PUT_FOR_PROJECT_REQUEST_EXAMPLE_BODY = """
            "mixinTypes": {
              "testMT": {
                "noPerDocumentQuery": true
              }
            },
            "primaryTypes": {
              "FileMT": {
                "extends": "SysContent",
                "mixins": [
                  "SysVersionable"
                ],
                "schemas": [
                  "system",
                  "sysfile"
                ],
                "subtypes": [
                  "SysFile"
                ]
              }
            },
            "schemas": {
              "identifierMT": {
                "fields": {
                  "nm_firstname": {
                    "type": "string"
                  },
                  "nm_lastname": {
                    "type": "string"
                  }
                },
                "prefix": "nm"
              }
            }
            """;

    public static final String PUT_FOR_PROJECT_REQUEST_EXAMPLE = "{" + PUT_FOR_PROJECT_REQUEST_EXAMPLE_BODY + "}";

    public static final String PUT_FOR_PROJECT_REQUEST_EXAMPLE_V2 = "{" + VERSION_EXAMPLE + ","
            + PUT_FOR_PROJECT_REQUEST_EXAMPLE_BODY + "}";

    private static final String PUT_FOR_PROJECT_RESPONSE_EXAMPLE_BODY = """
            "mixinTypes": {
              "testMT": {
                "noPerDocumentQuery": true,
                "projectId": "PF00002"
              }
            },
            "primaryTypes": {
              "File1": {
                "extends": "SysContent",
                "mixins": [
                  "SysVersionable"
                ],
                "projectId": "PF00002",
                "schemas": [
                  "system",
                  "sysfile"
                ],
                "subtypes": [
                  "SysFile",
                  "SysFolder"
                ]
              }
            },
            "schemas": {
              "test": {
                "fields": {
                  "test_desc": {
                    "type": "string"
                  }
                },
                "prefix": "test",
                "projectId": "PF00002"
              }
            }
            """;

    public static final String PUT_FOR_PROJECT_RESPONSE_EXAMPLE = "{" + PUT_FOR_PROJECT_RESPONSE_EXAMPLE_BODY + "}";

    public static final String PUT_FOR_PROJECT_RESPONSE_EXAMPLE_V2 = "{" + VERSION_INC_EXAMPLE + ","
            + PUT_FOR_PROJECT_RESPONSE_EXAMPLE_BODY + "}";

    /**
     * The patch applies to the document {@code GET} returns, so a path must exist for
     * {@code replace}, {@code remove} and {@code test}: use {@code add} to introduce a key that is
     * not there yet, such as a {@code projectId} on a type that has none.
     */
    public static final String PATCH_REQUEST_EXAMPLE = """
            [
                { "op": "add", "path": "/schemas/dublincore/fields/dc_foobar", "value": { "type" : "string" } },
                { "op": "add", "path": "/primaryTypes/SysFile/projectId", "value": "HxP001" },
                { "op": "replace", "path": "/types/newType/fields/oldMember", "value": { "type" : "date"} },
                { "op": "test", "path": "/types/newType/fields/oldMember","value": { "type" : "date"} }
            ]
            """;
}
