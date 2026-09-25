/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static com.hyland.openarch.ecm.core.cache.CacheDescriptor.DEFAULT_MAX_SIZE;
import static com.hyland.openarch.ecm.core.cache.CacheDescriptor.DEFAULT_TTL;
import static com.hyland.openarch.ecm.core.cache.CacheDescriptor.OPTION_MAX_SIZE;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, BasicClusterFeature.class })
@Deploy("com.hyland.openarch.ecm.core.cache:OSGI-INF/CacheService.xml")
public class TestCacheDescriptor {

    protected static final String NAME = "myid";

    @Inject
    protected CacheService service;

    protected CacheServiceImpl getService() {
        return (CacheServiceImpl) service;
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config.xml")
    public void testConfig() throws Exception {
        CacheDescriptor desc = getService().getCacheDescriptor(NAME);
        assertNotNull(desc);
        assertEquals(NAME, desc.getName());
        assertEquals(123, desc.getTTL());
        assertEquals(Map.of("foo", "foovalue", "bar", "barvalue"), desc.getOptions());
        assertNotNull(getService().getCache(NAME));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config.xml")
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config-merge.xml")
    public void testMerge() throws Exception {
        CacheDescriptor desc = getService().getCacheDescriptor(NAME);
        assertNotNull(desc);
        assertEquals(456, desc.getTTL());
        assertEquals(Map.of("foo", "foovalue2", "bar", "barvalue"), desc.getOptions());
        assertNotNull(getService().getCache(NAME));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config.xml")
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config-remove.xml")
    public void testRemove() throws Exception {
        assertNull(getService().getCacheDescriptor(NAME));
        assertNull(getService().getCache(NAME));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config.xml")
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config-remove.xml")
    @Deploy("com.hyland.openarch.ecm.core.cache.test:test-cache-config-merge.xml")
    public void testRemoveAndRemerge() throws Exception {
        CacheDescriptor desc = getService().getCacheDescriptor(NAME);
        assertNotNull(desc);
        assertEquals(456, desc.getTTL());
        assertEquals(Map.of("foo", "foovalue2"), desc.getOptions());
        assertNotNull(getService().getCache(NAME));
    }

    @Test
    public void testRegisterCache() {
        assertNull(getService().getCacheDescriptor(NAME));
        assertNull(getService().getCache(NAME));

        getService().registerCache(NAME);

        CacheDescriptor desc = getService().getCacheDescriptor(NAME);
        assertNotNull(desc);
        assertEquals(DEFAULT_TTL, desc.getTTL());
        assertEquals(Map.of(OPTION_MAX_SIZE, String.valueOf(DEFAULT_MAX_SIZE)), desc.getOptions());
        assertNotNull(getService().getCache(NAME));
    }

}
