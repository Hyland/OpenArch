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

import com.hyland.openarch.runtime.metrics.MetricsService;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.RatioGauge;
import io.dropwizard.metrics5.SharedMetricRegistries;

public class CacheMetrics extends CacheWrapper {

    protected MetricRegistry registry;

    protected Counter read;

    protected Counter readHit;

    protected Counter readMiss;

    protected RatioGauge readHitRatio;

    protected Counter write;

    protected Counter invalidation;

    protected Gauge<Long> size;

    protected final MetricName readHitName = nameOf("hit");

    protected final MetricName readHitRatioName = nameOf("hit.ratio");

    protected final MetricName readMissName = nameOf("miss");

    protected final MetricName readName = nameOf("read");

    protected final MetricName writeName = nameOf("write");

    protected final MetricName invalidateAllName = nameOf("invalidation");

    protected final MetricName sizeName = nameOf("size");

    protected MetricName nameOf(String name) {
        return MetricName.build("nuxeo", "cache", name).tagged("cache", getName());
    }

    public CacheMetrics(CacheManagement cache) {
        super(cache);
    }

    private RatioGauge newRatioGauge() {
        return new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(readHit.getCount(), read.getCount());
            }
        };
    }

    @Override
    public void start() {
        registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        read = registry.counter(readName);
        readHit = registry.counter(readHitName);
        readMiss = registry.counter(readMissName);
        readHitRatio = newRatioGauge();
        registry.register(readHitRatioName, readHitRatio);
        write = registry.counter(writeName);
        invalidation = registry.counter(invalidateAllName);
        size = this::getSize;
        registry.register(sizeName, size);
    }

    @Override
    public void stop() {
        registry.remove(readName);
        registry.remove(readHitName);
        registry.remove(readMissName);
        registry.remove(readHitRatioName);
        registry.remove(writeName);
        registry.remove(invalidateAllName);
        registry.remove(sizeName);
    }

    @Override
    public Object get(String key) {
        Object value = super.get(key);
        read.inc();
        if (value != null || super.hasEntry(key)) {
            readHit.inc();
        } else {
            readMiss.inc();
        }
        return value;
    }

    @Override
    public void put(String key, Object value) {
        try {
            super.put(key, value);
        } finally {
            write.inc();
        }
    }

    @Override
    public void invalidateAll() {
        try {
            super.invalidateAll();
        } finally {
            invalidation.inc();
        }
    }

}
