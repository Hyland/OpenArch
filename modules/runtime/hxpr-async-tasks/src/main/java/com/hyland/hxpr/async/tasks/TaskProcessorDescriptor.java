/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
package com.hyland.hxpr.async.tasks;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.common.xmap.registry.XEnable;
import org.nuxeo.common.xmap.registry.XRegistry;
import org.nuxeo.common.xmap.registry.XRegistryId;
import org.nuxeo.ecm.core.api.NuxeoException;

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

    public String getName() {
        return name;
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
            throw new NuxeoException(
                    "Cannot create async task executor class of type " + extendsAsyncTaskExecutorClass.getName(), e);
        }
    }
}
