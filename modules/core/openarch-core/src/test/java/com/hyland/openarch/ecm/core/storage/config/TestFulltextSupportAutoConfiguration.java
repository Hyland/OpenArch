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
package com.hyland.openarch.ecm.core.storage.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.storage.FulltextExtractionSupport;
import com.hyland.openarch.ecm.core.storage.FulltextSupport;
import com.hyland.openarch.ecm.core.storage.TestStubFulltextSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

public class TestFulltextSupportAutoConfiguration {

    @Nested
    @SpringBootTest(classes = { FulltextSupportConfiguration.class, FulltextSpringContext.class })
    class DefaultConfigurationContext {

        @Autowired
        protected ApplicationContext context;

        @Test
        public void autoConfiguration_registersSpringContextAndDefaultSupport() {
            Map<String, FulltextSupport> supportBeans = context.getBeansOfType(FulltextSupport.class);

            assertEquals(1, supportBeans.size());
            assertInstanceOf(FulltextExtractionSupport.class, context.getBean(FulltextSupport.class));
            assertInstanceOf(FulltextSpringContext.class, context.getBean(FulltextSpringContext.class));
        }
    }

    @Nested
    @SpringBootTest(classes = { CustomFulltextSupportConfiguration.class, FulltextSupportConfiguration.class,
            FulltextSpringContext.class })
    class CustomSupportContext {

        @Autowired
        protected ApplicationContext context;

        @Test
        public void autoConfiguration_backsOffWhenSupportBeanAlreadyExists() {
            Map<String, FulltextSupport> supportBeans = context.getBeansOfType(FulltextSupport.class);

            assertEquals(1, supportBeans.size());
            assertSame(context.getBean("customFulltextSupport"), context.getBean(FulltextSupport.class));
            assertInstanceOf(FulltextSpringContext.class, context.getBean(FulltextSpringContext.class));
        }
    }

    @TestConfiguration
    static class CustomFulltextSupportConfiguration {

        @Bean
        public FulltextSupport customFulltextSupport() {
            return new TestStubFulltextSupport();
        }
    }
}
