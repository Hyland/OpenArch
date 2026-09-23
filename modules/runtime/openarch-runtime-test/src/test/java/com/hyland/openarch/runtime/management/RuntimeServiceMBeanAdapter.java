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
package com.hyland.openarch.runtime.management;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.hyland.openarch.runtime.RuntimeService;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.RegistrationInfo;

/**
 * @author Stephane Lacoin (Nuxeo EP Software Engineer)
 */
public class RuntimeServiceMBeanAdapter implements RuntimeServiceMBean {

    private static final String PREFIX = "service:";

    protected RuntimeService doGetRuntime() {
        return Framework.getRuntime();
    }

    @Override
    public String getDescription() {
        return doGetRuntime().getDescription();
    }

    @Override
    public String getHomeLocation() {
        try {
            return doGetRuntime().getHome().getCanonicalPath();
        } catch (IOException e) {
            throw ManagementRuntimeException.wrap(e);
        }
    }

    @Override
    public String getName() {
        return doGetRuntime().getName();
    }

    @Override
    public String getVersion() {
        return doGetRuntime().getVersion().toString();
    }

    @Override
    public Map<String, Set<String>> getPendingComponents() {
        return doGetRuntime().getComponentManager().getPendingRegistrations();
    }

    @Override
    public Set<String> getResolvedComponents() {
        Collection<RegistrationInfo> registrations = doGetRuntime().getComponentManager().getRegistrations();
        return registrations.stream().map(ri -> PREFIX + ri.getName()).collect(toSet());
    }

}
