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
 *     Bogdan Stefanescu
 *     Anahide Tchertchian
 */

package org.nuxeo.runtime.model;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;

import org.nuxeo.common.xmap.registry.MapRegistry;
import org.nuxeo.common.xmap.registry.Registry;
import org.nuxeo.common.xmap.registry.SingleRegistry;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.RuntimeMessage.Level;
import org.nuxeo.runtime.RuntimeMessage.Source;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.impl.ComponentInstanceImpl;

/**
 * Default implementation for a component, to be extended by contributed components.
 */
public class DefaultComponent implements Component, Adaptable {

    /** @since 10.3 */
    private String name;

    @Override
    public void setName(String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public void activate() {
        // nothing
    }

    @Override
    public void deactivate() {
        // nothing
    }

    @Override
    public void start() {
        // nothing
    }

    @Override
    public void stop() throws InterruptedException {
        // nothing
    }

    protected void addRuntimeMessage(Level level, String message) {
        addRuntimeMessage(level, message, Source.COMPONENT, name);
    }

    protected void addRuntimeMessage(Level level, String message, Source source, String sourceComponent) {
        Framework.getRuntime()
                 .getMessageHandler()
                 .addMessage(new RuntimeMessage(level, message, source, sourceComponent));
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        return adapter.cast(this);
    }

    protected String getProperty(String property) {
        ComponentManager componentManager = Framework.getRuntime().getComponentManager();
        ComponentInstanceImpl component = (ComponentInstanceImpl) componentManager.getComponent(name);
        Property p = component.getRegistrationInfo().getProperties().get(property);
        return p == null ? null : p.getValue();
    }

    /**
     * Returns the registry for given extension point of this component.
     *
     * @since 11.5
     */
    @SuppressWarnings("unchecked")
    protected <T extends Registry> T getExtensionPointRegistry(String point) {
        return (T) Framework.getRuntime()
                            .getComponentManager()
                            .getExtensionPointRegistry(name, point)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    String.format("Unknown registry for extension point '%s--%s'", name, point)));
    }

    /**
     * Returns a single contribution from given target extension point.
     * <p>
     * Assumes the registry implements {@link SingleRegistry}.
     *
     * @since 11.5
     */
    protected <T> Optional<T> getRegistryContribution(String point) {
        SingleRegistry registry = getExtensionPointRegistry(point);
        return registry.getContribution();
    }

    /**
     * Returns a single contribution with given id from given target extension point.
     * <p>
     * Assumes the registry implements {@link MapRegistry}.
     *
     * @since 11.5
     */
    protected <T> Optional<T> getRegistryContribution(String point, String id) {
        MapRegistry registry = getExtensionPointRegistry(point);
        return registry.getContribution(id);
    }

    /**
     * Returns a list of contributions from given target extension point.
     * <p>
     * Assumes the registry implements {@link MapRegistry}.
     *
     * @since 11.5
     */
    protected <T> List<T> getRegistryContributions(String point) {
        MapRegistry registry = getExtensionPointRegistry(point);
        return registry.getContributionValues();
    }

}
