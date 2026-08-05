/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = TestEndpoint.TEST_AUTH_ENDPOINT_PATH, produces = APPLICATION_JSON_VALUE)
public class TestEndpoint {

    public static final String TEST_AUTH_ENDPOINT_PATH = "/testAuthEndpoint";

    public static final String DEFAULT_USERNAME = "anonymous";

    public static final String USER_PROP = "user";

    @GetMapping(path = "", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hello() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication authentication = ctx.getAuthentication();
        String name = authentication == null ? DEFAULT_USERNAME : authentication.getName();
        return ResponseEntity.ok(String.format("{\"%s\":\"%s\"}",USER_PROP, name));
    }
}
