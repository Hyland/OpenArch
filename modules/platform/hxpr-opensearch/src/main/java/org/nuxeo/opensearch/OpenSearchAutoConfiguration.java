/*
 * (C) Copyright 2024 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.opensearch;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the OpenSearch feature.
 * <p>
 * Beans are registered explicitly via {@link Import} instead of {@code @ComponentScan}: auto-configuration classes must
 * never enable component scanning (see the Spring Boot reference, "Locating Auto-configuration Candidates"). Every new
 * {@code @Component} added under {@code org.nuxeo.opensearch} must be listed here to be picked up by the application.
 */
@AutoConfiguration
@Import({ OpenSearchFilter.class })
public class OpenSearchAutoConfiguration {
}
