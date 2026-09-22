/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.version.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.inject.Inject;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;

/**
 * @since 9.1
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
public abstract class AbstractTestVersioning {

    protected static final String MY_FILE = "MyFile";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected CoreSession session;

    protected void maybeSleepToNextSecond() {
        coreFeature.getStorageConfiguration().maybeSleepToNextSecond();
    }

    protected void waitForAsyncCompletion() {
        transactionalFeature.nextTransaction();
    }

    protected void assertVersion(long expected, DocumentModel doc) {
        assertEquals(expected, (long) doc.getVersion());
    }

}
