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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestSessionImplAtomicKey {

    @Inject
    protected CoreSession session;

    @Test
    public void computeKeyForAtomicCreationCombinesRepositoryParentAndName() {
        DocumentModel doc = session.createDocumentModel("/", "mydoc", SYS_FILE);

        String key = ((SessionImpl) session).computeKeyForAtomicCreation(doc);

        String rootId = session.getRootDocument().getId();
        assertEquals(session.getRepositoryName() + "-" + rootId + "-mydoc", key);
    }
}
