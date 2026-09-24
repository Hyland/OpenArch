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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hyland.openarch.runtime.springboot.OpenArchApplication;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
public class HealthIndicatorTest {

    @Mock
    OpenArchApplication openArchApplication;

    @InjectMocks
    OpenArchApplicationHealthIndicator openArchApplicationHealthIndicator;

    @Test
    public void testHealthCheckIndicatorUp() {

        when(openArchApplication.getStatus()).thenReturn(OpenArchApplication.STATUS_STARTED);

        Health health = openArchApplicationHealthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    public void testHealthCheckIndicatorDown() {

        when(openArchApplication.getStatus()).thenReturn(OpenArchApplication.STATUS_STOPPED);

        Health health = openArchApplicationHealthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }

}
