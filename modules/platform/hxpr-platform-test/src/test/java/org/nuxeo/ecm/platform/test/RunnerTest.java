/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.platform.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * Tests features available in platform runner
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core:local-resource-test.xml")
public class RunnerTest {

    @Inject
    protected CoreSession session;

    protected SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = session.getSchemaManager();
    }

    @Test
    public void testLocalResource() {
        DocumentType dt = schemaManager.getDocumentType("MyFolder");
        assertEquals("MyFolder", dt.getName());
    }

    @Test
    public void testCoreSessionInjection() {
        assertNotNull(session);
    }

}
