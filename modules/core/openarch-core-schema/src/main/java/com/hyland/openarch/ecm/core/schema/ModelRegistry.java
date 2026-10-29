/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.XAnnotatedObject;
import com.hyland.openarch.common.xmap.registry.AbstractRegistry;

/** Ordered registrations, including resources with the same path in different bundles. */
public class ModelRegistry extends AbstractRegistry {

    private final List<ModelDescriptor> contributions = new ArrayList<>();

    @Override
    public void initialize() {
        contributions.clear();
        super.initialize();
    }

    @Override
    protected <T> T doRegister(Context context, XAnnotatedObject object, Element element, String extensionId) {
        T contribution = getInstance(context, object, element);
        contributions.add((ModelDescriptor) contribution);
        return contribution;
    }

    public List<ModelDescriptor> getModels() {
        checkInitialized();
        return List.copyOf(contributions);
    }
}
