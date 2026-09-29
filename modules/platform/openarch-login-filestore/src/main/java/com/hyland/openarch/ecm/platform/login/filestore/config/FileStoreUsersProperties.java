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
package com.hyland.openarch.ecm.platform.login.filestore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The property {@code hxpr.users.file} specifies the location of the YAML file containing users and groups. It accepts
 * any Spring {@code Resource} location, e.g.:
 * <ul>
 *   <li>{@code classpath:openarch-users.yml} — bundled inside the application JAR (default)</li>
 *   <li>{@code file:/etc/openarch/users.yml} — external file on the host for production overrides</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "hxpr.users")
public class FileStoreUsersProperties {

    /**
     * Location of the YAML user-store file. Defaults to the bundled classpath resource shipped with the application.
     */
    private String file = "classpath:openarch-users.yml";

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }
}
