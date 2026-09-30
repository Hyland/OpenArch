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
package com.hyland.openarch.runtime.springboot.junit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TestBearerTokenProvider {

    @AfterEach
    public void reset() {
        BearerTokenProvider.register(null);
    }

    @Test
    public void testReturnsNullByDefault() {
        assertNull(BearerTokenProvider.getOrNull());
    }

    @Test
    public void testReturnsRegisteredToken() {
        BearerTokenProvider.register(() -> "token-123");
        assertEquals("token-123", BearerTokenProvider.getOrNull());
    }

    @Test
    public void testRegisterNullSupplierYieldsNull() {
        BearerTokenProvider.register(() -> "token-123");
        BearerTokenProvider.register(null);
        assertNull(BearerTokenProvider.getOrNull());
    }

    @Test
    public void testRegisterOverridesPreviousSupplier() {
        BearerTokenProvider.register(() -> "first");
        BearerTokenProvider.register(() -> "second");
        assertEquals("second", BearerTokenProvider.getOrNull());
    }

    @Test
    public void testSupplierIsEvaluatedOnEachCall() {
        StringBuilder value = new StringBuilder("a");
        BearerTokenProvider.register(value::toString);
        assertEquals("a", BearerTokenProvider.getOrNull());
        value.append("b");
        assertEquals("ab", BearerTokenProvider.getOrNull());
    }

}
