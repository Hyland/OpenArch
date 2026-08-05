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
package org.nuxeo.rest.user;

import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.platform.test.UserManagerMockFeature;
import org.nuxeo.ecm.platform.test.UserManagerMockTestConfiguration;
import org.nuxeo.rest.exception.NuxeoExceptionController;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Community variant of {@link AbstractUserAPITest} — backed by the in-memory {@link org.nuxeo.ecm.platform.test.UserManagerMock}.
 */
@Features({ UserManagerMockFeature.class, DummyLoginFeature.class })
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest(classes = { UserAPI.class, NuxeoExceptionController.class })
class UserAPIMockTest extends AbstractUserAPITest {
}
