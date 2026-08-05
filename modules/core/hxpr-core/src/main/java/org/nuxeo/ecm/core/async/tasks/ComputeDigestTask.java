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
package org.nuxeo.ecm.core.async.tasks;

import static org.nuxeo.ecm.core.async.tasks.ComputeDigestTaskProcessor.COMPUTE_DIGEST_TASK_NAME;

import com.hyland.hxpr.async.tasks.message.AbstractTask;

import java.util.Objects;

public class ComputeDigestTask extends AbstractTask {

    protected String blobProviderId;

    protected String key;

    public ComputeDigestTask() {
        // Empty constructor for deserialization
    }

    public ComputeDigestTask(String blobProviderId, String key) {
        super(COMPUTE_DIGEST_TASK_NAME, null, null);
        this.blobProviderId = blobProviderId;
        this.key = key;
        this.timestamp = null; // not related to a cache invalidation
    }

    public String getBlobProviderId() {
        return blobProviderId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComputeDigestTask cdat)) {
            return false;
        }
        return Objects.equals(cdat.blobProviderId, blobProviderId) //
                && Objects.equals(cdat.key, key);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = hashCode * 17 + (blobProviderId == null ? 0 : blobProviderId.hashCode());
        hashCode = hashCode * 17 + (key == null ? 0 : key.hashCode());
        return hashCode;
    }
}
