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
package com.hyland.openarch.runtime.model.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XNodeList;
import com.hyland.openarch.common.xmap.annotation.XNodeMap;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.runtime.RuntimeMessage;
import com.hyland.openarch.runtime.RuntimeMessage.Level;
import com.hyland.openarch.runtime.RuntimeMessage.Source;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.Component;
import com.hyland.openarch.runtime.model.ComponentInstance;
import com.hyland.openarch.runtime.model.Extension;
import com.hyland.openarch.runtime.model.ExtensionPoint;
import com.hyland.openarch.runtime.model.Property;
import com.hyland.openarch.runtime.model.RegistrationInfo;
import com.hyland.openarch.runtime.model.RuntimeContext;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@XObject("component")
public class RegistrationInfoImpl implements RegistrationInfo {

    private static final Logger log = LogManager.getLogger(RegistrationInfoImpl.class);

    // Note: some of these instance variables are accessed directly from other
    // classes in this package.

    ComponentManagerImpl manager;

    @XNode("@service")
    ServiceDescriptor serviceDescriptor;

    // the managed object name
    @XNode("@name")
    String name;

    @XNode("@disable")
    protected boolean disable;

    // @since 11.5
    @XNode("@enable")
    protected boolean enable = true;

    // the registration state
    int state = UNREGISTERED;

    // the object names I depend of
    @XNodeList(value = "require", type = HashSet.class, componentType = String.class)
    Set<String> requires = new HashSet<>();

    @XNode("implementation@class")
    String implementation;

    @XNodeList(value = "extension-point", type = ExtensionPointImpl[].class, componentType = ExtensionPointImpl.class)
    ExtensionPointImpl[] extensionPoints = new ExtensionPointImpl[0];

    @XNodeList(value = "extension", type = ExtensionImpl[].class, componentType = ExtensionImpl.class)
    ExtensionImpl[] extensions = new ExtensionImpl[0];

    @XNodeMap(value = "property", key = "@name", type = HashMap.class, componentType = Property.class)
    Map<String, Property> properties = new HashMap<>();

    RuntimeContext context;

    // the managed component
    ComponentInstance component;

    public RegistrationInfoImpl() {
    }

    /**
     * Useful when dynamically registering components
     *
     * @param name the component name
     */
    public RegistrationInfoImpl(String name) {
        this.name = name;
    }

    @Override
    public void attach(ComponentManagerImpl manager) {
        if (this.manager != null) {
            throw new IllegalStateException("Registration '" + name + "' was already attached to a manager");
        }
        this.manager = manager;
    }

    public void setContext(RuntimeContext context) {
        this.context = context;
    }

    @Override
    public boolean isDisabled() {
        return disable;
    }

    @Override
    public boolean isEnabled() {
        return enable;
    }

    public void destroy() {
        requires.clear();
        properties.clear();
        extensionPoints = new ExtensionPointImpl[0];
        extensions = new ExtensionImpl[0];
        component = null;
        name = null;
        manager = null;
    }

    @Override
    public ExtensionPoint[] getExtensionPoints() {
        return extensionPoints;
    }

