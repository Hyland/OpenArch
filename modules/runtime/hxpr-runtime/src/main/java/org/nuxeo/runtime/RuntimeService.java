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
package org.nuxeo.runtime;

import java.io.File;
import java.util.Properties;

import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.RuntimeContext;

/**
 * The runtime service: a singleton object that provides access to the Nuxeo Runtime. The runtime service must be
 * started before any other runtime component or object that accesses the runtime.
 * <p>
 * This service is usually implemented for each target platform where Nuxeo Runtime should run.
 * <p>
 * After the runtime service was initialized, it may be accessed through the facade class
 * {@link org.nuxeo.runtime.api.Framework}.
 * <p>
 * See: {@link org.nuxeo.runtime.api.Framework}
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface RuntimeService {

    /**
     * Starts the runtime.
     */
    void start();

    /**
     * Stops the runtime.
     */
    void stop() throws InterruptedException;

    /**
     * Gets the home directory of the runtime.
     *
     * @return the home directory
     */
    File getHome();

    /**
     * Gets the name of this runtime service.
     *
     * @return the runtime service name
     */
    String getName();

    /**
     * Gets the description of this runtime service.
     *
     * @return the runtime service description
     */
    String getDescription();

    /**
     * Gets the version of this runtime service.
     *
     * @return the runtime service version
     */
    Version getVersion();

    /**
     * Gets runtime service properties.
     *
     * @return the runtime properties
     */
    Properties getProperties();

    /**
     * Gets a runtime service property given its name.
     *
     * @param name the property name
     * @return the property value if any or null if none
     */
    String getProperty(String name);

    /**
     * Gets a property value using a default value if the property was not set.
     *
     * @param name the property name
     * @param defaultValue the default value to use when the property doesn't exists
     * @return the property value
     */
    String getProperty(String name, String defaultValue);

    /**
     * Replaces any substring in the form <code>${property.name}</code> with the corresponding runtime property value if
     * any, otherwise leaves the substring unchanged.
     *
     * @param expression the expression to process
     * @return the expanded expression
     */
    String expandVars(String expression);

    /**
     * Gets the component manager.
     *
     * @return the component manager
     */
    ComponentManager getComponentManager();

    /**
     * Gets a component given its name.
     *
     * @param name the component name
     * @return the component, or null if no such component was registered
     */
    Component getComponent(String name);

    /**
     * Gets the context of the runtime bundle.
     *
     * @return the context object
     */
    RuntimeContext getContext();

    /**
     * Gets the service of type serviceClass if such a service was declared by a resolved runtime component.
     * <p>
     * If the component is not yet activated, it will be prior to return the service.
     *
     * @param <T> the service type
     * @param serviceClass the service class
     * @return the service object
     */
    <T> T getService(Class<T> serviceClass);

    /**
     * Gets the runtime message handler. You can add new messages or just retrieve them.
     * <p>
     * Warning messages don't block server startup, but error messages do in strict mode.
     *
     * @return the message handler for runtime.
     * @since 9.10
     */
    RuntimeMessageHandler getMessageHandler();

    /**
     * Computes the runtime status, adds it to the given string builder, and returns true if some problems have been
     * detected.
     *
     * @since 5.6
     * @return true if there are warnings/errors on current runtime.
     */
    boolean getStatusMessage(StringBuilder msg);

    /**
     * Returns true if some problems have been detected at runtime startup.
     *
     * @since TODO
     * @see #getStatusMessage(StringBuilder)
     */
    boolean hasErrors();

    /**
     * @since 7.4
     */
    void setProperty(String name, Object value);

}
