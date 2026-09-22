/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.test.runner;

import static com.hyland.openarch.runtime.test.runner.FeaturesLoader.Direction.BACKWARD;
import static com.hyland.openarch.runtime.test.runner.FeaturesLoader.Direction.FORWARD;

import java.lang.reflect.Method;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.mockito.RuntimeInjector;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class FeaturesExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final Namespace NAMESPACE = Namespace.create(FeaturesExtension.class);

    private static final String FEATURES_LOADER = "FeaturesLoader";

    private static final String INJECTOR = "Injector";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        FeaturesLoader featuresLoader = new FeaturesLoader(testClass);
        Store store = context.getRoot().getStore(NAMESPACE);
        store.put(FEATURES_LOADER, featuresLoader);

        try {
            featuresLoader.loadFeatures(testClass);
        } catch (Exception e) {
            throw new RuntimeServiceException(e);
        }

        featuresLoader.apply("initialize", FORWARD, feature -> feature.initialize(featuresLoader));
        featuresLoader.apply("start", FORWARD, RunnerFeature::start);
        featuresLoader.apply("beforeRun", FORWARD, RunnerFeature::beforeRun);

        Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
                binder -> binder.bind(FeaturesLoader.class).toInstance(featuresLoader), //
                featuresLoader.onModule()); // calls features configure method
        store.put(INJECTOR, injector);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Store store = context.getRoot().getStore(NAMESPACE);
        FeaturesLoader featuresLoader = store.get(FEATURES_LOADER, FeaturesLoader.class);

        featuresLoader.apply("afterRun", BACKWARD, RunnerFeature::afterRun);
        featuresLoader.apply("stop", BACKWARD, RunnerFeature::stop);
        featuresLoader.apply("uninitialize", BACKWARD, RunnerFeature::uninitialize);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object test = context.getRequiredTestInstance();
        Method method = context.getRequiredTestMethod();
        Store store = context.getRoot().getStore(NAMESPACE);
        FeaturesLoader featuresLoader = store.get(FEATURES_LOADER, FeaturesLoader.class);
        Injector injector = store.get(INJECTOR, Injector.class);

        featuresLoader.apply("createTest", FORWARD, feature -> feature.testCreated(injector, test));
        featuresLoader.apply("beforeSetup", FORWARD, feature -> feature.beforeSetup(method, test));
        injector.injectMembers(test);
        RuntimeInjector.inject(injector, test);
        featuresLoader.apply("beforeMethodRun", FORWARD, feature -> feature.beforeMethodRun(method, test));
        injector.injectMembers(test);
        RuntimeInjector.inject(injector, test);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Object test = context.getRequiredTestInstance();
        Method method = context.getRequiredTestMethod();
        Store store = context.getRoot().getStore(NAMESPACE);
        FeaturesLoader featuresLoader = store.get(FEATURES_LOADER, FeaturesLoader.class);

        featuresLoader.apply("afterMethodRun", BACKWARD, feature -> feature.afterMethodRun(method, test));
        featuresLoader.apply("afterTeardown", BACKWARD, feature -> feature.afterTeardown(method, test));
    }

}
