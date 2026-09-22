/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.test.runner;

import java.lang.reflect.Method;

import com.hyland.openarch.runtime.api.Framework;

/**
 * Feature that restart the component manger between each test.
 *
 * @since 10.2
 */
public class RestartFeature implements RunnerFeature {

    @Override
    public void afterTeardown(Method method, Object test) {
        Framework.getRuntime().getComponentManager().stop();
        Framework.getRuntime().getComponentManager().start();
    }
}
