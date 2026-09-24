/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot.application.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.osgi.BundleFile;
import com.hyland.openarch.osgi.BundleImpl;
import com.hyland.openarch.osgi.OSGiAdapter;
import com.hyland.openarch.osgi.application.loader.FrameworkLoader;
import com.hyland.openarch.runtime.springboot.osgi.SpringBootNestedJarBundleFile;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.springframework.boot.loader.jar.NestedJarFile;

/**
 * This is a wrapper for {@link FrameworkLoader} that adds support for Spring Boot nested jar files.
 */
public class SpringBootFrameworkLoader {

    private static final Logger log = LogManager.getLogger(SpringBootFrameworkLoader.class);

    private static boolean isStarted;

    private static List<File> bundleFiles;

    private SpringBootFrameworkLoader() {
        // Utility class
    }

    /**
     * Returns the OSGiAdapter instance from the FrameworkLoader.
     *
     * @return OSGiAdapter instance
     */
    public static OSGiAdapter osgi() {
        return FrameworkLoader.osgi();
    }

    /**
     * Returns the ClassLoader instance from the FrameworkLoader.
     *
     * @return ClassLoader instance
     */
    public static ClassLoader getLoader() {
        return FrameworkLoader.getLoader();
    }

    /**
     * Wraps the {@link FrameworkLoader#start(ClassLoader, File, List, Map)} method to add support for Spring Boot
     * nested bundles.
     * <p>
     * Initializes the {@link FrameworkLoader} with the given parameters.
     * <p>
     * Starts the framework loader and installs any nested jar files as bundles. If the framework loader is already
     * started, this method does nothing. If the framework loader is not initialized, this method throws an
     * IllegalStateException.
     *
     * @param cl
     * @param home
     * @param bundleFiles
     * @param hostEnv
     * @throws BundleException If there is an error starting the framework loader or installing the bundles
     */
    public static synchronized void start(ClassLoader cl, File home, List<File> bundleFiles,
            Map<String, Object> hostEnv) throws BundleException {
        if (isStarted) {
            return;
        }
        SpringBootFrameworkLoader.bundleFiles = bundleFiles == null ? new ArrayList<>() : bundleFiles;
        try {
            FrameworkLoader.start(cl, home, bundleFiles, hostEnv);
            startNestedBundles();
        } finally {
            isStarted = true;
        }
    }

    /**
     * Starts the nested bundles. This method iterates over all bundle files and checks if they are nested bundles. If a
     * file is a nested bundle, it attempts to install it. If the installation fails due to an IOException,
     * BundleException, or RuntimeException, an error message is logged. After all nested bundles have been processed, a
     * FrameworkEvent is fired to signal that the framework has started.
     *
     * @throws BundleException If there is an error starting the nested bundles
     */
    private static void startNestedBundles() throws BundleException {
        for (File file : bundleFiles) {
            if (!isNestedBundle(file)) {
                continue;
            }
            try {
                installNestedBundle(file, osgi(), getLoader());
            } catch (IOException | BundleException | RuntimeException e) {
                log.error("Failed to install bundle: {}", file, e);
            }
        }
        osgi().fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.STARTED, osgi().getSystemBundle(), null));
    }

    /**
     * Checks if the given file is a nested bundle. A file is considered a nested bundle if it is not a regular file or
     * directory and its path contains "jar/!". If the file is a regular file or directory, it will be loaded by
     * FrameWorkLoader.start() method.
     *
     * @param file The file to check
     * @return true if the file is a nested bundle, false otherwise
     */
    private static boolean isNestedBundle(File file) {
        // if it is a file or directory it will be loaded by FrameWorkLoader.start()
        return !file.isFile() && !file.isDirectory() && file.getPath().contains("jar/!");
    }

    /**
     * Installs the given nested bundle.
     *
     * @param file The file to install as a bundle
     * @param osgi The OSGiAdapter to use for installing the bundle
     * @param classLoader The ClassLoader to use for creating the bundle
     * @throws IOException If there is an error creating the BundleFile
     * @throws BundleException If there is an error installing the bundle
     */
    private static void installNestedBundle(File file, OSGiAdapter osgi, ClassLoader classLoader)
            throws IOException, BundleException {
        BundleFile bundleFile = createSpringBootNestedJarBundleFile(file);
        BundleImpl bundle = new BundleImpl(osgi, bundleFile, classLoader);
        if (bundle.getState() == 0) {
            // not a bundle (no Bundle-SymbolicName)
            return;
        }
        osgi.install(bundle);
    }

    /**
     * Creates a {@link SpringBootNestedJarBundleFile} from the given file. This method splits the file's path into a
     * parent path and a nested path using "!" as the delimiter. It then creates a {@link NestedJarFile} from the parent
     * path and the nested path. Finally, it creates a {@link SpringBootNestedJarBundleFile} from the nested path and
     * the {@link NestedJarFile}.
     *
     * @param file The file to create the {@link SpringBootNestedJarBundleFile} from
     * @return The created {@link SpringBootNestedJarBundleFile}
     * @throws IOException If there is an error creating the {@link NestedJarFile}
     */
    private static BundleFile createSpringBootNestedJarBundleFile(File file) throws IOException {
        String path = file.getPath();
        String[] paths = path.split("!");
        String parentPath = paths[0];
        String nestedPath = paths[1];

        NestedJarFile njf = new NestedJarFile(new File(parentPath), nestedPath);

        return new SpringBootNestedJarBundleFile(nestedPath, njf);
    }

    /**
     * Wraps the {@link FrameworkLoader#stop()} method. Stops the framework loader. If the framework loader is not
     * started, this method does nothing.
     *
     * @throws BundleException If there is an error stopping the framework loader
     */
    public static synchronized void stop() throws BundleException {
        FrameworkLoader.stop();
    }

}
