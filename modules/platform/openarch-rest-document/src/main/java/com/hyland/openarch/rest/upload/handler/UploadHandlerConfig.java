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
package com.hyland.openarch.rest.upload.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for {@link UploadHandler} bean injection.
 */
@Configuration
public class UploadHandlerConfig {

    private static final Logger log = LogManager.getLogger(UploadHandlerConfig.class);

    public static final String TRANSIENT_STORE_NAME = "uploadTransientStore";

    public UploadHandlerConfig() {
        log.debug("UploadHandlerConfig is being loaded");
    }

    @Bean
    @ConditionalOnMissingBean(UploadHandler.class)
    public UploadHandler handler() {
        return new DefaultUploadHandler();
    }

}
