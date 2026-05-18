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

import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ApplicationInfo.class)
public class HxprInfoContributor implements InfoContributor {

    private final ApplicationInfo applicationInfo;

    public HxprInfoContributor(ApplicationInfo applicationInfo) {
        this.applicationInfo = applicationInfo;
    }

    @Override
    public void contribute(Builder builder) {
        builder.withDetail("name", applicationInfo.name());
        builder.withDetail("version", applicationInfo.version());
        builder.withDetail("productVersion", applicationInfo.productVersion());
        builder.withDetail("springVersion", applicationInfo.springVersion());
    }

}
