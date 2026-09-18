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
package org.nuxeo.runtime.springboot.config;

import org.nuxeo.runtime.metrics.MetricsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;

@Configuration(proxyBeanMethods = false)
class Resilience4jDropwizardMetricsConfiguration {

    @Bean
    MetricRegistry dropwizardMetricRegistry() {
        return SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
    }

    @Bean
    Resilience4jDropwizardMetricsBinder resilience4jDropwizardMetricsBinder(MetricRegistry dropwizardMetricRegistry,
            ObjectProvider<RetryRegistry> retryRegistries, ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistries,
            ObjectProvider<TimeLimiterRegistry> timeLimiterRegistries) {
        // Binding occurs in afterPropertiesSet() after all fields are injected.
        // destroy() is called by Spring on context close to clean up listeners and metrics.
        // This explicit lifecycle prevents dead binders from staying attached to JVM-global registries.
        return new Resilience4jDropwizardMetricsBinder(dropwizardMetricRegistry, retryRegistries, circuitBreakerRegistries,
                timeLimiterRegistries);
    }
}
