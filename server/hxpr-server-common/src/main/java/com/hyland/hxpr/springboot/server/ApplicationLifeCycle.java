/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package com.hyland.hxpr.springboot.server;

import java.util.Arrays;

import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifeCycle implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifeCycle.class);

    public void onStart(ApplicationEnvironmentPreparedEvent event) {
        log.info("Starting with profiles {}", Arrays.toString(event.getEnvironment().getActiveProfiles()));
    }

    @EventListener
    public void handleStrictMode(ApplicationReadyEvent event) {
        boolean strict = event.getApplicationContext()
                              .getEnvironment()
                              .getProperty(Framework.NUXEO_STRICT_STARTUP_SYSTEM_PROP, Boolean.class, true);
        if (strict) {
            RuntimeService runtime = Framework.getRuntime();
            if (runtime == null) {
                log.error("Shutting down because Nuxeo Runtime is not initialized");
                SpringApplication.exit(event.getApplicationContext(), () -> 1);
            } else if (runtime.hasErrors()) {
                log.error("Shutting down because of Nuxeo Runtime errors at startup in strict mode");
                SpringApplication.exit(event.getApplicationContext(), () -> 1);
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        onStart(event);
    }

}
