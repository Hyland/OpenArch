/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api.local;

import java.lang.reflect.Method;

import javax.security.auth.login.LoginException;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.api.login.LoginContext;
import com.hyland.openarch.runtime.api.login.LoginService;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

import com.google.inject.Binder;

/**
 * Feature allowing to mock the {@link LoginService} in order to test services which need a security context.
 * <p>
 * This feature contributes {@link DummyLoginAs} to the component.
 * <p>
 * The feature will look for {@link WithUser} annotation on the test method, then on the class method, and use its value
 * to log in the given user.
 * <p>
 * You can then get the logged in principal with help of {@link #getPrincipal()} or by using injection.
 * <p>
 * By default the feature will log in as Administrator, like the {@code org.nuxeo.ecm.core.test.CoreFeature} does.
 *
 * @since 11.1
 */
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/dummy-login-config.xml")
public class DummyLoginFeature implements RunnerFeature {

    public static final String TEST_ADMIN_USER_NAME = "testuser";
    public static final String TEST_ADMIN_USER = "ff7b0360-3594-416c-b587-2fa4dd0839ce";
    public static final String TEST_ANONYMOUS_USER = "ANONYMOUS";

    protected FeaturesLoader loader;

    protected LoginContext loginContext;

    public NuxeoPrincipal getPrincipal() {
        return (NuxeoPrincipal) loginContext.getPrincipal();
    }

    public static UserPrincipal getTestPrincipal(String userid, boolean administrator) {
        return new UserPrincipal(userid, null, administrator);
    }

    public static UserPrincipal getTestAdminPrincipal() {
        return getTestPrincipal(TEST_ADMIN_USER, true);
    }

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void beforeRun() {
        try {
            login(loader.getConfig(WithUser.class));
        } catch (LoginException e) {
            throw new RuntimeServiceException(e);
        }
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(NuxeoPrincipal.class).toProvider(NuxeoPrincipal::getCurrent);
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        logout(); // logout the class login
        try {
            login(loader.getConfig(method, WithUser.class));
        } catch (LoginException e) {
            throw new RuntimeServiceException(e);
        }
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        logout();
    }

    @Override
    public void afterRun() {
        logout();
    }

    protected void login(WithUser withUser) throws LoginException {
        loginContext = Framework.loginUser(withUser.id());
    }

    protected void logout() {
        if (loginContext != null) {
            loginContext.close();
            loginContext = null;
        }
    }
}
