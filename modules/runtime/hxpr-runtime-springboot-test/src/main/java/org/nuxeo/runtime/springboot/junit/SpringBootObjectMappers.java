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
package org.nuxeo.runtime.springboot.junit;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * Test support for obtaining an {@link ObjectMapper} configured exactly like the one used by the running Spring Boot
 * application, instead of a bare {@code new ObjectMapper()}.
 * <p>
 * A bare {@code new ObjectMapper()} carries neither Spring Boot's Jackson auto-configuration nor the application's
 * {@link JsonMapperBuilderCustomizer} beans, so the JSON behaviour under test diverges from production. This helper
 * builds the mapper through {@link JacksonAutoConfiguration}, layering on whatever customizer configurations the caller
 * supplies, exactly as Spring Boot assembles the bean at runtime.
 * <p>
 * This module sits below the platform/REST layer and therefore cannot reference module-specific customizers (such as
 * the REST {@code ObjectMapperConfiguration}). Callers pass their own customizer configuration classes instead:
 *
 * <pre>{@code
 * private static final ObjectMapper MAPPER = SpringBootObjectMappers.productive(ObjectMapperConfiguration.class);
 * }</pre>
 *
 * Modules with no extra customizers can call {@link #productive(Class...)} with no arguments to get Spring Boot's
 * default Jackson mapper.
 */
public final class SpringBootObjectMappers {

    private SpringBootObjectMappers() {
    }

    /**
     * Builds an {@link ObjectMapper} that mirrors the productive Spring Boot mapper: Spring Boot's default Jackson
     * auto-configuration plus the supplied customizer configuration classes (typically {@link JsonMapperBuilderCustomizer}
     * beans). The returned instance stays usable after the bootstrap context is closed.
     *
     * @param customizerConfigurations configuration classes contributing Jackson customizers (may be empty)
     * @return a production-equivalent {@link ObjectMapper}
     */
    public static ObjectMapper productive(Class<?>... customizerConfigurations) {
        AtomicReference<ObjectMapper> holder = new AtomicReference<>();
        ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(JacksonAutoConfiguration.class));
        if (customizerConfigurations.length > 0) {
            runner = runner.withUserConfiguration(customizerConfigurations);
        }
        runner.run(context -> holder.set(context.getBean(ObjectMapper.class)));
        return holder.get();
    }
}
