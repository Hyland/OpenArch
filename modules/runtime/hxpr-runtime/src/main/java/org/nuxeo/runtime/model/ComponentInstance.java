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

/**
 * A component instance is a proxy to the component implementation object.
 * <p>
 * Component instance objects are created each time a component is activated, and destroyed at component deactivation.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface ComponentInstance extends Extensible, Adaptable {

    /**
     * Gets the actual component implementation instance.
     *
     * @return the component implementation instance
     */
    Component getInstance();

    /**
     * Gets the name of the component.
     *
     * @return the component name
     */
    String getName();

    /**
     * Gets the runtime context attached to this instance.
     *
     * @return the runtime context
     */
    RuntimeContext getContext();

    /**
     * Activates the implementation instance.
     */
    void activate();

    /**
     * Starts the implementation instance.
     *
     * @since 9.3
     */
    void start();

    /**
     * Stops the implementation instance.
     *
     * @since 9.3
     */
    void stop() throws InterruptedException;

    /**
     * Deactivates the implementation instance.
     */
    void deactivate();

    /**
     * Destroys this instance.
     */
    void destroy();

}
