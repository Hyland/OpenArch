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

import com.hyland.openarch.osgi.BundleFile;
import com.hyland.openarch.osgi.BundleImpl;
import com.hyland.openarch.osgi.OSGiAdapter;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;

/**
 * @author gracinet
 */
public class RootRuntimeBundle extends BundleImpl {

    public RootRuntimeBundle(OSGiAdapter osgi, BundleFile file, ClassLoader loader)
            throws BundleException {
        super(osgi, file, loader);
    }

    @Override
    public BundleActivator getActivator() {
        if (activator == null) {
            activator = new OSGIRuntimeTestActivator();
        }
        return activator;
    }

}
