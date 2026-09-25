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
package com.hyland.openarch.ecm.core.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CleanupOrphansTaskTest {

    @Test
    void shouldDefaultToZeroTimeoutAndDefaultBatchSize() {
        CleanupOrphansTask task = new CleanupOrphansTask("repo", "user");

        assertEquals(CleanupOrphansTask.DEFAULT_BATCH_SIZE, task.getBatchSize());
        assertEquals(Duration.ZERO, task.getBatchTransactionTimeoutDuration());
    }

    @Test
    void shouldUseProvidedBatchTimeout() {
        CleanupOrphansTask task = new CleanupOrphansTask("repo", "user", 30L);

        assertEquals(Duration.ofSeconds(30), task.getBatchTransactionTimeoutDuration());
    }

    @Test
    void emptyConstructorShouldKeepDefaults() {
        CleanupOrphansTask task = new CleanupOrphansTask();

        assertEquals(CleanupOrphansTask.DEFAULT_BATCH_SIZE, task.getBatchSize());
        assertEquals(Duration.ZERO, task.getBatchTransactionTimeoutDuration());
    }
}
