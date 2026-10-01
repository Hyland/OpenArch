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
package com.hyland.openarch.runtime.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link IdempotencyOutput}.
 */
class IdempotencyOutputTest {

    @Test
    void unwrap_registeredType_deserializesTheJsonTree() {
        IdempotencyResultTypeRegistry.register("sample-result", SampleResult.class);
        SampleResult expected = new SampleResult("value");

        Object actual = IdempotencyOutput.success(JsonMapper.builder().build().valueToTree(expected), "sample-result")
                                         .unwrap();

        assertEquals(expected, actual);
    }

    @Test
    void unwrap_registeredListType_deserializesElementsToTheirConcreteType() {
        IdempotencyResultTypeRegistry.registerList("sample-result-list", SampleResult.class);
        List<SampleResult> expected = List.of(new SampleResult("first"), new SampleResult("second"));

        Object actual = IdempotencyOutput
                .success(JsonMapper.builder().build().valueToTree(expected), "sample-result-list")
                .unwrap();

        assertEquals(expected, actual);
        assertInstanceOf(SampleResult.class, ((List<?>) actual).get(0));
    }

    record SampleResult(String value) {
    }
}
