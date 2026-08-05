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
package com.hyland.hxpr.async.tasks.message;

import java.io.Serializable;
import java.util.Map;

public interface Task {

    String getTaskId();

    String getTaskName();

    String getRepository();

    String getUsername();

    int getRetryCount();

    String getNodeId();

    Long getTimestamp();

    Map<String, Serializable> getProperties();

    /**
     * Returns true if this task type requires a RUNNING guard against SQS re-delivery storms.
     * Long-running tasks (e.g. scrollers) must opt in, since they hold their SQS message
     * unacknowledged for the duration of processing.
     */
    default boolean requiresRunningGuard() {
        return false;
    }

}
