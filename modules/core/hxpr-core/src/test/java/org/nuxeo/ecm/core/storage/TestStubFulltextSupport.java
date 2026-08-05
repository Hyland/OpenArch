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
package org.nuxeo.ecm.core.storage;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;

/**
 * Shared test stub for FulltextSupport.
 */
public class TestStubFulltextSupport implements FulltextSupport {

    @Override
    public String textFromUrl(InputStream targetInputStream, URL url, int sizeLimit) {
        return "";
    }

    @Override
    public void handlePlainText(String textFromBlob, int sizeLimit, CoreSession session, List<String> docIdsToUpdate) {
        // no-op, stub implementation
    }

    @Override
    public String replace0chars(String string) {
        return string;
    }

    @Override
    public String appendText(String currentText, String text, int sizeLimit) {
        return text;
    }

    @Override
    public List<String> getDocIdsToUpdate(String docIdsToUpdate) {
        return List.of();
    }

    @Override
    public String getDocIdsToUpdateAsString(List<String> docIdsToUpdate) {
        return "";
    }

    @Override
    public String getBlobInfo(Blob blob) {
        return "";
    }
}
