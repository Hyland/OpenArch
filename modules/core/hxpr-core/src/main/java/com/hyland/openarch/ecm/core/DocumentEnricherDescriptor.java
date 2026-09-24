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
package com.hyland.openarch.ecm.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistryId;
import com.hyland.openarch.ecm.core.api.DocumentEnricher;

/**
 * XMap descriptor for the {@code enrichers} extension point, instantiating a {@link DocumentEnricher}
 * from its {@code class} attribute.
 */
@XObject("enricher")
public class DocumentEnricherDescriptor {

    private static final Logger log = LogManager.getLogger(DocumentEnricherDescriptor.class);

    @XNode("@id")
    @XRegistryId
    public String id;

    private DocumentEnricher enricher;

    @XNode("@class")
    public void setClass(Class<? extends DocumentEnricher> enricherClass) throws ReflectiveOperationException {
        try {
            enricher = enricherClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            log.error("Failed to create document enricher {}", enricherClass);
            throw e;
        }
    }

    public DocumentEnricher getEnricher() {
        return enricher;
    }
}
