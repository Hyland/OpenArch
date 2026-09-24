/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import com.hyland.openarch.ecm.core.api.RepositoryException;

/**
 * Exception thrown when attempting to start garbage collection that is already
 * running.
 * This is used to gracefully handle concurrent GC attempts.
 *
 * @since 12.0
 */
public class GCAlreadyStartedException extends RepositoryException {

    private static final long serialVersionUID = 1L;

    public GCAlreadyStartedException() {
        super("Garbage collection already started");
    }

    public GCAlreadyStartedException(String message) {
        super(message);
    }
}
