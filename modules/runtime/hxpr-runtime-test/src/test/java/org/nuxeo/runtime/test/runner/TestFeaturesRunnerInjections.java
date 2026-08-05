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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.LoginServiceImpl;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.test.runner.TestFeaturesRunnerInjections.InjectingFeature;

import com.google.inject.Binder;

/**
 * @since TODO
 */
@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, InjectingFeature.class })
public class TestFeaturesRunnerInjections {

    @Inject
    FeaturesLoader injectedLoader;

    @RuntimeInject
    FeaturesLoader injectedLoader2;

    @Inject
    LoginServiceImpl injectedComponent;

    @RuntimeInject
    LoginServiceImpl injectedComponent2;

    @Inject
    RuntimeFeature injectedFeature;

    @RuntimeInject
    RuntimeFeature injectedFeature2;

    @Inject
    TestInstance injectedInstance;

    @RuntimeInject
    TestInstance injectedInstance2;

    @Test
    public void testRuntimeStarted() {
        assertTrue(Framework.isInitialized());
        assertTrue(Framework.isTestModeSet());
    }

    @Test
    public void testInjections() {
        assertNotNull(injectedLoader);
        assertNotNull(injectedLoader2);
        assertNotNull(injectedComponent);
        assertNotNull(injectedComponent2);
        assertNotNull(injectedFeature);
        assertNotNull(injectedFeature2);
        assertNotNull(injectedInstance);
        assertEquals("beforeMethodRun", injectedInstance.getId());
        assertNotNull(injectedInstance2);
        assertEquals("beforeMethodRun", injectedInstance2.getId());
    }

    public static class InjectingFeature implements RunnerFeature {

        protected TestInstance instance;

        @Override
        public void configure(Binder binder) {
            binder.bind(TestInstance.class).toProvider(() -> instance);
        }

        @Override
        public void beforeSetup(Method method, Object test) {
            instance = new TestInstance("init");
        }

        @Override
        public void beforeMethodRun(Method method, Object test) {
            instance = new TestInstance("beforeMethodRun");
        }

        @Override
        public void afterMethodRun(Method method, Object test) {
            instance = null;
        }

    }

    public static class TestInstance {

        protected String id;

        public TestInstance(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

    }

}
