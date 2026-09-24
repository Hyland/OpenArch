/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot.mock;

import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * Mock component for testing.
 */
public class MockComponent extends DefaultComponent implements IMockComponent {

    protected String prop;

    protected String refProp;

    protected String refProp2;

    protected String sysProp;

    @Override
    public void activate() {
        prop = getProperty("myProperty");
        refProp = getProperty("myReferenceProperty");
        refProp2 = getProperty("myReferenceProperty2");
        sysProp = getProperty("mySysProperty");
    }

    @Override
    public String getPropertyValue() {
        return prop;
    }

    @Override
    public String getReferencePropertyValue() {
        return refProp;
    }

    @Override
    public String getReferenceProperty2Value() {
        return refProp2;
    }

    @Override
    public String getSysPropertyValue() {
        return sysProp;
    }

}
