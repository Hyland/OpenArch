/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.osgi.application;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.FileNamePattern;
import org.nuxeo.common.utils.JarUtils;
import org.nuxeo.osgi.BundleFile;
import org.nuxeo.osgi.DirectoryBundleFile;
import org.nuxeo.osgi.JarBundleFile;
import org.osgi.framework.Constants;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class BundleWalker extends SimpleFileVisitor<Path> {

    private static final Logger log = LogManager.getLogger(BundleWalker.class);

    private static final FileNamePattern PATTERN = new FileNamePattern("*.jar");

    private final Callback callback;

    public BundleWalker(Callback cb) {
        callback = cb;
    }

    public void visit(File root) {
        try {
            Files.walkFileTree(root.toPath(), this);
        } catch (IOException e) {
            log.error(e, e);
        }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (!acceptFile(dir)) {
            return FileVisitResult.CONTINUE;
        }
        // check if this is an OSGi bundle
        try {
            Manifest mf = JarUtils.getDirectoryManifest(dir.toFile());
            if (mf == null) {
                return FileVisitResult.CONTINUE;
            }
            String bundleName = mf.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
            if (bundleName != null) {
                // notify the callback about the new bundle
                callback.visitBundle(new DirectoryBundleFile(dir.toFile(), mf));
                // assume that a directory OSGi bundle cannot contain other bundles so skip it
                return FileVisitResult.SKIP_SUBTREE;
            }
        } catch (IOException e) {
            log.error(e, e);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        // first check if this is a possible bundle
        if (!acceptFile(file)) {
            return FileVisitResult.CONTINUE;
        }
        // check if this is an OSGi bundle
        try (JarFile jarFile = new JarFile(file.toFile())) {
            if (jarFile.getManifest() == null) {
                return FileVisitResult.CONTINUE;
            }
            BundleFile bundleFile = new JarBundleFile(jarFile);
            if (bundleFile.getSymbolicName() != null) {
                // notify the callback about the new bundle
                callback.visitBundle(bundleFile);
            } else {
                // notify the callback about the new jar
                callback.visitJar(bundleFile);
            }
        } catch (IOException e) {
            // ignore
        }
        return FileVisitResult.CONTINUE;
    }

    private boolean acceptFile(Path path) {
        return PATTERN.match(path);
    }

    public interface Callback {
        void visitBundle(BundleFile bundleFile) throws IOException;

        void visitJar(BundleFile bundleFile) throws IOException;
    }

}
