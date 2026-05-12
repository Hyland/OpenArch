/*
 * (C) Copyright 2022 Hyland (http://nuxeo.com/) and others.
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
 *     Dawid Dombek
 */
package org.nuxeo.ecm.core.blob;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.jupiter.api.Test;

public class TestSimpleManagedBlob {

    @Test
    public void testBlobProviderIdFromKey() {
        final String KEY = "foobar";
        assertThrows(IllegalArgumentException.class, () -> SimpleManagedBlob.blobProviderIdFromKey(KEY));
    }

    @Test
    public void testSetLength() {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey("foo:bar");
        SimpleManagedBlob simpleManagedBlob = new SimpleManagedBlob(blobInfo);
        simpleManagedBlob.setLength(5);
        assertEquals(5, simpleManagedBlob.getLength());
    }
}
