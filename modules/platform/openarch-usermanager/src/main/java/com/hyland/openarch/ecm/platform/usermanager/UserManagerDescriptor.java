/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.platform.usermanager;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.runtime.model.ComponentStartOrders;

/**
 * APG-240 All attributes are defined public because the user manager service do not get access to the fields. OSGI
 * don't allow splitted packages having access to public members defined from an another package provider.
 *
 * @author matic
 */
@XObject(value = "userManager")
// merge handled by custom registry
@XRegistry(enable = false, remove = false)
public class UserManagerDescriptor {

    @XNode("@class")
    public Class<?> userManagerClass;

    @XNode("@priority")
    public int priority = ComponentStartOrders.DEFAULT;

    @XNode("userCacheName")
    public String userCacheName;

    /**
     * Merge with data from another descriptor.
     */
    public void merge(UserManagerDescriptor other) {
        if (other.userManagerClass != null && other.priority <= priority) {
            userManagerClass = other.userManagerClass;
        }
        if (other.priority < priority) {
            priority = other.priority;
        }
        if (other.userCacheName != null) {
            userCacheName = other.userCacheName;
        }
    }

}
