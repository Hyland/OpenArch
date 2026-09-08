/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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

import java.time.Duration;
import java.util.Map;

import org.nuxeo.runtime.RuntimeServiceException;

import jakarta.validation.constraints.NotNull;

/**
 * Cluster Service, defining cluster node state and identity.
 *
 * @since 11.1
 */
public interface ClusterService {

    String COMPONENT_NAME = "org.nuxeo.runtime.cluster.ClusterService";

    String XP_CONFIG = "configuration";

    String NODE_ID_OLD_PROP = "repository.clustering.id";

    String CLUSTERING_ENABLED_OLD_PROP = "repository.clustering.enabled";

    /**
     * Checks if cluster mode is enabled.
     *
     * @return {@code true} if cluster mode is enabled, {@code false} if not
     */
    boolean isEnabled();

    /**
     * Returns the node id. This is never {@code null}.
     *
     * @return the node id
     */
    @NotNull
    String getNodeId();

    /**
     * Returns a read-only view of the map used to maintain the status of the latest cache invalidation received from
     * each node. A missing entry means nothing is known about that node.
     */
    Map<String, Long> getCachesStatusPerNode();

    /**
     * Update the latest cache invalidation received from a node.
     * <p>
     * The status only ever moves forward: an older timestamp is ignored. A {@code null} timestamp is also ignored,
     * as an invalidation not produced by a transaction carries no transaction time and so cannot advance the status.
     */
    void updateCachesStatusPerNode(String nodeId, Long timestamp);

    /**
     * Runs a {@link Runnable} atomically in a cluster-wide critical section, outside a transaction.
     *
     * @param key the key used to determine atomicity
     * @param duration the duration during which we attempt to acquire the lock
     * @param pollDelay the delay between two subsequent polls of the lock
     * @param runnable the runnable
     * @throws RuntimeServiceException if locking failed
     */
    void runAtomically(String key, Duration duration, Duration pollDelay, Runnable runnable);

}
