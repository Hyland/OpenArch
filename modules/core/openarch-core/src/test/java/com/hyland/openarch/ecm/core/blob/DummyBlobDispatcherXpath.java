/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.model.InternalDocument;

/**
 * Dummy blob dispatcher that stores files/0/file in a second blob provider.
 */
public class DummyBlobDispatcherXpath extends DummyBlobDispatcher {

    protected static final String XPATH = "files/0/file";

    @Override
    public BlobDispatch getBlobProvider(InternalDocument doc, Blob blob, String xpath) {
        String provider = XPATH.equals(xpath) ? secondProvider : defaultProvider;
        return new BlobDispatch(provider, true);
    }

}
