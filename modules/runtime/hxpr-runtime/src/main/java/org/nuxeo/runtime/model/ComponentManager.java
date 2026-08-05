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
package org.nuxeo.runtime.model;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.nuxeo.common.xmap.registry.Registry;
import org.nuxeo.common.xmap.registry.XRegistry;

/**
 * Service managing components registration and processing.
 */
public interface ComponentManager {

    /**
     * Handles the registration of the given registration info.
     * <p>
     * This is called by the main registry when all dependencies of this registration info were solved and the object
     * can be registered.
     * <p>
     * If true is returned, the object will be added to the main registry under the name given in RegistrationInfo.
     *
     * @param ri the registration info
     */
    void register(RegistrationInfo ri);

    /**
     * Unregisters a component given its name.
     *
     * @param name the component name
     */
    void unregister(String name);

    /**
     * Gets object instance managed by the named component.
     *
     * @param name the object name
     * @return the object instance if any. may be null
     */
    ComponentInstance getComponent(String name);

    /**
     * Gets the registered components.
     *
     * @return a read-only collection of components
     */
    Collection<RegistrationInfo> getRegistrations();

    /**
     * Gets the pending registrations and their dependencies.
     *
     * @return the pending registrations
     */
    Map<String, Set<String>> getPendingRegistrations();

    /**
     * Returns the missing registrations, linked to missing target extension points.
     *
     * @since 8.10
     */
    Map<String, Set<Extension>> getMissingRegistrations();

    /**
     * Gets the pending extensions by component.
     *
     * @return the pending extensions
     */
    Collection<String> getActivatingRegistrations();

    /**
     * Gets the components that fail on applicationStarted notification
     *
     * @since 7.4
     */
    Collection<String> getStartFailureRegistrations();

    /**
     * Gets the number of registered objects in this registry.
     *
     * @return the number of registered objects
     */
    int size();

    /**
     * Shuts down the component registry.
     * <p>
     * This unregisters all objects registered in this registry.
     */
    void shutdown();

    /**
     * Gets the service of type serviceClass if such a service was declared by a resolved runtime component.
     * <p>
     * If the component is not yet activated it will be prior to return the service.
     *
     * @param <T> the service type
     * @param serviceClass the service class
     * @return the service object
     */
    <T> T getService(Class<T> serviceClass);

    /**
     * Get the list of all registered service names An empty array is returned if no registered services are found.
     *
     * @return an array of registered service.
     */
    String[] getServices();

    /**
     * Gets the component that provides the given service.
     *
     * @param serviceClass the service class
     * @return the component or null if none
     */
    ComponentInstance getComponentProvidingService(Class<?> serviceClass);

    /**
     * Activate and start all resolved components. If components were already started do nothing.
     *
     * @return false if components were already started, true otherwise
     * @since 9.2
     */
    boolean start();

    /**
     * Stop and deactivate all resolved components. If components were not yet started do nothing
     *
     * @return false if components were not yet started, true otherwise
     * @since 9.2
     */
    boolean stop();

    /**
     * Same as {@link #stop()} but log a warning if the timeout is reached while stopping components
     *
     * @since 9.2
     */
    void stop(int timeout);

    /**
     * Make a snapshot of the component registry. When calling restart
     *
     * @since 9.2
     */
    void snapshot();

    /**
     * Optionally reset the registry to the last snapshot and restart the components.
     * <p>
     * When restarting components all components will be stopped, deactivated and re-instantiated. It means that all
     * references to components before a restart will become invalid after the restart.
     * <p>
     * If no snapshot was created then the components will be restarted without changing the registry.
     * <p>
     * If the <code>reset</code> argument is true then the registry will be reverted to the last snapshot before
     * starting the components.
     *
     * @param reset whether or not to revert to the last snapshot
     * @since 9.2
     */
    void restart(boolean reset);

    /**
     * Reset the registry to the last snapshot if any and stop the components (if they are currently started). After a
     * reset all the components are stopped so we can contribute new components if needed. You must call
     * {@link #start()} to start again the components
     *
     * @return true if the components were stopped, false otherwise
     * @since 9.2
     */
    boolean reset();

    /**
     * Refresh the registry using stashed registrations if any. The
     * registry will be reverted to the last snapshot before applying the stash.
     * <p>
     * If the stash is empty it does nothing and return true, otherwise it will:
     * <ol>
     * <li>stop the components (if they are started)
     * <li>revert to the last snapshot
     * <li>apply the stash (the stash will remain empty after this operation)
     * <li>start the components (if they was started)
     * </ol>
     *
     * @return false if stash is empty and nothing was done, true otherwise
     * @since 9.2
     */
    boolean refresh();

    /**
     * Tests whether the components are running. That means they are either started either in standby mode.
     *
     * @since 9.2
     */
    boolean isRunning();

    /**
     * Tests whether components were deployed over the initial snapshot (i.e. the actual registry differs from the
     * snapshot) If no snapshot was done returns false.
     *
     * @since 9.2
     */
    boolean hasChanged();

    /**
     * Check if a snapshot was done
     *
     * @return true if a snapshot already exists, false otherwise
     * @since 9.2
     */
    boolean hasSnapshot();

    /**
     * Add a listener to be notified on manager actions like start / stop components.
     *
     * @since 9.2
     */
    void addListener(ComponentManager.Listener listener);

    /**
     * Remove the component manager listener previously added by {@link #addListener(Listener)}. If the listener were
     * not added then nothing is done.
     *
     * @since 9.2
     */
    void removeListener(ComponentManager.Listener listener);

    /**
     * Listener interface for component manager events
     *
     * @author bogdan
     * @since 9.2
     */
    interface Listener {

        /**
         * Called just before activating components.
         *
         * @since 11.5
         */
        default void beforeRuntimeDeactivation(ComponentManager mgr) {
            // nothing
        }

        /**
         * Called just before starting components.
         *
         * @param isResume true if the event was initiated by a {@link ComponentManager#resume()} call, false otherwise.
         * @since 11.5
         */
        default void beforeRuntimeStart(ComponentManager mgr) {
            // nothing
        }

        /**
         * Called just after all components were started
         *
         * @param isResume true if the event was initiated by a {@link ComponentManager#resume()} call, false otherwise.
         * @since 11.5
         */
        default void afterRuntimeStart(ComponentManager mgr) {
            // nothing
        }

        /**
         * Called just before stopping components.
         *
         * @param isStandby true if the event was initiated by a {@link ComponentManager#standby()} call, false
         *            otherwise
         * @since 11.5
         */
        default void beforeRuntimeStop(ComponentManager mgr) {
            // nothing
        }
    }

    /**
     * Returns the registry for this extension point, typically built thanks to {@link XRegistry} annotation on
     * corresponding descriptor class.
     *
     * @since 11.5
     */
    <T extends Registry> Optional<T> getExtensionPointRegistry(String component, String point);

}
