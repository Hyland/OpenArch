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
package org.nuxeo.runtime.model.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.model.RegistrationInfo;

/**
 * This class is synchronized to safely update and access the different maps managed by the registry
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class ComponentRegistry {

    private final Logger log = LogManager.getLogger(ComponentRegistry.class);

    /**
     * All registered components including unresolved ones. You can check the state of a component for getting the
     * unresolved ones.
     */
    protected Map<String, RegistrationInfo> components;

    /**
     * The list of resolved components. We need to use a linked hash map preserve the resolve order. We don't use a
     * simple list to optimize removal by name (used by unregister operations).
     *
     * @since 9.2
     */
    protected LinkedHashMap<String, RegistrationInfo> resolved;

    /**
     * Maps a component name to a set of component names that are depending on that component.
     */
    protected MappedSet requirements;

    /**
     * Map pending components to the set of unresolved components they are waiting for.
     */
    protected MappedSet pendings;

    public ComponentRegistry() {
        components = new HashMap<>();
        requirements = new MappedSet();
        pendings = new MappedSet();
        resolved = new LinkedHashMap<>();
    }

    public ComponentRegistry(ComponentRegistry reg) {
        components = new HashMap<>(reg.components);
        requirements = new MappedSet(reg.requirements);
        pendings = new MappedSet(reg.pendings);
        resolved = new LinkedHashMap<>(reg.resolved);
    }

    public synchronized void destroy() {
        components = null;
        requirements = null;
        pendings = null;
    }

    public final synchronized boolean isResolved(String name) {
        RegistrationInfo ri = components.get(name);
        if (ri == null) {
            return false;
        }
        return ri.getState() > RegistrationInfo.REGISTERED;
    }

    /**
     * @return true if the component was resolved, false if the component is pending
     */
    public synchronized boolean addComponent(RegistrationInfo ri) {
        String name = ri.getName();
        log.trace("Registering component: {}", () -> name);
        ri.register();
        // map the source id with the component name - see ComponentManager.unregisterByLocation
        components.put(name, ri);
        boolean hasUnresolvedDependencies = computePendings(ri);
        if (!hasUnresolvedDependencies) {
            resolveComponent(ri);
            return true;
        }
        return false;
    }

    public synchronized RegistrationInfo removeComponent(String name) {
        RegistrationInfo ri = components.remove(name);
        if (ri != null) {
            try {
                unresolveComponent(ri);
            } finally {
                ri.unregister();
            }
        }
        return ri;
    }

    /**
     * @return an unmodifiable collection of resolved registration infos, sorted by {@link LinkedHashMap}
     * @since 9.2
     */
    public synchronized Collection<RegistrationInfo> getResolvedRegistrationInfo() {
        return Collections.unmodifiableCollection(resolved.values());
    }

    /**
     * @return an unmodifiable collection of resolved component names, sorted by {@link LinkedHashMap}
     * @since 9.2
     */
    public synchronized Collection<String> getResolvedNames() {
        return Collections.unmodifiableCollection(resolved.keySet());
    }

    /**
     * @return an unmodifiable collection of missing dependencies
     * @since 9.2
     */
    public synchronized Set<String> getMissingDependencies(String name) {
        return Collections.unmodifiableSet(pendings.get(name));
    }

    /**
     * Get the registration info for the given component name or null if none was registered.
     *
     * @since 9.2
     */
    public synchronized RegistrationInfo getComponent(String name) {
        return components.get(name);
    }

    /**
     * Check if the component is already registered against this registry
     */
    public synchronized boolean contains(String name) {
        return components.containsKey(name);
    }

    /**
     * Get the registered components count
     */
    public synchronized int size() {
        return components.size();
    }

    /**
     * @return an unmodifiable collection of registered components
     */
    public synchronized Collection<RegistrationInfo> getComponents() {
        return Collections.unmodifiableCollection(components.values());
    }

    /**
     * Get a copy of the registered components as an array.
     */
    public synchronized RegistrationInfo[] getComponentsArray() {
        return components.values().toArray(new RegistrationInfo[0]);
    }

    /**
     * @return an unmodifiable map of pending components
     */
    public synchronized Map<String, Set<String>> getPendingComponents() {
        return Collections.unmodifiableMap(pendings.map);
    }

    /**
     * Fill the pending map with all unresolved dependencies of the given component. Returns false if no unresolved
     * dependencies are found, otherwise returns true.
     */
    protected final boolean computePendings(RegistrationInfo ri) {
        Set<String> set = ri.getRequiredComponents();
        if (set == null || set.isEmpty()) {
            return false;
        }
        boolean hasUnresolvedDependencies = false;
        // fill the requirements and pending map
        for (String name : set) {
            if (!isResolved(name)) {
                pendings.put(ri.getName(), name);
                hasUnresolvedDependencies = true;
            }
            requirements.put(name, ri.getName());
        }
        return hasUnresolvedDependencies;
    }

    protected void resolveComponent(RegistrationInfo ri) {
        String riName = ri.getName();

        ri.resolve();
        resolved.put(ri.getName(), ri); // track resolved components

        // try to resolve pending components that are waiting the newly resolved component
        Set<String> dependsOnMe = new HashSet<>();
        Set<String> reqs = requirements.get(riName);
        if (reqs != null) {
            dependsOnMe.addAll(reqs);
        }
        if (dependsOnMe.isEmpty()) {
            return;
        }
        for (String name : dependsOnMe) {
            pendings.remove(name, riName);
            Set<String> set = pendings.get(name);
            if (set == null || set.isEmpty()) {
                RegistrationInfo waitingRi = components.get(name);
                resolveComponent(waitingRi);
            }
        }
    }

    protected void unresolveComponent(RegistrationInfo ri) {
        Set<String> reqs = ri.getRequiredComponents();
        String name = ri.getName();
        ri.unresolve();
        resolved.remove(name);
        pendings.remove(name);
        if (reqs != null) {
            for (String req : reqs) {
                requirements.remove(req, name);
            }
        }
        Set<String> set = requirements.get(name);
        if (set != null && !set.isEmpty()) {
            for (String dep : set.toArray(new String[0])) {
                RegistrationInfo depRi = components.get(dep);
                if (depRi != null) {
                    unresolveComponent(depRi);
                }
            }
        }
    }

    protected static class MappedSet {

        protected Map<String, Set<String>> map;

        public MappedSet() {
            map = new HashMap<>();
        }

        /**
         * Create a clone of a mapped set (set values are cloned too)
         */
        public MappedSet(MappedSet mset) {
            this();
            for (Map.Entry<String, Set<String>> entry : mset.map.entrySet()) {
                String name = entry.getKey();
                Set<String> set = entry.getValue();
                Set<String> newSet = new HashSet<>(set);
                map.put(name, newSet);
            }
        }

        public Set<String> get(String name) {
            return map.get(name);
        }

        public Set<String> put(String key, String value) {
            Set<String> set = map.computeIfAbsent(key, k -> new HashSet<>());
            set.add(value);
            return set;
        }

        public Set<String> remove(String key) {
            return map.remove(key);
        }

        public Set<String> remove(String key, String value) {
            Set<String> set = map.get(key);
            if (set != null) {
                set.remove(value);
                if (set.isEmpty()) {
                    map.remove(key);
                }
            }
            return set;
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }

        public int size() {
            return map.size();
        }

        public void clear() {
            map.clear();
        }
    }
}
