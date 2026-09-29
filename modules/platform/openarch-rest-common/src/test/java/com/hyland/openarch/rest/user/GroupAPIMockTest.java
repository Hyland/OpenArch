/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
import com.hyland.openarch.ecm.platform.test.UserManagerMock;
import com.hyland.openarch.ecm.platform.test.UserManagerMockFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockTestConfiguration;
import com.hyland.openarch.rest.exception.RepositoryExceptionController;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Variant of {@link AbstractGroupAPITest} backed by the in-memory {@link UserManagerMock}.
 */
@Features({ UserManagerMockFeature.class, DummyLoginFeature.class })
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest(classes = { GroupAPI.class, RepositoryExceptionController.class })
class GroupAPIMockTest extends AbstractGroupAPITest {

    @Override
    protected int minGroupSize() {
        return 50;
    }
}
