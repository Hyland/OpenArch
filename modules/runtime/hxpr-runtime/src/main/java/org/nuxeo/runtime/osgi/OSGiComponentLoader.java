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
package org.nuxeo.runtime.osgi;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

/**
 * @author Bogdan Stefanescu
 * @author Ian Smith
 * @author Florent Guillaume
 */
public class OSGiComponentLoader implements BundleListener {

    private static final Logger log = LogManager.getLogger(OSGiComponentLoader.class);

    private final OSGiRuntimeService runtime;

    public OSGiComponentLoader(OSGiRuntimeService runtime) {
        this.runtime = runtime;
        install();
    }

    public void install() {
        BundleContext ctx = runtime.getBundleContext();
        ctx.addBundleListener(this);
        Bundle[] bundles = ctx.getBundles();
        int mask = Bundle.STARTING | Bundle.ACTIVE;
        for (Bundle bundle : bundles) {
            String name = bundle.getSymbolicName();
            int state = bundle.getState();
            log.debug("Install bundle: {} {}", () -> name, () -> bundleStateAsString(state));
            // check only resolved bundles
            if ((state & mask) != 0) {
                // check only bundles containing nuxeo components
                if (OSGiRuntimeService.getComponentsList(bundle) != null) {
                    log.debug("Install bundle: {} component list: {}", name,
                            OSGiRuntimeService.getComponentsList(bundle));
                    try {
                        runtime.createContext(bundle);
                    } catch (RuntimeException e) {
                        // only log error to keep on deploying other bundles
                        log.error(e, e);
                    }
                } else {
                    log.debug("Install bundle: {} has no components", name);
                }
            } else {
                log.debug("Install bundle: {} is not STARTING or ACTIVE, so no context was created", name);
            }
        }
    }

    public void uninstall() {
        runtime.getBundleContext().removeBundleListener(this);
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        int type = event.getType();
        if (type != BundleEvent.STARTING) {
            return;
        }
        String name = event.getBundle().getSymbolicName();
        Bundle bundle = event.getBundle();
        String componentsList = OSGiRuntimeService.getComponentsList(bundle);
        if (componentsList != null) {
            log.trace("Bundle changed: {} STARTING with components: {}", name, componentsList);
            try {
                runtime.createContext(bundle);
            } catch (RuntimeException e) {
                // only log error to keep on deploying other bundles
                log.error(e, e);
            }
        } else {
            log.trace("Bundle changed: {} STARTING with no components", name);
        }
    }

    /**
     * Used for generating good debug info. Convert bit vector into printable string.
     *
     * @param state bitwise-or of UNINSTALLED, INSTALLED, RESOLVED, STARTING, STOPPING, and ACTIVE
     * @return printable version of bits that are on
     */
    public static String bundleStateAsString(int state) {
        List<String> list = new LinkedList<>();
        if ((state & Bundle.UNINSTALLED) != 0) {
            list.add("UNINSTALLED");
        }
        if ((state & Bundle.INSTALLED) != 0) {
            list.add("INSTALLED");
        }
        if ((state & Bundle.RESOLVED) != 0) {
            list.add("RESOLVED");
        }
        if ((state & Bundle.STARTING) != 0) {
            list.add("STARTING");
        }
        if ((state & Bundle.STOPPING) != 0) {
            list.add("STOPPING");
        }
        if ((state & Bundle.ACTIVE) != 0) {
            list.add("ACTIVE");
        }
        return '[' + String.join(",", list) + ']';
    }

}
