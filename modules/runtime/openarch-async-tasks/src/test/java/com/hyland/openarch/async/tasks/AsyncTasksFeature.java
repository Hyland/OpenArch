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
package com.hyland.openarch.async.tasks;

import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesLoader;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;

@Deploy("com.hyland.openarch.runtime.kv")
@Deploy("com.hyland.openarch.async.tasks")
@Deploy("com.hyland.openarch.async.tasks.tests")
@Features({ RuntimeFeature.class, TransactionalFeature.class })
public class AsyncTasksFeature implements RunnerFeature {

    @Override
    public void initialize(FeaturesLoader loader) {
        loader.getFeature(TransactionalFeature.class)
              .addWaiter(duration -> Framework.getService(TaskService.class).awaitCompletion(duration));
    }
}
