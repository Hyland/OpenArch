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
package com.hyland.openarch.rest.vocabulary.exception;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import jakarta.ws.rs.core.Response;

import com.hyland.openarch.ecm.core.api.PathRef;

public class VocabularyEntryNotFoundException extends RepositoryException {
    public VocabularyEntryNotFoundException(PathRef entryKey) {
        super(String.format("VocabularyEntry of entry=[%s] not found.", entryKey.toString()),
                Response.Status.NOT_FOUND.getStatusCode());
    }
}
