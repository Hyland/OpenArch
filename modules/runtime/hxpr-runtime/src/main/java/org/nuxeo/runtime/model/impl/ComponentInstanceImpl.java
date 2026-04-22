/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.runtime.model.impl;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.RuntimeMessage.Level;
import org.nuxeo.runtime.RuntimeMessage.Source;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Adaptable;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.ExtensionPoint;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.nuxeo.runtime.model.RuntimeContext;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class ComponentInstanceImpl implements ComponentInstance {

    private static final Logger log = LogManager.getLogger(ComponentInstanceImpl.class);

    protected Component instance;

    protected RegistrationInfo ri;

    public ComponentInstanceImpl(RegistrationInfo ri) {
        this.ri = ri;
        if (ri.getImplementation() != null) {
            instance = createInstance();
        }
    }

    @Override
    public Component getInstance() {
        return instance;
    }

    protected Component createInstance() {
        try {
            Component component = (Component) ri.getContext().loadClass(ri.getImplementation()).getDeclaredConstructor().newInstance();
            component.setName(ri.getName());
            return component;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            throw new RuntimeServiceException(e);
        }
    }

    @Override
    public void destroy() {
        deactivate();
        instance = null;
        ri = null;
    }

    @Override
    public RuntimeContext getContext() {
        return ri.getContext();
    }

    @Override
    public String getName() {
        return ri.getName();
    }

    @Override
    public void activate() {
        if (instance != null) {
            instance.activate();
        }
    }

    @Override
    public void deactivate() {
        if (instance != null) {
            instance.deactivate();
        }
    }

    /**
     * @since 9.3
     */
    @Override
    public void start() {
        if (instance != null) {
            instance.start();
        }
    }

    /**
     * @since 9.3
     */
    @Override
    public void stop() throws InterruptedException {
        if (instance != null) {
            instance.stop();
        }
    }

    @Override
    public void registerExtension(Extension extension) {
        // if this the target extension point is extending another extension point from another component, then delegate
        // the registration to the original "super" component, and issue a warn for that feature
        Optional<ExtensionPoint> optXp = ri.getExtensionPoint(extension.getExtensionPoint());
        if (optXp.isPresent()) {
            String superCo = optXp.get().getSuperComponent();
            if (superCo != null) {
                // we don't implement extension point overriding for now in new implementation of RegistrationInfo
                ((ExtensionImpl) extension).target = superCo;
                ((RegistrationInfoImpl) ri).manager.registerExtension(extension);
            }
        } else {
            String compName = extension.getComponent().getName();
            String message = "Warning: target extension point '" + extension.getExtensionPoint() + "' of '"
                    + extension.getTargetComponent() + "' is unknown. Check your extension in component "
                    + compName;
            handleError(message, Source.EXTENSION, compName, null);
        }
    }

    // TODO: cache info about implementation to avoid computing it each time
    @Override
    public void unregisterExtension(Extension extension) {
        // nothing
    }

    protected void handleError(String message, Source source, String sourceId, Exception e) {
        Exception ee = e;
        if (e != null) {
            ee = ExceptionUtils.unwrapInvoke(e);
        }
        log.error(message, ee);
        Framework.getRuntime()
                 .getMessageHandler()
                 .addMessage(new RuntimeMessage(Level.ERROR, message, source, sourceId));
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        T res = null;
        Object object = getInstance();
        if (object == null) {
            return null;
        }
        if (object instanceof Adaptable adaptable) {
            res = adaptable.getAdapter(adapter);
        } else if (adapter.isAssignableFrom(object.getClass())) {
            res = adapter.cast(object);
        }
        return res;
    }

    @Override
    public String toString() {
        if (ri == null) {
            return super.toString();
        }
        return ri.toString();
    }

    public RegistrationInfo getRegistrationInfo() {
        return ri;
    }

}
