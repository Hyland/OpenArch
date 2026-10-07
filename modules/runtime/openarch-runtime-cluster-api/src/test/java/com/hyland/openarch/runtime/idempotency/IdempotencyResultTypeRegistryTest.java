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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JavaType;

/**
 * Unit tests for {@link IdempotencyResultTypeRegistry}.
 * <p>
 * The registry is a process-wide singleton and a type may only ever be bound to a single key, so
 * every test uses its own record type to stay independent of the execution order.
 */
class IdempotencyResultTypeRegistryTest {

    @Test
    @DisplayName("a non-generic type is registered and resolved back from its key")
    void register_nonGenericType_isResolvedFromItsKey() {
        IdempotencyResultTypeRegistry.register("registry-simple", SimpleDto.class);

        JavaType resolved = IdempotencyResultTypeRegistry.getType("registry-simple");

        assertEquals(SimpleDto.class, resolved.getRawClass());
        assertEquals("registry-simple", IdempotencyResultTypeRegistry.getKey(SimpleDto.class));
    }

    @Test
    @DisplayName("a list type keeps its element type")
    void registerList_keepsTheElementType() {
        IdempotencyResultTypeRegistry.registerList("registry-list", ListedDto.class);

        JavaType resolved = IdempotencyResultTypeRegistry.getType("registry-list");

        assertEquals(List.class, resolved.getRawClass());
        assertEquals(ListedDto.class, resolved.getContentType().getRawClass());
    }

    @Test
    @DisplayName("registering the same key with the same list type again is a no-op")
    void registerList_sameKeyAndElementType_isANoOp() {
        IdempotencyResultTypeRegistry.registerList("registry-list-idempotent", RelistedDto.class);

        assertDoesNotThrow(
                () -> IdempotencyResultTypeRegistry.registerList("registry-list-idempotent", RelistedDto.class));
    }

    @Test
    @DisplayName("registering the same key with the same type again is a no-op")
    void register_sameKeyAndType_isANoOp() {
        IdempotencyResultTypeRegistry.register("registry-idempotent", IdempotentDto.class);

        assertDoesNotThrow(() -> IdempotencyResultTypeRegistry.register("registry-idempotent", IdempotentDto.class));
    }

    @Test
    @DisplayName("registering a different type under an already used key is rejected")
    void register_keyAlreadyBoundToAnotherType_throws() {
        IdempotencyResultTypeRegistry.register("registry-conflicting-key", BoundDto.class);

        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyResultTypeRegistry.register("registry-conflicting-key", ConflictingDto.class));
    }

    @Test
    @DisplayName("registering an already registered type under another key is rejected")
    void register_typeAlreadyBoundToAnotherKey_throws() {
        IdempotencyResultTypeRegistry.register("registry-first-key", ReboundDto.class);

        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyResultTypeRegistry.register("registry-second-key", ReboundDto.class));
    }

    @Test
    @DisplayName("a list type and its element type are two distinct registrations")
    void registerList_doesNotCollideWithItsElementType() {
        IdempotencyResultTypeRegistry.register("registry-element", ElementDto.class);

        assertDoesNotThrow(
                () -> IdempotencyResultTypeRegistry.registerList("registry-element-list", ElementDto.class));
        assertEquals(ElementDto.class, IdempotencyResultTypeRegistry.getType("registry-element").getRawClass());
    }

    @Test
    @DisplayName("a blank key or a null type is rejected")
    void register_invalidArguments_throw() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotencyResultTypeRegistry.register("  ", InvalidDto.class));
        assertThrows(NullPointerException.class,
                () -> IdempotencyResultTypeRegistry.register("registry-null", null));
        assertThrows(NullPointerException.class,
                () -> IdempotencyResultTypeRegistry.registerList("registry-null", null));
    }

    @Test
    @DisplayName("an unknown key resolves to no type")
    void getType_unknownKey_returnsNull() {
        assertNull(IdempotencyResultTypeRegistry.getType("registry-never-registered"));
    }

    record SimpleDto(String name) {
    }

    record ListedDto(String name) {
    }

    record RelistedDto(String name) {
    }

    record IdempotentDto(String name) {
    }

    record BoundDto(String name) {
    }

    record ConflictingDto(String name) {
    }

    record ReboundDto(String name) {
    }

    record ElementDto(String name) {
    }

    record InvalidDto(String name) {
    }
}
