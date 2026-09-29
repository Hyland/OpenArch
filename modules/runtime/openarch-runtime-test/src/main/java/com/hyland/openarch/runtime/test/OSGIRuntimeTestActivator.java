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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.hyland.openarch.common.Environment;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.osgi.OSGiComponentLoader;
import com.hyland.openarch.runtime.osgi.OSGiRuntimeActivator;
import com.hyland.openarch.runtime.osgi.OSGiRuntimeService;

import org.osgi.framework.BundleContext;

/**
 * @author gracinet
 */
public class OSGIRuntimeTestActivator extends OSGiRuntimeActivator {

    private static final Logger log = LogManager.getLogger(OSGIRuntimeTestActivator.class);

    @Override
    public void start(BundleContext context) {
        log.debug("Starting Runtime Activator");
        // create the runtime
        runtime = new OSGiRuntimeService(context);
        String tempDir = Environment.getDefault().getTemp().getAbsolutePath();
        System.setProperty("java.io.tmpdir", tempDir);
        System.setProperty(Environment.TMP_DIR, tempDir);

        // start it
        Framework.initialize(runtime);
        // register bundle component loader
        componentLoader = new OSGiComponentLoader(runtime);
        // TODO register osgi services
    }

}
