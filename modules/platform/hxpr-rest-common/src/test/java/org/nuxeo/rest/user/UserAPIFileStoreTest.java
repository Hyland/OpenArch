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
 *     Nelson Silva
 */
package org.nuxeo.rest.user;

import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreFeature;
import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreTestConfiguration;
import org.nuxeo.rest.exception.NuxeoExceptionController;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Community variant of {@link AbstractUserAPITest} — backed by the file-based
 * {@link org.nuxeo.ecm.platform.login.filestore.UserManagerFileStore} loaded from the bundled
 * {@code hxpr-users.yml} test fixture.
 *
 * <p>Verifies that the real community {@link org.nuxeo.ecm.platform.usermanager.UserManager} implementation
 * satisfies the same contract as the enterprise version ({@link UserAPIEnterpriseTest}).
 */
@Features({ UserManagerFileStoreFeature.class, DummyLoginFeature.class })
@SpringBootTest(classes = { UserAPI.class, NuxeoExceptionController.class, UserManagerFileStoreTestConfiguration.class })
class UserAPIFileStoreTest extends AbstractUserAPITest {
}
