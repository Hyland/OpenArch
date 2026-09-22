/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManagerFeature;
import org.nuxeo.ecm.core.blob.BlobStatus;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.blob.apps.AppLink;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(FeaturesExtension.class)
@Features(BlobManagerFeature.class)
public class TestDummyBlobProvider {

    private final Map<String, String> mapSS = new HashMap<>();

    private final DummyBlobProvider dummyBlobProvider = new DummyBlobProvider();

    private final BlobInfo blobInfo = new BlobInfo();

    private final BlobStatus blobStatus = new BlobStatus();

    @Test
    public void testInitialize() throws IOException {
        mapSS.put("foo", "bar");

        dummyBlobProvider.initialize("foobar", mapSS);

        assertTrue(dummyBlobProvider.blobs.isEmpty());
        assertTrue(dummyBlobProvider.blobsStatus.isEmpty());
        assertTrue(dummyBlobProvider.counter.compareAndSet(new AtomicLong().longValue(), dummyBlobProvider.counter.longValue()));
        assertEquals("foobar", dummyBlobProvider.getBlobProviderId());
        assertEquals(mapSS, dummyBlobProvider.getProperties());
    }

    @Test
    public void testClose() throws IOException {
        mapSS.put("foo", "bar");
        dummyBlobProvider.initialize("foobar", mapSS);

        dummyBlobProvider.close();

        assertTrue(dummyBlobProvider.blobs.isEmpty());
    }

    @Test
    public void testReadBlob() throws IOException {
        blobInfo.setKey("foo:bar");
        dummyBlobProvider.initialize("foobar", mapSS);

        Blob blob = dummyBlobProvider.readBlob(blobInfo);

        assertNotNull(blob);
    }

    @Test
    public void testWriteBlob() throws IOException {
        byte[] bytes = {1, 2, 3};
        blobInfo.setKey("foo:bar");
        dummyBlobProvider.initialize("foobar", mapSS);
        dummyBlobProvider.blobs.put("bar", bytes);
        Blob blob = dummyBlobProvider.readBlob(blobInfo);

        String k = dummyBlobProvider.writeBlob(blob);

        assertEquals(bytes[0], dummyBlobProvider.blobs.get(k)[0]);
        assertEquals(bytes[1], dummyBlobProvider.blobs.get(k)[1]);
        assertEquals(bytes[2], dummyBlobProvider.blobs.get(k)[2]);
    }

    @Test
    public void testGetBlobKeyWithRightFormat() throws IOException {
        blobInfo.setKey("foo:bar");
        dummyBlobProvider.initialize("foobar", mapSS);
        ManagedBlob blob = new SimpleManagedBlob("foobar", blobInfo);

        assertEquals("bar", dummyBlobProvider.getBlobKey(blob));
    }

    @Test
    public void testGetBlobKeyWithoutRightFormat() throws IOException {
        blobInfo.setKey("foobar");
        dummyBlobProvider.initialize("foobar", mapSS);
        ManagedBlob blob = new SimpleManagedBlob("foobar", blobInfo);

        assertEquals("foobar", dummyBlobProvider.getBlobKey(blob));
    }

    @Test
    public void testGetStatus() throws IOException {
        blobInfo.setKey("foo:bar");
        ManagedBlob managedBlob = new SimpleManagedBlob("foobar", blobInfo);
        dummyBlobProvider.initialize("foobar", mapSS);

        dummyBlobProvider.addStatus(managedBlob, blobStatus);

        assertEquals(blobStatus, dummyBlobProvider.getStatus(managedBlob));
    }

    @Test
    public void testAddStatus() throws IOException {
        blobInfo.setKey("foo:bar");
        ManagedBlob managedBlob = new SimpleManagedBlob("foobar", blobInfo);
        dummyBlobProvider.initialize("foobar", mapSS);
        Map<String, BlobStatus> expectedMap = new HashMap<>();
        expectedMap.put("bar", blobStatus);

        dummyBlobProvider.addStatus(managedBlob, blobStatus);

        assertEquals(expectedMap, dummyBlobProvider.blobsStatus);
    }

    @Test
    public void testGetAppLinks() throws IOException {
        blobInfo.setKey("foo:bar");
        ManagedBlob managedBlob = new SimpleManagedBlob("foobar", blobInfo);
        dummyBlobProvider.initialize("foobar", mapSS);

        List<AppLink> list = dummyBlobProvider.getAppLinks("foobar", managedBlob);

        assertEquals(1, list.size());
        assertEquals("dummyApp", list.get(0).appName());
        assertEquals("dummyIcon", list.get(0).icon());
        assertEquals("dummyLink", list.get(0).link());
    }

}
