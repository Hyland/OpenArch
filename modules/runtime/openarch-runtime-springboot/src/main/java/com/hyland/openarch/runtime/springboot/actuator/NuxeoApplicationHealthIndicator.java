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
package com.hyland.openarch.runtime.springboot.actuator;

import com.hyland.openarch.runtime.springboot.NuxeoApplication;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component()
public class NuxeoApplicationHealthIndicator implements HealthIndicator {

    private final NuxeoApplication application;

    public NuxeoApplicationHealthIndicator(NuxeoApplication application) {
        this.application = application;
    }

    @Override
    public Health health() {
        if (NuxeoApplication.STATUS_STARTED.equals(application.getStatus())) {
            return Health.up().build();
        } else {
            return Health.down().build();
        }
    }

}
