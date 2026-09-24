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
package com.hyland.openarch.runtime.springboot.junit;

import java.util.List;

import com.hyland.openarch.runtime.springboot.auth.RepositoryLoginFilter;
import com.hyland.openarch.runtime.springboot.transaction.TransactionFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@TestConfiguration()
@Import({ RuntimeTestState.class, HttpMessageConvertersAutoConfiguration.class, RepositoryLoginFilter.class,
        TransactionFilter.class, JacksonAutoConfiguration.class, MethodValidationPostProcessor.class })
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableWebMvc
@EnableWebSecurity
public class RuntimeSpringBootTestConfiguration implements WebMvcConfigurer {

    private JsonMapper mapper;

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // StringHttpMessageConverter must precede the Jackson converter so that String response bodies (e.g. an
        // endpoint returning raw JSON as a String) are written verbatim instead of being JSON-encoded (double quoted).
        converters.add(new StringHttpMessageConverter());
        converters.add(messageConverter(mapper));
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    JsonMapper testJacksonObjectMapper(List<JsonMapperBuilderCustomizer> customizers) {
        JsonMapper.Builder builder = JsonMapper.builder();
        for (JsonMapperBuilderCustomizer customizer : customizers) {
            customizer.customize(builder);
        }
        JsonMapper objMapper = builder.build();
        this.mapper = objMapper;
        return objMapper;
    }

    @Bean
    public JacksonJsonHttpMessageConverter messageConverter(JsonMapper objectMapper) {
        return new JacksonJsonHttpMessageConverter(objectMapper);
    }
}
