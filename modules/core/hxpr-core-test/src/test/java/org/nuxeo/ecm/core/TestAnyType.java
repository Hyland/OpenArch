/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-anytype-contrib.xml")
public class TestAnyType {

    private static final String MY_ANY = "MyAny";

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    protected void reopenSession() {
        session = coreFeature.reopenCoreSession();
    }

    protected void nextTransaction() {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    @Test
    public void test() {
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_ANY);
        Object obj = Map.of( //
                "foo", "bar", //
                "baz", List.of(1, 2, 3));
        doc.setPropertyValue("any_obj", obj);
        doc.create();
        session.save();

        reopenSession();
        doc.refresh();

        Map<String, Object> expected = Map.of( //
                "foo", "bar", //
                "baz", List.of(1L, 2L, 3L));
        assertEquals(expected, doc.getPropertyValue("any_obj"));
    }

}
