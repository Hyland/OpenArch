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
package com.hyland.openarch.rest.download.test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.servlet.http.HttpServletRequest;

import com.hyland.openarch.ecm.core.blob.BlobService;
import com.hyland.openarch.ecm.core.blob.LocalBlobProvider;
import com.hyland.openarch.ecm.core.blob.ManagedBlob;

/**
 * Simple Url provider based on the LocalBlobProvider used in the DownloadApi tests. It returns a dummy S3 URI for the
 * requested Blob in order to not break the request to the Download pre-signed url service.
 */
public class PresignedUrlProvider extends LocalBlobProvider {

    @Override
    public URI getURI(
            ManagedBlob blob, BlobService.UsageHint hint, long expirationSeconds, HttpServletRequest servletRequest)
            throws IOException {
        return getURI(blob, hint, expirationSeconds, false, servletRequest);
    }

    @Override
    public URI getURI(
            ManagedBlob blob, BlobService.UsageHint hint, long expirationSeconds, boolean inline,
            HttpServletRequest servletRequest) throws IOException {
        try {
            // the received "inline" flag is reflected in the dummy URL so REST-level tests can assert on it
            return new URI(
                    "https://s3.amazonaws.com/your-bucket/your-object?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                            "&X-Amz-Credential=dummy-credential&X-Amz-Date=dummy-date&X-Amz-SignedHeaders=host" +
                            "&X-Amz-Signature=dummy-signature&X-Amz-Expires=3600&inline=" + inline);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
