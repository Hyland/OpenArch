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
package com.hyland.openarch.ecm.core.blob;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_CONTENT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.repository.RepositoryManager;
import com.hyland.openarch.ecm.core.query.sql.model.FromClause;
import com.hyland.openarch.ecm.core.query.sql.model.Predicates;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

/**
 * Helper class to compute and replace a blob digest.
 *
 * @since 11.5
 */
public class ComputeDigestHelper {

    private static final Logger log = LogManager.getLogger(ComputeDigestHelper.class);

    public final String blobProviderId;

    public final String key;

    public String newKey;

    public String digest;

    public ComputeDigestHelper(String blobProviderId, String key) {
        this.blobProviderId = blobProviderId;
        this.key = key;
    }

    public void computeAndReplaceDigest() {
        BlobService blobService = Framework.getService(BlobService.class);
        BlobProvider blobProvider = blobService.getBlobProvider(blobProviderId);
        if (blobProvider == null) {
            throw new NuxeoException("Unknown blob provider");
        }
        if (!(blobProvider instanceof BlobStoreBlobProvider)) {
            throw new NuxeoException("Invalid blob provider class: " + blobProvider.getClass().getName());
        }
        BlobStore blobStore = ((BlobStoreBlobProvider) blobProvider).getStore();

        // compute the digest
        digest = computeDigest(blobStore);
        if (digest == null) {
            log.debug("Blob with key: {} was not found in blob provider: {}", key, blobProviderId);
            return;
        }
        if (digest.equals(key)) {
            return;
        }

        // copy the blob to its new key based on the digest
        try {
            newKey = blobStore.copyOrMoveBlob(digest, blobStore, key, false);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        // set blob key replacement so that concurrent transactions will use the new key
        blobService.setBlobKeyReplacement(blobProviderId, key, newKey);

        // replace blob key and digest in the repository
        replaceDigestAllRepositories();

        // mark for deletion the blob which is now unused
        // we don't want an immediate delete in case a concurrent process just got hold of its (old) key
        blobService.markBlobForDeletion(blobProviderId, key);
    }

    // unit tested
    protected String computeDigest(BlobStore blobStore) {
        blobStore = blobStore.unwrap(); // we want the low-level version, with no caching
        KeyStrategy keyStrategy = blobStore.getKeyStrategy();
        if (!(keyStrategy instanceof KeyStrategyDigest)) {
            throw new NuxeoException("Invalid key strategy class: " + keyStrategy.getClass().getName());
        }
        String digestAlgorithm = ((KeyStrategyDigest) keyStrategy).digestAlgorithm;

        // you might think that it would be better to stream bytes from the blob store,
        // but it's actually more efficient to read in parallel multipart chunks into a temporary file

        Path tmp = null;
        try {
            tmp = Framework.createTempFilePath("blobdigest_", ".tmp");
            if (!blobStore.readBlob(key, tmp)) {
                return null;
            }
            return new DigestUtils(digestAlgorithm).digestAsHex(tmp.toFile());
        } catch (IOException e) {
            throw new NuxeoException("Failed to download blob", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    log.error(e, e);
                }
            }
        }
    }

    protected void replaceDigestAllRepositories() {
        RepositoryManager repositoryManager = Framework.getService(RepositoryManager.class);
        if (repositoryManager == null) {
            // happens in tests
            return;
        }
        repositoryManager.getRepositoryNames().forEach(this::replaceDigest);
    }

    protected void replaceDigest(String repositoryName) {
        log.debug("Replacing blob key: {} with: {} in repository: {}", key, newKey, repositoryName);
        TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(repositoryName, (CoreSession session) -> replaceDigest(session))); // NOSONAR (two methods with the same name)
    }

    protected void replaceDigest(CoreSession session) {
        // we assume there's a small number of impacted blobs (usually 1), so no paging/batching is done
        // find all documents with this blob
        // we need a variant with the prefixed key if a blob dispatcher is used
        String prefixedKey = blobProviderId + ':' + key;
        String newPrefixedKey = blobProviderId + ':' + newKey;
        Statement query = new Statement(new SelectClause(List.of(new Reference(SYS_ID))),
                new FromClause(List.of(SYS_CONTENT)),
                new WhereClause(Predicates.in(SYS_BLOB_KEYS, List.of(key, prefixedKey))));
        List<String> docIds = new ArrayList<>();
        try (IterableQueryResult it = session.queryAndFetch(query)) {
            it.forEach(map -> docIds.add(map.get(SYS_ID).toString()));
        }
        log.debug("Documents with this blob key: {}", docIds);
        if (docIds.isEmpty()) {
            return;
        }
        // in each document find and process the blob
        docIds.forEach(docId -> {
            session.replaceBlobDigest(new IdRef(docId), key, newKey, digest);
            session.replaceBlobDigest(new IdRef(docId), prefixedKey, newPrefixedKey, digest);
        });
        session.save();
    }

}
