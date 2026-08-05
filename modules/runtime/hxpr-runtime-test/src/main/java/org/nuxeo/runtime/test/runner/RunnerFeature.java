/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

import com.google.inject.Binder;
import com.google.inject.Injector;

/**
 * These are the states the runner goes through when using runtime feature:
 *
 * <pre>
 * CREATE FRAMEWORK
 * new feature()        --&gt; constructor
 * COLLECT DEFINED DEPLOYMENTS
 * START FRAMEWORK
 * feature.initialize() --&gt; can be used to configure nuxeo home or register JNDI objects
 * feature.start()
 * feature.beforeRun()
 * feature.configure() --&gt; can be used to add guice bindings and to dynamically deploy components using the harness
 * for each test method:
 *   feature.testCreated()
 *   feature.beforeSetup
 *   feature.beforeMethodRun()  --&gt; test method interceptor
 *   testMethod()
 *   feature.afterMethodRun()   --&gt; test method interceptor
 *   feature.afterTeardown()
 * feature.afterRun() --&gt; cleanup that require framework to be started
 * feature.stop()  --&gt; destructor
 * feature.uninitialize()   --&gt; undo initialization
 * STOP FRAMEWORK
 * </pre>
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface RunnerFeature {

    /**
     * Called when preparing to run the test class.
     * <p>
     * Framework is not started at this point. Here is time for the feature to configure the runner from annotations on
     * the test class.
     */
    default void initialize(FeaturesLoader loader) {
    }

    /**
     * Features are initialized. Runner is ready to create the injector.
     */
    default void start() {
    }

    /**
     * Before running tests. At this point Guice modules are registered and injector created.
     */
    default void beforeRun() {
    }

    /**
     * Configures Guice bindings if any is required by the feature.
     * <p>
     * This is called after the framework is started and before Guice module is built. The tests are launched after
     * guice module is built.
     */
    default void configure(Binder binder) {
    }

    /**
     * Notification that a test instance was created.
     * <p>
     * Can be used by features to make custom injection or other preparation of the test instance.
     */
    default void testCreated(Injector injector, Object test) {
    }

    /**
     * Before entering in the @Before methods
     */
    default void beforeSetup(Method method, Object test) {
    }

    /**
     * Before a test method is invoked.
     */
    default void beforeMethodRun(Method method, Object test) {
    }

    /**
     * After a test method was ran.
     */
    default void afterMethodRun(Method method, Object test) {
    }

    /**
     * After the call of the @After methods
     */
    default void afterTeardown(Method method, Object test) {
    }

    /**
     * After tests were run.
     */
    default void afterRun() {
    }

    /**
     * Before exiting the test.
     */
    default void stop() {
    }

    /**
     * Undo initialization.
     */
    default void uninitialize() {
    }

}
