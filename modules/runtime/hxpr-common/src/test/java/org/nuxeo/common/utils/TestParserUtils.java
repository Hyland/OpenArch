/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.common.utils;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.nuxeo.common.utils.ParserUtils.EXTERNAL_GENERAL_ENTITIES;
import static org.nuxeo.common.utils.ParserUtils.EXTERNAL_PARAMETER_ENTITIES;
import static org.nuxeo.common.utils.ParserUtils.LOAD_EXTERNAL_DTD;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.junit.jupiter.api.Test;

public class TestParserUtils {

    @Test
    public void testSettingAttributesForTransformer() throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(ACCESS_EXTERNAL_STYLESHEET, "");
        transformerFactory.setFeature(FEATURE_SECURE_PROCESSING, true);

        TransformerFactory testedTransformerFactory = TransformerFactory.newInstance();
        ParserUtils.setSecurityFeatures(testedTransformerFactory);

        assertEquals(transformerFactory.getAttribute(ACCESS_EXTERNAL_DTD), testedTransformerFactory.getAttribute(ACCESS_EXTERNAL_DTD));
        assertEquals(transformerFactory.getAttribute(ACCESS_EXTERNAL_STYLESHEET), testedTransformerFactory.getAttribute(ACCESS_EXTERNAL_STYLESHEET));
        assertEquals(transformerFactory.getFeature(FEATURE_SECURE_PROCESSING), testedTransformerFactory.getFeature(FEATURE_SECURE_PROCESSING));
    }

    @Test
    public void testSettingAttributesForDocumentBuilder() throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        documentBuilderFactory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        documentBuilderFactory.setFeature(LOAD_EXTERNAL_DTD, false);

        DocumentBuilderFactory testedDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
        ParserUtils.setSecurityFeatures(testedDocumentBuilderFactory);

        assertEquals(documentBuilderFactory.getFeature(EXTERNAL_GENERAL_ENTITIES), testedDocumentBuilderFactory.getFeature(EXTERNAL_GENERAL_ENTITIES));
        assertEquals(documentBuilderFactory.getFeature(EXTERNAL_PARAMETER_ENTITIES), testedDocumentBuilderFactory.getFeature(EXTERNAL_PARAMETER_ENTITIES));
        assertEquals(documentBuilderFactory.getFeature(LOAD_EXTERNAL_DTD), testedDocumentBuilderFactory.getFeature(LOAD_EXTERNAL_DTD));
    }

    @Test
    public void testExceptionWhenSettingAttributesForTransformer() throws Exception {
        TransformerFactory factoryMock= mock(TransformerFactory.class);
        doThrow(TransformerConfigurationException.class)
                .when(factoryMock)
                .setFeature(anyString(), anyBoolean());
        assertThrows(IOException.class, () -> ParserUtils.setSecurityFeatures(factoryMock));
    }

    @Test
    public void testExceptionWhenSettingAttributesForDocumentBuilder() throws Exception {
        DocumentBuilderFactory factoryMock= mock(DocumentBuilderFactory.class);
        doThrow(ParserConfigurationException.class)
                .when(factoryMock)
                .setFeature(anyString(), anyBoolean());
        assertThrows(IOException.class, () -> ParserUtils.setSecurityFeatures(factoryMock));
    }
}
