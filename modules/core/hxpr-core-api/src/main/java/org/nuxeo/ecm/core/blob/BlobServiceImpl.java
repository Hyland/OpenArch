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
package org.nuxeo.ecm.core.blob;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.kv.KeyValueService;
import com.hyland.openarch.runtime.kv.KeyValueStore;
import com.hyland.openarch.runtime.kv.KeyValueStoreProvider;
import com.hyland.openarch.runtime.model.DefaultComponent;

/**
 * Implementation of the service managing the storage and retrieval of {@link Blob}s, through internally-registered
 * {@link BlobProvider}s.
 *
 * @since 7.2
 */
public class BlobServiceImpl extends DefaultComponent implements BlobService {

    private static final Logger log = LogManager.getLogger(BlobServiceImpl.class);

    protected static final String XP = "configuration";

    public static final String DEFAULT_ID = "default";

    /**
     * Blob providers whose id starts with this prefix are automatically marked transient.
     *
     * @since 10.10
     * @see BlobProvider#isTransient
     */
    public static final String TRANSIENT_ID_PREFIX = "transient";

    /** @since 11.5 */
    public static final String BLOB_KEY_REPLACEMENT_KV = "blobKeyReplacement";

    protected static final Duration BLOB_KEY_REPLACEMENT_TTL = Duration.ofHours(1);

    protected static final String BLOB_DELETION_KV = "blobToDelete";

    public static final String BLOB_DELETION_DELAY_PROP = "nuxeo.blobmanager.delete.delay";

    protected static final Duration BLOB_DELETION_DELAY_DEFAULT = Duration.ofHours(1);

    protected boolean blobProvidersInitialized;

    protected Map<String, BlobProvider> blobProviders = new ConcurrentHashMap<>();

    @Override
    public void start() {
        synchronized (this) {
            blobProvidersInitialized = false;
        }
    }

    @Override
    public void stop() throws InterruptedException {
        // close each blob provider
        for (BlobProvider blobProvider : blobProviders.values()) {
            blobProvider.close();
        }
        blobProviders.clear();
        synchronized (this) {
            blobProvidersInitialized = false;
        }
    }

    // public for tests
    public void registerBlobProvider(BlobProviderDescriptor descr) {
        closeOldBlobProvider(descr.name);
        blobProviders.put(descr.name, createBlobProvider(descr));
    }

    // public for tests
    public void unregisterBlobProvider(BlobProviderDescriptor descr) {
        closeOldBlobProvider(descr.name);
        blobProviders.remove(descr.name);
    }

    /**
     * We're about to change something about a contributed blob provider. Close the old one.
     */
    protected synchronized void closeOldBlobProvider(String id) {
        BlobProvider blobProvider = blobProviders.remove(id);
        if (blobProvider != null) {
            blobProvider.close();
        }
    }

