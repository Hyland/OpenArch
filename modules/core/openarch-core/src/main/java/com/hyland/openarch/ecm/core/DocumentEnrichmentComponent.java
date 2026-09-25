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

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.DocumentEnricher;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentContext;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentService;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * Default {@link DocumentEnrichmentService} implementation. Collects the {@link DocumentEnricher}
 * contributions registered against the {@code enrichers} extension point and fans create/update
 * calls out to each one in registration order.
 */
public class DocumentEnrichmentComponent extends DefaultComponent implements DocumentEnrichmentService {

    private static final Logger log = LogManager.getLogger(DocumentEnrichmentComponent.class);

    protected static final String XP_ENRICHERS = "enrichers";

    @SuppressWarnings("java:S3077") // Using volatile to guard the reference; the list is always immutable.
    private volatile List<DocumentEnricher> enrichers = List.of();

    @Override
    public void start() {
        enrichers = this.<DocumentEnricherDescriptor> getRegistryContributions(XP_ENRICHERS)
                        .stream()
                        .map(DocumentEnricherDescriptor::getEnricher)
                        .toList();
        log.debug("Registered {} document enricher(s)", enrichers.size());
    }

    @Override
    public void stop() {
        enrichers = List.of();
    }

    @Override
    public void enrichOnCreate(DocumentEnrichmentContext context) {
        enrichers.forEach(enricher -> enricher.onCreate(context));
    }

    @Override
    public void enrichOnUpdate(DocumentEnrichmentContext context) {
        enrichers.forEach(enricher -> enricher.onUpdate(context));
    }
}
