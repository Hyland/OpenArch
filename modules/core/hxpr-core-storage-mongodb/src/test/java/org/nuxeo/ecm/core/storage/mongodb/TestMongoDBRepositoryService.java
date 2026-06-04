/*
 * (C) Copyright 2019-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 *     Salem Aouana
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryDescriptor;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryService;
import org.nuxeo.runtime.cluster.BasicClusterFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, BasicClusterFeature.class, TransactionalFeature.class })
@Deploy("com.hyland.hxpr.runtime.pubsub")
@Deploy("com.hyland.hxpr.config")
@Deploy("org.nuxeo.runtime.mongodb")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/RepositoryService.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/dbs-repository-service.xml")
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core.storage.mongodb")
@Deploy("org.nuxeo.ecm.core.storage.mongodb:mock-repo-manager-contrib.xml")
public class TestMongoDBRepositoryService {

    @Inject
    protected MongoDBRepositoryService mongoDBRepositoryService;

    @Inject
    protected DBSRepositoryService dbsService;

    @Inject
    protected RepositoryManager repositoryManager;

    /**
     * Checks correct declaration of contribution.
     */
    protected void check(String name, String label) {
        DBSRepositoryDescriptor desc = dbsService.getRepositoryDescriptor(name);
        assertNotNull(desc);
        assertEquals(label, desc.label);
        assertTrue(desc instanceof MongoDBRepositoryDescriptor);

        Map<String, Repository> repos = ((MockRepositoryManager) repositoryManager).getResolvedRepositories();
        Repository repo = repos.get(name);
        assertNotNull(repo);
        assertEquals(label, repo.getLabel());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.mongodb:test-repo-contrib-1.xml")
    public void testService() {
        check("testmongo", "1");
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.mongodb:test-repo-contrib-1.xml")
    @Deploy("org.nuxeo.ecm.core.storage.mongodb:test-repo-contrib-2.xml")
    public void testMerge() {
        check("testmongo", "2");
    }

}
