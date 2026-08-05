/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.model;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.nuxeo.runtime.model.impl.ComponentManagerImpl;

/**
 * The component registration info.
 * <p>
 * A registration info object is keeping all the information needed to deploy a component, like the component
 * implementation, properties, dependencies and also the defined extension points and contributed extensions.
 * <p>
 * When a component is activated the registration info is creating a component instance using the current runtime
 * context.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface RegistrationInfo {

    int UNREGISTERED = 0;

    int REGISTERED = 1;

    /**
     * Component dependencies are resolved
     */
    int RESOLVED = 2;

    /**
     * Before component activation
     */
    int ACTIVATING = 3;

    int DEACTIVATING = 4;

    /**
     * Component activation successful
     */
    int ACTIVATED = 5;

    /**
     * Notification of applicationStarted fails
     *
     * @since 7.4
     */
    int START_FAILURE = 6;

    /**
     * Component was started
     *
     * @since 9.2
     */
    int STARTED = 7;

    /**
     * The component is being started
     */
    int STARTING = 8;

    /**
     * The component is being stopped
     */
    int STOPPING = 9;

    Comparator<RegistrationInfo> COMPARATOR = Comparator.comparing(RegistrationInfo::getApplicationStartedOrder)
                                                        .thenComparing(RegistrationInfo::getName);

    /**
     * Gets the runtime context that created this registration info.
     *
     * @return the runtime context
     */
    RuntimeContext getContext();

    /**
     * Gets the component properties.
     *
     * @return the component properties
     */
    Map<String, Property> getProperties();

    /**
     * Gets the list of the required components.
     *
     * @return the required components
     */
    Set<String> getRequiredComponents();

    /**
     * Gets the defined extension points.
     *
     * @return the defined extension points
     */
    ExtensionPoint[] getExtensionPoints();

    /**
     * Gets the defined extension points with name.
     *
     * @param name the extension point name to retrieve
     * @return the defined extension points with name
     * @since 9.3
     */
    default Optional<ExtensionPoint> getExtensionPoint(String name) {
        return Stream.of(getExtensionPoints()).filter(xp -> xp.getName().equals(name)).findFirst();
    }

    /**
     * Gets the name of the component.
     *
     * @return the component name
     */
    String getName();

    /**
     * Returns true if the component is not enabled.
     * <p>
     * To be deployed, a component should be enabled and should not be disabled.
     *
     * @see #isEnabled()
     */
    boolean isDisabled();

    /**
     * Returns true if the component is enabled.
     * <p>
     * To be deployed, a component should be enabled and should not be disabled.
     *
     * @see #isDisabled()
     * @since 11.5
     */
    boolean isEnabled();

    /**
     * Gets the component instance or null if the component was not yet activated.
     *
     * @return the component instance
     */
    ComponentInstance getComponent();

    /**
     * Gets the component state.
     *
     * @return the component state
     */
    int getState();

    /**
     * Checks whether this component is activated.
     *
     * @return true if the component is activated, false otherwise
     */
    boolean isActivated();

    /**
     * Checks whether this component is resolved (i.e. all its dependencies are satisfied).
     *
     * @return true if the component is resolved, false otherwise
     */
    boolean isResolved();

    /**
     * Checks whether this component is started
     *
     * @since 9.2
     */
    boolean isStarted();

    /**
     * Gets the list of provided services or null if no service is provided.
     *
     * @return an array containing the service class names or null if no service are provided
     */
    String[] getProvidedServiceNames();

    /**
     * Give the class name for the component implementation if this is a java component
     *
     * @return class name
     */
    String getImplementation();

    /**
     * The component notification order for {@link ComponentManager#start()}.
     *
     * @return the order, 1000 by default
     * @since 5.6
     */
    int getApplicationStartedOrder();

    void attach(ComponentManagerImpl manager);

    void register();

    void unregister();

    void resolve();

    void unresolve();

    boolean instantiate();

    void activate();

    void deactivate(boolean mustUnregisterExtensions);

    void start();

    void stop() throws InterruptedException;

}
