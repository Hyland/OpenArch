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
package com.hyland.openarch.rest.config;

import com.hyland.openarch.autoconfiguration.RestDocumentAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Component scanning for the REST document module.
 * <p>
 * This is a plain {@code @Configuration} (not an {@code @AutoConfiguration}) so that it may legitimately carry
 * {@code @ComponentScan}. It is pulled in from
 * {@link RestDocumentAutoConfiguration} via {@code @Import}. The auto-configuration
 * itself deliberately lives outside {@code com.hyland.openarch.rest} so it is never picked up by this scan (see the Spring Boot
 * reference, "Locating Auto-configuration Candidates").
 */
@Configuration
@ComponentScan(basePackages = { "com.hyland.openarch.rest" })
public class RestDocumentComponentConfiguration {
}
