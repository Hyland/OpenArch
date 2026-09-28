/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.user;

import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.platform.login.filestore.UserManagerFileStore;
import com.hyland.openarch.ecm.platform.login.filestore.UserManagerFileStoreFeature;
import com.hyland.openarch.ecm.platform.login.filestore.UserManagerFileStoreTestConfiguration;
import com.hyland.openarch.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.rest.exception.RepositoryExceptionController;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Community variant of {@link AbstractUserAPITest} — backed by the file-based
 * {@link UserManagerFileStore} loaded from the bundled
 * {@code openarch-users.yml} test fixture.
 *
 * <p>Verifies that the real community {@link UserManager} implementation
 * satisfies the same contract as the enterprise version ({@link UserAPIEnterpriseTest}).
 */
@Features({ UserManagerFileStoreFeature.class, DummyLoginFeature.class })
@SpringBootTest(classes = { UserAPI.class, RepositoryExceptionController.class, UserManagerFileStoreTestConfiguration.class })
class UserAPIFileStoreTest extends AbstractUserAPITest {
}
