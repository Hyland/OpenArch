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
package com.hyland.openarch.async.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.async.tasks.message.Task;

/**
 * Plain unit test (no OpenArch runtime bootstrap) for the runner-shutdown fan-out in
 * {@link TaskServiceImpl#beforeRuntimeStop}.
 */
class TaskServiceImplBeforeRuntimeStopTest {

    private static final int RUNNER_COUNT = 10;

    private static final long PER_RUNNER_STOP_DELAY_MS = 300;

    @SuppressWarnings("unchecked")
    private static Map<String, TaskRunner<?>> getRunnersField(TaskServiceImpl service) throws Exception {
        Field field = TaskServiceImpl.class.getDeclaredField("runners");
        field.setAccessible(true);
        return (Map<String, TaskRunner<?>>) field.get(service);
    }

    @Test
    void stopsAllRunnersConcurrentlyRatherThanSerially() throws Exception {
        TaskServiceImpl service = new TaskServiceImpl();
        CountDownLatch allStopsStarted = new CountDownLatch(RUNNER_COUNT);
        AtomicInteger stoppedCount = new AtomicInteger();

        Map<String, TaskRunner<?>> runners = getRunnersField(service);
        for (int i = 0; i < RUNNER_COUNT; i++) {
            runners.put("queue-" + i, new SlowTaskRunner(allStopsStarted, stoppedCount));
        }

        long start = System.nanoTime();
        service.beforeRuntimeStop(null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(stoppedCount.get()).isEqualTo(RUNNER_COUNT);
        // Each runner's stopRunner() only proceeds once every other runner's stop has also started, so a
        // serial forEach would deadlock each one for the full latch-await timeout in turn. Run
        // concurrently, this should finish close to a single runner's own delay.
        assertThat(elapsedMs).isLessThan(RUNNER_COUNT * PER_RUNNER_STOP_DELAY_MS / 2);
    }

    @Test
    void aRunnerThrowingDuringStopDoesNotPreventOthersFromStopping() throws Exception {
        TaskServiceImpl service = new TaskServiceImpl();
        AtomicInteger stoppedCount = new AtomicInteger();

        Map<String, TaskRunner<?>> runners = getRunnersField(service);
        runners.put("queue-throwing", new ThrowingTaskRunner());
        for (int i = 0; i < RUNNER_COUNT; i++) {
            runners.put("queue-" + i, new SlowTaskRunner(new CountDownLatch(0), stoppedCount));
        }

        service.beforeRuntimeStop(null);

        assertThat(stoppedCount.get()).isEqualTo(RUNNER_COUNT);
    }

    private static final class ThrowingTaskRunner implements TaskRunner<Task> {

        @Override
        public void initTaskProcessor(TaskProcessor<Task> processor) {
            // not used
        }

        @Override
        public void submitTask(String key, String message, boolean throwOnError) {
            // not used
        }

        @Override
        public void stopRunner() {
            throw new IllegalStateException("boom");
        }
    }

    private static final class SlowTaskRunner implements TaskRunner<Task> {

        private final CountDownLatch allStopsStarted;

        private final AtomicInteger stoppedCount;

        SlowTaskRunner(CountDownLatch allStopsStarted, AtomicInteger stoppedCount) {
            this.allStopsStarted = allStopsStarted;
            this.stoppedCount = stoppedCount;
        }

        @Override
        public void initTaskProcessor(TaskProcessor<Task> processor) {
            // not used
        }

        @Override
        public void submitTask(String key, String message, boolean throwOnError) {
            // not used
        }

        @Override
        public void stopRunner() {
            allStopsStarted.countDown();
            try {
                allStopsStarted.await(2, TimeUnit.SECONDS);
                // A latch that's never released blocks for the full timeout, standing in for the delay a
                // real stopRunner() would take, without a raw Thread.sleep in test code.
                new CountDownLatch(1).await(PER_RUNNER_STOP_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stoppedCount.incrementAndGet();
        }
    }
}
