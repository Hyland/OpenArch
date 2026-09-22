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
package com.hyland.openarch.runtime.test.runner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.RuntimeHarnessImpl;
import org.osgi.framework.BundleException;

import com.google.inject.Binder;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Features({ MDCFeature.class, WithFrameworkPropertyFeature.class, WithApplicationPropertiesFeature.class })
public class RuntimeFeature implements RunnerFeature {

    protected RuntimeHarness harness;

    protected RuntimeDeployment deployment;

    protected FeaturesLoader loader;

    /**
     * Providers contributed by other features to override the default service provider used for a nuxeo service.
     */
    protected final Map<Class<?>, ServiceProvider<?>> serviceProviders;

    public RuntimeFeature() {
        serviceProviders = new HashMap<>();
    }

    public <T> void addServiceProvider(ServiceProvider<T> provider) {
        serviceProviders.put(provider.getServiceClass(), provider);
    }

    public RuntimeHarness getHarness() {
        return harness;
    }

    @Override
    public void initialize(FeaturesLoader loader) {
        harness = new RuntimeHarnessImpl(loader.getTargetTestClass());
        deployment = RuntimeDeployment.onTest(loader);
        this.loader = loader;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void configure(Binder binder) {
        binder.bind(RuntimeHarness.class).toInstance(getHarness());
        for (String svc : Framework.getRuntime().getComponentManager().getServices()) {
            try {
                Class clazz = Thread.currentThread().getContextClassLoader().loadClass(svc);
                ServiceProvider<?> provider = serviceProviders.get(clazz);
                if (provider == null) {
                    provider = new ServiceProvider(clazz);
                }
                binder.bind(clazz).toProvider(provider).in(provider.getScope());
            } catch (Exception e) {
                throw new RuntimeException("Failed to bind service: " + svc, e);
            }
        }
    }

    @Override
    public void start() {
        try {
            harness.start();
        } catch (BundleException e) {
            throw new RuntimeServiceException(e);
        }
        deployment.deploy(loader, harness);
    }

    @Override
    public void stop() {
        harness.stop();
    }

    @Override
    public void beforeRun() {
        // this will make a snapshot of the component registry and will start the components
        harness.fireFrameworkStarted();
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        RuntimeDeployment.onMethod(loader, method);
    }

}
