/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.test.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.TestFeaturesRunner.CheckFeature1;
import org.nuxeo.runtime.test.runner.TestFeaturesRunner.CheckFeature2;

import com.google.inject.Binder;
import com.google.inject.Injector;

/**
 * @since TODO
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(FeaturesExtension.class)
@Features({ CheckFeature1.class, CheckFeature2.class })
public class TestFeaturesRunner {

    protected static final int NB_TESTS = 2;

    @Test
    @Order(1)
    public void testBeforeRunnerFeatureCalls() { // NOSONAR (no need to assert anything)
        // NOOP: additional test to check before/after method calls
    }

    @Test
    @Order(2)
    public void testRunnerFeatureCalls() {
        assertEquals(1, CheckFeature1.countInit.get());
        assertEquals(1, CheckFeature1.countStart.get());
        assertEquals(1, CheckFeature1.countBeforeRun.get());
        assertEquals(1, CheckFeature1.countConfigure.get());
        assertEquals(NB_TESTS, CheckFeature1.countTestCreated.get());
        assertEquals(NB_TESTS, CheckFeature1.countBeforeSetup.get());
        assertEquals(NB_TESTS, CheckFeature1.countBeforeMethodRun.get());
        // checking before these have been called for this test, see additional checks in CheckFeature2
        assertEquals(NB_TESTS - 1, CheckFeature1.countAfterMethodRun.get());
        assertEquals(NB_TESTS - 1, CheckFeature1.countAfterTeardown.get());
        assertEquals(0, CheckFeature1.countAfterRun.get());
        assertEquals(0, CheckFeature1.countStop.get());
    }

    public static class CheckFeature1 implements RunnerFeature {

        static AtomicInteger countInit = new AtomicInteger(0);

        static AtomicInteger countStart = new AtomicInteger(0);

        static AtomicInteger countBeforeRun = new AtomicInteger(0);

        static AtomicInteger countConfigure = new AtomicInteger(0);

        static AtomicInteger countTestCreated = new AtomicInteger(0);

        static AtomicInteger countBeforeSetup = new AtomicInteger(0);

        static AtomicInteger countBeforeMethodRun = new AtomicInteger(0);

        static AtomicInteger countAfterMethodRun = new AtomicInteger(0);

        static AtomicInteger countAfterTeardown = new AtomicInteger(0);

        static AtomicInteger countAfterRun = new AtomicInteger(0);

        static AtomicInteger countStop = new AtomicInteger(0);

        @Override
        public void initialize(FeaturesLoader loader) {
            assertNotNull(loader);
            assertNotNull(loader.getFeature(CheckFeature2.class));
            countInit.incrementAndGet();
        }

        @Override
        public void start() {
            countStart.incrementAndGet();
        }

        @Override
        public void beforeRun() {
            countBeforeRun.incrementAndGet();
        }

        @Override
        public void configure(Binder binder) {
            countConfigure.incrementAndGet();
        }

        @Override
        public void testCreated(Injector injector, Object test) {
            countTestCreated.incrementAndGet();
        }

        @Override
        public void beforeSetup(Method method, Object test) {
            countBeforeSetup.incrementAndGet();
        }

        @Override
        public void beforeMethodRun(Method method, Object test) {
            countBeforeMethodRun.incrementAndGet();
        }

        @Override
        public void afterMethodRun(Method method, Object test) {
            countAfterMethodRun.incrementAndGet();
        }

        @Override
        public void afterTeardown(Method method, Object test) {
            countAfterTeardown.incrementAndGet();
        }

        @Override
        public void afterRun() {
            countAfterRun.incrementAndGet();
        }

        @Override
        public void stop() {
            countStop.incrementAndGet();
        }

    }

    public static class CheckFeature2 implements RunnerFeature {

        @Inject
        FeaturesLoader injectedLoader;

        @Override
        public void beforeSetup(Method method, Object test) {
            assertNotNull(injectedLoader);
        }

        @Override
        public void beforeMethodRun(Method method, Object test) {
            assertNotNull(injectedLoader);
        }

        @Override
        public void stop() {
            assertEquals(NB_TESTS, CheckFeature1.countAfterMethodRun.get());
            assertEquals(NB_TESTS, CheckFeature1.countAfterTeardown.get());
            assertEquals(1, CheckFeature1.countAfterRun.get());
            // called in reverse order -> stop on CheckFeature1 has not been called yet
            assertEquals(0, CheckFeature1.countStop.get());
        }

    }

}
