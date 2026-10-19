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

import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OpenArchApplication {

    public static void main(String... args) {
        SpringApplication.run(OpenArchApplication.class, args);
    }

    @Bean
    public OpenApiCustomizer communityBasicAuthentication() {
        return openApi -> {
            String schemeName = "BasicAuth";
            openApi.getComponents().addSecuritySchemes(schemeName,
                    new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                                        .description("Authenticate with a configured OpenArch username and password."));
            // Separate requirements are alternatives (OR), not credentials required together (AND).
            openApi.addSecurityItem(new SecurityRequirement().addList(schemeName));
        };
    }
}
