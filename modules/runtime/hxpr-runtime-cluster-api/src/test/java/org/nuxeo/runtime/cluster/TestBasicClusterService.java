/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
package org.nuxeo.runtime.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for the {@link BasicClusterService}.
 */
@ExtendWith(FeaturesExtension.class)
@Features(BasicClusterFeature.class)
public class TestBasicClusterService {

    @Inject
    protected ClusterService clusterService;

    @Test
    public void testNothing() {
        // BasicClusterService always reports cluster as disabled
        assertFalse(clusterService.isEnabled());
        // A node id is always assigned (random when no config)
        assertNotNull(clusterService.getNodeId());
    }

    @Test
    @Deploy("org.nuxeo.runtime.cluster.api.tests:OSGI-INF/test-nodeid.xml")
    public void testClusterNodeId() {
        // Even with enabled="true" in the config, BasicClusterService always returns false
        assertTrue(clusterService.isEnabled());
        // The node id from the contribution is respected
        assertEquals("123", clusterService.getNodeId());
    }

    @Test
    @Deploy("org.nuxeo.runtime.cluster.api.tests:OSGI-INF/test-disable.xml")
    public void testDisable() {
        assertFalse(clusterService.isEnabled());
        assertNotNull(clusterService.getNodeId());
    }

    @Test
    @Deploy("org.nuxeo.runtime.cluster.api.tests:OSGI-INF/test-blank.xml")
    public void testClusterNodeBlank() {
        assertFalse(clusterService.isEnabled());
    }

    @Test
    @Deploy("org.nuxeo.runtime.cluster.api.tests:OSGI-INF/test-nodeid.xml")
    public void testUpdateCachesStatusPerNodeIsIgnored() {
        // BasicClusterService does not store cache status updates
        clusterService.updateCachesStatusPerNode("node1", 123L);
        assertTrue(clusterService.getCachesStatusPerNode().isEmpty());
        assertNull(clusterService.getCachesStatusPerNode().get("node1"));
    }

    @Test
    public void testGetCachesStatusPerNodeReturnsEmptyMap() {
        assertTrue(clusterService.getCachesStatusPerNode().isEmpty());
    }

    @Test
    @Deploy("org.nuxeo.runtime.cluster.api.tests:OSGI-INF/test-nodeid.xml")
    public void testRunAtomicallyExecutesRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        clusterService.runAtomically("some-key", Duration.ofSeconds(5), Duration.ofMillis(100),
                () -> executed.set(true));
        assertTrue(executed.get());
    }
}
