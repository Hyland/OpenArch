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

package com.hyland.openarch.runtime.osgi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.Environment;
import com.hyland.openarch.runtime.api.Framework;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The default BundleActivator for NXRuntime over an OSGi comp. platform.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiRuntimeActivator implements BundleActivator {

    private static final Logger log = LogManager.getLogger(OSGiRuntimeActivator.class);

    private static OSGiRuntimeActivator instance;

    protected OSGiRuntimeService runtime;

    protected OSGiComponentLoader componentLoader;

    protected BundleContext context;

    public static OSGiRuntimeActivator getInstance() {
        return instance;
    }

    @Override
    public void start(BundleContext context) {
        log.info("Starting Runtime Activator");
        instance = this; // NOSONAR OSGi singleton
        this.context = context;

        // assert the environment is setup
        if (Environment.getDefault() == null) {
            throw new IllegalStateException("Environment is not setup");
        }

        // create the runtime
        runtime = new OSGiRuntimeService(context);

        // start it
        Framework.initialize(runtime);
        // register bundle component loader
        componentLoader = new OSGiComponentLoader(runtime);
        // TODO register osgi services
    }

    @Override
    public void stop(BundleContext context) {
        log.info("Stopping Runtime Activator");
        instance = null; // NOSONAR OSGi singleton
        // remove component loader
        componentLoader.uninstall();
        componentLoader = null;
        // unregister
        try {
            Framework.shutdown();
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during shutdown", cause);
        } finally {
            runtime = null;
            this.context = null;
        }
    }

}
