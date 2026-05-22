/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Gildas Lefevre
 */
package com.hyland.hxpr.async.tasks.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class DocumentTask extends AbstractTask {

    @JsonInclude(Include.NON_NULL)
    protected String documentId;

    public DocumentTask() {
        // Empty constructor for deserialization
    }

    public DocumentTask(String taskName, String repository, String username, String documentId) {
        super(taskName, repository, username);
        this.documentId = documentId;
    }

    public DocumentTask(String taskName, String repository, String username, String documentId, Long timestamp) {
        super(taskName, repository, username);
        this.documentId = documentId;
        this.timestamp = timestamp;
    }

    public String getDocumentId() {
        return documentId;
    }
}
