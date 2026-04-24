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

package org.nuxeo.osgi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiBundleContext implements BundleContext {

    final BundleImpl bundle;

    public OSGiBundleContext(BundleImpl bundle) {
        this.bundle = bundle;
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public Bundle[] getBundles() {
        return bundle.osgi.getInstalledBundles();
    }

    @Override
    public File getDataFile(String filename) {
        return new File(bundle.osgi.getWorkingDir(), filename);
    }

    @Override
    public String getProperty(String key) {
        return bundle.osgi.getProperty(key);
    }

    @Override
    public Bundle installBundle(String location) throws BundleException {
        File file = new File(location);
        try {
            BundleFile bf = file.isDirectory() ? new DirectoryBundleFile(file) : new JarBundleFile(file);
            BundleImpl b = new BundleImpl(bundle.osgi, bf, bundle.loader);
            if (b.getSymbolicName() != null) {
                bundle.osgi.install(b);
            }
            return b;
        } catch (IOException e) {
            throw new BundleException("Failed to install bundle at " + location, e);
        }
    }

    @Override
    public void addBundleListener(BundleListener listener) {
        bundle.osgi.addBundleListener(listener);
    }

    @Override
    public void removeBundleListener(BundleListener listener) {
        bundle.osgi.removeBundleListener(listener);
    }

    @Override
    public void addFrameworkListener(FrameworkListener listener) {
        bundle.osgi.addFrameworkListener(listener);
    }

    @Override
    public void removeFrameworkListener(FrameworkListener listener) {
        bundle.osgi.removeFrameworkListener(listener);
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Filter createFilter(String filter) throws InvalidSyntaxException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getBundle(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getBundle(String location) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getService(ServiceReference reference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference getServiceReference(String clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
            throws InvalidSyntaxException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle installBundle(String location, InputStream input) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceRegistration registerService(String[] clazzes, Object service, Dictionary properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceRegistration registerService(String clazz, Object service, Dictionary properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean ungetService(ServiceReference reference) {
        throw new UnsupportedOperationException();
    }

}
