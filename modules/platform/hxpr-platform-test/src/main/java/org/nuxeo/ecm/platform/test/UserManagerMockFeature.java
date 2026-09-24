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
package org.nuxeo.ecm.platform.test;

import java.lang.reflect.Method;

import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.ecm.core.cache.InMemoryCacheFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.springboot.junit.security.MockAuthenticationSupport;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

/**
 * Test feature that provides an in-memory {@link org.nuxeo.ecm.platform.usermanager.UserManager} implementation
 * ({@link UserManagerMock}) for community/platform tests that must not depend on {@code hxpr-login-hxp}.
 * <p>
 * Use this feature instead of {@code UserManagerFeature} wherever a real IdP connection is not needed.
 *
 * <pre>{@code
 * @Features({UserManagerMockFeature.class, DummyLoginFeature.class})
 * @Import(UserManagerMockTestConfiguration.class)
 * @SpringBootTest(classes = { MyController.class })
 * class MyControllerTest extends RuntimeSpringBootTest { ... }
 * }</pre>
 *
 * @see UserManagerMock
 * @see UserManagerMockTestConfiguration
 */
@Features({InMemoryCacheFeature.class})
@Deploy("org.nuxeo.ecm.platform.usermanager")
@Deploy("org.nuxeo.ecm.platform.test:OSGI-INF/usermanager-mock-config.xml")
public class UserManagerMockFeature implements RunnerFeature {

    protected FeaturesLoader loader;

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        MockAuthenticationSupport.authenticate(loader.getConfig(method, WithUser.class));
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        MockAuthenticationSupport.clear();
    }

    @Override
    public void afterRun() {
        MockAuthenticationSupport.clear();
    }
}
