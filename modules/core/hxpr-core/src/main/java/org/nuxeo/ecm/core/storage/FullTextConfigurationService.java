/*
 * (C) Copyright 2023 Hyland (http://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Gildas Lefevre
 */
package org.nuxeo.ecm.core.storage;

import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.schema.SchemaManager;

/**
 * Service for managing the FullText configuration of all repositories.
 */
public interface FullTextConfigurationService {

    FulltextConfiguration makeConfiguration(SchemaManager schemaManager, int fulltextFieldSizeLimit,
                                            boolean fulltextStoredInBlob);
}
