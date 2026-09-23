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
package com.hyland.openarch.runtime.springboot.junit.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import com.hyland.openarch.osgi.application.SharedClassLoader;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import com.hyland.openarch.runtime.springboot.junit.test.TestRuntimeSpringBootTestFeatures.CheckFeature1;
import com.hyland.openarch.runtime.springboot.junit.test.TestRuntimeSpringBootTestFeatures.CheckFeature2;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import org.springframework.boot.test.context.SpringBootTest;

import com.google.inject.Binder;
import com.google.inject.Injector;

/**
 * Checks that a runtime is started with the callback, and that features can be retrieved from another feature.
 * <p>
 * Tests should be run all at once and in given order for checked counters to be accurate.
 */
@Features({ RuntimeFeature.class, CheckFeature1.class, CheckFeature2.class })
@Order(0)
@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = RuntimeTestState.class)
public class TestRuntimeSpringBootTestFeatures extends RuntimeSpringBootTest {

    protected static final int NB_TESTS = 2;

    @Test
    @Order(1)
    public void testRuntimeStarted() {
        assertTrue(Framework.isInitialized());
        assertTrue(Framework.isTestModeSet());
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
            assertThreadClassLoader(true);
            assertNotNull(loader);
            assertNotNull(loader.getFeature(RuntimeFeature.class));
            assertNotNull(loader.getFeature(CheckFeature2.class));
            countInit.incrementAndGet();
        }

        @Override
        public void start() {
            assertThreadClassLoader(false);
            countStart.incrementAndGet();
        }

        @Override
        public void beforeRun() {
            assertThreadClassLoader(false);
            countBeforeRun.incrementAndGet();
        }

        @Override
        public void configure(Binder binder) {
            assertThreadClassLoader(false);
            countConfigure.incrementAndGet();
        }

        @Override
        public void testCreated(Injector injector, Object test) {
            assertThreadClassLoader(false);
            assertAppClassLoader(test.getClass().getClassLoader());
            countTestCreated.incrementAndGet();
        }

        @Override
        public void beforeSetup(Method method, Object test) {
            assertThreadClassLoader(false);
            countBeforeSetup.incrementAndGet();
        }

        @Override
        public void beforeMethodRun(Method method, Object test) {
            assertThreadClassLoader(false);
            countBeforeMethodRun.incrementAndGet();
        }

        @Override
        public void afterMethodRun(Method method, Object test) {
            assertThreadClassLoader(false);
            countAfterMethodRun.incrementAndGet();
        }

        @Override
        public void afterTeardown(Method method, Object test) {
            assertThreadClassLoader(false);
            countAfterTeardown.incrementAndGet();
        }

        @Override
        public void afterRun() {
            assertThreadClassLoader(false);
            countAfterRun.incrementAndGet();
        }

        @Override
        public void stop() {
            assertThreadClassLoader(false);
            countStop.incrementAndGet();
        }

        private void assertThreadClassLoader(boolean isApp) {
            if (isApp) {
                assertAppClassLoader(Thread.currentThread().getContextClassLoader());
            } else {
                assertSharedClassLoader(Thread.currentThread().getContextClassLoader());
            }
        }

        private void assertSharedClassLoader(ClassLoader contextClassLoader) {
            assertInstanceOf(SharedClassLoader.class, contextClassLoader);
            assertAppClassLoader(contextClassLoader.getParent());
        }

        private void assertAppClassLoader(ClassLoader contextClassLoader) {
            assertTrue(contextClassLoader.getClass().getName().contains("AppClassLoader"));
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
