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
package com.hyland.hxpr.springboot.server;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import com.hyland.openarch.runtime.springboot.actuator.ApplicationInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping(path = "/v", produces = APPLICATION_JSON_VALUE)
public class VersionController {

    ApplicationInfo appInfo;

    public VersionController(ApplicationInfo appInfo) {
        this.appInfo = appInfo;
    }

    @GetMapping()
    @Operation(summary = "Returns the application version.", hidden = true)
    public Map<String, String> getVersion() {
        return Collections.singletonMap("version", appInfo.version());
    }

}
