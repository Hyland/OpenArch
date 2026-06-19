/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.query.conf;

import static org.nuxeo.rest.query.conf.NamedQueryHelper.ADVANCED_FOLDER_LISTING;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.ADVANCED_SEARCH;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.DOCUMENT_SUGGESTION;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.FOLDER_LISTING;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.SIMPLE_SEARCH;
import static org.nuxeo.rest.query.conf.NamedQueryHelper.TREE_CHILDREN;

import org.nuxeo.rest.query.spec.NamedQueryDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefaultNamedQueries {

    @Bean
    public NamedQueryDefinition produceFolderListing() {
        return FOLDER_LISTING;
    }

    @Bean
    public NamedQueryDefinition produceAdvancedFolderListing() {
        return ADVANCED_FOLDER_LISTING;
    }

    @Bean
    public NamedQueryDefinition produceTreeChildren() {
        return TREE_CHILDREN;
    }

    @Bean
    public NamedQueryDefinition produceDocumentSuggestion() {
        return DOCUMENT_SUGGESTION;
    }

    @Bean
    public NamedQueryDefinition produceSimpleSearch() {
        return SIMPLE_SEARCH;
    }

    @Bean
    public NamedQueryDefinition produceAdvancedSearch() {
        return ADVANCED_SEARCH;
    }

}
