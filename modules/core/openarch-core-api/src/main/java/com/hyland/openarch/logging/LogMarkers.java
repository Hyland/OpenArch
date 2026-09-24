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
package com.hyland.openarch.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Shared SLF4J markers for logging concerns that need to cross module boundaries.
 */
public final class LogMarkers {

    /**
     * Name of the marker that bypasses the {@code CondensingDuplicateMessageFilter} so every occurrence is logged.
     */
    public static final String BYPASS_CONDENSING_MARKER_NAME = "BYPASS_CONDENSING";

    /**
     * Attach this marker to a log statement to bypass the {@code CondensingDuplicateMessageFilter}.
     */
    public static final Marker BYPASS_CONDENSING = MarkerFactory.getMarker(BYPASS_CONDENSING_MARKER_NAME);

    private LogMarkers() {
    }
}
