/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage;

import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.schema.TypeService;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FullTextService {

    @EventListener
    public void onSchemaUpdated(TypeService.SchemaManagerUpdatedEvent ev) {
        var repositoryName = ev.repositoryName();
        reloadFullTextConfiguration(repositoryName);
    }

    public void reloadFullTextConfiguration(String repositoryName) {
        var repository = Framework.getService(RepositoryService.class).getRepository(repositoryName);
        if (repository != null) {
            var fulltextConfiguration = repository.getFulltextConfiguration();
            if (fulltextConfiguration != null) {
                // Only reload if the fulltext is not disabled
                var schemaManager = Framework.getService(TypeService.class).getSchemaManager(repositoryName);
                var fullTextConfigurationService = Framework.getService(FullTextConfigurationService.class);
                var updatedConfig = fullTextConfigurationService.makeConfiguration(schemaManager,
                        fulltextConfiguration.getFulltextFieldSizeLimit(),
                        fulltextConfiguration.isFulltextStoredInBlob());
                repository.reloadFullTextConfiguration(updatedConfig);
            }
        }
    }

}
