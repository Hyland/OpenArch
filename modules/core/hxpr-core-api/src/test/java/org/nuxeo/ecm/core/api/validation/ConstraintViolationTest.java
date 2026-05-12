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
package org.nuxeo.ecm.core.api.validation;

import java.util.List;

import org.nuxeo.ecm.core.schema.validation.violations.AbstractValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.AbstractValidationViolationTest;

public class ConstraintViolationTest extends AbstractValidationViolationTest {

    @Override
    protected AbstractValidationViolation getTestObject() {
        return new ConstraintViolation(List.of(), null, getClass());
    }

    @Override
    protected boolean runGetDefaultMessage() {
        return false;
    }

    @Override
    protected boolean runGetMessageParameters() {
        return false;
    }

}
