/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.async.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that a task declaring a {@code requiredProperty} is only activated when that property resolves to a non-blank
 * value, so a task can ship disabled by default and be turned on purely by configuration.
 */
@SpringBootTest(classes = RuntimeTestState.class)
@Features(RequiredPropertyTasksFeature.class)
public class TaskRequiredPropertyTest extends RuntimeSpringBootTest {

    @RuntimeInject
    private TaskService asyncTasksService;

    @Test
    public void activatesTaskWhenRequiredPropertyIsPresent() {
        assertThat(((TaskServiceImpl) asyncTasksService).isTaskAvailable("gatedPresentTask")).isTrue();
    }

    @Test
    public void disablesTaskWhenRequiredPropertyIsMissing() {
        assertThat(((TaskServiceImpl) asyncTasksService).isTaskAvailable("gatedAbsentTask")).isFalse();
    }
}
