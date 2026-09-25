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
package com.hyland.openarch.ecm.platform.login.filestore;

import com.hyland.openarch.ecm.platform.login.filestore.config.FileStoreUsersProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
@Import(FileStoreSecurityConfiguration.class)
@EnableConfigurationProperties(FileStoreUsersProperties.class)
public class LoginFileStoreAutoConfiguration {

    @Bean
    public UserStoreLoader userStoreLoader(FileStoreUsersProperties properties, ResourceLoader resourceLoader) {
        return new UserStoreLoader(properties, resourceLoader);
    }
}
