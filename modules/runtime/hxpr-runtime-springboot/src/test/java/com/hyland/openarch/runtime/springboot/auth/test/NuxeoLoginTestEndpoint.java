/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot.auth.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.Principal;

import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class NuxeoLoginTestEndpoint {

    protected String sayHi(Principal user) {
        String username = user == null ? "Anonymous" : user.getName();
        return String.format("Hello %s", username);
    }

    @GetMapping("/hello")
    public String hello(Principal user) {
        assertPrincipal(user);
        return sayHi(user);
    }

    @GetMapping("/secured")
    public String secured(Principal user) {
        return hello(user);
    }

    @GetMapping("/exception")
    public String exception(Principal user) {
        return "This will throw an exception";
    }

    private void assertPrincipal(Principal user) {
        if (user != null) {
            assertEquals(user.getName(), NuxeoPrincipal.getCurrent().getId());
        }
    }
}
