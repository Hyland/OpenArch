/*
 * (C) Copyright 2007-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.scheduler;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XEnable;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;

/**
 * Schedule descriptor.
 */
@XObject("schedule")
@XRegistry(enable = false)
public class ScheduleDescriptor {

    @XNode("@id")
    @XRegistryId
    public String id;

    @XNode("class")
    protected String className;

    @XNode("cronExpression")
    public String cronExpression;

    /**
     * @since 10.2
     */
    @XNode("timezone")
    public String timeZone;

    @XNode("userid")
    public String userid;

    /**
     * @since 5.7.3
     */
    @XNode(value = XEnable.ENABLE, fallback = "@enabled", defaultAssignment = "true")
    @XEnable
    public boolean enabled;

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getUserId() {
        return userid;
    }

    @Override
    public String toString() {
        return "Schedule " + id + " (cron=" + cronExpression + ')';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ScheduleDescriptor)) {
            return false;
        }
        return id.equals(((ScheduleDescriptor) obj).getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @since 10.2
     */
    public String getTimeZone() {
        return timeZone;
    }
}
