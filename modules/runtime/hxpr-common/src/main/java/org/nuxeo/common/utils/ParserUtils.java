/*
 * (C) Copyright 2022 Hyland.
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
 *     Krzysztof Krolewski
 */
package org.nuxeo.common.utils;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

/**
 * Helper class for setting security attributes and features for XML parsers
 *
 * @author Krzysztof Krolewski
 */
public class ParserUtils {

    public static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";

    public static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    public static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private ParserUtils() {
        // This is a utility class.
    }

    public static void setSecurityFeatures(TransformerFactory factory) throws IOException {
        try {
            // disable use of all protocols by external entities
            factory.setAttribute(ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(ACCESS_EXTERNAL_STYLESHEET, "");
            factory.setFeature(FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            throw new IOException(e);
        }
    }

    public static void setSecurityFeatures(DocumentBuilderFactory factory) throws IOException {
        try {
            // disable external entities
            factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
            // disable external doctypes
            factory.setFeature(LOAD_EXTERNAL_DTD, false);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        }
    }
}
