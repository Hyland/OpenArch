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
package org.nuxeo.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nuxeo.runtime.api.Framework;

class BlobDeleteScheduledJobTest {

    @Test
    void runDeletesBlobsWhenServiceAvailable() {
        BlobService blobService = mock(BlobService.class);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(BlobService.class)).thenReturn(blobService);

            new BlobDeleteScheduledJob().run();
        }

        verify(blobService).deleteBlobsMarkedForDeletion();
    }

    @Test
    void runIsNoOpWhenServiceUnavailable() {
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(BlobService.class)).thenReturn(null);

            assertDoesNotThrow(() -> new BlobDeleteScheduledJob().run());
        }
    }
}
