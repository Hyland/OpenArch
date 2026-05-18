/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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

import java.util.List;

import org.nuxeo.runtime.springboot.auth.NuxeoLoginFilter;
import org.nuxeo.runtime.springboot.transaction.TransactionalInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

@TestConfiguration()
@Import({ RuntimeTestState.class, HttpMessageConvertersAutoConfiguration.class, NuxeoLoginFilter.class,
        TransactionalInterceptor.class, JacksonAutoConfiguration.class,
        Jackson2ObjectMapperBuilder.class, MethodValidationPostProcessor.class })
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableWebMvc
@EnableWebSecurity
public class RuntimeSpringBootTestConfiguration implements WebMvcConfigurer {

    private ObjectMapper mapper;

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(messageConverter(mapper));
        converters.add(new StringHttpMessageConverter());
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    ObjectMapper testJacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objMapper = builder.createXmlMapper(false).build();
        this.mapper = objMapper;
        return objMapper;
    }

    @Bean
    @Primary
    public Jackson2ObjectMapperBuilder testJacksonObjectMapperBuilder(ApplicationContext applicationContext,
            List<Jackson2ObjectMapperBuilderCustomizer> customizers) {
        final var builder = new Jackson2ObjectMapperBuilder();
        builder.applicationContext(applicationContext);
        for (Jackson2ObjectMapperBuilderCustomizer customizer : customizers) {
            customizer.customize(builder);
        }
        return builder;
    }

    @Bean
    public MappingJackson2HttpMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }
}
