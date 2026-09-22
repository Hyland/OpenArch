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

package org.nuxeo.osgi;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.inject.spi.CDI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.Environment;
import org.nuxeo.runtime.api.Framework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiAdapter {

    private static final Logger log = LogManager.getLogger(OSGiAdapter.class);

    protected final File workingDir;

    protected final File dataDir;

    protected List<FrameworkListener> frameworkListeners;

    protected List<BundleListener> bundleListeners;

    // symbolic name -> bundle
    private Map<String, BundleImpl> bundles;

    protected Properties properties;

    protected SystemBundle systemBundle;

    public OSGiAdapter(File workingDir) {
        this(workingDir,
                new File(System.getProperty(Environment.NUXEO_DATA_DIR, workingDir + File.separator + "data")),
                new Properties());
    }

    public OSGiAdapter(File workingDir, File dataDir, Properties properties) {
        this.workingDir = workingDir;
        this.dataDir = dataDir;
        this.dataDir.mkdirs();
        this.workingDir.mkdirs();
        initialize(properties);
    }

    protected void initialize(Properties properties) {
        this.properties = properties;
        bundles = new LinkedHashMap<>();
        frameworkListeners = new CopyOnWriteArrayList<>();
        bundleListeners = new CopyOnWriteArrayList<>();
        // setting up default properties
        properties.put(Constants.FRAMEWORK_VENDOR, "Nuxeo");
        properties.put(Constants.FRAMEWORK_VERSION, "1.0.0");
    }

    public void setSystemBundle(SystemBundle systemBundle) throws BundleException {
        if (this.systemBundle != null) {
            throw new IllegalStateException("Cannot set system bundle");
        }
        install(systemBundle);
        addBundleAlias("system.bundle", systemBundle.getSymbolicName());
        this.systemBundle = systemBundle;
    }

    private void addBundleAlias(String alias, String symbolicName) {
        BundleImpl bundle = bundles.get(symbolicName);
        if (bundle != null) {
            bundles.put(alias, bundle);
        }
    }

    public String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        return value;
    }

    public String getProperty(String key, String defvalue) {
        String val = getProperty(key);
        if (val == null) {
            val = defvalue;
        }
        return val;
    }

    /**
     * @param name the property name.
     * @param value the property value.
     */
    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public void shutdown() throws IOException {
        shutdownBundles();
        properties.clear();
        bundles = null;
        frameworkListeners = null;
        bundleListeners = null;
        properties = null;
    }

    private void shutdownBundles() {
        List<BundleImpl> copy = new ArrayList<>(bundles.values());
        for (BundleImpl bundle : copy) {
            try {
                bundle.shutdown();
            } catch (BundleException | RuntimeException e) {
                log.error("Failed to stop bundle {}", bundle.getSymbolicName(), e);
            }
        }
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public File getDataDir() {
        return dataDir;
    }

    public BundleImpl getBundle(String symbolicName) {
        return bundles.get(symbolicName);
    }

    public URL getBundleEntry(String symbolicName, String path) {
        return bundles.get(symbolicName).getEntry(path);
    }

    public BundleImpl[] getInstalledBundles() {
        return bundles.values().toArray(BundleImpl[]::new);
    }

    public void install(BundleImpl bundle) throws BundleException {
        if (bundle.getState() != Bundle.UNINSTALLED) {
            return;
        }
        String name = bundle.getSymbolicName();
        BundleImpl existing = bundles.get(name);
        if (existing != null) {
            bundle = existing;
        }
        double s = System.currentTimeMillis();
        log.debug("Registering resolved bundle: {}", name);
        bundles.put(name, bundle);
        bundle.setInstalled();
        bundle.setResolved();
        bundle.start();
    }

    public void addFrameworkListener(FrameworkListener listener) {
        frameworkListeners.add(listener);
    }

    public void removeFrameworkListener(FrameworkListener listener) {
        frameworkListeners.remove(listener);
    }

    public void addBundleListener(BundleListener listener) {
        bundleListeners.add(listener);
    }

    public void removeBundleListener(BundleListener listener) {
        bundleListeners.remove(listener);
    }

    public void fireFrameworkEvent(FrameworkEvent event) {
        log.debug("Firing FrameworkEvent on {} listeners", frameworkListeners.size());
        for (FrameworkListener listener : frameworkListeners) {
            log.debug("Start execution of {} listener", listener.getClass());
            try {
                listener.frameworkEvent(event);
                log.debug("End execution of {} listener", listener.getClass());
            } catch (RuntimeException e) {
                if (Boolean.getBoolean(Framework.NUXEO_STRICT_STARTUP_SYSTEM_PROP)) {
                    throw e;
                } else {
                    log.error("Error during Framework Listener execution: {}", listener.getClass(), e);
                }
            }
        }
        try {
            CDI.current().getBeanManager().getEvent().select().fire(event);
        } catch (IllegalStateException e) {
            // No CDIProvider available
        }
    }

    public void fireBundleEvent(BundleEvent event) {
        bundleListeners.forEach(listener -> listener.bundleChanged(event));
    }

    public Bundle getSystemBundle() {
        return systemBundle;
    }

}
