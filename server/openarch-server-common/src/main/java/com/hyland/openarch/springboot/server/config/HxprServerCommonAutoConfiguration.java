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
package com.hyland.openarch.springboot.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

import com.hyland.openarch.springboot.server.ApplicationLifeCycle;
import com.hyland.openarch.springboot.server.GreetingResourceController;
import com.hyland.openarch.springboot.server.RootResourceController;
import com.hyland.openarch.springboot.server.VersionController;

/**
 * Spring Boot auto-configuration for HxPR Community Server Common.
 * <p>
 * Shared server beans (lifecycle listeners, controllers, configuration classes) are registered explicitly via
 * {@link Import} instead of {@code @ComponentScan}: auto-configuration classes must never enable component scanning (see
 * the Spring Boot reference, "Locating Auto-configuration Candidates"). Every new {@code @Component} /
 * {@code @Controller} / {@code @Configuration} added under {@code com.hyland.hxpr.springboot.server} must be listed here
 * to be picked up by the application.
 */
@AutoConfiguration
@Import({
        ApplicationLifeCycle.class,
        VersionController.class,
        GreetingResourceController.class,
        RootResourceController.class,
        CorsPolicyConfiguration.class,
        OpenApiConfiguration.class,
})
public class HxprServerCommonAutoConfiguration {

}
