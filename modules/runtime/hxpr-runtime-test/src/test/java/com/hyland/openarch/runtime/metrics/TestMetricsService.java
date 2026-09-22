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

package com.hyland.openarch.runtime.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

import io.dropwizard.metrics5.MetricName;

/**
 * @since 11.1
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.runtime.metrics")
@Deploy("org.nuxeo.runtime.metrics:test-metrics-contrib.xml")
public class TestMetricsService {

    @Inject
    protected MetricsService service;

    @Test
    public void testService() {
        assertNotNull(service);
        service.stopReporters();
        service.startReporters();
        service.stopReporters();
        service.startReporters();
    }

    @Test
    public void testMockReporter() {
        assertNotNull(MockReporter.getFilterForTesting());
        assertEquals(42, MockReporter.getPollIntervalForTesting());
    }

    @Test
    public void testFilterMetrics() {
        List<String> metrics = Arrays.asList("nuxeo.directories.continent.cache.hits", //
                "nuxeo.directories.continent.cache.invalidations", //
                "nuxeo.directories.continent.cache.misses", //
                "nuxeo.directories.continent.cache.size", //
                "nuxeo.directories.digestauth.cache.hits", //
                "nuxeo.directories.digestauth.cache.invalidations", //
                "nuxeo.directories.digestauth.cache.misses", //
                "nuxeo.directories.digestauth.cache.size", //
                "nuxeo.cache.vocab-WorkflowType-cache-without-references.invalidate-all-counter", //
                "nuxeo.cache.vocab-WorkflowType-cache-without-references.read-counter", //
                "nuxeo.cache.vocab-WorkflowType-cache-without-references.read-hit-counter", //
                "nuxeo.cache.vocab-WorkflowType-cache-without-references.read-hit-ratio", //
                "nuxeo.cache.vocab-WorkflowType-cache-without-references.read-miss-counter", //
                "nuxeo.opensearch.service.bulkIndex", //
                "nuxeo.opensearch.service.delete", //
                "nuxeo.opensearch.service.fetch", //
                "nuxeo.opensearch.service.index", //
                "nuxeo.opensearch.service.scroll", //
                "nuxeo.opensearch.service.search");

        List<String> expectedMetrics = Arrays.asList("nuxeo.opensearch.service.bulkIndex", //
                "nuxeo.opensearch.service.delete", //
                "nuxeo.opensearch.service.fetch", //
                "nuxeo.opensearch.service.index", //
                "nuxeo.opensearch.service.scroll", //
                "nuxeo.opensearch.service.search");
        assertNotEquals(expectedMetrics.size(), metrics.size());

        assertNotNull(MockReporter.getFilterForTesting());
        List<String> filteredMetrics = metrics.stream()
                                              .filter(name -> MockReporter.getFilterForTesting()
                                                                          .matches(MetricName.build(name), null))
                                              .toList();
        assertEquals(expectedMetrics.size(), filteredMetrics.size());
    }

}
