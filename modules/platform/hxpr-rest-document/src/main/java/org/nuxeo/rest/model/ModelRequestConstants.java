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
package org.nuxeo.rest.model;

public class ModelRequestConstants {

    private ModelRequestConstants() {
        // utility class
    }

    public static final String PUT_REQUEST_EXAMPLE = """
            {
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
            }
            """;

    public static final String PUT_FOR_PROJECT_REQUEST_EXAMPLE = """
            {
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
            }
            """;

    public static final String PUT_FOR_PROJECT_RESPONSE_EXAMPLE = """
            {
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
             }
            """;

    public static final String PATCH_REQUEST_EXAMPLE = """
            [
                { "op": "add", "path": "/schemas/dublincore/fields/dc_foobar", "value": { "type" : "string" } },
                { "op": "remove", "path": "/primaryTypes/SysFile/projectId" },
                { "op": "replace", "path": "/types/newType/fields/oldMember", "value": { "type" : "date"} },
                { "op": "test", "path": "/types/newType/fields/oldMember","value": { "type" : "date"} }
            ]
            """;
}
