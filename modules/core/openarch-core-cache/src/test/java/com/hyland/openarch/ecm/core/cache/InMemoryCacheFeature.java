/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.cache;

import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

import com.google.inject.Binder;
import com.google.inject.name.Names;

@Features(CacheFeature.class)
@Deploy("com.hyland.openarch.ecm.core.cache:inmemory-cache-config.xml")
public class InMemoryCacheFeature implements RunnerFeature {

    public static final String MAXSIZE_TEST_CACHE_NAME = "maxsize-test-cache";

    @Override
    public void configure(Binder binder) {
        binder.bind(Cache.class).annotatedWith(Names.named(MAXSIZE_TEST_CACHE_NAME)).toProvider(
                () -> Framework.getService(CacheService.class).getCache(MAXSIZE_TEST_CACHE_NAME));
    }

}