    @Override
    public ComponentInstance getComponent() {
        return component;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Property> getProperties() {
        return properties;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public Set<String> getRequiredComponents() {
        return requires;
    }

    @Override
    public RuntimeContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "RegistrationInfo: " + name;
    }

    @Override
    public synchronized void register() {
        if (state != UNREGISTERED) {
            return;
        }
        state = REGISTERED;
    }

    @Override
    public synchronized void unregister() {
        if (state == UNREGISTERED) {
            return;
        }
        if (state == ACTIVATED || state == RESOLVED || state == START_FAILURE) {
            unresolve();
        }
        state = UNREGISTERED;
        destroy();
    }

    @Override
    public int getApplicationStartedOrder() {
        if (component == null) {
            return 0;
        }
        Component ci = component.getInstance();
        if (ci == null) {
            return 0;
        }
        return ci.getApplicationStartedOrder();
    }

    @Override
    public synchronized void start() {
        if (state != ACTIVATED) {
            return;
        }
        state = STARTING;
        try {
            if (component != null) {
                component.start();
            }
            log.debug("Component started: {}", name);
            state = STARTED;
        } catch (RuntimeException e) {
            String message = String.format("Component %s notification of application started failed: %s",
                    component == null ? null : component.getName(), e.getMessage());
            log.error(message, e);
            Framework.getRuntime()
                     .getMessageHandler()
                     .addMessage(
                             new RuntimeMessage(Level.ERROR, message, Source.COMPONENT,
                                     Optional.ofNullable(component)
                                             .map(ComponentInstance::getName)
                                             .orElse("Component is null")));
            state = START_FAILURE;
        }
    }

    @Override
    public boolean isStarted() {
        return state == STARTED;
    }

    @Override
    public synchronized void stop() throws InterruptedException {
        // TODO use timeout
        if (state != STARTED) {
            return;
        }
        state = STOPPING;
        if (component != null) {
            component.stop();
        }
        log.debug("Component stopped: {}", name);
        state = ACTIVATED;
    }

    /**
     * Instantiates corresponding component.
     * <p>
     * Allows registering listeners on ComponentManager at component instantiation, before all components activation.
     * <p>
     * Should be called before {@link #activate()}.
     *
     * @return false in case of error during instantiation, true otherwise.
     * @since 11.3
     */
    @Override
    public synchronized boolean instantiate() {
        if (state != RESOLVED) {
            return false;
        }
        try {
            component = new ComponentInstanceImpl(this);
            return true;
        } catch (RuntimeException e) {
            String msg = "Failed to instantiate component: " + implementation;
            log.error(msg, e);
            msg += " (" + e.toString() + ')';
            Framework.getRuntime()
                     .getMessageHandler()
                     .addMessage(new RuntimeMessage(Level.ERROR, msg, Source.COMPONENT, getName()));
            return false;
        }
    }

    @Override
    public synchronized void activate() {
        if (state != RESOLVED) {
            return;
        }

        state = ACTIVATING;

        // activate component
        try {
            component.activate();
        } catch (RuntimeException e) {
            String msg = "Failed to activate component: " + implementation;
            log.error(msg, e);
            msg += " (" + e.toString() + ')';
            Framework.getRuntime()
                     .getMessageHandler()
                     .addMessage(new RuntimeMessage(Level.ERROR, msg, Source.COMPONENT, getName()));
            return;
        }

        log.debug("Component activated: {}", name);
        state = ACTIVATED;

        // register contributed extensions if any
        if (extensions != null) {
            for (Extension xt : checkExtensions()) {
                xt.setComponent(component);
                try {
                    manager.registerExtension(xt);
                } catch (RuntimeException e) {
                    String compName = xt.getComponent().getName();
                    String msg = "Failed to register extension to: " + xt.getTargetComponent() + ", xpoint: "
                            + xt.getExtensionPoint() + " in component: " + compName;
                    log.error(msg, e);
                    msg += " (" + e.toString() + ')';
                    Framework.getRuntime()
                             .getMessageHandler()
                             .addMessage(new RuntimeMessage(Level.ERROR, msg, Source.EXTENSION, compName));
                }
            }
        }

        // register pending extensions if any

        Set<Extension> pendingExt = manager.pendingExtensions.remove(name);
        if (pendingExt != null) {
            for (Extension xt : pendingExt) {
                try {
                    manager.register(this, xt);
                    component.registerExtension(xt);
                } catch (RuntimeException e) {
                    String compName = xt.getComponent().getName();
                    String msg = "Failed to register extension to: " + xt.getTargetComponent() + ", xpoint: "
                            + xt.getExtensionPoint() + " in component: " + compName;
                    log.error(msg, e);
                    msg += " (" + e.toString() + ')';
                    Framework.getRuntime()
                             .getMessageHandler()
                             .addMessage(new RuntimeMessage(Level.ERROR, msg, Source.EXTENSION, compName));
                }
            }
        }
    }

    /**
     * Deactivate the component. If mustUnregisterExtensions is false then the call was made by the manager because all
     * components are stopped (and deactivated) so the extension unregister should not be done (this will speedup the
     * stop and also fix broken component which are not correctly defining dependencies.)
     *
     * @since 9.2
     */
    @Override
    public synchronized void deactivate(boolean mustUnregisterExtensions) {
        if (state != ACTIVATED && state != START_FAILURE) {
            return;
        }

        state = DEACTIVATING;

        if (mustUnregisterExtensions && extensions != null) {
            // unregister contributed extensions if any
            for (Extension xt : extensions) {
                try {
                    manager.unregisterExtension(xt);
                } catch (RuntimeException e) {
                    String msg = "Failed to unregister extension. Contributor: " + xt.getComponent() + " to "
                            + xt.getTargetComponent() + "; xpoint: " + xt.getExtensionPoint();
                    log.error(msg, e);
                    Framework.getRuntime()
                            .getMessageHandler()
                            .addMessage(new RuntimeMessage(Level.ERROR, msg, Source.EXTENSION,
                                    xt.getComponent().getName()));
                }
            }
        }

        component.deactivate();
        log.debug("Component deactivated: {}", name);

        component = null;

        state = RESOLVED;
    }

    @Override
    public synchronized void resolve() {
        if (state != REGISTERED) {
            return;
        }

        // register services
        manager.registerServices(this);

        // components are no more automatically activated when resolved. see ComponentManager#start()
        state = RESOLVED;
    }

    @Override
    public synchronized void unresolve() {
        if (state == REGISTERED || state == UNREGISTERED) {
            return;
        }

        // un-register services
        manager.unregisterServices(this);

        // components are no more automatically deactivated when unresolved. see ComponentManager#stop()
        state = REGISTERED;
    }

    @Override
    // not synchronized, intermediate states from other synchronized methods
    // are not a problem
    public boolean isActivated() {
        return state == ACTIVATED;
    }

    @Override
    // not synchronized, intermediate states from other synchronized methods
    // are not a problem
    public boolean isResolved() {
        return state == RESOLVED;
    }

    @Override
    public String[] getProvidedServiceNames() {
        if (serviceDescriptor != null) {
            return serviceDescriptor.services;
        }
        return null;
    }

    public ServiceDescriptor getServiceDescriptor() {
        return serviceDescriptor;
    }

    @Override
    public String getImplementation() {
        return implementation;
    }

    /**
     * Checks extensions and returns only valid ones.
     *
     * @since 11.3
     */
    protected Extension[] checkExtensions() {
        var validExtensions = new ArrayList<Extension>();
        for (ExtensionImpl xt : extensions) {
            if (xt.target == null) {
                String msg = String.format(
                        "Bad extension declaration (no target attribute specified) on component '%s'", getName());
                Framework.getRuntime()
                         .getMessageHandler()
                         .addMessage(new RuntimeMessage(Level.ERROR, msg, Source.EXTENSION, getName()));
                continue;
            } else {
                validExtensions.add(xt);
            }
        }
        return validExtensions.toArray(Extension[]::new);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof RegistrationInfo registrationInfo) {
            return name.equals(registrationInfo.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
