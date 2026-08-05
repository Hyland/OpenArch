/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

import java.lang.reflect.Method;

import org.apache.logging.log4j.ThreadContext;

import com.google.inject.Binder;
import com.google.inject.Injector;

public class MDCFeature implements RunnerFeature {

    protected static final String F_TEST = "fTest";

    protected static final String F_SUITE = "fSuite";

    protected static final String F_STATE = "fState";

    @Override
    public void initialize(FeaturesLoader loader) {
        ThreadContext.put(F_STATE, "initialize");
    }

    @Override
    public void configure(Binder binder) {
        ThreadContext.put(F_STATE, "configure");
    }

    @Override
    public void beforeRun() {
        ThreadContext.put(F_STATE, "beforeRun");
    }

    @Override
    public void afterRun() {
        ThreadContext.put(F_STATE, "afterRun");
    }

    @Override
    public void start() {
        ThreadContext.put(F_STATE, "start");
    }

    @Override
    public void testCreated(Injector injector, Object test) {
        ThreadContext.put(F_STATE, "testCreated");
        ThreadContext.put(F_SUITE, test.getClass().getName());
    }

    @Override
    public void stop() {
        ThreadContext.remove(F_STATE);
        ThreadContext.remove(F_SUITE);
        ThreadContext.remove(F_TEST);
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        ThreadContext.put(F_STATE, "beforeSetup");
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        ThreadContext.put(F_STATE, "afterTeardown");
    }

    @Override
    public void beforeMethodRun(Method method, Object test) {
        ThreadContext.put(F_STATE, "beforeMethodRun");
        ThreadContext.put(F_TEST, method.getName());
    }

    @Override
    public void afterMethodRun(Method method, Object test) {
        ThreadContext.put(F_STATE, "afterMethodRun");
        ThreadContext.remove(F_TEST);
    }

}
