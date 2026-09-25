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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricSet;

class GuavaCacheMetricTest {

    private static Cache<String, String> newCache() {
        return CacheBuilder.newBuilder().recordStats().build();
    }

    @Test
    void shouldBuildMetricSetFromMetricName() {
        MetricSet metrics = GuavaCacheMetric.of(newCache(), MetricName.build("test", "cache"));

        assertNotNull(metrics);
        assertFalse(metrics.getMetrics().isEmpty());
    }

    @Test
    void shouldBuildMetricSetFromStringNames() {
        MetricSet metrics = GuavaCacheMetric.of(newCache(), "test", "cache");

        assertNotNull(metrics);
        // size, eviction, hit count, hit ratio, miss count, read count
        assertTrue(metrics.getMetrics().size() >= 6);
    }

    @Test
    void gaugesShouldBeReadableAfterCacheActivity() {
        Cache<String, String> cache = newCache();
        cache.put("k", "v");
        cache.getIfPresent("k");      // records a hit
        cache.getIfPresent("absent"); // records a miss

        MetricSet metrics = GuavaCacheMetric.of(cache, "test");

        boolean sawGauge = false;
        for (Metric metric : metrics.getMetrics().values()) {
            if (metric instanceof Gauge<?> gauge) {
                sawGauge = true;
                assertNotNull(gauge.getValue());
            }
        }
        assertTrue(sawGauge);
    }
}
