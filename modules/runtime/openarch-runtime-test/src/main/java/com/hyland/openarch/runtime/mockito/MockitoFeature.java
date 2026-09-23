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
package com.hyland.openarch.runtime.mockito;

import static org.mockito.MockitoAnnotations.initMocks;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.configuration.IMockitoConfiguration;
import org.mockito.internal.configuration.GlobalConfiguration;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.api.DefaultServiceProvider;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

import com.google.inject.Injector;

public class MockitoFeature implements RunnerFeature {

    protected final MockProvider provider = new MockProvider();

    @Override
    public void start() {
        provider.installSelf();
    }

    @Override
    public void testCreated(Injector injector, Object test) {
        DefaultServiceProvider.setProvider(provider);
        initMocks(test);
    }

    @Override
    public void afterRun() {
        try {
            cleanupThread();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeServiceException(e);
        }
    }

    @Override
    public void stop() {
        provider.uninstallSelf();
    }

    protected void cleanupThread() throws ReflectiveOperationException {
        ThreadLocal<IMockitoConfiguration> holder = (ThreadLocal<IMockitoConfiguration>) FieldUtils.readStaticField(
                GlobalConfiguration.class, "GLOBAL_CONFIGURATION", true);
        holder.remove();
    }
}
