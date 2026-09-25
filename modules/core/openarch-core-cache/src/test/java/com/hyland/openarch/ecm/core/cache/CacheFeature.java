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

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;

import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.RunnerFeature;

import com.google.inject.Binder;
import com.google.inject.name.Names;

@Deploy("com.hyland.openarch.ecm.core.cache")
@Features(BasicClusterFeature.class)
public class CacheFeature implements RunnerFeature {

    public static final String DEFAULT_TEST_CACHE_NAME = "default-test-cache";

    public static final String KEY = "key1";

    public static final String VAL = "val1";

    @Override
    public void configure(Binder binder) {
        bindCache(binder, DEFAULT_TEST_CACHE_NAME);
    }

    protected void bindCache(Binder binder, final String name) {
        binder.bind(Cache.class).annotatedWith(Names.named(name)).toProvider(
                () -> Framework.getService(CacheService.class).getCache(name));
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        Cache cache = Framework.getService(CacheService.class).getCache(DEFAULT_TEST_CACHE_NAME);
        ((CacheManagement) cache).putLocal(KEY, VAL);
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        clearCache(DEFAULT_TEST_CACHE_NAME);
    }

    protected void clearCache(String name) {
        Cache cache = Framework.getService(CacheService.class).getCache(name);
        ((CacheManagement) cache).invalidateLocalAll();
    }

    public static <T extends Cache> T unwrapImpl(Class<T> type, Cache cache) {
        if (!(cache instanceof CacheWrapper)) {
            fail("Not a wrapper " + cache.getClass());
        }
        cache = ((CacheWrapper) cache).cache;
        if (cache instanceof CacheWrapper) {
            return unwrapImpl(type, cache);
        }
        if (!type.isAssignableFrom(cache.getClass())) {
            fail("Not of requested type  " + type);
        }
        return type.cast(cache);
    }
}
