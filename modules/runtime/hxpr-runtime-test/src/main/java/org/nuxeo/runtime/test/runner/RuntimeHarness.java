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
package org.nuxeo.runtime.test.runner;

import java.io.File;

import org.nuxeo.osgi.OSGiAdapter;
import org.nuxeo.runtime.model.RuntimeContext;
import org.osgi.framework.BundleException;

/**
 * TODO: Move this to org.nuxeo.runtime package
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface RuntimeHarness {

    /**
     * Gets the framework working directory.
     */
    File getWorkingDir();

    /**
     * Resume the runtime
     */
    void fireFrameworkStarted();

    /**
     * Deploys a whole OSGI bundle.
     * <p>
     * The lookup is first done on symbolic name, as set in <code>MANIFEST.MF</code> and then falls back to the bundle
     * url (e.g., <code>nuxeo-platform-search-api</code>) for backwards compatibility.
     *
     * @param bundle the symbolic name
     */
    void deployBundle(String bundle) throws BundleException;

    /**
     * Deploys a contribution from a given bundle.
     * <p>
     * The path will be relative to the bundle root. Example: <code>
     * deployContrib("org.nuxeo.ecm.core", "OSGI-INF/CoreExtensions.xml")
     * </code>
     * <p>
     * For compatibility reasons the name of the bundle may be a jar name, but this use is discouraged and deprecated.
     *
     * @param bundle the name of the bundle to peek the contrib in
     * @param contrib the path to contrib in the bundle.
     */
    void deployContrib(String bundle, String contrib);

    void start() throws BundleException;

    void stop();

    boolean isStarted();

    /**
     * Runtime context for deployment
     *
     * @since 5.4.2
     */
    RuntimeContext getContext();

    /**
     * OSGI bridge
     *
     * @since 5.4.2
     */
    OSGiAdapter getOSGiAdapter();

}
