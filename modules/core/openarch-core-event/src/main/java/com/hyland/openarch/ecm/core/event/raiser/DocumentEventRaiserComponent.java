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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * This component exposing the {@code raisers} extension point and implementing
 * {@link DocumentEventRaiserService}.
 * <p>
 * It acts as a central hub responsible for raising different kinds of document events (for example
 * {@code DOCUMENT_UPDATED} or {@code DOCUMENT_BLOB_MODIFIED}). Distributions contribute a
 * {@link DocumentEventRaiser} bound to an event name; when {@link #raiseEvent} is invoked the
 * component looks up the raiser that supports the requested event and delegates to it.
 *
 */
public class DocumentEventRaiserComponent extends DefaultComponent implements DocumentEventRaiserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventRaiserComponent.class);
    protected static final String XP_RAISERS = "raisers";
    /** Contributed raisers, resolved at {@link #start()}. */
    @SuppressWarnings("java:S3077") // The list reference is guarded; entries are populated on start.
    private volatile List<DocumentEventRaiser> raisers = List.of();

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Called by the OpenArch runtime when the component is started. Resolves the contributed
     * {@link DocumentEventRaiserDescriptor}s into the raiser lookup list.
     */
    @Override
    public void start() {
        raisers = this.<DocumentEventRaiserDescriptor> getRegistryContributions(XP_RAISERS)
                      .stream()
                      .map(DocumentEventRaiserDescriptor::getRaiser)
                      .toList();
        log.debug("Registered {} document event raiser(s)", raisers.size());
    }

    @Override
    public void stop() {
        raisers = List.of();
    }

    @Override
    public void raiseEvent(DocumentEventRaiserContext context) {
        raisers.forEach(raiser -> raiser.raise(context));
    }
}
