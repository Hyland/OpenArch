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
package com.hyland.hxpr.runtime.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

@DisplayName("IdempotencyResult tests")
class IdempotencyResultTest {

    @Test
    @DisplayName("success result stores and retrieves value")
    void testSuccessResult() {
        IdempotencyResult<String> result = IdempotencyResult.success("test-value");

        assertTrue(result.isSuccess());
        assertEquals("test-value", result.value().orElse(null));
        assertTrue(result.exception().isEmpty());
        assertEquals("test-value", result.unwrap());
    }

    @Test
    @DisplayName("failure result stores and retrieves exception")
    void testFailureResult() {
        Exception exception = new RuntimeException("Test error");
        IdempotencyResult<String> result = IdempotencyResult.failure(exception);

        assertFalse(result.isSuccess());
        assertTrue(result.value().isEmpty());
        assertEquals(exception, result.exception().orElse(null));
    }

    @Test
    @DisplayName("unwrap rethrows stored exception")
    void testUnwrapThrows() {
        Exception original = new RuntimeException("Test error");
        IdempotencyResult<String> result = IdempotencyResult.failure(original);

        RuntimeException thrown = assertThrows(RuntimeException.class, result::unwrap);
        assertEquals(original, thrown);
    }

    @Test
    @DisplayName("unwrap wraps checked exceptions")
    void testUnwrapWrapsCheckedException() {
        Exception checked = new Exception("Checked error");
        IdempotencyResult<String> result = IdempotencyResult.failure(checked);

        RuntimeException thrown = assertThrows(RuntimeException.class, result::unwrap);
        assertEquals(checked, thrown.getCause());
    }

    @Test
    @DisplayName("createdAtMillis returns creation timestamp")
    void testCreatedAtMillis() {
        long beforeCreation = System.currentTimeMillis();
        IdempotencyResult<String> result = IdempotencyResult.success("value");
        long afterCreation = System.currentTimeMillis();

        long createdAt = result.createdAtMillis();
        assertTrue(createdAt >= beforeCreation);
        assertTrue(createdAt <= afterCreation);
    }

}
