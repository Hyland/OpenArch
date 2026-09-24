/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.mem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.repository.Repository;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.storage.dbs.DBSRepositoryDescriptor;
import com.hyland.openarch.ecm.core.storage.dbs.DBSRepositoryService;
import com.hyland.openarch.runtime.cluster.BasicClusterFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.5
 */
@ExtendWith(FeaturesExtension.class)
@Features({ RuntimeFeature.class, BasicClusterFeature.class, TransactionalFeature.class })
@Deploy("org.nuxeo.ecm.core:OSGI-INF/RepositoryService.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/dbs-repository-service.xml")
@Deploy("com.hyland.openarch.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core.storage.mem")
@Deploy("org.nuxeo.ecm.core.storage.mem:mock-repo-manager-contrib.xml")
public class TestMemRepositoryService {

    @Inject
    protected MemRepositoryService memRepositoryService;

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
        assertTrue(desc instanceof MemRepositoryDescriptor);

        Map<String, Repository> repos = ((MockRepositoryManager) repositoryManager).getResolvedRepositories();
        assertEquals(1, repos.size());
        Repository repo = repos.get(name);
        assertNotNull(repo);
        assertEquals(label, repo.getLabel());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.mem:test-repo-contrib-1.xml")
    public void testContrib() {
        check("testmem", "1");
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.mem:test-repo-contrib-1.xml")
    @Deploy("org.nuxeo.ecm.core.storage.mem:test-repo-contrib-2.xml")
    public void testMerge() {
        check("testmem", "2");
    }

}
