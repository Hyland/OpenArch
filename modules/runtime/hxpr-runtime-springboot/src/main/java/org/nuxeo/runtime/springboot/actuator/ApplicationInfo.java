/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *     dedwards
 */
package org.nuxeo.runtime.springboot.actuator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.SpringVersion;

@ConfigurationProperties(prefix = "springboot.app")
public record ApplicationInfo(String name, String version, String productVersion, String springVersion) {

    private static final String UNKNOWN = "unknown";

    public ApplicationInfo {
        name = orUnknown(name);
        version = orUnknown(version);
        productVersion = orUnknown(productVersion);
        springVersion = orUnknown(springVersion);
    }

    @ConstructorBinding
    public ApplicationInfo(String name, String version, String productVersion) {
        this(name, version, productVersion, SpringVersion.getVersion());
    }

    private static String orUnknown(String value) {
        return value != null ? value : UNKNOWN;
    }

}
