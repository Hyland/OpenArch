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

package org.nuxeo.runtime.osgi;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.XMap;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.model.impl.RegistrationInfoImpl;
import org.nuxeo.runtime.model.impl.XMapContext;
import org.osgi.framework.Bundle;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class OSGiRuntimeContext implements RuntimeContext {

    private static final Logger log = LogManager.getLogger(OSGiRuntimeContext.class);

    protected final Bundle bundle;

    private final RuntimeService runtime;

    private final XMap registrationInfoImplXMap;

    private List<String> components;

    protected Bundle hostBundle;

    public OSGiRuntimeContext(Bundle bundle, OSGiRuntimeService runtime) {
        this.bundle = bundle;
        this.runtime = runtime;
        components = new ArrayList<>();
        registrationInfoImplXMap = new XMap();
        registrationInfoImplXMap.register(RegistrationInfoImpl.class);
    }

    @Override
    public RuntimeService getRuntime() {
        return runtime;
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public URL getResource(String name) {
        URL url = bundle.getResource(name);
        if (url == null) {
            url = Framework.getResourceLoader().getResource(name);
        }
        return url;
    }

    @Override
    public URL getLocalResource(String name) {
        URL url = bundle.getEntry(name);
        if (url == null) {
            url = Framework.getResourceLoader().getResource(name);
        }
        return url;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return bundle.loadClass(className);
        } catch (ClassNotFoundException e) {
            return Framework.getResourceLoader().loadClass(className);
        }
    }

    @Override
    public RegistrationInfo deploy(URL url) throws IOException {
        RegistrationInfo ri = createRegistrationInfo(url);
        log.debug("Deploying component from url {}", url);
        runtime.getComponentManager().register(ri);
        components.add(ri.getName());
        return ri;
    }

    @Override
    public RegistrationInfo deploy(String location) {
        URL url = getLocalResource(location);
        if (url == null) {
            throw new IllegalArgumentException("No local resources was found with this name: " + location);
        }
        try {
            return deploy(url);
        } catch (IOException e) {
            throw new RuntimeServiceException("Cannot deploy: " + location, e);
        }
    }

    private RegistrationInfo createRegistrationInfo(URL url) throws IOException {
        String source;
        try (InputStream stream = url.openStream()) {
            source = IOUtils.toString(stream, UTF_8);
        }
        if (source.isBlank()) {
            throw new IOException("Empty registration from " + url);
        }
        String expanded = Framework.expandVars(source);
        try (InputStream in = new ByteArrayInputStream(expanded.getBytes(UTF_8))) {
            RegistrationInfoImpl ri = createRegistrationInfo(this, in);
            if (ri == null || ri.getName() == null) {
                throw new IOException("Could not resolve registration from " + url);
            }
            return ri;
        } catch (IOException e) {
            throw new IOException(
                    String.format("Could not resolve registration from %s (%s)", url, e.getMessage()), e);
        }
    }

    private RegistrationInfoImpl createRegistrationInfo(RuntimeContext ctx, InputStream in) throws IOException {
        Object[] result = registrationInfoImplXMap.loadAll(new XMapContext(ctx), in);
        if (result.length > 0) {
            if (result[0] instanceof RegistrationInfoImpl ri) {
                ri.setContext(this);
                return ri;
            }
            throw new IOException(String.format(
                    "Expected \"<component>\" tag for component registration, resolved object '%s' instead.",
                    result[0]));
        }
        return null;
    }

    @Override
    public void destroy() {
        ComponentManager mgr = runtime.getComponentManager();
        for (String cname : components) {
            mgr.unregister(cname);
        }
    }

}
