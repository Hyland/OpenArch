/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.async.tasks;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XEnable;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;
import com.hyland.openarch.ecm.core.api.RepositoryException;

@XObject("asyncTask")
@XRegistry
public class TaskProcessorDescriptor {

    private static final String DEFAULT_BUCKET_SIZE = "100";

    private static final String DEFAULT_BATCH_SIZE = "25";

    @XNode("@name")
    @XRegistryId
    private String name;

    @XNode("@class")
    private Class<? extends TaskProcessor<?>> extendsAsyncTaskExecutorClass;

    @XNode(value = "@bucketSize", defaultAssignment = DEFAULT_BUCKET_SIZE)
    private Integer bucketSize;

    @XNode(value = "@batchSize", defaultAssignment = DEFAULT_BATCH_SIZE)
    private Integer batchSize;

    @XNode(value = XEnable.ENABLE, fallback = "@enabled", defaultAssignment = "true")
    @XEnable
    private Boolean enabled = true;

    @XNode("@conditionalOnProperty")
    private String conditionalOnProperty;

    public String getName() {
        return name;
    }

    /**
     * Name of a runtime property that must resolve to a non-blank value for this task to be activated. When set, the
     * task is only enabled if the property is present, allowing a task to be disabled by default and conditionally
     * activated by configuration (e.g. only once its backing SQS queue has been provisioned). Returns {@code null} when
     * the task has no such requirement.
     */
    public String getConditionalOnProperty() {
        return conditionalOnProperty;
    }

    public Integer getBucketSize() {
        return bucketSize;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public TaskProcessor<?> createTaskProcessor() {
        try {
            return extendsAsyncTaskExecutorClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RepositoryException(
                    "Cannot create async task executor class of type " + extendsAsyncTaskExecutorClass.getName(), e);
        }
    }
}
