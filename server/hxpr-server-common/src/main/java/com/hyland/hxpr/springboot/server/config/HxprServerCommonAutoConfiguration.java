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
package com.hyland.hxpr.springboot.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for HxPR Community Server Common.
 * <p>
 * Activates component scanning over {@code com.hyland.hxpr.springboot.server} so that all shared
 * server beans (lifecycle listeners, controllers, configuration classes) are registered automatically
 * when this module is on the classpath.
 */
@AutoConfiguration
@ComponentScan(basePackages = { "com.hyland.hxpr.springboot.server" })
public class HxprServerCommonAutoConfiguration {

}
