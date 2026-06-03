/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithApplicationProperties;

/**
 * @since TODO
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@WithApplicationProperties("test-application.properties")
public class TestWithApplicationProperties {

    @Test
    public void testAddMessageBefore() {
        assertTrue(Framework.isBooleanPropertyTrue("org.nuxeo.prop.enabled"));
        assertEquals("3600", Framework.getProperty("nuxeo.prop.defaultTTL"));
        assertEquals("testiing", Framework.getProperty("nuxeo.prop.val"));
    }

}
