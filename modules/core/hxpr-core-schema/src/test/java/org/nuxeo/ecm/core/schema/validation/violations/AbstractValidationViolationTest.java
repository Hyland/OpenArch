/*
 * (C) Copyright 2022 Hyland (https://www.hyland.com/) and others.
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
 *     David Edwards
 */
package org.nuxeo.ecm.core.schema.validation.violations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

public abstract class AbstractValidationViolationTest {

    /**
     * The object used in the abstract tests. It must not use a known message key.
     */
    protected abstract AbstractValidationViolation getTestObject();

    protected String getExpectedMessage() {
        return null;
    }

    protected List<Object> getExpectedParameters() {
        return null;
    }

    protected boolean runGetDefaultMessage() {
        return true;
    }

    protected boolean runGetMessageParameters() {
        return true;
    }

    @Test
    public void testGetDefaultMessage() {
        assumeTrue(runGetDefaultMessage());

        assertEquals(getExpectedMessage(), getTestObject().getDefaultMessage(null));
        assertEquals(getExpectedMessage(), getTestObject().getMessage(Locale.ENGLISH));
        assertEquals(getExpectedMessage(), getTestObject().getMessage(Locale.FRENCH));
    }

    @Test
    public void testGetLog() {
        assertNotNull(getTestObject().getLog());
    }

    @Test
    public void testGetMessageParameters() {
        assumeTrue(runGetDefaultMessage());

        assertEquals(getExpectedParameters(), getTestObject().getMessageParameters(null));
    }
}
