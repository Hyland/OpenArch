/*
 * (C) Copyright 2015-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.blob;

import static java.util.stream.Collectors.toSet;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.blob.BlobDispatcher.BlobDispatch;
import org.nuxeo.ecm.core.blob.binary.BinaryGarbageCollector;
import org.nuxeo.ecm.core.blob.binary.BinaryManagerStatus;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalDocument.BlobAccessor;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Implementation of the service managing {@link Blob}s associated to a {@link InternalDocument} or a repository.
 *
 * @since 9.2
 */
public class DocumentBlobServiceImpl extends DefaultComponent implements DocumentBlobService {

    private static final Logger log = LogManager.getLogger(DocumentBlobServiceImpl.class);

    protected static final String XP = "configuration";

    protected static final BlobDispatcher DEFAULT_BLOB_DISPATCHER = new DefaultBlobDispatcher();

    // public for tests
    public BlobDispatcher blobDispatcher;

    protected static final int BINARY_GC_TX_TIMEOUT_SEC = 86_400; // 1 day

    @Override
    public void start() {
        blobDispatcher = this.<BlobDispatcherDescriptor> getRegistryContribution(XP)
                             .map(BlobDispatcherDescriptor::getBlobDispatcher)
                             .orElse(DEFAULT_BLOB_DISPATCHER);
    }

    @Override
    public void stop() throws InterruptedException {
        blobDispatcher = null;
    }

    protected BlobDispatcher getBlobDispatcher() {
        return blobDispatcher;
    }

    protected BlobProvider getBlobProvider(String providerId) {
        return Framework.getService(BlobService.class).getBlobProvider(providerId);
    }

