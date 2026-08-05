/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.upload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.nuxeo.rest.document.DocumentEvent;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Releases the blob storage of uploads consumed by the current request, after a successful transaction observing
 * {@link DocumentEvent}. The upload entries themselves are kept alive (with their parameters: status, attachStatus,
 * attachDocId, ...) so clients can poll the upload info endpoint to observe the attach phase outcome. They will
 * eventually be cleaned up by the underlying transient store.
 */
@Component
@Scope("prototype")
public class UploadHandlerCleaner {

    static ThreadLocal<Set<String>> uploadIdContainer = ThreadLocal.withInitial(HashSet::new);

    private final UploadHandler handler;

    public UploadHandlerCleaner(UploadHandler handler) {
        this.handler = handler;
    }

    public void add(String id) {
        uploadIdContainer.get().add(id);
    }

    public void cleanAll() {
        uploadIdContainer.get().clear();
    }

    public Set<String> get() {
        return Collections.unmodifiableSet(uploadIdContainer.get());
    }

    @EventListener(DocumentEvent.class)
    public void onSuccessfulTransaction(DocumentEvent docEvent) {
        uploadIdContainer.get()
                         .stream()
                         .map(handler::getUpload)
                         .filter(Objects::nonNull)
                         .forEach(Upload::releaseBlobs);
        uploadIdContainer.get().clear();
    }

}
