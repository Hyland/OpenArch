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
package com.hyland.openarch.rest.document;

/**
 * The result of a create-or-update, carrying the resulting document and whether it was created (HTTP 201) or updated
 * (HTTP 200).
 *
 * @param document the created or updated document
 * @param created {@code true} if a document was created, {@code false} if an existing one was updated
 */
public record UpsertDocumentResult(DocumentDTO document, boolean created) {
}
