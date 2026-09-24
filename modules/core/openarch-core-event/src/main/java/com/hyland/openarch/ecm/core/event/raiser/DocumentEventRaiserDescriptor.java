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
package com.hyland.openarch.ecm.core.event.raiser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;

/**
 * XMap descriptor for the {@code raisers} extension point.
 * <p>
 * Each descriptor binds one event <em>name</em> (used as the registry key) to a
 * {@link DocumentEventRaiser} implementation instantiated from its {@code class} attribute.
 *
 */
@XObject("raiser")
@XRegistry
public class DocumentEventRaiserDescriptor {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventRaiserDescriptor.class);

    /** The event name this raiser handles. Used as the registry key. */
    @XNode("@name")
    @XRegistryId
    public String name;

    private DocumentEventRaiser raiser;

    @XNode("@class")
    public void setClass(Class<? extends DocumentEventRaiser> raiserClass) throws ReflectiveOperationException {
        try {
            raiser = raiserClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            log.error("Failed to create document event raiser {}", raiserClass);
            throw e;
        }
    }

    public String getName() {
        return name;
    }

    public DocumentEventRaiser getRaiser() {
        return raiser;
    }
}
