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
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Manifest;

import com.hyland.openarch.common.utils.ExceptionUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class BundleImpl implements Bundle {

    protected final String symbolicName;

    protected final Dictionary<String, String> headers;

    protected final BundleContext context;

    protected final OSGiAdapter osgi;

    protected final BundleFile file;

    protected final ClassLoader loader;

    protected int state;

    protected BundleActivator activator;

    public BundleImpl(OSGiAdapter osgi, BundleFile file, ClassLoader loader) throws BundleException {
        this.osgi = osgi;
        this.loader = loader;
        this.file = file;
        Manifest mf = file.getManifest();
        if (mf == null) {
            headers = null;
            symbolicName = null;
            context = null;
            return;
        }
        try {
            headers = BundleManifestReader.getHeaders(mf);
        } catch (BundleException e) {
            throw new BundleException("Invalid OSGi Manifest in file " + file + " : " + e.getMessage(), e);
        }
        symbolicName = headers.get(Constants.BUNDLE_SYMBOLICNAME);
        context = createContext();
        state = UNINSTALLED;
    }

    protected final BundleContext createContext() {
        return new OSGiBundleContext(this);
    }

    @Override
    public BundleContext getBundleContext() {
        return context;
    }

    @Override
    public URL getResource(String name) {
        return loader.getResource(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return loader.loadClass(name);
        } catch (NoClassDefFoundError e) {
            throw e;
        }
    }

    @Override
    public URL getEntry(String name) {
        return file.getEntry(name);
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return headers;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }

    private String getActivatorClassName() {
        return headers == null ? null : headers.get(Constants.BUNDLE_ACTIVATOR);
    }

    public BundleActivator getActivator() throws BundleException {
        if (activator == null) {
            activator = NullActivator.INSTANCE;
            String className = getActivatorClassName();
            if (className == null) {
                return activator;
            }
            try {
                activator = (BundleActivator) loadClass(className).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                throw new BundleException("Activator not found: " + className, e);
            } catch (ReflectiveOperationException e) {
                throw new BundleException("Activator not instantiable: " + className, e);
            }
        }
        return activator;
    }

    @Override
    public void start() throws BundleException {
        try {
            setStarting();
            getActivator().start(context);
            setStarted();
        } catch (BundleException e) {
            throw new BundleException("Failed to start bundle at: " + file + " with activator: "
                    + getActivatorClassName(), e);
        } catch (Exception e) { // stupid OSGi API throws Exception
            RuntimeException re = ExceptionUtils.runtimeException(e);
            throw new BundleException("Failed to start bundle at: " + file + " with activator: "
                    + getActivatorClassName(), re);
        }
    }

    public void shutdown() throws BundleException {
        try {
            state = STOPPING;
            getActivator().stop(context);
            state = UNINSTALLED;
        } catch (BundleException e) {
            throw new BundleException("Failed to stop activator: " + getActivatorClassName(), e);
        } catch (Exception e) { // stupid OSGi API throws Exception
            RuntimeException re = ExceptionUtils.runtimeException(e);
            throw new BundleException("Failed to stop activator: " + getActivatorClassName(), re);
        }
    }

    void setInstalled() {
        if (state == INSTALLED) {
            return;
        }
        state = INSTALLED;
    }

    void setResolved() {
        if (state == RESOLVED) {
            return;
        }
        state = RESOLVED;
    }

    void setStarting() {
        if (state != RESOLVED) {
            return;
        }
        state = STARTING;
        BundleEvent event = new BundleEvent(BundleEvent.STARTING, this);
        osgi.fireBundleEvent(event);
    }

    void setStarted() {
        if (state != STARTING) {
            return;
        }
        state = ACTIVE;
    }

    @Override
    public int hashCode() {
        return symbolicName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Bundle bundle) {
            return symbolicName.equals(bundle.getSymbolicName());
        }
        return false;
    }

    @Override
    public int compareTo(Bundle obj) {
        return symbolicName.compareTo(obj.getSymbolicName());
    }

    @Override
    public String toString() {
        return symbolicName;
    }

    @Override
    public void start(int options) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop(int options) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLocation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getBundleId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasPermission(Object permission) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uninstall() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(InputStream in) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> A adapt(Class<A> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getDataFile(String filename) {
        throw new UnsupportedOperationException();
    }

}
