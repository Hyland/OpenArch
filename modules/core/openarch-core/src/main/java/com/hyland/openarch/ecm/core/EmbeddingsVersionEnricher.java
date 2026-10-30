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

import com.hyland.openarch.ecm.core.api.DocumentEnricher;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentContext;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.model.InternalDocument;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_CHUNKS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_CHUNKS_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_ON_PREM_PROCESSING_SUCCESS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;

public class EmbeddingsVersionEnricher implements DocumentEnricher {

    @Override
    public void onCreate(DocumentEnrichmentContext context) {
        InternalDocument document = context.getDocument();
        if (!SYS_EMBEDDINGS.equals(document.getType().getName())) {
            return;
        }
        document.setPropertyValue(SYSEMB_CHUNKS_VERSION, 1L);
    }

    @Override
    public void onUpdate(DocumentEnrichmentContext context) {
        InternalDocument document = context.getDocument();
        if (!SYS_EMBEDDINGS.equals(document.getType().getName())) {
            return;
        }
        if (context.getUpdatedFields().containsKey(SYSEMB_CHUNKS_VERSION)) {
            return;
        }
        Object onPremProcessingSuccess = context.getUpdatedFields().get(SYSEMB_ON_PREM_PROCESSING_SUCCESS);
        // If the on-prem processing success field is being updated, we don't want to increment the version number.
        if (onPremProcessingSuccess instanceof Boolean) {
            return;
        }
        // This will skip old parquet format but that is OK since all onPrem documents will use new format
        if (!context.getUpdatedFields().containsKey(SYSEMB_CHUNKS)) {
            return;
        }
        Object current = document.getPropertyValue(SYSEMB_CHUNKS_VERSION);
        if (current instanceof Long l) {
            document.addUpdateCondition(UpdateCondition.hxql(SYSEMB_CHUNKS_VERSION + " = " + l), context.getPrincipal());
            document.setPropertyValue(SYSEMB_CHUNKS_VERSION, l + 1L);
        } else {
            document.addUpdateCondition(UpdateCondition.hxql(SYSEMB_CHUNKS_VERSION + " IS NULL"), context.getPrincipal());
            document.setPropertyValue(SYSEMB_CHUNKS_VERSION, 1L);
        }
    }
}
