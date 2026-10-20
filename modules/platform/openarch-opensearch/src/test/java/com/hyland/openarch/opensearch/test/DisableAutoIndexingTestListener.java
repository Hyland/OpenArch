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
package com.hyland.openarch.opensearch.test;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;

import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventListener;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.opensearch.OpenSearchConstants;

/**
 * Stands in for a listener that runs ahead of the OpenSearch listener and disables automatic indexing on the event
 * context: it does so for documents whose title is {@link #SKIP_INDEXING_TITLE}.
 */
public class DisableAutoIndexingTestListener implements EventListener {

    public static final String SKIP_INDEXING_TITLE = "skip-indexing";

    @Override
    public void handleEvent(Event event) {
        if (event.getContext() instanceof DocumentEventContext docCtx
                && SKIP_INDEXING_TITLE.equals(docCtx.getSourceDocument().getPropertyValue(SYS_TITLE))) {
            docCtx.setProperty(OpenSearchConstants.DISABLE_AUTO_INDEXING, true);
        }
    }
}
