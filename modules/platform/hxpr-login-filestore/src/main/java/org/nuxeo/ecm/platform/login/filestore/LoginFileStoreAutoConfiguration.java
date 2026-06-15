/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 * This is unpublished proprietary source code of Hyland. All rights reserved.
 * Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.login.filestore;

import org.nuxeo.ecm.platform.login.filestore.config.FileStoreUsersProperties;
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