    /**
     * Delayed blob provider creation: the blob provider initialization may require a connection that has not been
     * registered yet.
     */
    protected BlobProvider createBlobProvider(BlobProviderDescriptor descr) {
        BlobProvider blobProvider;
        String providerId = descr.name;
        Class<?> klass = descr.klass;
        Map<String, String> properties = descr.properties;
        try {
            if (BlobProvider.class.isAssignableFrom(klass)) {
                @SuppressWarnings("unchecked")
                Class<? extends BlobProvider> blobProviderClass = (Class<? extends BlobProvider>) klass;
                blobProvider = blobProviderClass.getDeclaredConstructor().newInstance();
            } else {
                throw new RuntimeException("Unknown class for blob provider: " + klass);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        // make it transient if needed
        if (providerId.startsWith(TRANSIENT_ID_PREFIX)) {
            descr.properties.put(BlobProviderDescriptor.TRANSIENT, "true");
        }
        try {
            blobProvider.initialize(providerId, properties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return blobProvider;
    }

    @Override
    public synchronized BlobProvider getBlobProvider(String providerId) {
        BlobProvider blobProvider = blobProviders.get(providerId);
        if (blobProvider == null) {
            this.<BlobProviderDescriptor> getRegistryContribution(XP, providerId)
                .ifPresent(descr -> blobProviders.put(providerId, createBlobProvider(descr)));
            return blobProviders.get(providerId);
        }
        return blobProvider;
    }

    @Override
    public synchronized BlobProvider getBlobProviderWithNamespace(String providerId, String defaultId) {
        BlobProvider blobProvider = getBlobProvider(providerId);
        if (blobProvider != null) {
            return blobProvider;
        }
        // create and register a blob provider from the "default" configuration
        BlobProviderDescriptor defaultDescr = this.<BlobProviderDescriptor> getRegistryContribution(XP, defaultId)
                                                  .orElseThrow(() -> new NuxeoException(
                                                          "Missing configuration for blob provider: " + defaultId));
        // copy
        BlobProviderDescriptor descr = new BlobProviderDescriptor(defaultDescr);
        // set new name and namespace
        descr.name = providerId;
        descr.properties.put(BlobProviderDescriptor.NAMESPACE, providerId);
        // register and return it
        registerBlobProvider(descr);
        return getBlobProvider(providerId);
    }

    @Override
    public BlobProvider getBlobProvider(Blob blob) {
        if (!(blob instanceof ManagedBlob managedBlob)) {
            return null;
        }
        return getBlobProvider(managedBlob.getProviderId());
    }

    @Override
    public URI getURI(Blob blob, UsageHint hint, long expirationSeconds, HttpServletRequest servletRequest) throws IOException {
        BlobProvider blobProvider = getBlobProvider(blob);
        if (blobProvider == null) {
            return null;
        }
        return blobProvider.getURI((ManagedBlob) blob, hint, expirationSeconds, servletRequest);
    }

    @Override
    public URI getURI(Blob blob, UsageHint hint, long expirationSeconds, boolean inline, HttpServletRequest servletRequest) throws IOException {
        BlobProvider blobProvider = getBlobProvider(blob);
        if (blobProvider == null) {
            return null;
        }
        return blobProvider.getURI((ManagedBlob) blob, hint, expirationSeconds, inline, servletRequest);
    }

    @Override
    public Map<String, URI> getAvailableConversions(Blob blob, UsageHint hint) throws IOException {
        BlobProvider blobProvider = getBlobProvider(blob);
        if (blobProvider == null) {
            return Collections.emptyMap();
        }
        return blobProvider.getAvailableConversions((ManagedBlob) blob, hint);
    }

    @Override
    public synchronized Map<String, BlobProvider> getBlobProviders() {
        if (!blobProvidersInitialized) {
            this.<BlobProviderDescriptor> getRegistryContributions(XP)
                .stream()
                .filter(desc -> !blobProviders.containsKey(desc.name))
                .forEach(this::registerBlobProvider);
            blobProvidersInitialized = true;
        }
        return Collections.unmodifiableMap(blobProviders);
    }

    protected KeyValueStore getBlobKeyReplacementKeyValuestore() {
        KeyValueService kvService = Framework.getService(KeyValueService.class);
        return kvService == null ? null : kvService.getKeyValueStore(BLOB_KEY_REPLACEMENT_KV);
    }

    @Override
    public void setBlobKeyReplacement(String blobProviderId, String key, String newKey) {
        KeyValueStore kvStore = getBlobKeyReplacementKeyValuestore();
        if (kvStore == null) {
            return;
        }
        kvStore.put(blobProviderId + ':' + key, newKey, BLOB_KEY_REPLACEMENT_TTL.getSeconds());
    }

    @Override
    public String getBlobKeyReplacement(String blobProviderId, String key) {
        KeyValueStore kvStore = getBlobKeyReplacementKeyValuestore();
        if (kvStore == null) {
            return key;
        }
        String newKey = kvStore.getString(blobProviderId + ':' + key);
        return newKey == null ? key : newKey;
    }

    protected KeyValueStore getBlobDeletionKeyValueStore() {
        KeyValueService kvService = Framework.getService(KeyValueService.class);
        return kvService == null ? null : kvService.getKeyValueStore(BLOB_DELETION_KV);
    }

    @Override
    public void markBlobForDeletion(String blobProviderId, String key) {
        KeyValueStore kvStore = getBlobDeletionKeyValueStore();
        if (kvStore == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // to mark a blob for deletion, we store in the key/value store
        // a key which is the blob provider id + ":" + the blob key
        // and a value which is an epoch time of when the marking is done
        kvStore.put(blobProviderId + ':' + key, now);
    }

    @Override
    public void deleteBlobsMarkedForDeletion() {
        KeyValueStoreProvider kvStore = (KeyValueStoreProvider) getBlobDeletionKeyValueStore();
        if (kvStore == null) {
            return;
        }
        Duration delay = Framework.getDurationProperty(BLOB_DELETION_DELAY_PROP, BLOB_DELETION_DELAY_DEFAULT);
        long maxTime = Instant.now().minus(delay).toEpochMilli();
        kvStore.keyStream().forEach(k -> {
            Long v = kvStore.getLong(k);
            if (v == null) {
                // already concurrently processed
                return;
            }
            if (v > maxTime) {
                log.debug(String.format("Blob marked for deletion is not old enough: %s", k));
                return;
            }
            kvStore.put(k, (String) null);
            deleteBlob(k);
        });
    }

    /**
     * Deletes a blob.
     *
     * @param k the blob provider id + ":" + the blob key
     */
    protected void deleteBlob(String k) {
        int pos = k.indexOf(':');
        if (pos == -1) {
            log.debug("Invalid key for blob marked for deletion: %s", k);
            return;
        }
        String blobProviderId = k.substring(0, pos);
        String key = k.substring(pos + 1);

        BlobProvider blobProvider = getBlobProvider(blobProviderId);
        if (blobProvider == null) {
            log.debug("Unknown blob provider for blob marked for deletion: %s", k);
            return;
        }
        if (!(blobProvider instanceof BlobStoreBlobProvider)) {
            log.debug("Invalid blob provider class: %s for blob marked for deletion: %s",
                    blobProvider.getClass().getName(), k);
            return;
        }
        BlobStore blobStore = ((BlobStoreBlobProvider) blobProvider).getStore();
        blobStore.deleteBlob(key);
    }

}
