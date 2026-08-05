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

package org.nuxeo.ecm.platform.usermanager;

import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreFeature;
import org.nuxeo.ecm.platform.login.filestore.UserManagerFileStoreTestConfiguration;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for {@link UserManagerResolver} backed by the community file-based
 * {@link org.nuxeo.ecm.platform.login.filestore.UserManagerFileStore}.
 */
@Features({ UserManagerFileStoreFeature.class })
@SpringBootTest(classes = { UserManagerFileStoreTestConfiguration.class })
public class UserManagerResolverFileStoreTest extends AbstractUserManagerResolverTestCase {

}
