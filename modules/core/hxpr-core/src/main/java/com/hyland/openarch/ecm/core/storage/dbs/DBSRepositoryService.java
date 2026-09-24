/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.dbs;

import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.repository.Repository;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.repository.RepositoryFactory;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.ComponentStartOrders;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * Service holding the configuration for DBS repositories.
 *
 * @since 7.10-HF04, 8.1
 */
public class DBSRepositoryService extends DefaultComponent {

    protected static final String XP = "repositoryContributor";

    protected DBSRepositoryRegistry getDBSRegistry() {
        return getExtensionPointRegistry(XP);
    }

    @Override
    public int getApplicationStartedOrder() {
        return ComponentStartOrders.REPOSITORY - 1;
    }

    @Override
    public void start() {
        DBSRepositoryRegistry registry = getDBSRegistry();
        registry.getRepositoryContributors().forEach(desc -> {
            updateRegistration(registry.getRepositoryDescriptor(desc.name), desc.factory);
        });
    }

    /**
     * Update repository registration in high-level repository service.
     */
    protected void updateRegistration(DBSRepositoryDescriptor descriptor,
            Class<? extends DBSRepositoryFactory> factoryClass) {
        String repositoryName = descriptor.name;
        RepositoryFactory repositoryFactory;
        try {
            repositoryFactory = factoryClass.getConstructor(String.class).newInstance(repositoryName);
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException(e);
        }
        if (descriptor.isCacheEnabled()) {
            repositoryFactory = new DBSCachingRepositoryFactory(repositoryName, repositoryFactory);
        }
        // extract label, isDefault, and pass it to high-level registry
        Repository repository = new Repository(repositoryName, descriptor.label, descriptor.isDefault(),
                descriptor.isHeadless(), repositoryFactory, descriptor.pool);
        Framework.getService(RepositoryManager.class).addRepository(repository);
    }

    // called by factory
    public DBSRepositoryDescriptor getRepositoryDescriptor(String name) {
        return getDBSRegistry().getRepositoryDescriptor(name);
    }

}
