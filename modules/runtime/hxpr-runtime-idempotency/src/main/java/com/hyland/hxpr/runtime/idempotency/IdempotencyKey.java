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

import java.util.Objects;

/**
 * Represents an idempotency key used to deduplicate requests and ensure idempotent operation execution.
 * <p>
 * Idempotency keys should be unique for each operation and remain constant across retries of the same
 * operation, enabling the system to detect and handle duplicate requests safely.
 *
 * @since 12.0
 */
public final class IdempotencyKey {

    private final String value;

    private IdempotencyKey(String value) {
        this.value = Objects.requireNonNull(value, "Idempotency key value cannot be null");
    }

    /**
     * Creates an idempotency key from the given string value.
     *
     * @param value the key value, must not be null or empty
     * @return a new IdempotencyKey instance
     * @throws IllegalArgumentException if value is empty
     */
    public static IdempotencyKey of(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key value cannot be empty");
        }
        return new IdempotencyKey(value);
    }

    /**
     * Gets the string representation of this idempotency key.
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey)) return false;
        IdempotencyKey that = (IdempotencyKey) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "IdempotencyKey{" + value + '}';
    }

}
