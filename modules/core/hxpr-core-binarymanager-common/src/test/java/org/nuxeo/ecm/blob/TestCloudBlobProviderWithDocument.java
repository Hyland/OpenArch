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
package org.nuxeo.ecm.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.blob.BlobService;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.KeyStrategyManaged;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.TransactionalFeature;

/**
 * Base document-level tests for a cloud blob provider, shared by concrete provider implementations (S3, Azure, ...).
 * <p>
 * The shared {@code CloudFile} document type, its {@code cloud-test}/{@code cloud-other} schemas and the blob
 * dispatcher are provided by this bundle's {@code test-cloud-doctype.xml} contribution. Concrete subclasses provide,
 * through their {@code @Features}, a feature that sets up the backend and deploys two blob providers named {@code test}
 * (using a managed key strategy) and {@code other}, to which the {@code cloud-test}/{@code cloud-other} blob properties
 * are dispatched.
 */
@ExtendWith(FeaturesExtension.class)
@Deploy("org.nuxeo.ecm.core.storage.binarymanager.common.tests:OSGI-INF/test-cloud-doctype.xml")
public abstract class TestCloudBlobProviderWithDocument {

    protected static final String CLOUD_DOC_TYPE = "CloudFile";

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected BlobService blobService;

    /**
     * The expected prefix of the actual blob key (as returned by {@code BlobStore.getActualBlobKey}) for the given
     * provider.
     */
    protected abstract String getActualKeyPrefix(String blobProviderId);

    @Test
    public void testStoreToBlobProviderTest() throws IOException {
        testStoreToBlobProvider("test");
    }

    @Test
    public void testStoreToBlobProviderOther() throws IOException {
        testStoreToBlobProvider("other");
    }

    protected ManagedBlob testStoreToBlobProvider(String blobProviderId) throws IOException {
        var blobContent = "A simple blob " + UUID.randomUUID();
        var xpath = "cloud-" + blobProviderId + "_blob";
        var doc = session.createDocumentModel("/", "document", CLOUD_DOC_TYPE);
        doc.setPropertyValue(xpath, Blobs.createBlob(blobContent));
        doc.create();
        session.save();
        txFeature.nextTransaction(); // avoid an error in FulltextExtractorWork -> ManagedBlob

        var blobObject = doc.getPropertyValue(xpath);
        assertTrue(blobObject instanceof ManagedBlob);
        var blob = (ManagedBlob) blobObject;
        var digest = blob.getDigest(); // digest is the key
        assertEquals(blobProviderId, blob.getProviderId());
        assertTrue(blob.getKey().startsWith(blobProviderId + ':'));
        assertEquals(blobContent, blob.getString());
        if (isKeyStrategyManaged(blobProviderId)) {
            assertTrue(blob.getKey().contains("-"));
        } else {
            assertEquals(blobProviderId + ':' + digest, blob.getKey());
        }
        assertEquals(blobContent, blob.getString());
        return blob;
    }

    // NXP-30632
    @Test
    public void testMoveFromBlobProviderTestToBlobProviderOther() throws IOException {
        testMoveFromTestToOther();
    }

    // NXP-30632
    @Test
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.common.tests:OSGI-INF/test-cloud-other-managed.xml")
    public void testMoveFromBlobProviderTestToBlobProviderOtherWithManagedKey() throws IOException {
        testMoveFromTestToOther();
    }

    protected void testMoveFromTestToOther() throws IOException {
        var blobContent = "A simple blob";
        var doc = session.createDocumentModel("/", "document", CLOUD_DOC_TYPE);
        doc.setPropertyValue("cloud-test_blob", Blobs.createBlob(blobContent));
        doc.create();

        var blob = (ManagedBlob) doc.getPropertyValue("cloud-test_blob");
        doc.setPropertyValue("cloud-test_blob", null);
        doc.setPropertyValue("cloud-other_blob", blob);
        session.save();
        txFeature.nextTransaction(); // avoid an error in FulltextExtractorWork -> ManagedBlob

        blob = (ManagedBlob) doc.getPropertyValue("cloud-other_blob");
        assertEquals("other", blob.getProviderId());
        assertTrue(blob.getKey().startsWith("other:"));
        if (isKeyStrategyManaged("other")) {
            assertTrue(blob.getKey().contains("-"));
        } else {
            assertEquals("other:" + blob.getDigest(), blob.getKey());
        }
        assertEquals(blobContent, blob.getString());
    }

    @Test
    public void testGetActualKeyForBlobs() throws IOException {
        ManagedBlob blobTest = testStoreToBlobProvider("test");
        ManagedBlob blobOther = testStoreToBlobProvider("other");
        BlobStoreBlobProvider providerTest = (BlobStoreBlobProvider) blobService.getBlobProvider("test");
        BlobStoreBlobProvider providerOther = (BlobStoreBlobProvider) blobService.getBlobProvider("other");
        String keyProviderTest = providerTest.getStore().getActualBlobKey(blobTest);
        assertTrue(keyProviderTest.startsWith(getActualKeyPrefix("test")) && keyProviderTest.contains("-")); // managed
        assertEquals(getActualKeyPrefix("other") + blobOther.getDigest(),
                providerOther.getStore().getActualBlobKey(blobOther));
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.common.tests:OSGI-INF/test-cloud-other-withuserid.xml")
    public void testStoreToBlobProviderWithUserId() throws IOException {
        ManagedBlob blob = testStoreToBlobProvider("other");
        var provider = Framework.getService(BlobService.class).getBlobProvider("other");
        var status = provider.getStatus(blob);
        assertEquals(DummyLoginFeature.TEST_ADMIN_USER, status.getUserId());
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.common.tests:OSGI-INF/test-cloud-other-withuserid.xml")
    public void testStoreToBlobProviderWithUri() throws IOException {
        ManagedBlob blob = testStoreToBlobProvider("other");
        blob.setFilename("file.txt");
        var provider = Framework.getService(BlobService.class).getBlobProvider("other");
        URI uri = provider.getURI(blob, BlobService.UsageHint.DOWNLOAD, 5000, null);
        assertNotNull(uri);
        assertDownloadUriForOther(uri);
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.common.tests:OSGI-INF/test-cloud-other-withuserid.xml")
    public void testStoreToBlobProviderWithUriInline() throws IOException {
        ManagedBlob blob = testStoreToBlobProvider("other");
        blob.setFilename("file.txt");
        var provider = Framework.getService(BlobService.class).getBlobProvider("other");
        for (boolean inline : List.of(false, true)) {
            URI uri = provider.getURI(blob, BlobService.UsageHint.DOWNLOAD, 5000, inline, null);
            assertNotNull(uri);
            assertContentDispositionInUri(uri, inline);
        }
    }

    /** Asserts that a direct-download URI generated for the {@code other} provider targets the expected storage. */
    protected abstract void assertDownloadUriForOther(URI uri);

    /** Asserts that the generated URI's {@code Content-Disposition} reflects the requested {@code inline} choice. */
    protected void assertContentDispositionInUri(URI uri, boolean inline) {
        String expected = contentDispositionQueryParamName() + "=" + (inline ? "inline" : "attachment");
        assertTrue(uri.toString().contains(expected), uri.toString());
    }

    /** The query parameter carrying the response {@code Content-Disposition} override in a generated download URI. */
    protected abstract String contentDispositionQueryParamName();

    protected boolean isKeyStrategyManaged(String blobProviderId) {
        BlobStoreBlobProvider bp = (BlobStoreBlobProvider) blobService.getBlobProvider(blobProviderId);
        return bp.getKeyStrategy() instanceof KeyStrategyManaged;
    }
}
