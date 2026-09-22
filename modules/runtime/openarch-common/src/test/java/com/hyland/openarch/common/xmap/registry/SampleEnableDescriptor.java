/*
 * (C) Copyright 2020-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.common.xmap.registry;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;

/**
 * Sample descriptor with id and enable value held by field annotations.
 *
 * @since 11.5
 */
@XObject("descriptor")
@XRegistry(enable = false)
public class SampleEnableDescriptor {

    @XRegistryId
    @XNode("@name")
    public String name;

    @XEnable
    @XNode(value = "@activated", fallback = XEnable.ENABLE)
    public Boolean activated;

    @XNode("value")
    public String value;

    @XNode(value = "bool")
    public Boolean bool;

}
