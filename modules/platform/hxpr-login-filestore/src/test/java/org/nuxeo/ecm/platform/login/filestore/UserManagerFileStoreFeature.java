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
package org.nuxeo.ecm.platform.login.filestore;

import java.lang.reflect.Method;

import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.runtime.springboot.junit.security.MockAuthenticationSupport;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

/**
 * Test feature that provides the file-based {@link org.nuxeo.ecm.platform.usermanager.UserManager} implementation
 * ({@link UserManagerFileStore}) for tests that exercise the community profile.
 * <p>
 * Use this feature as a drop-in replacement for
 * {@link org.nuxeo.ecm.platform.test.UserManagerMockFeature} when you want to validate behaviour
 * against the real community {@code UserManager} rather than the hard-coded mock.
 *
 * <pre>{@code
 * @Features({UserManagerCommunityFeature.class, DummyLoginFeature.class})
 * @Import(UserManagerCommunityTestConfiguration.class)
 * @SpringBootTest(classes = { MyController.class })
 * class MyControllerTest extends RuntimeSpringBootTest { ... }
 * }</pre>
 *
 * <p>
 * Authentication is managed per test via the {@link WithUser} annotation; the default test principal is
 * {@code testuser} (UUID {@code ff7b0360-3594-416c-b587-2fa4dd0839ce}), matching the entry in the bundled
 * {@code hxpr-users.yml} test fixture.
 *
 * @see UserManagerFileStore
 * @see UserManagerFileStoreTestConfiguration
 */
@Deploy("org.nuxeo.ecm.platform.usermanager")
@Deploy("org.nuxeo.ecm.platform.login.filestore")
public class UserManagerFileStoreFeature implements RunnerFeature {

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
