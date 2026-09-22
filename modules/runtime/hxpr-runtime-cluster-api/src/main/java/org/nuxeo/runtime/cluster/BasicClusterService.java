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
package org.nuxeo.runtime.cluster;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.registry.SingleRegistry;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class BasicClusterService extends DefaultComponent implements ClusterService {

    private static final Logger log = LogManager.getLogger(BasicClusterService.class);

    /** Very early as other services depend on us. */
    public static final int APPLICATION_STARTED_ORDER = -1000;

    protected boolean enabled;

    protected String nodeId;

    protected static final Random RANDOM = new Random(); // NOSONAR (doesn't need cryptographic strength)

    @Override
    public int getApplicationStartedOrder() {
        return APPLICATION_STARTED_ORDER;
    }

    @Override
    public void start() {
        ClusterNodeDescriptor descr = (ClusterNodeDescriptor) getRegistryContribution(XP_CONFIG).orElse(null);
        setupFromContribution(descr);
    }

    protected ClusterNodeDescriptor getClusterNodeContribution() {
        SingleRegistry registry = (SingleRegistry) Framework.getRuntime()
                .getComponentManager()
                .getExtensionPointRegistry(COMPONENT_NAME, XP_CONFIG)
                .orElseThrow(() -> new IllegalArgumentException("Unknown registry for extension point '%s--%s'".formatted(COMPONENT_NAME, XP_CONFIG)));
        Optional<ClusterNodeDescriptor> descriptor = registry.getContribution();
        return descriptor.orElseThrow(() -> new NuxeoException("No Cluster Config has been defined"));
    }

    protected void setupFromContribution(ClusterNodeDescriptor descr) {
        // enabled
        Boolean enabledProp = descr == null ? null : descr.getEnabled();
        if (enabledProp != null) {
            enabled = enabledProp.booleanValue();
        } else {
            // compat with old framework property
            enabled = Framework.isBooleanPropertyTrue(CLUSTERING_ENABLED_OLD_PROP);
        }
        // node id
        String id = descr == null ? null : defaultIfBlank(descr.getName(), null);
        if (id != null) {
            nodeId = id.trim();
        } else {
            // compat with old framework property
            id = Framework.getProperty(NODE_ID_OLD_PROP);
            if (isNotBlank(id)) {
                nodeId = id.trim();
            } else {
                // use a random node id
                long l;
                do {
                    l = RANDOM.nextLong();
                } while (l < 0); // keep a positive value to avoid weird node ids
                nodeId = String.valueOf(l);
                if (enabled) {
                    log.warn("Missing cluster node id configuration, please define it explicitly. "
                            + "Using random cluster node id instead: {}", nodeId);
                } else {
                    log.info("Using random cluster node id: {}", nodeId);
                }
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public Map<String, Long> getCachesStatusPerNode() {
        return Map.of();
    }

    @Override
    public void updateCachesStatusPerNode(String nodeId, Long timestamp) {
        // Nothing to do in this implementation
    }

    @Override
    public void runAtomically(String key, Duration duration, Duration pollDelay, Runnable runnable) {
        runnable.run();
    }
}
