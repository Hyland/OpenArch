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
package com.hyland.openarch.rest.query.test;

import org.mockito.Mockito;
import com.hyland.openarch.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.rest.acl.ACEMapper;
import com.hyland.openarch.rest.acl.ACLMapper;
import com.hyland.openarch.rest.document.DocumentManager;
import com.hyland.openarch.rest.document.DocumentMapper;
import com.hyland.openarch.rest.document.utils.ACLChangesHandler;
import com.hyland.openarch.rest.query.OpenSearchQueryManager;
import com.hyland.openarch.rest.query.QueryConfig;
import com.hyland.openarch.rest.query.RepositoryQueryManager;
import com.hyland.openarch.rest.query.conf.DefaultNamedQueries;
import com.hyland.openarch.rest.query.conf.DefaultNamedQueryRegistry;
import com.hyland.openarch.rest.upload.UploadHandlerCleaner;
import com.hyland.openarch.rest.upload.UploadService;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Lock API test configuration.
 */
@Import({ //
        DocumentManager.class, //
        DocumentMapper.class, //
        ACLMapper.class, //
        ACEMapper.class, //
        ACLChangesHandler.class, //
        DefaultNamedQueries.class, //
        RepositoryQueryManager.class, //
        OpenSearchQueryManager.class, //
        DefaultNamedQueryRegistry.class, //
        UploadHandlerCleaner.class //
})
@EnableConfigurationProperties({ QueryConfig.class })
@TestConfiguration
public class QueryAPITestConfiguration {

    // replacement for @MockBeans
    // MockitoBeans do not work with for configuration classes
    // look at: https://github.com/spring-projects/spring-framework/issues/33934

    @Bean
    UploadService uploadService() {
        return Mockito.mock(UploadService.class);
    }

    @Bean
    UploadHandler uploadHandler() {
        return Mockito.mock(UploadHandler.class);
    }

    @Bean
    UserManager userManager() {
        return Mockito.mock(UserManager.class);
    }


}
