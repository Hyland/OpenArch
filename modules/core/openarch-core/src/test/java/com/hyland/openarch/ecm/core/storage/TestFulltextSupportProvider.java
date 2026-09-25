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
package com.hyland.openarch.ecm.core.storage;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.storage.config.FulltextSpringContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

public class TestFulltextSupportProvider {

    private static void clearProviderSingleton() {
        try {
            Field instanceField = FulltextSupportProvider.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to reset FulltextSupportProvider singleton", e);
        }
    }

    @Test
    public void staticGet_withoutSingleton_returnsFallback() {
        clearProviderSingleton();
        FulltextSupport result = FulltextSupportProvider.get();
        assertNotNull(result);
        assertInstanceOf(FulltextExtractionSupport.class, result);
    }

    @Nested
    @SpringBootTest(classes = { FulltextSpringContext.class, FulltextSupportProvider.class })
    class WithoutSupportBeanContext {

        @Autowired
        protected FulltextSupportProvider supportProvider;

        @Test
        public void getFulltextSupport_returnsFallbackWhenBeanNotAvailable() {
            FulltextSupport result = supportProvider.getFulltextSupport();

            assertNotNull(result);
            assertInstanceOf(FulltextExtractionSupport.class, result);
        }

        @Test
        public void staticGet_withSingleton_returnsFallbackFromContext() {
            FulltextSupport result = FulltextSupportProvider.get();

            assertNotNull(result);
            assertInstanceOf(FulltextExtractionSupport.class, result);
        }
    }

    @Nested
    @SpringBootTest(classes = { FulltextSpringContext.class, FulltextSupportProvider.class,
            CustomFulltextSupportConfiguration.class })
    class WithCustomSupportBeanContext {

        @Autowired
        protected FulltextSupportProvider supportProvider;

        @Autowired
        protected ApplicationContext context;

        @Test
        public void getFulltextSupport_returnsFromObjectProvider() {
            FulltextSupport expected = context.getBean("testStubFulltextSupport", FulltextSupport.class);
            FulltextSupport result = supportProvider.getFulltextSupport();

            assertSame(expected, result);
        }

        @Test
        public void staticGet_withSingleton_delegatesToInstance() {
            FulltextSupport expected = context.getBean("testStubFulltextSupport", FulltextSupport.class);
            FulltextSupport result = FulltextSupportProvider.get();

            assertSame(expected, result);
        }
    }

    @TestConfiguration
    static class CustomFulltextSupportConfiguration {

        @Bean
        public FulltextSupport testStubFulltextSupport() {
            return new TestStubFulltextSupport();
        }
    }
}
