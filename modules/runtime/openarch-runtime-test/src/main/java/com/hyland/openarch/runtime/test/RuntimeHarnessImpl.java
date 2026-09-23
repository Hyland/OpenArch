/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.test;

import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.Environment;
import com.hyland.openarch.osgi.BundleFile;
import com.hyland.openarch.osgi.DirectoryBundleFile;
import com.hyland.openarch.osgi.JarBundleFile;
import com.hyland.openarch.osgi.OSGiAdapter;
import com.hyland.openarch.osgi.SystemBundle;
import com.hyland.openarch.osgi.SystemBundleFile;
import com.hyland.openarch.osgi.application.StandaloneBundleLoader;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.RuntimeContext;
import com.hyland.openarch.runtime.osgi.OSGiRuntimeService;
import com.hyland.openarch.runtime.test.runner.RuntimeHarness;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;

import io.github.classgraph.ClassGraph;

/**
 * Default RuntimeHarness implementation.
 *
 * @since 10.2
 */
public class RuntimeHarnessImpl implements RuntimeHarness {

    protected static final Logger log = LogManager.getLogger(RuntimeHarnessImpl.class);

    protected static URL[] introspectClasspath() {
        return new ClassGraph().getClasspathFiles().stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException cause) {
                throw new RuntimeServiceException("Could not get URL from " + file, cause);
            }
        }).toArray(URL[]::new);
    }

    protected StandaloneBundleLoader bundleLoader;

    protected Map<String, BundleFile> bundles;

    protected boolean frameworkStarted;

    protected OSGiAdapter osgi;

    protected Set<URI> readUris;

    protected OSGiRuntimeService runtime;

    protected Bundle runtimeBundle;

    protected TargetResourceLocator targetResourceLocator;

    protected URL[] urls; // classpath urls, used for bundles lookup

    protected File workingDir;

    protected RuntimeHarnessImpl() {
    }

    public RuntimeHarnessImpl(Class<?> clazz) {
        this();
        targetResourceLocator = new TargetResourceLocator(clazz);
    }

    @Override
    public void deployBundle(String name) throws BundleException {
        // install only if not yet installed
        Bundle bundle = bundleLoader.getOSGi().getBundle(name);
        if (bundle == null) {
            BundleFile bundleFile = lookupBundle(name);
            bundleLoader.loadBundle(bundleFile);
            bundleLoader.installBundle(bundleFile);
            bundle = bundleLoader.getOSGi().getBundle(name);
        } else {
            log.info("A bundle with name {} has been found. Deploy is ignored.", name);
        }
        if (runtime.getContext(bundle.getSymbolicName()) == null) {
            runtime.createContext(bundle);
        }
    }

    @Override
    public void deployContrib(String name, String contrib) {
        RuntimeContext context = runtime.getContext(name);
        if (context == null) {
            context = runtime.getContext();
            BundleFile file = lookupBundle(name);
            URL location = file.getEntry(contrib);
            if (location == null) {
                throw new AssertionError("Cannot locate " + contrib + " in " + name);
            }
            try {
                context.deploy(location);
            } catch (IOException e) {
                throw new RuntimeServiceException(e.getMessage());
            }
        } else {
            context.deploy(contrib);
        }
    }

    @Override
    public void fireFrameworkStarted() {
        if (frameworkStarted) {
            throw new IllegalStateException("fireFrameworkStarted must not be called more than once");
        }
        frameworkStarted = true;
        boolean txStarted = !TransactionHelper.isTransactionActiveOrMarkedRollback()
                && TransactionHelper.startTransaction();
        boolean txFinished = false;
        try {
            osgi.fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.STARTED, runtimeBundle, null));
            txFinished = true;
        } finally {
            if (!txFinished) {
                TransactionHelper.setTransactionRollbackOnly();
            }
            if (txStarted) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
    }

    @Override
    public RuntimeContext getContext() {
        return runtime.getContext();
    }

    @Override
    public OSGiAdapter getOSGiAdapter() {
        return osgi;
    }

    @Override
    public File getWorkingDir() {
        return workingDir;
    }

    @Override
    public boolean isStarted() {
        return runtime != null;
    }

    @Override
    public void start() {
        System.setProperty("com.hyland.openarch.runtime.testing", "true");
        wipeEmptyTestSystemProperties();
        wipeRuntime();
        initUrls();
        if (urls == null) {
            throw new UnsupportedOperationException("no bundles available");
        }
        try {
            initOsgiRuntime();
        } catch (IOException | BundleException e) {
            throw new RuntimeServiceException(e);
        }
    }

    @Override
    public void stop() {
        wipeRuntime();
        if (workingDir != null) {
            if (workingDir.exists() && !FileUtils.deleteQuietly(workingDir)) {
                log.warn("Cannot delete {}", workingDir);
            }
            workingDir = null;
        }
        readUris = null;
        bundles = null;
    }

    /**
     * Inits the osgi runtime.
     *
     * @throws IOException
     * @throws BundleException
     * @throws Exception the exception
     */
    protected void initOsgiRuntime() throws IOException, BundleException {
        try {
            Environment.setDefault(null);
            if (System.getProperties().remove("nuxeo.home") != null) {
                log.warn("Removed System property nuxeo.home.");
            }
            workingDir = File.createTempFile("nxruntime-" + Thread.currentThread().getName() + "-", null,
                    new File(getBuildDirectory()));
            Files.delete(workingDir.toPath());
        } catch (IOException e) {
            log.error("Could not init working directory", e);
            throw e;
        }
        osgi = new OSGiAdapter(workingDir);
        BundleFile bf = new SystemBundleFile(workingDir);
        bundleLoader = new StandaloneBundleLoader(osgi, RuntimeHarnessImpl.class.getClassLoader());
        SystemBundle systemBundle = new SystemBundle(osgi, bf, bundleLoader.getSharedClassLoader().getLoader());
        osgi.setSystemBundle(systemBundle);
        Thread.currentThread().setContextClassLoader(bundleLoader.getSharedClassLoader().getLoader());

        bundleLoader.setScanForNestedJARs(false); // for now
        bundleLoader.setExtractNestedJARs(false);

        BundleFile bundleFile = lookupBundle("com.hyland.openarch.runtime");
        runtimeBundle = new RootRuntimeBundle(osgi, bundleFile, bundleLoader.getClass().getClassLoader());
        runtimeBundle.start();

        runtime = (OSGiRuntimeService) Framework.getRuntime();

    }

    /** @since 11.1 */
    protected static final String CUSTOM_ENVIRONMENT_SYSTEM_PROPERTY = "custom.environment";

    /** @since 11.1 */
    protected static final String DEFAULT_BUILD_DIRECTORY = "target";

    /**
     * Returns the Maven build directory, depending on the {@value #CUSTOM_ENVIRONMENT_SYSTEM_PROPERTY} system property.
     *
     * @since 11.1
     */
    protected String getBuildDirectory() {
        String customEnvironment = System.getProperty(CUSTOM_ENVIRONMENT_SYSTEM_PROPERTY);
        return customEnvironment == null ? DEFAULT_BUILD_DIRECTORY
                : String.format("%s-%s", DEFAULT_BUILD_DIRECTORY, customEnvironment);
    }

    /**
     * Inits the urls.
     */
    protected void initUrls() {
        urls = introspectClasspath();
        log.debug("URLs on the classpath:\n{}", () -> Stream.of(urls).map(URL::toString).collect(joining("\n")));
        readUris = new HashSet<>();
        bundles = new HashMap<>();
    }

    /**
     * Lookup bundle.
     *
     * @param bundleName the bundle name
     * @return the bundle file
     * @throws Exception the exception
     */
    protected BundleFile lookupBundle(String bundleName) {
        BundleFile bundleFile = bundles.get(bundleName);
        if (bundleFile != null) {
            return bundleFile;
        }
        for (URL url : urls) {
            URI uri;
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                throw new RuntimeServiceException(e);
            }
            if (readUris.contains(uri)) {
                continue;
            }
            File file = new File(uri);
            readUris.add(uri);
            try {
                if (file.isDirectory()) {
                    bundleFile = new DirectoryBundleFile(file);
                } else {
                    bundleFile = new JarBundleFile(file);
                }
            } catch (IOException e) {
                // no manifest => not a bundle
                continue;
            }
            String symbolicName = readSymbolicName(bundleFile);
            if (symbolicName != null) {
                log.debug("Bundle '{}' has URL {}", symbolicName, url);
                bundles.put(symbolicName, bundleFile);
            }
            if (bundleName.equals(symbolicName)) {
                return bundleFile;
            }
        }
        throw new RuntimeServiceException(String.format("No bundle with symbolic name '%s';", bundleName));
    }

    /**
     * Read symbolic name.
     *
     * @param bf the bf
     * @return the string
     */
    protected String readSymbolicName(BundleFile bf) {
        Manifest manifest = bf.getManifest();
        if (manifest == null) {
            return null;
        }
        Attributes attrs = manifest.getMainAttributes();
        String name = attrs.getValue("Bundle-SymbolicName");
        if (name == null) {
            return null;
        }
        String[] sp = name.split(";", 2);
        return sp[0];
    }

    /**
     * Makes sure there is no previous runtime hanging around.
     * <p>
     * This happens for instance if a previous test had errors in its <code>setUp()</code>, because
     * <code>tearDown()</code> has not been called.
     */
    protected void wipeRuntime() {
        // Make sure there is no active runtime (this might happen if an
        // exception is raised during a previous setUp -> tearDown is not called afterwards).
        runtime = null;
        frameworkStarted = false;
        if (Framework.getRuntime() != null) {
            try {
                Framework.shutdown();
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new RuntimeServiceException("Interrupted during shutdown", cause);
            }
        }
    }

    /**
     * Removes Nuxeo test system properties that are empty.
     * <p>
     * This is needed when using maven surefire &gt; 2.17 because since SUREFIRE-649 surefire propagates empty system
     * properties.
     */
    protected void wipeEmptyTestSystemProperties() {
        List<String> emptyProps = System.getProperties()
                                        .entrySet()
                                        .stream()
                                        .filter(this::isAnEmptyTestProperty)
                                        .map(entry -> entry.getKey().toString())
                                        .toList();
        emptyProps.forEach(System::clearProperty);
        if (log.isDebugEnabled()) {
            emptyProps.forEach(property -> log.debug("Removed empty test system property: {}", property));
        }
    }

    protected boolean isAnEmptyTestProperty(Map.Entry<Object, Object> entry) {
        if (!entry.getKey().toString().startsWith("nuxeo.test.")) {
            return false;
        }
        return entry.getValue() == null || entry.getValue().toString().isEmpty();
    }

}
