/*
 * (C) Copyright 2012-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class)
public class QueryResultsAreAutomaticallyClosedTest {

    @Inject
    protected CoreFeature coreFeature;

    // needs a JCA connection for this to work
    @Test
    public void testTransactional() {
        CoreSession session = coreFeature.getCoreSessionSystem();
        try (IterableQueryResult results = session.queryAndFetch("SELECT * FROM SysContent")) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
            assertFalse(results.mustBeClosed());
        }
    }

    protected static class NestedQueryRunner extends UnrestrictedSessionRunner {

        public NestedQueryRunner(String reponame) {
            super(reponame);
        }

        protected IterableQueryResult result;

        @Override
        public void run() {
            result = session.queryAndFetch("SELECT * FROM SysContent");
        }

    }

    @SuppressWarnings("resource") // we specifically test indirect close
    @Test
    public void testNested() {
        IterableQueryResult mainResults;
        CoreSession main = coreFeature.getCoreSessionSystem();
        NestedQueryRunner runner = new NestedQueryRunner(main.getRepositoryName());
        mainResults = main.queryAndFetch("SELECT * FROM SysContent");
        runner.runUnrestricted();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        assertFalse(mainResults.mustBeClosed());
    }
}
