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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;

class ReplayableFailureRegistryTest {

    private static final String MESSAGE = "boom";

    @Test
    void repositoryExceptionItself_isRebuilt() {
        Exception rebuilt = ReplayableFailureRegistry.rebuild(RepositoryException.class.getName(), MESSAGE, 403);
        assertInstanceOf(RepositoryException.class, rebuilt);
        assertSame(RepositoryException.class, rebuilt.getClass());
        assertEquals(MESSAGE, rebuilt.getMessage());
        assertEquals(403, ((RepositoryException) rebuilt).getStatusCode());
    }

    @Test
    void unregisteredType_fallsBackToRepositoryException() {
        Exception rebuilt = ReplayableFailureRegistry.rebuild(IllegalArgumentException.class.getName(), MESSAGE,
                500);
        assertInstanceOf(RepositoryException.class, rebuilt);
        assertSame(RepositoryException.class, rebuilt.getClass());
        assertEquals(500, ((RepositoryException) rebuilt).getStatusCode());
    }

    @Test
    void unknownClassName_fallsBackWithoutThrowing() {
        Exception rebuilt = ReplayableFailureRegistry.rebuild("com.example.NotAClassAtAll", MESSAGE, 418);
        assertInstanceOf(RepositoryException.class, rebuilt);
        assertEquals(418, ((RepositoryException) rebuilt).getStatusCode());
    }

    @Test
    void nullClassName_fallsBackWithoutThrowing() {
        Exception rebuilt = ReplayableFailureRegistry.rebuild(null, MESSAGE, 500);
        assertInstanceOf(RepositoryException.class, rebuilt);
        assertNotNull(rebuilt);
        assertSame(RepositoryException.class, rebuilt.getClass());
        assertEquals(500, ((RepositoryException) rebuilt).getStatusCode());
    }

    @Test
    void registeredType_rebuiltViaCustomFactory() {
        ReplayableFailureRegistry.register(CustomRepositoryException.class, CustomRepositoryException::new);

        Exception rebuilt = ReplayableFailureRegistry.rebuild(CustomRepositoryException.class.getName(), MESSAGE,
                409);
        assertInstanceOf(CustomRepositoryException.class, rebuilt);
        assertEquals(MESSAGE, rebuilt.getMessage());
        assertEquals(409, ((RepositoryException) rebuilt).getStatusCode());
    }

    private static final class CustomRepositoryException extends RepositoryException {

        CustomRepositoryException(String message, int statusCode) {
            super(message, statusCode);
        }

    }

}
