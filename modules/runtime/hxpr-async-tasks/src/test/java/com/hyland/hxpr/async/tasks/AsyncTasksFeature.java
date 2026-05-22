/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package com.hyland.hxpr.async.tasks;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@Deploy("org.nuxeo.runtime.kv")
@Deploy("com.hyland.hxpr.async.tasks")
@Deploy("com.hyland.hxpr.async.tasks.tests")
@Features({ RuntimeFeature.class, TransactionalFeature.class })
public class AsyncTasksFeature implements RunnerFeature {

    @Override
    public void initialize(FeaturesLoader loader) {
        loader.getFeature(TransactionalFeature.class)
              .addWaiter(duration -> Framework.getService(TaskService.class).awaitCompletion(duration));
    }
}
