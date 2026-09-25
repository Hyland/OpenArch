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
package com.hyland.openarch.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@Disabled("TODO DISABLED reimplement default values later")
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/defaultvalue-docTypes.xml")
public class TestSQLRepositoryDefaultValues {

    @Inject
    protected CoreSession session;

    @Test
    public void testSimpleCreatedWithDefaultValue() throws Exception {
        // given a doc saved with a property with a default value not modified
        DocumentModel doc = session.createDocumentModel("/", "doc1", "DocDefaultValue");
        doc.create();
        session.save();

        // when I get it
        doc = session.getDocument(doc.getRef());

        // then the default value must be set
        assertEquals("value", doc.getPropertyValue("dv_simpleWithDefault"));
    }

    @Test
    public void testSimpleSetNullSetsDefaultValue() throws Exception {
        // given a doc saved with a property with a default value updated
        DocumentModel doc = session.createDocumentModel("/", "doc1", "DocDefaultValue");
        doc.setPropertyValue("dv_simpleWithDefault", "newValue");
        doc.create();
        session.save();

        // when I get the doc and set the value to null
        doc = session.getDocument(doc.getRef());
        doc.setPropertyValue("dv_simpleWithDefault", null);
        session.save();

        // then the property should be reset to the default value
        doc = session.getDocument(doc.getRef());
        assertEquals("value", doc.getPropertyValue("dv_simpleWithDefault"));
    }

    @Test
    public void testMultiCreatedWithDefaultValue() throws Exception {
        // given a doc saved with a property with a default value not modified
        DocumentModel doc = session.createDocumentModel("/", "doc1", "DocDefaultValue");
        doc.create();
        session.save();

        // when I get it
        doc = session.getDocument(doc.getRef());

        // then the default value must be set
        Object[] value = (Object[]) doc.getPropertyValue("dv_multiWithDefault");
        assertNotNull(value);
        assertEquals(Arrays.asList("value1", "value2"), Arrays.asList(value));
    }

    @Test
    public void testMultiSetNullSetsDefaultValue() throws Exception {
        // given a doc saved with a property with a default value updated
        DocumentModel doc = session.createDocumentModel("/", "doc1", "DocDefaultValue");
        doc.setPropertyValue("dv_multiWithDefault", new String[] { "newValue1", "newValue2" });
        doc.create();
        session.save();

        // when I get the doc and set the value to null
        doc = session.getDocument(doc.getRef());
        doc.setPropertyValue("dv_multiWithDefault", null);
        session.save();

        // then the property should be reset to the default value
        doc = session.getDocument(doc.getRef());
        Object[] value = (Object[]) doc.getPropertyValue("dv_multiWithDefault");
        assertNotNull(value);
        assertEquals(Arrays.asList("value1", "value2"), Arrays.asList(value));
    }

    @Test
    public void testMultiSetEmptyArraySetsDefaultValue() throws Exception {
        // given a doc saved with a property with a default value updated
        DocumentModel doc = session.createDocumentModel("/", "doc1", "DocDefaultValue");
        doc.setPropertyValue("dv_multiWithDefault", new String[] { "newValue1", "newValue2" });
        doc.create();
        session.save();

        // when I get the doc and set the value to an empty array
        doc = session.getDocument(doc.getRef());
        doc.setPropertyValue("dv_multiWithDefault", new Object[0]);
        session.save();

        // then the property should be reset to the default value
        doc = session.getDocument(doc.getRef());
        Object[] value = (Object[]) doc.getPropertyValue("dv_multiWithDefault");
        assertNotNull(value);
        assertEquals(Arrays.asList("value1", "value2"), Arrays.asList(value));
    }

}