    protected DocumentBlobProvider getDocumentBlobProvider(Blob blob) {
        BlobProvider blobProvider = Framework.getService(BlobService.class).getBlobProvider(blob);
        if (blobProvider instanceof DocumentBlobProvider dbp) {
            return dbp;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link BlobInfo} (coming from the database) contains the blob key, which may or may not be prefixed by a blob
     * provider id.
     */
    @Override
    public Blob readBlob(BlobInfo blobInfo, InternalDocument doc, String xpath) throws IOException {
        return readBlob(blobInfo, doc, xpath, doc.getRepositoryName());
    }

    // helper used while deprecated signature below is kept
    protected Blob readBlob(BlobInfo blobInfo, InternalDocument doc, String xpath, String repositoryName) throws IOException {
        String key = blobInfo.getKey();
        if (key == null) {
            return null;
        }
        BlobProvider blobProvider = getBlobProvider(key, repositoryName);
        if (blobProvider == null) {
            throw new NuxeoException("No registered blob provider for key: " + key);
        }
        return blobProvider.readBlob(new BlobInfoContext(blobInfo, doc, xpath));
    }

    protected BlobProvider getBlobProvider(String key, String repositoryName) {
        int colon = key.indexOf(':');
        String providerId;
        if (colon < 0) {
            // no prefix, use the blob dispatcher to find the blob provider id
            providerId = getBlobDispatcher().getBlobProvider(repositoryName);
        } else {
            // use the prefix as blob provider id
            providerId = key.substring(0, colon);
        }
        return getBlobProvider(providerId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the blob is managed and already uses the provider that's expected for this blob and document, there is no need
     * to recompute a key. Otherwise, go through the blob provider.
     */
    @Override
    public String writeBlob(Blob blob, InternalDocument doc, String xpath) throws IOException {
        if (blob == null) {
            if (SYSFILE_BLOB.equals(xpath) && doc.isUnderRetentionOrLegalHold()) {
                if (!InternalSession.canDeleteUndeletable(NuxeoPrincipal.getCurrent())) {
                    throw new DocumentSecurityException(
                            "Cannot delete blob FROM SysContent " + doc.getId() + ", it is under retention / hold");
                }
            }
            return null;
        }

        BlobDispatch dispatch = null;
        if (blob instanceof ManagedBlob managedBlob) {
            String currentProviderId = managedBlob.getProviderId();
            // is the blob non-transient, so that reusing the key is an option?
            if (!getBlobProvider(currentProviderId).isTransient()) {
                // is it something we don't have to dispatch?
                if (!blobDispatcher.getBlobProviderIds().contains(currentProviderId)) {
                    // not something we have to dispatch, reuse the key
                    return getBlobKeyReplacement(managedBlob);
                }
                dispatch = blobDispatcher.getBlobProvider(doc, blob, xpath);
                if (dispatch.providerId.equals(currentProviderId)) {
                    // same provider, just reuse the key
                    return getBlobKeyReplacement(managedBlob);
                }
            }
        }
        if (dispatch == null) {
            dispatch = blobDispatcher.getBlobProvider(doc, blob, xpath);
        }
        BlobProvider blobProvider = getBlobProvider(dispatch.providerId);
        if (blobProvider == null) {
            throw new NuxeoException("No registered blob provider with id: " + dispatch.providerId);
        }
        if (SYSFILE_BLOB.equals(xpath) && blobProvider.isRecordMode() && doc.isUnderRetentionOrLegalHold()) {
            throw new DocumentSecurityException(
                    "Cannot change blob FROM SysContent " + doc.getId() + ", it is under retention / hold");
        }
        String key = blobProvider.writeBlob(new BlobContext(blob, doc, xpath));
        if (dispatch.addPrefix) {
            key = dispatch.providerId + ':' + key;
        }
        return key;
    }

    /** A key may have been replaced by an async digest computation, use the new one. */
    protected String getBlobKeyReplacement(ManagedBlob blob) {
        String key = blob.getKey();
        String prefix = null;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            prefix = key.substring(0, colon);
            key = key.substring(colon + 1);
        }
        key = Framework.getService(BlobService.class).getBlobKeyReplacement(blob.getProviderId(), key);
        // keep dispatch prefix if there was one originally
        if (prefix != null) {
            key = prefix + ':' + key;
        }
        return key;
    }

    @Override
    public InputStream getConvertedStream(Blob blob, String mimeType, DocumentModel doc) throws IOException {
        DocumentBlobProvider blobProvider = getDocumentBlobProvider(blob);
        if (blobProvider == null) {
            return null;
        }
        return blobProvider.getConvertedStream((ManagedBlob) blob, mimeType, doc);
    }

    protected void freezeVersion(BlobAccessor accessor, InternalDocument doc) {
        Blob blob = accessor.getBlob();
        DocumentBlobProvider blobProvider = getDocumentBlobProvider(blob);
        if (blobProvider == null) {
            return;
        }
        try {
            Blob newBlob = blobProvider.freezeVersion((ManagedBlob) blob, doc);
            if (newBlob != null) {
                accessor.setBlob(newBlob);
            }
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void freezeVersion(InternalDocument doc) {
        // finds all blobs, then ask their providers if there's anything to do on check in
        doc.visitBlobs(accessor -> freezeVersion(accessor, doc));
    }

    @Override
    public void notifyChanges(InternalDocument doc, Set<String> xpaths) {
        getBlobDispatcher().notifyChanges(doc, xpaths);
    }

    @Override
    public void notifyMakeRecord(InternalDocument doc) {
        getBlobDispatcher().notifyMakeRecord(doc);
    }

    @Override
    public void notifyAfterCopy(InternalDocument doc) {
        getBlobDispatcher().notifyAfterCopy(doc);
    }

    @Override
    public void notifyBeforeRemove(InternalDocument doc) {
        getBlobDispatcher().notifyBeforeRemove(doc);
    }

    @Override
    public void notifySetRetainUntil(InternalDocument doc, Instant retainUntil) {
        updateBlob(doc, context -> context.withUpdateRetainUntil(retainUntil));
    }

    @Override
    public void notifySetLegalHold(InternalDocument doc, boolean hold) {
        updateBlob(doc, context -> context.withUpdateLegalHold(hold));
    }

    public void updateBlob(InternalDocument doc, Consumer<BlobUpdateContext> contextFiller) {
        ManagedBlob blob = getMainBlob(doc);
        if (blob == null) {
            return;
        }
        BlobProvider blobProvider = Framework.getService(BlobService.class).getBlobProvider(blob);
        if (blobProvider == null) {
            log.error("No blob provider found for blob: {}", blob::getKey);
            return;
        }
        if (!blobProvider.isRecordMode()) {
            log.debug("Blob provider of blob: {} is not in record mode", blob.getKey());
            return;
        }
        try {
            String key = stripBlobKeyPrefix(blob.getKey());
            BlobUpdateContext blobUpdateContext = new BlobUpdateContext(key);
            contextFiller.accept(blobUpdateContext);
            blobProvider.updateBlob(blobUpdateContext);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    protected ManagedBlob getMainBlob(InternalDocument doc) {
        Blob blob;
        try {
            blob = (Blob) doc.getValue(SYSFILE_BLOB);
        } catch (PropertyNotFoundException | ClassCastException e) {
            // not a standard file schema
            return null;
        }
        if (blob == null) {
            // no blob in this document
            return null;
        }
        if (blob instanceof ManagedBlob managedBlob) {
            return managedBlob;
        }
        log.error("Blob is not managed: {}", blob);
        return null;
    }

    // TODO restore to version also changes the blob

    protected String stripBlobKeyPrefix(String key) {
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        return key;
    }

    // find which GCs to use
    // only GC the binary managers to which we dispatch blobs
    protected List<BinaryGarbageCollector> getGarbageCollectors() {
        List<BinaryGarbageCollector> gcs = new LinkedList<>();
        for (String providerId : getBlobDispatcher().getBlobProviderIds()) {
            BlobProvider blobProvider = getBlobProvider(providerId);
            BinaryGarbageCollector gc = blobProvider.getBinaryGarbageCollector();
            if (gc != null) {
                gcs.add(gc);
            }
        }
        return gcs;
    }

    @Override
    public BinaryManagerStatus garbageCollectBinaries(boolean delete) {
        // do the GC in a long-running transaction to avoid timeouts
        return runInTransaction(() -> {
            List<BinaryGarbageCollector> gcs = getGarbageCollectors();
            // check whether two GCs share storage
            Set<String> ids = gcs.stream().map(BinaryGarbageCollector::getId).collect(toSet());
            boolean sharedStorage = ids.size() < gcs.size();
            // start gc
            long start = System.currentTimeMillis();
            for (BinaryGarbageCollector gc : gcs) {
                gc.start();
            }
            BiConsumer<String, String> markerCallback;
            if (sharedStorage) {
                // mark in all GCs, as the blob may be visible from several blob providers
                markerCallback = (key, repositoryName) -> {
                    String skey = stripBlobKeyPrefix(key);
                    gcs.forEach(gc -> gc.mark(skey));
                };
            } else {
                markerCallback = (key, repositoryName) -> {
                    BlobProvider blobProvider = getBlobProvider(key, repositoryName);
                    BinaryGarbageCollector gc = blobProvider.getBinaryGarbageCollector();
                    if (gc != null) {
                        String skey = stripBlobKeyPrefix(key);
                        gc.mark(skey);
                    } else {
                        log.error("Unknown binary manager for key: {}", key);
                    }
                };
            }
            // in all repositories, mark referenced binaries
            RepositoryService repositoryService = Framework.getService(RepositoryService.class);
            for (String repositoryName : repositoryService.getRepositoryNames()) {
                Repository repository = repositoryService.getRepository(repositoryName);
                repository.markReferencedBlobs(markerCallback);
            }
            // stop gc
            BinaryManagerStatus globalStatus = new BinaryManagerStatus();
            for (BinaryGarbageCollector gc : gcs) {
                gc.stop(delete);
                BinaryManagerStatus status = gc.getStatus();
                globalStatus.setNumBinaries(status.getNumBinaries() + status.getNumBinaries());
                globalStatus.setSizeBinaries(status.getSizeBinaries() + status.getSizeBinaries());
                globalStatus.setNumBinariesGC(status.getNumBinariesGC() + status.getNumBinariesGC());
                globalStatus.setSizeBinariesGC(status.getSizeBinariesGC() + status.getSizeBinariesGC());
            }
            globalStatus.setGcDuration(System.currentTimeMillis() - start);
            return globalStatus;
        }, BINARY_GC_TX_TIMEOUT_SEC);
    }

    /**
     * Runs the given {@link Supplier} in a transaction with the given {@code timeout}.
     *
     * @since 11.1
     */
    protected <R> R runInTransaction(Supplier<R> supplier, int timeout) {
        if (TransactionHelper.isTransactionMarkedRollback()) {
            throw new NuxeoException("Cannot run supplier when current transaction is marked rollback.");
        }
        boolean txActive = TransactionHelper.isTransactionActive();
        boolean txStarted = false;
        try {
            if (txActive) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            txStarted = TransactionHelper.startTransaction(timeout);
            return supplier.get();
        } finally {
            if (txStarted) {
                TransactionHelper.commitOrRollbackTransaction();
            }
            if (txActive) {
                // go back to default transaction timeout
                TransactionHelper.startTransaction();
            }
        }
    }

    @Override
    public boolean isBinariesGarbageCollectionInProgress() {
        for (BinaryGarbageCollector gc : getGarbageCollectors()) {
            if (gc.isInProgress()) {
                return true;
            }
        }
        return false;
    }

}
