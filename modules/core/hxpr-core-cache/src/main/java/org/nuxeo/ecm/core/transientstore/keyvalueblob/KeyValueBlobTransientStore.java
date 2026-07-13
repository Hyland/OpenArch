/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.transientstore.keyvalueblob;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobService;
import org.nuxeo.ecm.core.blob.BlobServiceImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.binary.BinaryGarbageCollector;
import org.nuxeo.ecm.core.transientstore.api.MaximumTransientSpaceExceeded;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreConfig;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreProvider;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.kv.KeyValueStoreProvider;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Transient Store storing properties in a Key/Value store, and storing blobs using a Blob Provider.
 * <p>
 * This transient store is configured with the following properties:
 * <ul>
 * <li><em>keyValueStore</em>: the name of the key/value store to use. If not provided, it defaults to "transient_" +
 * the transient store name.
 * <li><em>blobProvider</em>: the name of the blob provider to use. If not provided, it defaults to "transient_" + the
 * transient store name.
 * <li><em>defaultBlobProvider</em>: if the configured or defaulted blob provider doesn't exist, a namespaced copy of
 * this one will be used instead. The default is "default".
 * </ul>
 * <p>
 * The storage format is the following:
 *
 * <pre>
 *   __blobsize__:       storage size; because entries may expire without us being notified due to their TTL,
 *                       this may be higher than the actual storage size
 *
 *   entryKey.completed: "true" if completed, "false" if not; presence of this key marks entry existence
 *
 *   entryKey.params:    {"foo": "bar", "chunked": true, "partialFileSize": 1048576}
 *                       single JSON object holding all parameters as native JSON
 *                       values. Supported value types are String, Boolean, Integer,
 *                       and Long; see {@link #setNodeValue} for the contract.
 *                       Read and written via Jackson's tree model (ObjectNode) to
 *                       keep the codec non-polymorphic; see {@link #readParamsTree}
 *                       for why. Entries created on pre-{@code DOT_PARAMS} code
 *                       are read via a fallback path (see {@link #getParameter}
 *                       and {@link #getParameters}); the fallback can be removed
 *                       after one {@link TransientStoreConfig#getFirstLevelTTL}
 *                       window has passed since the deploy.
 *
 *   entryKey.bloblock:  "true" if there is a blob read/write in progress, null otherwise
 *   entryKey.blobinfo:  {"count": number of blobs,
 *                        "size": storage size of the blobs}
 *   entryKey.blob.0:    {"key": key in blob provider for first blob,
 *                        "mimeType": MIME Type,
 *                        "encoding": encoding,
 *                        "filename": filename,
 *                        "digest": digest}
 *   entryKey.blob.1:    {...} same for second blob
 *   etc.
 * </pre>
 *
 * @since 9.3
 */
public class KeyValueBlobTransientStore implements TransientStoreProvider {

    private static final Logger log = LogManager.getLogger(KeyValueBlobTransientStore.class);

    public static final String SEP = ".";

    public static final String STORAGE_SIZE = "__blobsize__";

    public static final String DOT_COMPLETED = SEP + "completed";

    /** Per-upload params bag holding all parameters as native JSON values, see class Javadoc for the layout. @since 0.0.68 */
    public static final String DOT_PARAMS = SEP + "params";

    /** Legacy per-upload params index list ({@code ["foo", "bar"]}). Read-only fallback for entries created before {@link #DOT_PARAMS}; never written by current code. */
    public static final String DOT_PARAMINFO = SEP + "paraminfo";

    /** Legacy per-parameter key prefix ({@code <id>.param.<name>}). Read-only fallback for entries created before {@link #DOT_PARAMS}; never written by current code. */
    public static final String DOT_PARAM_DOT = SEP + "param" + SEP;

    /** Legacy per-parameter format suffix; only {@link #FORMAT_JAVA} appears in practice. Read-only fallback. */
    public static final String FORMAT = "__format";

    /** Legacy format marker used by the pre-{@link #DOT_PARAMS} layout for non-String values stored as a Java-serialised {@code byte[]}. Read-only fallback. */
    public static final String FORMAT_JAVA = "java";

    /** @since 11.1 */
    public static final String DOT_BLOBLOCK = SEP + "bloblock";

    public static final String DOT_BLOBINFO = SEP + "blobinfo";

    public static final String COUNT = "count";

    public static final String SIZE = "size";

    public static final String DOT_BLOB_DOT = SEP + "blob" + SEP;

    public static final String CONFIG_KEY_VALUE_STORE = "keyValueStore";

    public static final String CONFIG_BLOB_PROVIDER = "blobProvider";

    /** @since 11.1 */
    public static final String CONFIG_DEFAULT_BLOB_PROVIDER = "defaultBlobProvider";

    /** @since 11.1 */
    public static final String CONFIG_DEFAULT_BLOB_PROVIDER_DEFAULT = "default";

    /** @since 11.1 */
    protected static final int BLOB_LOCK_TTL = 60; // don't keep any lock longer than 60s

    /** @since 11.1 */
    protected static final long LOCK_ACQUIRE_TIME_NANOS = TimeUnit.SECONDS.toNanos(5);

    /** @since 11.1 */
    protected static final long LOCK_EXPONENTIAL_BACKOFF_AFTER_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    protected String name;

    protected String keyValueStoreName;

    protected String blobProviderId;

    protected String defaultBlobProviderId;

    /** Basic TTL for all entries. */
    protected int ttl;

    /** TTL used to keep objects around a bit longer if there's space for them, for caching. */
    protected int releaseTTL;

    protected long targetMaxSize;

    protected long absoluteMaxSize;

    protected ObjectMapper mapper;

    // ---------- TransientStoreProvider ----------

    @Override
    public void init(TransientStoreConfig config) {
        name = config.getName();
        String defaultName = name;
        if (!defaultName.startsWith(BlobServiceImpl.TRANSIENT_ID_PREFIX)) {
            defaultName = BlobServiceImpl.TRANSIENT_ID_PREFIX + "_" + defaultName;
        }
        Map<String, String> properties = config.getProperties();
        if (properties == null) {
            properties = Collections.emptyMap();
        }
        keyValueStoreName = defaultIfBlank(properties.get(CONFIG_KEY_VALUE_STORE), defaultName);
        blobProviderId = defaultIfBlank(properties.get(CONFIG_BLOB_PROVIDER), defaultName);
        defaultBlobProviderId = defaultIfBlank(properties.get(CONFIG_DEFAULT_BLOB_PROVIDER),
                CONFIG_DEFAULT_BLOB_PROVIDER_DEFAULT);
        mapper = new ObjectMapper();
        ttl = config.getFirstLevelTTL() * 60;
        releaseTTL = config.getSecondLevelTTL() * 60;
        targetMaxSize = config.getTargetMaxSizeMB() * 1024L * 1024;
        absoluteMaxSize = config.getAbsoluteMaxSizeMB() * 1024L * 1024;
    }

    protected KeyValueStore getKeyValueStore() {
        return Framework.getService(KeyValueService.class).getKeyValueStore(keyValueStoreName);
    }

    public BlobProvider getBlobProvider() {
        BlobProvider blobProvider = Framework.getService(BlobService.class)
                                             .getBlobProviderWithNamespace(blobProviderId, defaultBlobProviderId);
        if (blobProvider == null) {
            throw new NuxeoException("No blob provider with id '%s'".formatted(blobProviderId));
        }
        if (!blobProvider.isTransient()) {
            throw new NuxeoException("Blob provider: " + blobProviderId + " used for Key/Value store: "
                    + keyValueStoreName + " must be configured as transient");
        }
        return blobProvider;
    }

    @Override
    public void shutdown() {
        // nothing to do
    }

    @Override
    public Stream<String> keyStream() {
        KeyValueStoreProvider kvs = (KeyValueStoreProvider) getKeyValueStore();
        int len = DOT_COMPLETED.length();
        return kvs.keyStream() //
                  .filter(key -> key.endsWith(DOT_COMPLETED))
                  .map(key -> key.substring(0, key.length() - len));
    }

    @Override
    public long getStorageSize() {
        KeyValueStore kvs = getKeyValueStore();
        // getLong accepts both native Long and stringified digits (see
        // MongoDBKeyValueStore.toLong), so historic entries written by
        // pre-fix computeStorageSize remain readable until the next GC
        // rewrites them.
        Long size = kvs.getLong(STORAGE_SIZE);
        return size == null ? 0 : size;
    }

    /**
     * Atomically adjusts the {@code __blobsize__} accumulator by {@code delta}.
     * <p>
     * Uses {@link KeyValueStore#addAndGet} (native MongoDB / DocumentDB {@code $inc})
     * rather than a read-then-CAS loop, which would otherwise contend on this
     * single key under concurrent {@link #putBlobs} / {@link #removeBlobs} load.
     * <p>
     * DocumentDB (and MongoDB) reject {@code $inc} on a non-numeric field with
     * error code 14 / 16837, and {@link org.nuxeo.ecm.core.mongodb.kv.MongoDBKeyValueStore}
     * falls back to a CAS loop in that case. To keep the fast path, the
     * accumulator must always be stored as a native {@link Long} - see
     * {@link #computeStorageSize()}.
     */
    protected void addStorageSize(long delta, KeyValueStore kvs) {
        kvs.addAndGet(STORAGE_SIZE, delta);
    }

    /**
     * Computes an exact value for the current storage size (sum of all blobs size). THIS METHOD IS COSTLY.
     * <p>
     * Does not take into account blob de-duplication that may be done by the blob provider.
     * <p>
     * Does not take into account blobs that still exist in the blob provider but are not referenced anymore (due to TTL
     * expiration or GC not having been done).
     * <p>
     * Stores the result as a native {@link Long}: MongoDB / DocumentDB {@code $inc}
     * (used by {@link #addStorageSize} on every {@link #putBlobs} / {@link #removeBlobs})
     * requires a numeric field. Writing the size as a string here would trip the
     * subsequent {@code $inc} into a CAS-loop fallback, re-introducing per-key
     * contention on {@code __blobsize__} under concurrent uploads.
     */
    protected void computeStorageSize() {
        KeyValueStore kvs = getKeyValueStore();
        long size = keyStream().mapToLong(key -> getSize(key, kvs)).sum();
        kvs.put(STORAGE_SIZE, Long.valueOf(size));
    }

    @Override
    public void doGC() {
        BlobProvider bp = getBlobProvider();
        BinaryGarbageCollector gc = bp.getBinaryGarbageCollector();
        boolean delete = false;
        gc.start();
        // if a concurrent user of the key/value store adds new keys after this point,
        // it's ok because the GC doesn't delete keys created after GC start
        try {
            keyStream().map(this::getBlobKeys) //
                       .flatMap(Collection::stream)
                       .forEach(gc::mark);
            delete = true;
        } finally {
            // don't delete if there's an exception, but still stop the GC
            gc.stop(delete);
        }
        computeStorageSize();
    }

    @Override
    public void removeAll() {
        KeyValueStoreProvider kvs = (KeyValueStoreProvider) getKeyValueStore();
        kvs.clear();
        doGC();
    }

    // ---------- TransientStore ----------

    protected static final TypeReference<List<String>> LIST_STRING = new TypeReference<List<String>>() {
    };

    protected static final TypeReference<Map<String, String>> MAP_STRING_STRING = new TypeReference<Map<String, String>>() {
    };

    /**
     * Reads the per-upload params bag as a Jackson tree.
     * <p>
     * Uses {@link ObjectNode} rather than {@code Map<String, Object>} to keep the
     * read/write codec non-polymorphic: the latter routes through Jackson's
     * {@code UntypedObjectSerializer} / {@code UntypedObjectDeserializer}, which
     * dispatches on the runtime type of every value and compounds badly with the
     * {@link #atomicUpdate} CAS retry loop under concurrent uploads.
     * <p>
     * Returns {@code null} when the entry is absent or holds malformed JSON;
     * callers handle that as a missing entry.
     *
     * @since 0.0.68
     */
    @SuppressWarnings("java:S1168")
    protected ObjectNode readParamsTree(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (node.isObject()) {
                return (ObjectNode) node;
            }
            log.warn("Expected JSON object for params, got {}: {}", node.getNodeType(), json);
            return null;
        } catch (JacksonException e) {
            log.error("Invalid params JSON: {}", json);
            return null;
        }
    }

    /**
     * Converts a Jackson value node back to the {@code Object} shape callers of
     * {@link TransientStore#getParameter} expect. Mirrors the value types
     * {@link #setNodeValue} accepts; numeric nodes are always returned as
     * {@code Long} so callers can cast to {@code Long} without first checking
     * whether Jackson chose {@code IntNode} for values that fit in 32 bits.
     *
     * @since 0.0.68
     */
    protected Object nodeToObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        return node.toString();
    }

    /**
     * Writes a parameter value into the per-upload {@link ObjectNode}.
     * <p>
     * Supported value types are {@code String}, {@code Boolean}, {@code Integer},
     * and {@code Long} — these are the four types production writers pass and
     * the four with unambiguous JSON encodings. {@code Integer} is widened to
     * {@code Long} on read (see {@link #nodeToObject}). {@code null} is stored
     * as a JSON null. Any other type throws {@link IllegalArgumentException}
     * at write time: complex objects (Maps, Lists, byte arrays, POJOs) are not
     * supported because round-tripping them via Jackson's polymorphic codec
     * re-introduces the runtime-type-dispatch cost {@link #readParamsTree}
     * avoids. A future caller that needs an additional type should add it
     * explicitly here rather than route through a polymorphic codec.
     *
     * @since 0.0.68
     */
    protected void setNodeValue(ObjectNode params, String name, Object value) {
        switch (value) {
            case null -> params.putNull(name);
            case String s -> params.put(name, s);
            case Boolean b -> params.put(name, b);
            case Integer i -> params.put(name, i);
            case Long l -> params.put(name, l);
            default -> throw new IllegalArgumentException("Unsupported parameter type for '" + name + "': "
                    + value.getClass().getName() + ". Supported: String, Boolean, Integer, Long.");
        }
    }

    protected List<String> jsonToList(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, LIST_STRING);
        } catch (JacksonException e) {
            log.error("Invalid JSON array: {}", json);
            return null;
        }
    }

    protected Map<String, String> jsonToMap(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, MAP_STRING_STRING);
        } catch (JacksonException e) {
            log.error("Invalid JSON object: {}", json);
            return null;
        }
    }

    protected String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new NuxeoException(e);
        }
    }

    protected void atomicUpdate(String key, Function<String, String> updateFunction, long ttl, KeyValueStore kvs) {
        for (;;) {
            String oldValue = kvs.getString(key);
            String newValue = updateFunction.apply(oldValue);
            if (kvs.compareAndSet(key, oldValue, newValue, ttl)) {
                break;
            }
        }
    }

    @Override
    public boolean exists(String key) {
        KeyValueStore kvs = getKeyValueStore();
        return kvs.getString(key + DOT_COMPLETED) != null;
    }

    protected void markEntryExists(String key, KeyValueStore kvs) {
        kvs.compareAndSet(key + DOT_COMPLETED, null, "false", ttl);
    }

    @Override
    public void putParameter(String key, String parameter, Object value) {
        // singletonMap (not Map.of) because null values are valid here:
        // setNodeValue stores them as JSON null. Map.of(k, null) throws NPE.
        putParameters(key, Collections.singletonMap(parameter, value));
    }

    @Override
    public void putParameters(String key, Map<String, Object> parameters) {
        if (parameters.isEmpty()) {
            return;
        }
        KeyValueStore kvs = getKeyValueStore();
        // All entries land in DOT_PARAMS via a single atomicUpdate: a caller passing
        // N entries pays one CAS round-trip, not N. Callers that update multiple
        // params in one logical step (Upload.addChunk, DefaultUploadHandler.newUpload)
        // batch through here to avoid serialising on the per-upload params doc.
        atomicUpdate(key + DOT_PARAMS, json -> {
            ObjectNode params = readParamsTree(json);
            if (params == null) {
                params = mapper.createObjectNode();
            }
            for (Map.Entry<String, Object> e : parameters.entrySet()) {
                setNodeValue(params, e.getKey(), e.getValue());
            }
            return toJson(params);
        }, ttl, kvs);
        markEntryExists(key, kvs);
    }

    @Override
    public Object getParameter(String key, String parameter) {
        KeyValueStore kvs = getKeyValueStore();
        ObjectNode params = readParamsTree(kvs.getString(key + DOT_PARAMS));
        if (params != null) {
            return nodeToObject(params.get(parameter));
        }
        // Fallback: legacy per-key layout. Used for entries created on
        // pre-DOT_PARAMS code; phased out as TTL expires after the deploy.
        String legacyKey = key + DOT_PARAM_DOT + parameter;
        String legacyFormat = kvs.getString(legacyKey + FORMAT);
        if (legacyFormat == null) {
            return kvs.getString(legacyKey);
        }
        byte[] bytes = kvs.get(legacyKey);
        return deserializeJava(bytes);
    }

    /** @since 0.0.68 */
    protected Object deserializeJava(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public Map<String, Object> getParameters(String key) {
        KeyValueStore kvs = getKeyValueStore();
        ObjectNode params = readParamsTree(kvs.getString(key + DOT_PARAMS));
        if (params != null) {
            Map<String, Object> map = new HashMap<>(params.size());
            for (Map.Entry<String, JsonNode> e : params.properties()) {
                Object v = nodeToObject(e.getValue());
                if (v != null) {
                    map.put(e.getKey(), v);
                }
            }
            return map;
        }
        // Fallback: legacy paraminfo + per-key reads. Used for entries created
        // on pre-DOT_PARAMS code; phased out as TTL expires after the deploy.
        List<String> legacyNames = jsonToList(kvs.getString(key + DOT_PARAMINFO));
        if (legacyNames == null) {
            // Entry doesn't exist at all -> null; entry exists but has no params -> empty map.
            return kvs.getString(key + DOT_COMPLETED) == null ? null : Collections.emptyMap();
        }
        Map<String, Object> map = new HashMap<>(legacyNames.size());
        for (String p : legacyNames) {
            Object value = getParameter(key, p);
            if (value != null) {
                map.put(p, value);
            }
        }
        return map;
    }

    protected void removeParameters(String key, KeyValueStore kvs) {
        kvs.put(key + DOT_PARAMS, (String) null);
        // Also clean up legacy per-key entries if present. Skip the lookup
        // when no legacy paraminfo key exists to keep the common-case cost
        // at one read.
        String legacyJson = kvs.getString(key + DOT_PARAMINFO);
        if (legacyJson != null) {
            List<String> parameters = jsonToList(legacyJson);
            if (parameters != null) {
                for (String parameter : parameters) {
                    String k = key + DOT_PARAM_DOT + parameter;
                    kvs.put(k, (String) null);
                    kvs.put(k + FORMAT, (String) null);
                }
            }
            kvs.put(key + DOT_PARAMINFO, (String) null);
        }
    }

    @Override
    public void putBlobs(String key, List<Blob> blobs) {
        if (absoluteMaxSize > 0 && getStorageSize() > absoluteMaxSize) {
            // do the costly computation of the exact storage size if needed
            doGC();
            if (getStorageSize() > absoluteMaxSize) {
                throw new MaximumTransientSpaceExceeded();
            }
        }

        // first, outside the lock
        // store the blobs, and compute the total size and the blob maps
        BlobProvider bp = getBlobProvider();
        long totalSize = 0;
        List<String> blobMapJsons = new ArrayList<>();
        for (Blob blob : blobs) {
            long size = blob.getLength();
            if (size >= 0) {
                totalSize += size;
            }
            // store blob
            String blobKey;
            try {
                blobKey = bp.writeBlob(blob);
            } catch (IOException e) {
                throw new NuxeoException(e);
            }
            // compute blob data
            Map<String, String> blobMap = new HashMap<>();
            blobMap.put(KEY_BLOB_KEY, blobKey);
            blobMap.put(KEY_BLOB_MIME_TYPE, blob.getMimeType());
            blobMap.put(KEY_BLOB_ENCODING, blob.getEncoding());
            blobMap.put(KEY_BLOB_FILENAME, blob.getFilename());
            blobMap.put(KEY_BLOB_LENGTH, String.valueOf(size));
            blobMap.put(KEY_BLOB_DIGEST, blob.getDigest());
            String blobMapJson = toJson(blobMap);
            blobMapJsons.add(blobMapJson);
        }
        Map<String, String> blobInfoMap = new HashMap<>();
        blobInfoMap.put(COUNT, String.valueOf(blobs.size()));
        blobInfoMap.put(SIZE, String.valueOf(totalSize));
        String blobInfoMapJson = toJson(blobInfoMap);

        // acquire a lock while writing
        KeyValueStore kvs = getKeyValueStore();
        acquireBlobLockOrThrow(key, kvs);
        try {
            // remove previous blobs
            removeBlobs(key, kvs);
            // write new blobs maps
            int i = 0;
            for (String blobMapJson : blobMapJsons) {
                kvs.put(key + DOT_BLOB_DOT + i, blobMapJson, ttl);
                i++;
            }
            // write blob info
            kvs.put(key + DOT_BLOBINFO, blobInfoMapJson, ttl);
            addStorageSize(totalSize, kvs);
            markEntryExists(key, kvs);
        } finally {
            releaseBlobLock(key, kvs);
        }
    }

    protected void removeBlobs(String key, KeyValueStore kvs) {
        String json = kvs.getString(key + DOT_BLOBINFO);
        Map<String, String> map = jsonToMap(json);
        if (map == null) {
            return;
        }
        String countStr = map.get(COUNT);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        String sizeStr = map.get(SIZE);
        long size = sizeStr == null ? 0 : Long.parseLong(sizeStr);

        // remove blobs
        for (int i = 0; i < count; i++) {
            kvs.put(key + DOT_BLOB_DOT + i, (String) null);
        }
        kvs.put(key + DOT_BLOBINFO, (String) null);
        // fix storage size
        addStorageSize(-size, kvs);
    }

    @Override
    public List<Blob> getBlobs(String key) {
        KeyValueStore kvs = getKeyValueStore();
        BlobProvider bp = getBlobProvider();
        List<String> blobMapJsons = new ArrayList<>();

        // try to acquire a lock but still proceed without the lock (best effort)
        boolean lockAcquired = tryAcquireBlobLock(key, kvs);
        try {
            String info = kvs.getString(key + DOT_BLOBINFO);
            if (info == null) {
                // if the entry doesn't exist at all return null, otherwise empty
                if (kvs.getString(key + DOT_COMPLETED) == null) {
                    return null;
                } else {
                    return Collections.emptyList();
                }
            }
            Map<String, String> blobInfoMap = jsonToMap(info);
            String countStr = blobInfoMap.get(COUNT);
            if (countStr == null) {
                return Collections.emptyList();
            }
            int count = Integer.parseInt(countStr);
            for (int i = 0; i < count; i++) {
                String blobMapJson = kvs.getString(key + DOT_BLOB_DOT + i);
                blobMapJsons.add(blobMapJson);
            }
        } finally {
            if (lockAcquired) {
                releaseBlobLock(key, kvs);
            }
        }

        // compute blobs from read blob maps
        List<Blob> blobs = new ArrayList<>();
        for (String blobMapJson : blobMapJsons) {
            if (blobMapJson == null) {
                // corrupted entry, bail out
                break;
            }
            Map<String, String> blobMap = jsonToMap(blobMapJson);
            String blobKey = blobMap.get(KEY_BLOB_KEY);
            if (blobKey == null) {
                // corrupted entry, bail out
                break;
            }
            String mimeType = blobMap.get(KEY_BLOB_MIME_TYPE);
            String encoding = blobMap.get(KEY_BLOB_ENCODING);
            String filename = blobMap.get(KEY_BLOB_FILENAME);
            String lengthStr = blobMap.get(KEY_BLOB_LENGTH);
            Long length = lengthStr == null ? null : Long.valueOf(lengthStr);
            String digest = blobMap.get(KEY_BLOB_DIGEST);
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.setKey(blobKey);
            blobInfo.setMimeType(mimeType);
            blobInfo.setEncoding(encoding);
            blobInfo.setFilename(filename);
            blobInfo.setLength(length);
            blobInfo.setDigest(digest);
            try {
                Blob blob = bp.readBlob(blobInfo);
                blobs.add(blob);
            } catch (IOException e) {
                // ignore, the blob was removed from the blob provider
                // maybe by a concurrent GC from this transient store
                // or from the blob provider itself (if it's incorrectly shared)
                log.debug("Failed to read blob: {} in blob provider: {} for transient store: {}" , digest, blobProviderId
                        , name);
            }
        }
        return blobs;
    }

    // used by GC
    protected List<String> getBlobKeys(String key) {
        KeyValueStore kvs = getKeyValueStore();
        String info = kvs.getString(key + DOT_BLOBINFO);
        if (info == null) {
            return Collections.emptyList();
        }
        Map<String, String> blobInfoMap = jsonToMap(info);
        String countStr = blobInfoMap.get(COUNT);
        if (countStr == null) {
            return Collections.emptyList();
        }
        int count = Integer.parseInt(countStr);
        List<String> blobKeys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String blobMapJson = kvs.getString(key + DOT_BLOB_DOT + i);
            if (blobMapJson == null) {
                // corrupted entry, bail out
                break;
            }
            Map<String, String> blobMap = jsonToMap(blobMapJson);
            String blobKey = blobMap.get(KEY_BLOB_KEY);
            if (blobKey == null) {
                // corrupted entry, bail out
                break;
            }
            blobKeys.add(blobKey);
        }
        return blobKeys;
    }

    protected void acquireBlobLockOrThrow(String key, KeyValueStore kvs) {
        if (tryAcquireBlobLock(key, kvs)) {
            return;
        }
        throw new NuxeoException("Failed to acquire blob lock for: " + key);
    }

    protected boolean tryAcquireBlobLock(String key, KeyValueStore kvs) {
        return acquireLock(() -> tryAcquireOnceBlobLock(key, kvs));
    }

    protected boolean tryAcquireOnceBlobLock(String key, KeyValueStore kvs) {
        return kvs.compareAndSet(key + DOT_BLOBLOCK, null, "true", BLOB_LOCK_TTL);
    }

    protected void releaseBlobLock(String key, KeyValueStore kvs) {
        kvs.put(key + DOT_BLOBLOCK, (String) null);
    }

    protected boolean acquireLock(BooleanSupplier tryAcquireOnce) {
        long start = System.nanoTime();
        long sleep = 1; // ms
        long elapsed;
        while ((elapsed = System.nanoTime() - start) < LOCK_ACQUIRE_TIME_NANOS) {
            if (tryAcquireOnce.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(sleep);
                if (elapsed > LOCK_EXPONENTIAL_BACKOFF_AFTER_NANOS) {
                    sleep *= 2;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    @Override
    public long getSize(String key) {
        return getSize(key, getKeyValueStore());
    }

    private long getSize(String key, KeyValueStore kvs) {
        String json = kvs.getString(key + DOT_BLOBINFO);
        Map<String, String> map = jsonToMap(json);
        String size;
        if (map == null || (size = map.get(SIZE)) == null) {
            return 0;
        }
        return Long.parseLong(size);
    }

    @Override
    public boolean isCompleted(String key) {
        KeyValueStore kvs = getKeyValueStore();
        String completed = kvs.getString(key + DOT_COMPLETED);
        return Boolean.parseBoolean(completed);
    }

    @Override
    public void setCompleted(String key, boolean completed) {
        KeyValueStore kvs = getKeyValueStore();
        kvs.put(key + DOT_COMPLETED, String.valueOf(completed), ttl);
    }

    protected void removeCompleted(String key, KeyValueStore kvs) {
        kvs.put(key + DOT_COMPLETED, (String) null);
    }

    @Override
    public void release(String key) {
        if (targetMaxSize > 0 && getStorageSize() > targetMaxSize) {
            // do the costly computation of the exact storage size if needed
            doGC();
            if (getStorageSize() > targetMaxSize) {
                remove(key);
                return;
            }
        }
        setReleaseTTL(key);
    }

    // set TTL on all keys for this entry
    protected void setReleaseTTL(String key) {
        KeyValueStore kvs = getKeyValueStore();
        kvs.setTTL(key + DOT_COMPLETED, releaseTTL);
        kvs.setTTL(key + DOT_PARAMS, releaseTTL);
        // Legacy paraminfo + per-parameter keys + their format keys.
        // No-op for entries already using the consolidated layout.
        String legacyJson = kvs.getString(key + DOT_PARAMINFO);
        if (legacyJson != null) {
            List<String> parameters = jsonToList(legacyJson);
            if (parameters != null) {
                parameters.forEach(parameter -> {
                    String k = key + DOT_PARAM_DOT + parameter;
                    kvs.setTTL(k, releaseTTL);
                    kvs.setTTL(k + FORMAT, releaseTTL);
                });
            }
            kvs.setTTL(key + DOT_PARAMINFO, releaseTTL);
        }
        String blobInfoJson = kvs.getString(key + DOT_BLOBINFO);
        Map<String, String> map = jsonToMap(blobInfoJson);
        if (map != null) {
            String countStr = map.get(COUNT);
            int count = countStr == null ? 0 : Integer.parseInt(countStr);
            for (int i = 0; i < count; i++) {
                kvs.setTTL(key + DOT_BLOB_DOT + i, releaseTTL);
            }
        }
        kvs.setTTL(key + DOT_BLOBINFO, releaseTTL);
    }

    @Override
    public void remove(String key) {
        KeyValueStore kvs = getKeyValueStore();
        removeBlobs(key, kvs);
        removeParameters(key, kvs);
        removeCompleted(key, kvs);
    }

}
