/*
 * (C) Copyright 2026 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.storage.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.storage.FulltextExtractionSupport;
import org.nuxeo.ecm.core.storage.FulltextSupport;
import org.nuxeo.ecm.core.storage.TestStubFulltextSupport;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

public class TestFulltextSpringContext {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    public void getFulltextSupport_withoutSupportBean_returnsFallback() {
        contextRunner.withUserConfiguration(OnlySpringContextConfiguration.class).run(context -> {
            FulltextSpringContext springContext = context.getBean(FulltextSpringContext.class);

            FulltextSupport first = springContext.getFulltextSupport();
            FulltextSupport second = springContext.getFulltextSupport();

            assertNotNull(first);
            assertNotNull(second);
            assertInstanceOf(FulltextExtractionSupport.class, first);
            assertInstanceOf(FulltextExtractionSupport.class, second);
            assertNotSame(first, second);
        });
    }

    @Test
    public void getFulltextSupport_withDefaultSupportBean_returnsConfiguredBean() {
        contextRunner.withUserConfiguration(OnlySpringContextConfiguration.class, FulltextSupportConfiguration.class)
                .run(context -> {
                    FulltextSupport expected = context.getBean(FulltextSupport.class);
                    FulltextSupport actual = context.getBean(FulltextSpringContext.class).getFulltextSupport();

                    assertSame(expected, actual);
                    assertInstanceOf(FulltextExtractionSupport.class, actual);
                });
    }

    @Test
    public void getFulltextSupport_withCustomSupportBean_returnsCustomBean() {
        contextRunner.withUserConfiguration(OnlySpringContextConfiguration.class, CustomSupportConfiguration.class)
                .run(context -> {
                    FulltextSupport expected = context.getBean("dummyStubFulltextSupport", FulltextSupport.class);
                    FulltextSupport actual = context.getBean(FulltextSpringContext.class).getFulltextSupport();

                    assertEquals(1, context.getBeansOfType(FulltextSupport.class).size());
                    assertSame(expected, actual);
                    assertInstanceOf(TestStubFulltextSupport.class, actual);
                });
    }

    @Test
    public void getFulltextSupport_withMultipleBeansAndPrimary_returnsPrimaryBean() {
        contextRunner.withUserConfiguration(OnlySpringContextConfiguration.class, MultipleSupportWithPrimaryConfiguration.class)
                .run(context -> {
                    FulltextSupport expected = context.getBean("primaryFulltextSupport", FulltextSupport.class);
                    FulltextSupport actual = context.getBean(FulltextSpringContext.class).getFulltextSupport();

                    assertSame(expected, actual);
                    assertInstanceOf(PrimaryStubFulltextSupport.class, actual);
                });
    }

    @Test
    public void getFulltextSupport_withMultipleBeansWithoutPrimary_throwsNoUniqueBeanDefinitionException() {
        contextRunner.withUserConfiguration(OnlySpringContextConfiguration.class,
                MultipleSupportWithoutPrimaryConfiguration.class).run(context -> {
                    FulltextSpringContext springContext = context.getBean(FulltextSpringContext.class);

                    assertThrows(NoUniqueBeanDefinitionException.class, springContext::getFulltextSupport);
                });
    }

    @TestConfiguration
    @Import(FulltextSpringContext.class)
    static class OnlySpringContextConfiguration {
    }

    @TestConfiguration
    static class CustomSupportConfiguration {

        @Bean
        public FulltextSupport dummyStubFulltextSupport() {
            return new TestStubFulltextSupport();
        }
    }

    @TestConfiguration
    static class MultipleSupportWithPrimaryConfiguration {

        @Bean
        @Primary
        public FulltextSupport primaryFulltextSupport() {
            return new PrimaryStubFulltextSupport();
        }

        @Bean
        public FulltextSupport secondaryFulltextSupport() {
            return new SecondaryStubFulltextSupport();
        }
    }

    @TestConfiguration
    static class MultipleSupportWithoutPrimaryConfiguration {

        @Bean
        public FulltextSupport firstFulltextSupport() {
            return new TestStubFulltextSupport();
        }

        @Bean
        public FulltextSupport secondFulltextSupport() {
            return new SecondaryStubFulltextSupport();
        }
    }

    static class PrimaryStubFulltextSupport extends TestStubFulltextSupport {
    }

    static class SecondaryStubFulltextSupport extends TestStubFulltextSupport {
    }
}
