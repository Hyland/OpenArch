/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.query.test;

import static com.hyland.openarch.rest.query.test.TestNamedQueryRegistry.FOLDER_LISTING_TEST2;

import com.hyland.openarch.rest.query.conf.DefaultNamedQueryRegistry;
import com.hyland.openarch.rest.query.spec.NamedQueryDefinition;
import com.hyland.openarch.rest.query.spec.NamedQueryRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Tests custom registry registration.
 *
 */
@TestConfiguration
public class TestNamedQueryConfig {

    @Bean
    @Primary
    public NamedQueryRegistry namedQueryRegistry(DefaultNamedQueryRegistry defaultRegistry) {
        return new TestNamedQueryRegistry(defaultRegistry);
    }

    @Bean
    public NamedQueryDefinition produceFolderListing2() {
        return FOLDER_LISTING_TEST2;
    }

}
