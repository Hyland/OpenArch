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
package com.hyland.openarch.springboot.callback.api;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public class CoreSpringTestCallbacks implements BeforeEachCallback, BeforeAllCallback,
        AfterAllCallback, AfterEachCallback {

    /**
     * Scan callbacks declared at the type level
     *
     * @param testClass
     * @return
     */
    protected Stream<JunitTestCallback> scanCallbacks(Class<?> testClass) {
        Set<Class<? extends JunitTestCallback>> qtcs = new LinkedHashSet<>();
        WithCallbacks callbacks = testClass.getAnnotation(WithCallbacks.class);
        if (callbacks != null) {
            Arrays.stream(callbacks.value()).map(WithCallback::value).flatMap(Arrays::stream).forEach(qtcs::add);
        }

        WithCallback annotation = testClass.getAnnotation(WithCallback.class);
        if (annotation != null) {
            qtcs.addAll(Arrays.asList(annotation.value()));
        }

        return qtcs.stream().map(this::newTestCallback);
    }

    /**
     * Scan callbacks declared at the method level
     *
     * @param method
     * @return
     */
    protected Stream<JunitTestCallback> scanCallbacks(Method method) {
        Set<Class<? extends JunitTestCallback>> qtcs = new HashSet<>();
        WithCallbacks callbacks = method.getAnnotation(WithCallbacks.class);
        if (callbacks != null) {
            Arrays.stream(callbacks.value()).map(WithCallback::value).flatMap(Arrays::stream).forEach(qtcs::add);
        }

        WithCallback annotation = method.getAnnotation(WithCallback.class);
        if (annotation != null) {
            qtcs.addAll(Arrays.asList(annotation.value()));
        }

        return qtcs.stream().map(this::newTestCallback);
    }

    protected JunitTestCallback newTestCallback(Class<? extends JunitTestCallback> callback) {
        try {
            return callback.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Method testMethod = context.getRequiredTestMethod();
        Class<?> testClass = testMethod.getDeclaringClass();

        scanCallbacks(testClass).forEach(cb -> cb.afterEach(context));
        scanCallbacks(testMethod).forEach(cb -> cb.afterEach(context));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getTestInstance().getClass();
        scanCallbacks(testClass).forEach(cb -> cb.afterAll(context));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getTestInstance().getClass();
        scanCallbacks(testClass).forEach(cb -> cb.beforeClass(context));
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
         Method testMethod = context.getRequiredTestMethod();
        Class<?> testClass = testMethod.getDeclaringClass();

        scanCallbacks(testClass).forEach(cb -> cb.beforeEach(context));
        scanCallbacks(testMethod).forEach(cb -> cb.beforeEach(context));
    }
}
