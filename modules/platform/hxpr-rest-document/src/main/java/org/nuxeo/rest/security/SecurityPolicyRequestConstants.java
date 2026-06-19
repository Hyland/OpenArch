/*
 * (C) Copyright 2024 Hyland (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.rest.security;

public class SecurityPolicyRequestConstants {

    private SecurityPolicyRequestConstants() {
        // utility class
    }

    public static final String GET_RESPONSE_EXAMPLE = """
            {
              "security clearance": {
                "engine": "securitylevel",
                "contentTypes": ["Contract"],
                "mixin": "Clearance",
                "userAttribute": "clearance"
              }
            }
            """;

    public static final String PUT_REQUEST_EXAMPLE = """
            {
              "security clearance": {
                "engine": "securitylevel",
                "contentTypes": ["Contract"],
                "mixin": "Clearance",
                "userAttribute": "user_clearance"
              }
            }
            """;

    public static final String PUT_RESPONSE_EXAMPLE = PUT_REQUEST_EXAMPLE;

    public static final String PUT_FOR_PROJECT_REQUEST_EXAMPLE = """
            {
              "organisational clearance": {
                "engine": "securitylevel",
                "contentTypes": ["Invoice"],
                "mixin": "Organisation",
                "userAttribute": "rank"
              }
            }
            """;

    public static final String PUT_FOR_PROJECT_RESPONSE_EXAMPLE = """
            {
              "organisational clearance": {
                "engine": "securitylevel",
                "projectId": "12345",
                "contentTypes": ["Invoice"],
                "mixin": "Organisation",
                "userAttribute": "rank"
              }
            }
            """;

    public static final String PATCH_REQUEST_EXAMPLE = """
            [
              {
                "op": "add",
                "path": "/organisational clearance",
                "value": {
                  "engine": "securitylevel",
                  "contentTypes": ["Invoice"],
                  "mixin": "Organisation",
                  "userAttribute": "rank"
                }
              },
              {
                "op": "replace",
                "path": "/security clearance/userAttribute",
                "value": "user_clearance"
              },
              {
                "op": "test",
                "path": "/security clearance/userAttribute",
                "value": "user_clearance"
              }
            ]
            """;
    public static final String PATCH_RESPONSE_EXAMPLE = """
            {
              "organisational clearance": {
                "engine": "securitylevel",
                "contentTypes": ["Invoice"],
                "mixin": "Organisation",
                "userAttribute": "rank"
              },
              "security clearance": {
                "engine": "securitylevel",
                "contentTypes": ["Contract"],
                "mixin": "Clearance",
                "userAttribute": "user_clearance"
              }
            }
            """;
}
