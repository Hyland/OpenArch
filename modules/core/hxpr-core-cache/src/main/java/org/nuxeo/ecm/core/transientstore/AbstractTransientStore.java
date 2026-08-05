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

package org.nuxeo.ecm.core.transientstore;

import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.transientstore.api.MaximumTransientSpaceExceeded;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreConfig;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreProvider;

/**
 * Base class for a {@link TransientStore} implementation.
 *
 * @since 7.2
 */
public abstract class AbstractTransientStore implements TransientStoreProvider {

    protected static final Logger log = LogManager.getLogger(AbstractTransientStore.class);

    protected TransientStoreConfig config;

    protected File cacheDir;

    @Override
    public void init(TransientStoreConfig config) {
        this.config = config;
        File data = getDataDir(config);
        data.mkdirs();
        cacheDir = data.getAbsoluteFile();
    }

    private File getDataDir(TransientStoreConfig config) {
        String dataDirPath = config.getDataDir();
        if (StringUtils.isBlank(dataDirPath)) {
            File transienStoreHome = new File(Environment.getDefault().getData(), "transientstores");
            return new File(transienStoreHome, config.getName());
        } else {
            return new File(dataDirPath);
        }
    }

    /**
     * Updates the total storage size and the storage size of the entry with the given {@code key} according to
     * {@code sizeOfBlobs} and stores the blob information in this entry.
     */
    protected abstract void persistBlob(String key, long sizeOfBlob, Map<String, String> blobInfo);

    /**
     * Sets the size of the disk storage in bytes.
     */
    protected abstract void setStorageSize(long newSize);

    protected abstract long incrementStorageSize(long size);

    protected abstract long decrementStorageSize(long size);

    protected abstract void removeAllEntries();

    protected abstract void removeEntry(String key);

    @Override
    public void putBlob(String key, Blob blob) {
        if (config.getAbsoluteMaxSizeMB() < 0 || getStorageSize() < config.getAbsoluteMaxSizeMB() * (1024 * 1024)) {
            // Store blob on the file system
            Map<String, String> blobInfo = storeBlob(key, blob);
            // Persist blob information in the store
            persistBlob(key, getSizeOfBlob(blob), blobInfo);
        } else {
            throw new MaximumTransientSpaceExceeded();
        }
    }

    protected Map<String, String> storeBlob(String key, Blob blob) {
        if (blob == null) {
            return null;
        }
        // Store blob on the file system and compute blob information
        Map<String, String> blobInfo = new HashMap<>();
        File cachingDir = getCachingDirectory(key);
        String uuid = UUID.randomUUID().toString();
        File cachedFile = new File(cachingDir, uuid);
        try {
            if (blob instanceof FileBlob fileBlob && fileBlob.isTemporary()) {
                fileBlob.moveTo(cachedFile);
            } else {
                blob.transferTo(cachedFile);
            }
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
        Path cachedFileRelativePath = Paths.get(cachingDir.getName(), uuid);
        blobInfo.put("file", cachedFileRelativePath.toString());
        // Redis doesn't support null values
        if (blob.getFilename() != null) {
            blobInfo.put(KEY_BLOB_FILENAME, blob.getFilename());
        }
        if (blob.getEncoding() != null) {
            blobInfo.put(KEY_BLOB_ENCODING, blob.getEncoding());
        }
        if (blob.getMimeType() != null) {
            blobInfo.put(KEY_BLOB_MIME_TYPE, blob.getMimeType());
        }
        if (blob.getDigest() != null) {
            blobInfo.put(KEY_BLOB_DIGEST, blob.getDigest());
        }
        log.debug("Stored blob on the file system: {}", blobInfo);
        return blobInfo;
    }

    public File getCachingDirectory(String key) {
        String cachingDirName = getCachingDirName(key);
        try {
            File cachingDir = new File(cacheDir.getCanonicalFile(), cachingDirName);
            if (!cachingDir.getCanonicalFile().toPath().startsWith(cacheDir.getCanonicalFile().toPath())) {
                throw new NuxeoException("Trying to traverse illegal path: " + cachingDir + " for key: " + key);
            }
            if (!cachingDir.exists()) {
                cachingDir.mkdir();
            }
            return cachingDir;
        } catch (IOException e) {
            throw new NuxeoException("Error when trying to access cache directory: " + cacheDir + "/" + cachingDirName
                    + " for key: " + key, e);
        }
    }

    protected String getCachingDirName(String key) {
        String dirName = Base64.encodeBase64String(key.getBytes());
        dirName = dirName.replace("/", "_");
        return dirName;
    }

    protected long getSizeOfBlob(Blob blob) {
        if (blob == null) {
            return 0;
        }
        long length = blob.getLength();
        return length > -1 ? length : 0;
    }

    protected Blob loadBlob(Map<String, String> blobInfo) {
        if (blobInfo == null) {
            return null;
        }
        log.debug("Loading blob from the file system: {}", blobInfo);
        File blobFile = new File(cacheDir, blobInfo.get("file"));
        Blob blob = new FileBlob(blobFile);
        blob.setEncoding(blobInfo.get(KEY_BLOB_ENCODING));
        blob.setMimeType(blobInfo.get(KEY_BLOB_MIME_TYPE));
        blob.setFilename(blobInfo.get(KEY_BLOB_FILENAME));
        blob.setDigest(blobInfo.get(KEY_BLOB_DIGEST));
        return blob;
    }

    @Override
    public void doGC() {
        log.debug("Performing GC for TransientStore %s", config.getName());
        long newSize = 0;
        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(cacheDir.getAbsolutePath()))) {
                for (Path entry : stream) {
                    String key = getKeyCachingDirName(entry.getFileName().toString());
                    if (exists(key)) {
                        newSize += getFilePathSize(entry);
                        continue;
                    }
                    FileUtils.deleteQuietly(entry.toFile());
                }
            }
        } catch (IOException e) {
            log.error("Error while performing GC", e);
        }
        setStorageSize(newSize);
    }

    protected String getKeyCachingDirName(String dir) {
        String key = dir.replace("_", "/");
        return new String(Base64.decodeBase64(key));
    }

    protected long getFilePathSize(Path entry) {
        long size = 0;
        for (File file : entry.toFile().listFiles()) {
            size += file.length();
        }
        return size;
    }

    @Override
    public void remove(String key) {
        removeEntry(key);
        removeBlob(key);
    }

    protected void removeBlob(String key) {
        FileUtils.deleteQuietly(getCachingDirectory(key));
    }

    @Override
    public void removeAll() {
        log.debug("Removing all entries from TransientStore {}" , config.getName());
        removeAllEntries();
        doGC();
    }

    public File getCacheDir() {
        return cacheDir;
    }

}
