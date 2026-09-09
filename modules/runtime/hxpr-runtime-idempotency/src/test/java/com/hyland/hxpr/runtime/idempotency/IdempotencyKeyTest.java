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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

@DisplayName("IdempotencyKey tests")
class IdempotencyKeyTest {

    @Test
    @DisplayName("creates key with valid string")
    void testCreateValid() {
        IdempotencyKey key = IdempotencyKey.of("test-key-123");
        assertEquals("test-key-123", key.value());
    }

    @Test
    @DisplayName("throws on null value")
    void testCreateNull() {
        assertThrows(NullPointerException.class, () -> IdempotencyKey.of(null));
    }

    @Test
    @DisplayName("throws on empty value")
    void testCreateEmpty() {
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of(""));
    }

    @Test
    @DisplayName("throws on blank value")
    void testCreateBlank() {
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("   "));
    }

    @Test
    @DisplayName("equals and hashCode work correctly")
    void testEqualsAndHashCode() {
        IdempotencyKey key1 = IdempotencyKey.of("same-key");
        IdempotencyKey key2 = IdempotencyKey.of("same-key");
        IdempotencyKey key3 = IdempotencyKey.of("different-key");

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1.hashCode(), key3.hashCode());
    }

    @Test
    @DisplayName("equals returns true for the same instance")
    void testEqualsSameInstance() {
        IdempotencyKey key = IdempotencyKey.of("same-key");
        assertEquals(key, key);
    }

    @Test
    @DisplayName("equals returns false for a different type or null")
    void testEqualsDifferentTypeOrNull() {
        IdempotencyKey key = IdempotencyKey.of("same-key");
        assertNotEquals("same-key", key);
        assertNotEquals(null, key);
    }

    @Test
    @DisplayName("toString represents key value")
    void testToString() {
        IdempotencyKey key = IdempotencyKey.of("test-key");
        assertEquals("IdempotencyKey{test-key}", key.toString());
    }

}
