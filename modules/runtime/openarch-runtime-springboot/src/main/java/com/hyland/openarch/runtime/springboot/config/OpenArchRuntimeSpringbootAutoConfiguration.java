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
package com.hyland.openarch.runtime.springboot.config;

import com.hyland.openarch.runtime.springboot.actuator.OpenArchInfoContributor;
import com.hyland.openarch.runtime.springboot.actuator.OpenArchApplicationHealthIndicator;
import com.hyland.openarch.runtime.springboot.auth.RepositoryLoginFilter;
import com.hyland.openarch.runtime.springboot.transaction.TransactionFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Auto-configuration for the Spring Boot runtime feature.
 * <p>
 * Beans are registered explicitly via {@link Import} instead of {@code @ComponentScan}: auto-configuration classes must
 * never enable component scanning (see the Spring Boot reference, "Locating Auto-configuration Candidates"). Every new
 * {@code @Component} / {@code @Configuration} added under {@code com.hyland.openarch.runtime.springboot} must be listed here to be
 * picked up by the application.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableRetry
@Import({
        OpenArchApplicationConfiguration.class,
        Resilience4jDropwizardMetricsConfiguration.class,
        TransactionFilter.class,
        RepositoryLoginFilter.class,
        OpenArchInfoContributor.class,
        OpenArchApplicationHealthIndicator.class,
})
public class OpenArchRuntimeSpringbootAutoConfiguration {

}
