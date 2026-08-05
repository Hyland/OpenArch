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
package org.nuxeo.ecm.core.transientstore.keyvalueblob;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Objects;
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
 *                       for why.
 *
 *   entryKey.blob:      {"key": "&lt;key in blob provider&gt;",
 *                        "mimeType": "...",
 *                        "encoding": "...",
 *                        "filename": "...",
 *                        "length": &lt;bytes as native JSON number&gt;,
 *                        "digest": "..."}
 *                       HxPR always stores exactly one blob per entry.
 *                       Entries written before this layout carry a separate {@code entryKey.blob.0}
 *                       key; {@link #getBlob} reads both transparently until old entries expire.
 *                       Entries written by pre-{@code DOT_BLOB} code used {@code entryKey.blobinfo}
 *                       as the document key; {@link #getBlobInfoJson} falls back to that key too.
 *
 *   __blobkey__.*:      one key per blob currently held: {@code __blobkey__.<blobKey>}, empty value,
 *                       same TTL as the entry. Allows {@link #doGC()} to enumerate all live blob keys
 *                       with a single prefix scan. Written in {@link #putBlob}, deleted in
 *                       {@link #removeBlob}.
 * </pre>
 *
 * @since 9.3
 */
public class KeyValueBlobTransientStore implements TransientStoreProvider {

    private static final Logger log = LogManager.getLogger(KeyValueBlobTransientStore.class);

    public static final String SEP = ".";

    public static final String STORAGE_SIZE = "__blobsize__";

    public static final String DOT_COMPLETED = SEP + "completed";

    /** Per-upload params bag; see class Javadoc for the layout. */
    public static final String DOT_PARAMS = SEP + "params";

    public static final String DOT_BLOB = SEP + "blob";

    /** @deprecated Legacy blob-info key ({@code entryKey.blobinfo}). Written by pre-{@code DOT_BLOB} code;
     *  read as a fallback in {@link #getBlobInfoJson}. Remove after one {@link TransientStoreConfig#getFirstLevelTTL} window. */
    @Deprecated(since = "CIN-15767")
    public static final String DOT_BLOBINFO = SEP + "blobinfo";

    /** @deprecated Old size field name in legacy {@code entryKey.blobinfo} documents (pre-flat layout). */
    @Deprecated(since = "CIN-15767")
    protected static final String SIZE = "size";

    /**
     * Prefix for per-blob-key index entries: {@code __blobkey__.<blobKey>}, empty value, same TTL
     * as the entry. A prefix scan on this lets {@link #doGC()} mark all live blobs in one query.
     */
    public static final String BLOBKEY_PREFIX = "__blobkey__" + SEP;

    /** @deprecated Legacy per-blob key ({@code entryKey.blob.0}, …). Written by pre-flat-layout code;
     *  read as a fallback in {@link #getBlob}. Remove after one {@link TransientStoreConfig#getFirstLevelTTL} window. */
    @Deprecated(since = "CIN-15767")
    public static final String DOT_BLOB_DOT = SEP + "blob" + SEP;

    public static final String CONFIG_KEY_VALUE_STORE = "keyValueStore";

    public static final String CONFIG_BLOB_PROVIDER = "blobProvider";

    /** @since 11.1 */
    public static final String CONFIG_DEFAULT_BLOB_PROVIDER = "defaultBlobProvider";

    /** @since 11.1 */
    public static final String CONFIG_DEFAULT_BLOB_PROVIDER_DEFAULT = "default";

    protected String name;

    protected String keyValueStoreName;

    protected String blobProviderId;

    protected String defaultBlobProviderId;

    /** Basic TTL for all entries. */
    protected int ttl;

    /** TTL used to keep objects around a bit longer if there's space for them, for caching. */
    protected int releaseTTL;

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
        Long size = kvs.getLong(STORAGE_SIZE);
        return size == null ? 0 : size;
    }

    /**
     * Atomically adjusts the {@code __blobsize__} accumulator by {@code delta} using
     * {@link KeyValueStore#addAndGet} (native {@code $inc}) to avoid CAS contention
     * under concurrent {@link #putBlob} / {@link #removeBlob} load. The accumulator
     * must always be stored as a native {@link Long} — see {@link #computeStorageSize()}.
     */
    protected void addStorageSize(long delta, KeyValueStore kvs) {
        kvs.addAndGet(STORAGE_SIZE, delta);
    }

    /**
     * Computes an exact value for the current storage size (sum of all blob sizes). THIS METHOD IS COSTLY.
     * <p>
     * Does not account for blob de-duplication or blobs that exist in the provider but are no longer referenced.
     * <p>
     * Stores the result as a native {@link Long} so the {@code $inc} fast path in {@link #addStorageSize}
     * remains available on subsequent calls.
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
        boolean blobStoreNotEmpty = !gc.isToDeleteEmpty();
        try {
            if (blobStoreNotEmpty) {
                // Mark live blobs via the __blobkey__ prefix index (one scan for the whole store).
                // Old entries without index entries are covered by the getBlobKey fallback;
                // remove that fallback after one firstLevelTTL window.
                KeyValueStoreProvider kvs = (KeyValueStoreProvider) getKeyValueStore();
                int prefixLen = BLOBKEY_PREFIX.length();
                kvs.keyStream(BLOBKEY_PREFIX)
                   .map(k -> k.substring(prefixLen))
                   .forEach(gc::mark);
                // Legacy fallback for entries without __blobkey__ index entries.
                keyStream().map(this::getBlobKey)
                           .filter(Objects::nonNull)
                           .forEach(gc::mark);
            }
            delete = true;
        } finally {
            // don't delete if there's an exception, but still stop the GC
            gc.stop(delete);
        }
        if (blobStoreNotEmpty) {
            computeStorageSize();
        }
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

    protected static final TypeReference<Map<String, Object>> MAP_STRING_OBJECT = new TypeReference<Map<String, Object>>() {
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

    /**
     * Deserializes a JSON object to a {@code Map<String, String>}.
     * Used for legacy documents (old {@code .blobinfo}, {@code .blob.0}, params).
     * For the current {@code .blob} document, use {@link #jsonToObjectMap} instead.
     * Remove this method when the legacy fallback code (CIN-16299) is cleaned up.
     */
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

    /**
     * Deserializes a JSON object preserving native value types (numbers stay as {@link Number},
     * strings as {@link String}). Used for the {@code .blob} document where {@code length} is
     * stored as a native JSON number.
     */
    @SuppressWarnings("java:S1168") // null means absent/malformed, not an empty map
    protected Map<String, Object> jsonToObjectMap(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, MAP_STRING_OBJECT);
        } catch (JacksonException e) {
            log.error("Invalid JSON object: {}", json);
            return null;
        }
    }

    /**
     * Extracts the blob length from a blob document map. Handles both the current format
     * (native JSON number → {@link Number}) and the legacy string format (e.g. {@code "102400"}).
     * Returns {@code -1} if the field is absent.
     */
    protected static long extractBlobLength(Map<String, Object> map) {
        Object val = map.get(KEY_BLOB_LENGTH);
        if (val == null) {
            return -1;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        // legacy: stored as string before this change
        return Long.parseLong(val.toString());
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
        return null;
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
        // Entry doesn't exist at all -> null; entry exists but has no params -> empty map.
        return kvs.getString(key + DOT_COMPLETED) == null ? null : Collections.emptyMap();
    }

    protected void removeParameters(String key, KeyValueStore kvs) {
        kvs.put(key + DOT_PARAMS, (String) null);
    }

    @Override
    public void putBlob(String key, Blob blob) {
        if (absoluteMaxSize > 0 && getStorageSize() > absoluteMaxSize) {
            // do the costly computation of the exact storage size if needed
            doGC();
            if (getStorageSize() > absoluteMaxSize) {
                throw new MaximumTransientSpaceExceeded();
            }
        }

        // write new blob and index entries
        KeyValueStore kvs = getKeyValueStore();
        removeBlob(key, kvs);

        if (blob != null) {
            // store blob in the blob provider
            long size = blob.getLength();
            String blobKey;
            try {
                blobKey = getBlobProvider().writeBlob(blob);
            } catch (IOException e) {
                throw new NuxeoException(e);
            }
            // write flat .blob document; length is stored as a native JSON number
            Map<String, Object> flatMap = new HashMap<>();
            flatMap.put(KEY_BLOB_KEY, blobKey);
            flatMap.put(KEY_BLOB_MIME_TYPE, blob.getMimeType());
            flatMap.put(KEY_BLOB_ENCODING, blob.getEncoding());
            flatMap.put(KEY_BLOB_FILENAME, blob.getFilename());
            flatMap.put(KEY_BLOB_LENGTH, size);
            flatMap.put(KEY_BLOB_DIGEST, blob.getDigest());
            kvs.put(key + DOT_BLOB, toJson(flatMap), ttl);
            // write __blobkey__ index entry
            kvs.put(BLOBKEY_PREFIX + blobKey, "", ttl);
            if (size >= 0) {
                addStorageSize(size, kvs);
            }
        }

        markEntryExists(key, kvs);
    }

    /**
     * Reads the blob-info JSON for the given entry key, trying the new {@link #DOT_BLOB} key first
     * and falling back to the legacy {@link #DOT_BLOBINFO} key for entries written by older code.
     * Returns {@code null} if neither key is present.
     */
    @SuppressWarnings("java:S1874") // intentional use of deprecated DOT_BLOBINFO for migration fallback
    protected String getBlobInfoJson(String key, KeyValueStore kvs) {
        String json = kvs.getString(key + DOT_BLOB);
        if (json == null) {
            json = kvs.getString(key + DOT_BLOBINFO); // legacy fallback
        }
        return json;
    }

    protected void removeBlob(String key, KeyValueStore kvs) {
        String json = getBlobInfoJson(key, kvs);
        Map<String, Object> map = jsonToObjectMap(json);
        if (map == null) {
            return;
        }
        // extract length; new layout stores a native number, old layout a string or "size" key
        long size = Math.max(0, extractBlobLength(map));
        if (size == 0) {
            // old layout: try the "size" string field
            Object sizeVal = map.get(SIZE);
            if (sizeVal != null) {
                size = Math.max(0, Long.parseLong(sizeVal.toString()));
            }
        }

        // new flat layout: blob.key inlined in blobinfo
        Object blobKeyVal = map.get(KEY_BLOB_KEY);
        String blobKey = blobKeyVal != null ? blobKeyVal.toString() : null;
        if (blobKey == null) {
            // old layout: blob key in a separate .blob.0 key
            String blobMapJson = kvs.getString(key + DOT_BLOB_DOT + "0");
            if (blobMapJson != null) {
                Map<String, String> blobMap = jsonToMap(blobMapJson);
                if (blobMap != null) {
                    blobKey = blobMap.get(KEY_BLOB_KEY);
                }
            }
        }
        if (blobKey != null) {
            kvs.put(BLOBKEY_PREFIX + blobKey, (String) null);
        }
        kvs.put(key + DOT_BLOB_DOT + "0", (String) null); // remove legacy key if present
        kvs.put(key + DOT_BLOB, (String) null);
        kvs.put(key + DOT_BLOBINFO, (String) null); // remove legacy key if present
        addStorageSize(-size, kvs);
    }

    @Override
    public Blob getBlob(String key) {
        KeyValueStore kvs = getKeyValueStore();
        BlobProvider bp = getBlobProvider();

        String info = getBlobInfoJson(key, kvs);
        if (info == null) {
            return null;
        }
        Map<String, Object> blobInfoMap = jsonToObjectMap(info);
        if (blobInfoMap == null) {
            return null; // malformed JSON — treat as no blob
        }

        // new flat layout: blob metadata inlined
        BlobInfo blobInfo = extractFlatBlobInfo(blobInfoMap);
        if (blobInfo == null) {
            // old layout: metadata in a separate .blob.0 key
            String blobMapJson = kvs.getString(key + DOT_BLOB_DOT + "0");
            if (blobMapJson == null) {
                return null;
            }
            Map<String, String> blobMap = jsonToMap(blobMapJson);
            if (blobMap == null) {
                return null;
            }
            String blobKey = blobMap.get(KEY_BLOB_KEY);
            if (blobKey == null) {
                return null;
            }
            blobInfo = new BlobInfo();
            blobInfo.setKey(blobKey);
            blobInfo.setMimeType(blobMap.get(KEY_BLOB_MIME_TYPE));
            blobInfo.setEncoding(blobMap.get(KEY_BLOB_ENCODING));
            blobInfo.setFilename(blobMap.get(KEY_BLOB_FILENAME));
            String lengthStr = blobMap.get(KEY_BLOB_LENGTH);
            blobInfo.setLength(lengthStr == null ? null : Long.valueOf(lengthStr));
            blobInfo.setDigest(blobMap.get(KEY_BLOB_DIGEST));
        }
        try {
            return bp.readBlob(blobInfo);
        } catch (IOException e) {
            log.debug("Failed to read blob: {} in blob provider: {} for transient store: {}",
                    blobInfo.getDigest(), blobProviderId, name);
            return null;
        }
    }

    /**
     * Extracts a {@link BlobInfo} from a flat {@code .blob} map (new layout).
     * Returns {@code null} if the map uses the old layout (no {@code key} field present).
     */
    @SuppressWarnings("java:S1168") // null is a sentinel meaning "old layout"
    protected BlobInfo extractFlatBlobInfo(Map<String, Object> blobInfoMap) {
        Object blobKeyVal = blobInfoMap.get(KEY_BLOB_KEY);
        if (blobKeyVal == null) {
            return null; // old layout
        }
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.setKey(blobKeyVal.toString());
        Object mimeType = blobInfoMap.get(KEY_BLOB_MIME_TYPE);
        blobInfo.setMimeType(mimeType != null ? mimeType.toString() : null);
        Object encoding = blobInfoMap.get(KEY_BLOB_ENCODING);
        blobInfo.setEncoding(encoding != null ? encoding.toString() : null);
        Object filename = blobInfoMap.get(KEY_BLOB_FILENAME);
        blobInfo.setFilename(filename != null ? filename.toString() : null);
        long length = extractBlobLength(blobInfoMap);
        blobInfo.setLength(length >= 0 ? length : null);
        Object digest = blobInfoMap.get(KEY_BLOB_DIGEST);
        blobInfo.setDigest(digest != null ? digest.toString() : null);
        return blobInfo;
    }

    /**
     * Returns the blob key for the given entry, falling back to the old {@code .blob.0} layout
     * for entries without a {@link #BLOBKEY_PREFIX} index. Remove after one firstLevelTTL window.
     */
    protected String getBlobKey(String key) {
        KeyValueStore kvs = getKeyValueStore();
        String info = getBlobInfoJson(key, kvs);
        if (info == null) {
            return null;
        }
        Map<String, Object> blobInfoMap = jsonToObjectMap(info);
        if (blobInfoMap == null) {
            return null;
        }

        // new flat layout: skip — already covered by the __blobkey__ prefix scan in doGC
        if (blobInfoMap.get(KEY_BLOB_KEY) != null) {
            return null;
        }

        // old layout: blob key in a separate .blob.0 key
        String blobMapJson = kvs.getString(key + DOT_BLOB_DOT + "0");
        if (blobMapJson == null) {
            return null;
        }
        Map<String, String> blobMap = jsonToMap(blobMapJson);
        return blobMap == null ? null : blobMap.get(KEY_BLOB_KEY);
    }

    @Override
    public long getSize(String key) {
        return getSize(key, getKeyValueStore());
    }

    private long getSize(String key, KeyValueStore kvs) {
        String json = getBlobInfoJson(key, kvs);
        Map<String, Object> map = jsonToObjectMap(json);
        if (map == null) {
            return 0;
        }
        // new flat layout: native number in KEY_BLOB_LENGTH; old layout: string in SIZE field
        long length = extractBlobLength(map);
        if (length >= 0) {
            return length;
        }
        Object sizeVal = map.get(SIZE); // old layout fallback
        return sizeVal == null ? 0 : Math.max(0, Long.parseLong(sizeVal.toString()));
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
        // Only .completed and .params survive at this point: blobs were already removed by
        // putBlob(key, null) before release() is called.
        KeyValueStore kvs = getKeyValueStore();
        kvs.setTTL(key + DOT_COMPLETED, releaseTTL);
        kvs.setTTL(key + DOT_PARAMS, releaseTTL);
    }

    @Override
    public void remove(String key) {
        KeyValueStore kvs = getKeyValueStore();
        removeBlob(key, kvs);
        removeParameters(key, kvs);
        removeCompleted(key, kvs);
    }

}
