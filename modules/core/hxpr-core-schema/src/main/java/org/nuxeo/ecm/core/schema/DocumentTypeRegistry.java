/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.schema;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.XAnnotatedObject;
import com.hyland.openarch.common.xmap.registry.MapRegistry;
import org.w3c.dom.Element;

/**
 * Registry for descriptors {@link DocumentTypeDescriptor} to the "doctype" extension point.
 * <p>
 * Handles custom merges.
 *
 * @since 11.5
 */
public class DocumentTypeRegistry extends MapRegistry {

    // additional registry to synchronize allowed/denied subtypes definition from higher-level TypeService
    protected MapRegistry programmaticTypes = new MapRegistry();

    @Override
    public void initialize() {
        super.initialize();
        // merge with programmatic types registrations
        List<DocumentTypeDescriptor> types = programmaticTypes.getContributionValues();
        types.forEach(type -> {
            DocumentTypeDescriptor localType = (DocumentTypeDescriptor) contributions.get(type.name);
            if (localType == null) {
                return;
            }
            localType.subtypes = mergeSubTypes(localType.subtypes, type.subtypes);
        });
    }

    protected String[] mergeSubTypes(String[] orig, String[] other) {
        return Stream.concat(Arrays.stream(orig), Arrays.stream(other)).distinct().toArray(String[]::new);
    }

    // custom API

    /**
     * Register method used to synchronize allowed/denied subtypes definition from higher-level TypeService.
     */
    public void registerDocumentType(Context ctx, XAnnotatedObject xObject, Element element, String tag) {
        programmaticTypes.register(ctx, xObject, element, tag);
    }

    /**
     * Unregister method used to synchronize allowed/denied subtypes definition from higher-level TypeService.
     */
    public void unregisterDocumentType(String tag) {
        programmaticTypes.unregister(tag);
    }

}
