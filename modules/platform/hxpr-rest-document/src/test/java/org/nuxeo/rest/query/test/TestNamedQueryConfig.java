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
package org.nuxeo.rest.query.test;

import static org.nuxeo.rest.query.test.TestNamedQueryRegistry.FOLDER_LISTING_TEST2;

import org.nuxeo.rest.query.conf.DefaultNamedQueryRegistry;
import org.nuxeo.rest.query.spec.NamedQueryDefinition;
import org.nuxeo.rest.query.spec.NamedQueryRegistry;
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
