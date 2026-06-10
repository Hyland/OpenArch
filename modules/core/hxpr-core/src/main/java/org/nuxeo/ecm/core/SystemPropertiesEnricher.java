/*
 * (C) Copyright 2026 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentEnricher;
import org.nuxeo.ecm.core.api.DocumentEnrichmentContext;

/**
 * Community-provided enricher that stamps the document system fields ({@code sys_created},
 * {@code sys_creator}, {@code sys_modified}, {@code sys_lastContributor}, {@code sys_contributors})
 * on create and update. Registered against the {@code enrichers} extension point so it runs at the
 * same point as any downstream enrichers, in the same transaction.
 */
public class SystemPropertiesEnricher implements DocumentEnricher {

    @Override
    public void onCreate(DocumentEnrichmentContext context) {
        var now = context.getTimestamp();
        var principalId = context.getPrincipal().getActingId();
        context.setProperty(SYS_CREATED, now);
        context.setProperty(SYS_CREATOR, principalId);
        context.setProperty(SYS_MODIFIED, now);
        context.setProperty(SYS_LAST_CONTRIBUTOR, principalId);
        addContributor(context, principalId);
    }

    @Override
    public void onUpdate(DocumentEnrichmentContext context) {
        var principalId = context.getPrincipal().getActingId();
        context.setProperty(SYS_MODIFIED, context.getTimestamp());
        context.setProperty(SYS_LAST_CONTRIBUTOR, principalId);
        addContributor(context, principalId);
    }

    private void addContributor(DocumentEnrichmentContext context, String principalId) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) context.getProperty(SYS_CONTRIBUTORS);
        if (list == null) {
            // no contributors yet, create it
            context.setProperty(SYS_CONTRIBUTORS, List.of(principalId));
            return;
        }
        if (list.contains(principalId)) {
            // already present
            return;
        }
        // add to existing contributors
        List<Object> newList = new ArrayList<>(list.size() + 1);
        newList.addAll(list);
        newList.add(principalId);
        context.setProperty(SYS_CONTRIBUTORS, newList);
    }
}
