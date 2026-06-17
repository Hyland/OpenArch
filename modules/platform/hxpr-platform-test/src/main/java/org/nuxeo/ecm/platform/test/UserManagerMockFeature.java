/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 * This is unpublished proprietary source code of Hyland. All rights reserved.
 * Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.test;

import java.lang.reflect.Method;

import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.ecm.core.cache.InMemoryCacheFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.springboot.junit.security.MockAuthenticationSupport;
import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RunnerFeature;

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
