/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.runtime.springboot.junit;

import static org.nuxeo.runtime.test.runner.FeaturesLoader.Direction.BACKWARD;
import static org.nuxeo.runtime.test.runner.FeaturesLoader.Direction.FORWARD;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mockito.RuntimeInjector;
import org.nuxeo.runtime.springboot.NuxeoApplication;
import org.nuxeo.runtime.springboot.auth.NuxeoLoginFilter;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.nuxeo.runtime.springboot.junit.security.IdpTestAuthenticationSupport;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

/**
 * Base class to extend to benefit for {@link Features} and {@link Deploy} annotations in a {@link SpringBootTest}
 * class.
 * <p>
 * This class has the following annotation: <code>@SpringBootTest(classes = { RuntimeTestState.class })</code>
 * <p>
 * Tests should extend this base abstract class.
 * <p>
 * The <code>@SpringBootTest</code> annotation is <b>not</b> required by extending classes unless specific, Spring bean,
 * customisation is required.
 * <p>
 * It's worth noting that the <code>@SpringBootTest</code> will not extend on child classes with their own
 * <code>@SpringBootTest</code> annotation and this new annotation will override the annotation on this base class. If
 * you wish to inherit Spring Beans, use <code>@Import</code>
 * <p>
 */
@SpringBootTest()
@Import({ RuntimeSpringBootTestConfiguration.class })
@TestPropertySource(properties = { "spring.profiles.active=default,local" })
@AutoConfigureMockMvc
public abstract class RuntimeSpringBootTest {

    protected MockMvc mvc;

    /**
     * Retrieves the {@link RuntimeTestState} bean from the application context.
     *
     * @param applicationContext The application context from which to retrieve the bean.
     * @return The {@link RuntimeTestState} bean.
     */
    protected static RuntimeTestState getState(ApplicationContext applicationContext) {
        try {
            return applicationContext.getBean(RuntimeTestState.class);
        } catch (NoSuchBeanDefinitionException e) {
            // If the bean is not found we attempt to create it
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RuntimeTestState.class);
            AbstractBeanDefinition testStateBean = builder.getBeanDefinition();
            ((DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory()).registerBeanDefinition(
                    RuntimeTestState.class.getName(), testStateBean);
            return applicationContext.getBean(RuntimeTestState.class);
        }
    }

    /**
     * Initializes the test state, loading features, and setting it in the test state.
     *
     * @param testInfo           The information about the current test.
     * @param applicationContext The application context for the test.
     * @throws RuntimeServiceException If there is an error during the initialization process.
     */
    @BeforeAll
    public static void initializeFramework(TestInfo testInfo, ApplicationContext applicationContext) {
        RuntimeTestState state = getState(applicationContext);
        Class<?> testClass = testInfo.getTestClass().orElseThrow(() -> new RuntimeServiceException("No test class"));
        FeaturesLoader loader = new FeaturesLoader(testClass);
        state.setLoader(loader);
        try {
            loader.loadFeatures(testClass);
        } catch (Throwable t) { // NOSONAR
            throw new RuntimeServiceException(t);
        }
        Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
                aBinder -> aBinder.bind(FeaturesLoader.class).toInstance(loader));

        loader.apply("initialize", FORWARD, feature -> feature.initialize(loader));
        loader.apply("start", FORWARD, RunnerFeature::start);
        loader.apply("beforeRun", FORWARD, RunnerFeature::beforeRun);
        // calls features configure method
        injector = injector.createChildInjector(loader.onModule());
        state.setInjector(injector);
        if (Framework.isInitialized()) {
            NuxeoApplication.setApplicationContexts(applicationContext);
        }

    }

    /**
     * Performs cleanup operations after all tests have been executed. This method is called once after all test methods
     * have been run. If the {@link RuntimeTestState} is null or the {@link FeaturesLoader} is null, the method returns
     * without performing any cleanup operations. Otherwise, it applies the "afterRun" and "stop" operations to the
     * {@link FeaturesLoader} in a backward direction.
     *
     * @param applicationContext the application context, used to retrieve the {@link RuntimeTestState} and
     *                           {@link FeaturesLoader} objects.
     */
    @AfterAll
    public static void afterAll(ApplicationContext applicationContext) {
        RuntimeTestState state = getState(applicationContext);
        FeaturesLoader loader = state.getLoader();
        // loader can be null in case of errors in #beforeAll
        if (loader == null) {
            return;
        }
        loader.apply("afterRun", BACKWARD, RunnerFeature::afterRun);
        loader.apply("stop", BACKWARD, RunnerFeature::stop);
        loader.apply("uninitialize", BACKWARD, RunnerFeature::uninitialize);
    }

    @BeforeEach
    public void setup(@Autowired WebApplicationContext context, @Autowired NuxeoLoginFilter interceptor) {

        var builder = MockMvcBuilders.webAppContextSetup(context)
                                     .apply(SecurityMockMvcConfigurers.springSecurity())
                                     .addFilters(interceptor);
        String bearerToken = IdpTestAuthenticationSupport.bearerTokenOrNull();
        if (bearerToken != null) {
            builder.defaultRequest(MockMvcRequestBuilders.get("/")
                                                          .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken));
        }
        mvc = builder.build();
    }

    /**
     * This method is executed before each test case is run. It performs the necessary setup steps for the test case,
     * such as creating the test object, invoking the appropriate features, and injecting dependencies.
     *
     * @param testInfo           The information about the current test case.
     * @param applicationContext The application context for the test case.
     */
    @BeforeEach
    public void beforeEach(TestInfo testInfo, ApplicationContext applicationContext) {
        RuntimeTestState state = getState(applicationContext);
        if (state.getLoader() == null) {
            /* After test failures the context can be refreshed and the Framework is not initialized, try to
            reinitialize it */
            initializeFramework(testInfo, applicationContext);
        }
        FeaturesLoader loader = state.getLoader();
        Injector injector = state.getInjector();
        Object test = this;
        loader.apply("createTest", FORWARD, feature -> feature.testCreated(injector, test));
        Method method = testInfo.getTestMethod().orElseThrow(() -> new RuntimeServiceException("no test method"));
        loader.apply("beforeSetup", FORWARD, feature -> feature.beforeSetup(method, test));
        RuntimeInjector.inject(injector, test);
        loader.apply("beforeMethodRun", FORWARD, feature -> feature.beforeMethodRun(method, test));
        RuntimeInjector.inject(injector, test);
    }

    /**
     * This method is called after each test case execution. It performs cleanup operations and executes the necessary
     * actions defined by the loaded features.
     *
     * @param testInfo           The information about the test case being executed.
     * @param applicationContext The application context of the test case.
     */
    @AfterEach
    public void afterEach(TestInfo testInfo, ApplicationContext applicationContext) {
        RuntimeTestState state = getState(applicationContext);
        FeaturesLoader loader = state.getLoader();
        Object test = this;
        Method method = testInfo.getTestMethod().orElseThrow(() -> new RuntimeServiceException("no test method"));
        loader.apply("afterMethodRun", BACKWARD, feature -> feature.afterMethodRun(method, test));
        loader.apply("afterTeardown", BACKWARD, feature -> feature.afterTeardown(method, test));
    }

}
