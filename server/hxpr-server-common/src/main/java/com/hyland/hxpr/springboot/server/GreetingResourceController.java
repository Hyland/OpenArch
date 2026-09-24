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
package com.hyland.hxpr.springboot.server;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import com.hyland.openarch.runtime.springboot.OpenArchApplication;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = GreetingResourceController.PATH , produces = MediaType.TEXT_PLAIN_VALUE )
@Tag(name = "Greeting", description = "Hello World Endpoint for HXPR Community")
@ApiResponse200(description = "OK")
public class GreetingResourceController {

    public static final String PATH = "/hello"; // NOSONAR

    public static final String HELLO_MESSAGE_PATTERN = "Hello %s! Application status is: %s";

    private OpenArchApplication application;

    public GreetingResourceController(OpenArchApplication application) {
        this.application = application;
    }

    @GetMapping(produces = "text/plain")
    public String hello(Authentication auth) {
        String username = auth == null ? "anonymous" : auth.getName();
        return String.format(HELLO_MESSAGE_PATTERN, username, application.getStatus());
    }

    @GetMapping(value = "/protected" , produces = "text/plain")
    @Authenticated
    public String helloUser(Authentication auth) {
        return String.format(HELLO_MESSAGE_PATTERN, auth.getName(), application.getStatus());
    }
}
