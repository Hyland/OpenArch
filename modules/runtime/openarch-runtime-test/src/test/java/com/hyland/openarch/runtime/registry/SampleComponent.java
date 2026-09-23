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

package com.hyland.openarch.runtime.registry;

import java.util.HashMap;
import java.util.Map;

import com.hyland.openarch.common.xmap.registry.MapRegistry;
import com.hyland.openarch.common.xmap.registry.SingleRegistry;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * @since 11.5
 */
public class SampleComponent extends DefaultComponent {

    public static final String SINGLE_XP = "single";

    public static final String MAP_XP = "map";

    public static final String MAP_COMPAT_WARN_XP = "map_compat_warn";

    public static final String CUSTOM_XP = "custom";

    protected boolean registryContributionRegistered;

    protected boolean registryContributionUnregistered;

    protected Map<String, SampleLegacyDescriptor> legacyRegistry = new HashMap<>();

    public SingleRegistry getSingleRegistry() {
        return getExtensionPointRegistry(SINGLE_XP);
    }

    public MapRegistry getMapRegistry() {
        return getExtensionPointRegistry(MAP_XP);
    }

    public MapRegistry getCompatWarnMapRegistry() {
        return getExtensionPointRegistry(MAP_COMPAT_WARN_XP);
    }

    public SampleRegistry getCustomRegistry() {
        return getExtensionPointRegistry(CUSTOM_XP);
    }

}
