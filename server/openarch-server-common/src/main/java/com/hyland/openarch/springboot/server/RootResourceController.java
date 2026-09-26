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
package com.hyland.openarch.springboot.server;

import static org.springframework.http.MediaType.TEXT_HTML_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.hyland.openarch.runtime.springboot.actuator.ApplicationInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Resource for default index page.
 */
@Controller // not using @RestController because of getIndex()
@RequestMapping(RootResourceController.PATH)
@Tag(name = "Application Resource", description = "Application Information API")
@Schema(hidden = true)
@EnableConfigurationProperties(ApplicationInfo.class)
@ConditionalOnProperty(name = "info.pages.debug.enabled", havingValue = "true")
public class RootResourceController {

    public static final String PATH = "";

    ApplicationInfo appInfo;

    public RootResourceController(ApplicationInfo appInfo) {
        this.appInfo = appInfo;
    }

    @ModelAttribute
    public void addAttributes(Model model) {
        model.addAttribute("appInfo", appInfo);
        model.addAttribute("title", "%s %s".formatted(appInfo.name(), appInfo.version()));
    }

    @GetMapping(value = "", produces = TEXT_HTML_VALUE)
    @Operation(hidden = true)
    public String getIndex() {
        return "index";
    }
}
