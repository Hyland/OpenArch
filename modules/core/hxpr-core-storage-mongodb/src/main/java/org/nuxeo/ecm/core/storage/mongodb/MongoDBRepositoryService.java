/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage.mongodb;

import org.nuxeo.ecm.core.storage.dbs.AbstractDBSRepositoryDescriptorRegistry;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryRegistry;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Service holding the configuration for MongoDB repositories.
 * <p>
 * Since 11.5, all logic is led by associated registry {@link Registry}.
 *
 * @since 5.9.4
 */
public class MongoDBRepositoryService extends DefaultComponent {

    protected static final String COMPONENT_NAME = "org.nuxeo.ecm.core.storage.mongodb.MongoDBRepositoryService";

    protected static final String XP = "repository";

    /**
     * Registry for {@link MongoDBRepositoryDescriptor}, forwarding to {@link DBSRepositoryRegistry}.
     * <p>
     * Also handles custom merge.
     *
     * @since 11.5
     */
    public static final class Registry extends AbstractDBSRepositoryDescriptorRegistry {

        public Registry() {
            super(COMPONENT_NAME, XP, MongoDBRepositoryFactory.class);
        }

    }

}
