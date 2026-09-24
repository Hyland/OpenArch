/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hyland.openarch.ecm.core.blob.binary.BinaryGarbageCollector;

/**
 * Blob storage as files on a local filesystem. The actual storage path chosen for a given key is decided based on a
 * {@link PathStrategy}.
 *
 * @since 11.1
 */
public class LocalBlobStore extends AbstractBlobStore {

    private static final Logger log = LoggerFactory.getLogger(LocalBlobStore.class);

    protected final PathStrategy pathStrategy;

    protected final LocalBlobGarbageCollector gc;

    public LocalBlobStore(String name, KeyStrategy keyStrategy, PathStrategy pathStrategy) {
        super(name, keyStrategy);
        this.pathStrategy = pathStrategy;
        gc = new LocalBlobGarbageCollector();
    }

    @Override
    protected String writeBlobGeneric(BlobWriteContext blobWriteContext) throws IOException {
        Path tmp = pathStrategy.createTempFile();
        try {
            write(blobWriteContext, tmp);
            logTrace("->", "write " + Files.size(tmp) + " bytes");
            logTrace("hnote right: " + tmp.getFileName().toString());
            String key = blobWriteContext.getKey(); // may depend on WriteObserver, for example for digests
            Path dest = pathStrategy.getPathForKey(key);
            Files.createDirectories(dest.getParent());
            logTrace(name, "-->", name, "rename");
            logTrace("hnote right of " + name + ": " + key);
            markForGC(key);
            PathStrategy.atomicMove(tmp, dest);
            return key;
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    // overridden for encrypted storage
    protected void write(BlobWriteContext blobWriteContext, Path file) throws IOException {
        transfer(blobWriteContext, file);
    }

    @Override
    public boolean copyBlobIsOptimized(BlobStore sourceStore) {
        return sourceStore.unwrap() instanceof LocalBlobStore;
    }

    @Override
    public String copyOrMoveBlob(String key, BlobStore sourceStore, String sourceKey, boolean atomicMove)
            throws IOException {
        BlobStore unwrappedSourceStore = sourceStore.unwrap();
        if (unwrappedSourceStore instanceof LocalBlobStore sourceLocalBlobStore) {
            return copyBlob(key, sourceLocalBlobStore, sourceKey, atomicMove);
        }
        return copyBlobGeneric(key, sourceStore, sourceKey, atomicMove);
    }

    /**
     * Optimized file-to-file copy/move.
     */
    protected String copyBlob(String key, LocalBlobStore sourceStore, String sourceKey, boolean atomicMove)
            throws IOException {
        Path source = sourceStore.pathStrategy.getPathForKey(sourceKey);
        if (!Files.exists(source)) { // NOSONAR (squid:S3725)
            return null;
        }
        if (key == null) { // fast compute or trigger async digest computation
            // here we can do a fast compute
            String digestAlgorithm = ((KeyStrategyDigest) keyStrategy).digestAlgorithm;
            key = new DigestUtils(digestAlgorithm).digestAsHex(source.toFile());
        }
        Path dest = pathStrategy.getPathForKey(key);
        Files.createDirectories(dest.getParent());
        markForGC(key);
        if (atomicMove) {
            if (sourceStore == this) {
                logTrace("hnote right of " + name + ": " + sourceKey);
                logTrace(name, "-->", name, "rename");
                logTrace("hnote right of " + name + ": " + key);
            } else {
                logTrace("hnote right of " + sourceStore.name + ": " + sourceKey);
                logTrace(sourceStore.name, "->", name, "move");
                logTrace("hnote right: " + key);
            }
            PathStrategy.atomicMove(source, dest);
        } else {
            logTrace("hnote right of " + sourceStore.name + ": " + sourceKey);
            logTrace(sourceStore.name, "->", name, "copy");
            logTrace("hnote right: " + key);
            PathStrategy.atomicCopy(source, dest);
        }
        return key;
    }

    /**
     * Generic copy/move to a local file.
     */
    protected String copyBlobGeneric(String key, BlobStore sourceStore, String sourceKey, boolean atomicMove)
            throws IOException {
        Path dest = pathStrategy.getPathForKey(key);
        Files.createDirectories(dest.getParent());
        markForGC(key);
        Path tmp = null;
        try {
            Path readTo;
            if (atomicMove) {
                readTo = tmp = pathStrategy.createTempFile();
            } else {
                readTo = dest;
            }
            OptionalOrUnknown<Path> fileOpt = sourceStore.getFile(sourceKey);
            if (fileOpt.isPresent()) {
                PathStrategy.atomicCopy(fileOpt.get(), readTo);
            } else {
                boolean found = sourceStore.readBlob(sourceKey, readTo);
                if (!found) {
                    return null;
                }
            }
            if (atomicMove) {
                PathStrategy.atomicMove(readTo, dest);
                sourceStore.deleteBlob(sourceKey);
            }
            return key;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public OptionalOrUnknown<Path> getFile(String key) {
        return getStoredFile(key);
    }

    protected OptionalOrUnknown<Path> getStoredFile(String key) {
        Path file = pathStrategy.getPathForKey(key);
        return Files.exists(file) ? OptionalOrUnknown.of(file) : OptionalOrUnknown.missing(); // NOSONAR (squid:S3725)
    }

    @Override
    public OptionalOrUnknown<InputStream> getStream(String key) throws IOException {
        Path file = pathStrategy.getPathForKey(key);
        try {
            return OptionalOrUnknown.of(new BufferedInputStream(Files.newInputStream(file)));
        } catch (NoSuchFileException e) {
            return OptionalOrUnknown.missing();
        }
    }

    @Override
    public boolean readBlob(String key, Path dest) throws IOException {
        Path file = pathStrategy.getPathForKey(key);
        if (Files.exists(file)) { // NOSONAR (squid:S3725)
            logTrace("<-", "read " + Files.size(file) + " bytes");
            logTrace("hnote right: " + key);
            PathStrategy.atomicCopy(file, dest);
            return true;
        } else {
            logTrace("<--", "read blob missing");
            logTrace("hnote right: " + key);
            return false;
        }
    }

    @Override
    public void deleteBlob(String key) {
        Path file = pathStrategy.getPathForKey(key);
        try {
            logTrace("->", "delete");
            logTrace("hnote right: " + key);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    @Override
    public void clear() {
        try {
            FileUtils.cleanDirectory(pathStrategy.dir.toFile());
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    @Override
    public BinaryGarbageCollector getBinaryGarbageCollector() {
        return gc;
    }

    public class LocalBlobGarbageCollector extends AbstractBlobGarbageCollector {

        @Override
        public String getId() {
            return pathStrategy.dir.toUri().toString();
        }

        @Override
        public void computeToDelete() {
            Path dir = pathStrategy.dir;
            Path recoveryDir = dir.resolve(GC_RECOVERY_DIRECTORY);
            if (!Files.isDirectory(dir)) {
                return;
            }
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path currentDir, BasicFileAttributes attrs) {
                        return currentDir.equals(recoveryDir) ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // skip temp files: they are in-flight writes, not blobs (their random name isn't a key,
                        // so it would neither resolve for deletion nor should it be deleted from under a writer)
                        if (attrs.isRegularFile() && Files.isWritable(file) && !pathStrategy.isTempFile(file)) {
                            status.setSizeBinaries(status.getSizeBinaries() + attrs.size());
                            status.setNumBinaries(status.getNumBinaries() + 1);
                            toDelete.add(file.getFileName().toString());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        // I/O error or missing permission on a file or directory; skip and continue
                        log.warn("GC cannot access: {}", file, e);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.warn("GC failed to traverse directory: {}", dir, e);
            }
        }

        @Override
        protected long removeUnmarkedBlob(String key, boolean delete) {
            OptionalOrUnknown<Path> fileOpt = getStoredFile(key);
            if (!fileOpt.isPresent()) {
                // shouldn't happen except if blob concurrently removed
                return -1;
            }
            Path path = fileOpt.get();
            long length;
            try {
                length = Files.size(path);
            } catch (IOException e) {
                log.error("Binary garbage collector failed to read file size for key: {}", key, e);
                return -1;
            }
            if (delete) {
                long t0 = System.currentTimeMillis();
                try {
                    if (DELETE_WITH_RECOVERY) {
                        Path relativePath = pathStrategy.dir.relativize(path);
                        Path recoveryPath = pathStrategy.dir.resolve(GC_RECOVERY_DIRECTORY).resolve(relativePath);
                        Files.createDirectories(recoveryPath.getParent());
                        PathStrategy.atomicMove(path, recoveryPath);
                        log.debug("Moved blob {} to recovery in {}ms", key, System.currentTimeMillis() - t0);
                    } else {
                        Files.delete(path);
                        log.debug("Deleted blob {} in {}ms", key, System.currentTimeMillis() - t0);
                    }
                } catch (IOException e) {
                    log.error("Binary garbage collector failed to delete file for key: {}", key, e);
                }
            }
            return length;
        }

        @Override
        protected void removeRecoveryDirectory() {
            Path recoveryDir = pathStrategy.dir.resolve(GC_RECOVERY_DIRECTORY);
            long t0 = System.currentTimeMillis();
            try {
                FileUtils.deleteDirectory(recoveryDir.toFile());
                log.debug("Deleted recovery directory {} in {}ms", recoveryDir, System.currentTimeMillis() - t0);
            } catch (IOException e) {
                log.error("Binary garbage collector failed to delete recovery directory: {}", recoveryDir, e);
            }
        }

        @Override
        protected void recoverRecoveryDirectory() {
            Path recoveryDir = pathStrategy.dir.resolve(GC_RECOVERY_DIRECTORY);
            if (!Files.isDirectory(recoveryDir)) {
                return;
            }
            long t0 = System.currentTimeMillis();
            try {
                Files.walkFileTree(recoveryDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path recoveryPath, BasicFileAttributes attrs) throws IOException {
                        Path path = pathStrategy.dir.resolve(recoveryDir.relativize(recoveryPath));
                        if (!Files.exists(path)) {
                            Files.createDirectories(path.getParent());
                            PathStrategy.atomicMove(recoveryPath, path);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                        if (e != null) {
                            throw e;
                        }
                        try {
                            Files.delete(dir);
                        } catch (DirectoryNotEmptyException ignored) {
                            // recovery files with live-path collisions remain untouched
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                log.debug("Recovered directory {} in {}ms", recoveryDir, System.currentTimeMillis() - t0);
            } catch (IOException e) {
                log.error("Binary garbage collector failed to recover directory: {}", recoveryDir, e);
            }
        }

    }

}
