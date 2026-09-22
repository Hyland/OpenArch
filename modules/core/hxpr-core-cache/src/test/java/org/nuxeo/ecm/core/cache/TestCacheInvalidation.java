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
package org.nuxeo.ecm.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.cache.CacheServiceImpl.AbstractCachePubSubInvalidator;
import org.nuxeo.ecm.core.cache.CacheServiceImpl.CacheInvalidation;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(FeaturesExtension.class)
@Features(CacheFeature.class)
@Deploy("com.hyland.hxpr.runtime.pubsub")
@Deploy("org.nuxeo.ecm.core.cache:inmemory-cache-config.xml")
@Deploy("org.nuxeo.ecm.core.cache:test-cluster.xml")
@Deploy("org.nuxeo.ecm.core.cache:test-cache-no-cluster-invalidation.xml")
public class TestCacheInvalidation {

    // from XML file
    protected static final String NODE1 = "123";

    protected static final String NODE2 = "456";

    public static List<CacheInvalidation> RECEIVED_INVALIDATIONS = new CopyOnWriteArrayList<>();

    public class DummyCachePubSubInvalidator extends AbstractCachePubSubInvalidator {

        @Override
        public CacheInvalidation deserialize(String message) throws IOException {
            return CacheInvalidation.deserialize(message);
        }

        @Override
        public void receivedMessage(CacheInvalidation invalidation) {
            RECEIVED_INVALIDATIONS.add(invalidation);
            super.receivedMessage(invalidation);
        }

        @Override
        protected Cache getCache(String name) {
            if (!name.equals(cache.getName())) {
                throw new UnsupportedOperationException(name);
            }
            return cache;
        }
    }

    @Inject
    @Named(CacheFeature.DEFAULT_TEST_CACHE_NAME)
    protected Cache cache;

    protected DummyCachePubSubInvalidator invalidator;

    @BeforeEach
    public void setUp() {
        // manually register a second invalidator with a different node id
        invalidator = new DummyCachePubSubInvalidator();
        invalidator.initialize(CacheServiceImpl.CACHE_INVAL_PUBSUB_TOPIC, NODE2);
    }

    @AfterEach
    public void tearDown() {
        invalidator.close();
    }

    @Test
    @Order(1)
    public void testInvalidationsSent() {
        // write to the cache
        RECEIVED_INVALIDATIONS.clear();
        cache.put("key2", "val2");
        // no need to wait, everything is synchronous for in-memory pubsub

        // check invalidation
        assertEquals(1, RECEIVED_INVALIDATIONS.size());
        CacheInvalidation inval = RECEIVED_INVALIDATIONS.get(0);
        assertEquals(CacheFeature.DEFAULT_TEST_CACHE_NAME, inval.cacheName);
        assertEquals("key2", inval.key);

        // remove an entry from the cache
        RECEIVED_INVALIDATIONS.clear();
        cache.invalidate("key2");

        // check invalidation
        assertEquals(1, RECEIVED_INVALIDATIONS.size());
        inval = RECEIVED_INVALIDATIONS.get(0);
        assertEquals(CacheFeature.DEFAULT_TEST_CACHE_NAME, inval.cacheName);
        assertEquals("key2", inval.key);

        // invalidate all the keys
        RECEIVED_INVALIDATIONS.clear();
        cache.invalidateAll();

        // check invalidation for all keys
        assertEquals(1, RECEIVED_INVALIDATIONS.size());
        inval = RECEIVED_INVALIDATIONS.get(0);
        assertEquals(CacheFeature.DEFAULT_TEST_CACHE_NAME, inval.cacheName);
        assertEquals(AbstractCachePubSubInvalidator.ALL_KEYS, inval.key);
    }

    @Test
    @Order(2)
    public void testInvalidationsReceived() {
        // we have an entry in the cache
        assertEquals("val1", cache.get("key1"));

        // send an invalidation
        invalidator.sendInvalidation(CacheFeature.DEFAULT_TEST_CACHE_NAME, "key1");
        // no need to wait, everything is synchronous for in-memory pubsub

        // check the entry is gone
        assertNull(cache.get("key1"));
    }

    @Test
    @Order(3)
    public void testNoInvalidationsSentWhenClusterInvalidationDisabled() {
        Cache noInvalCache = Framework.getService(CacheService.class).getCache("no-invalidation-test-cache");

        // write, invalidate and invalidateAll — none of these should produce a pub/sub message
        RECEIVED_INVALIDATIONS.clear();
        noInvalCache.put("key1", "val1");
        noInvalCache.invalidate("key1");
        noInvalCache.put("key2", "val2");
        noInvalCache.invalidateAll();

        assertTrue(RECEIVED_INVALIDATIONS.isEmpty(), "No invalidation messages should be sent for a cache with clusterInvalidation=false");
    }

}
