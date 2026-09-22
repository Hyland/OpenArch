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
package com.hyland.hxpr.async.tasks;

import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;

/**
 * Test feature that sets the {@code test.async.tasks.required.present} system property before the runtime starts, so a
 * task gated on it is activated. The matching absent property is deliberately never set.
 */
@Features(AsyncTasksFeature.class)
@Deploy("com.hyland.hxpr.async.tasks.tests:OSGI-INF/required-property-tasks-contrib.xml")
public class RequiredPropertyTasksFeature extends AsyncTasksFeature {

    public static final String PRESENT_PROPERTY = "test.async.tasks.required.present";

    @Override
    public void initialize(FeaturesLoader loader) {
        super.initialize(loader);
        System.setProperty(PRESENT_PROPERTY, "content-lake-test-evStoreWrite");
    }

    @Override
    public void uninitialize() {
        System.clearProperty(PRESENT_PROPERTY);
    }
}
