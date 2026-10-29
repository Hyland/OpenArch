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
package com.hyland.openarch.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-repository-value-types.xml")
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/testquery-core-types-contrib.xml")
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib-2.xml")
public class TestCombinedModelContributions {

    @Inject
    protected TypeService typeService;

    @Test
    public void testCombinedFulltextFixturesUseExplicitOverrides() {
        var manager = typeService.getDefaultSchemaManager();
        assertTrue(manager.getDocumentType("Note").hasSchema("note"));
        assertTrue(manager.getDocumentType("MyDocType").hasSchema("myschema"));
        assertEquals(Set.of("system", "info"), manager.getDocumentType("MyDocType2").getSchemaNames());
        assertTrue(manager.getDocumentType("GovernableFile").hasSchema("info"));
        assertTrue(manager.getDocumentType("GovernableFile").hasMixin("SysGovernable"));
        assertTrue(manager.getDocumentType("Tag").hasSchema("mydublincore"));
    }

    @Test
    @Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/test-repo-core-types-note-not-indexable-contrib.xml")
    public void testExplicitNoteReplacementDeclaresItsMarkerMixins() {
        var manager = typeService.getDefaultSchemaManager();
        assertEquals(Set.of("system", "sysversionable", "note", "sysfiles"),
                manager.getDocumentType("Note").getSchemaNames());
        assertTrue(manager.getDocumentType("Note").hasMixin("Publishable"));
        assertTrue(manager.getDocumentType("Note").hasMixin("Commentable"));
    }
}
