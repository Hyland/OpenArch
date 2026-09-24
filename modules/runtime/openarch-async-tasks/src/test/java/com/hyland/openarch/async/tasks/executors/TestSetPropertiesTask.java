/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.async.tasks.executors;

import static com.hyland.openarch.async.tasks.executors.SetPropertiesBatchTaskProcessor.PARAM_CHECK_IN;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

public class TestSetPropertiesTask {

    @Test
    public void auditAndVersioningFieldsShouldHaveDefaultValue() {
        Collection<Serializable> values = new ArrayList<>();
        values.add(null);
        values.add("");
        values.add("tutu");
        values.add("false");
        values.add("[false]");
        values.add("none");
        values.add(Boolean.FALSE);
        values.add(new String[] { "true" });
        values.add(new ArrayList<>(singleton("tutu")));
        values.add(new ArrayList<>(singleton("true")));
        testParamParsing(PARAM_CHECK_IN, values, false, false);
    }

    @Test
    public void auditFieldShouldBeTrue() {
        Collection<Serializable> values = new ArrayList<>();
        values.add("true");
        values.add(Boolean.TRUE);
        testParamParsing(PARAM_CHECK_IN, values, true, true);
    }

    @Test
    public void versioningFieldShouldBeTrue() {
        Collection<Serializable> values = new ArrayList<>();
        values.add("true");
        values.add(Boolean.TRUE);
        testParamParsing(PARAM_CHECK_IN, values, false, true);
    }

    protected void testParamParsing(String param, Collection<Serializable> values, boolean audit, boolean versioning) {
        for (Serializable value : values) {
            var properties = new HashMap<String, Serializable>();
            properties.put(param, value);
            SetPropertiesBatchTaskProcessor computation = new SetPropertiesBatchTaskProcessor();
            computation.initParams(properties);
            assertEquals(versioning, computation.checkIn);
        }
    }

}
