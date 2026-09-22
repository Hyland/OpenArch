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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.Environment;
import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.XMap;
import com.hyland.openarch.common.xmap.XMapException;
import com.hyland.openarch.common.xmap.registry.NullRegistry;
import com.hyland.openarch.common.xmap.registry.Registry;
import com.hyland.openarch.runtime.RuntimeMessage;
import com.hyland.openarch.runtime.RuntimeMessage.Level;
import com.hyland.openarch.runtime.RuntimeMessage.Source;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.Component;
import com.hyland.openarch.runtime.model.ComponentInstance;
import com.hyland.openarch.runtime.model.ComponentManager;
import com.hyland.openarch.runtime.model.Extension;
import com.hyland.openarch.runtime.model.ExtensionPoint;
import com.hyland.openarch.runtime.model.RegistrationInfo;
import com.hyland.openarch.runtime.util.Watch;

/**
 * Component handling all Runtime components.
 */
public class ComponentManagerImpl implements ComponentManager {

    private static final Logger log = LogManager.getLogger(ComponentManagerImpl.class);

    // must use an ordered Set to avoid loosing the order of the pending
    // extensions
    protected final ConcurrentMap<String, Set<Extension>> pendingExtensions;

    /**
     * Manager listeners. Listen too events like start stop restart etc.
     *
     * @since 9.2
     */
    private Listeners listeners;

    private final ConcurrentMap<String, RegistrationInfo> services;

    /**
     * The list of started components (sorted according to the start order). This list is null if the components were
     * not yet started or were stopped
     *
     * @since 9.2
     */
    protected List<RegistrationInfo> started;

    /**
     * A list of registrations that were deployed while the manager was started.
     *
     * @since 9.2
     */
    protected Stash stash;

    /**
     * @since 9.2
     */
    protected ComponentRegistry registry;

    /**
     * @since 9.2
     */
    protected ComponentRegistry snapshot;

    /**
     * Target Component &gt; Extension Point &gt; Registry
     *
     * @since 11.5
     */
    protected final Map<String, Map<String, Registry>> registries;

    protected static final Registry NULL_REGISTRY = new NullRegistry();

    /**
     * @since 9.2
     */
    protected boolean isFlushingStash = false;

    /**
     * @since 9.2
     */
    protected boolean changed = false;

    public ComponentManagerImpl() {
        registry = new ComponentRegistry();
        pendingExtensions = new ConcurrentHashMap<>();
        listeners = new Listeners();
        services = new ConcurrentHashMap<>();
        stash = new Stash();
        registries = new HashMap<>();
    }

    /**
     * @since 9.2
     */
    public final ComponentRegistry getRegistry() {
        return registry;
    }

    @Override
    public Collection<RegistrationInfo> getRegistrations() {
        return registry.getComponents();
    }

    @Override
    public synchronized Map<String, Set<String>> getPendingRegistrations() {
        Map<String, Set<String>> pending = new HashMap<>();
        for (Map.Entry<String, Set<String>> p : registry.getPendingComponents().entrySet()) {
            pending.put(p.getKey(), new LinkedHashSet<>(p.getValue()));
        }
        return pending;
    }

    @Override
    public synchronized Map<String, Set<Extension>> getMissingRegistrations() {
        Map<String, Set<Extension>> missing = new HashMap<>();
        // also add pending extensions, not resolved because of missing target extension point
        for (Set<Extension> p : pendingExtensions.values()) {
            for (Extension e : p) {
                missing.computeIfAbsent(e.getComponent().getName(), k -> new LinkedHashSet<>()).add(e);
            }
        }
        return missing;
    }

    /**
     * Get the needed component names. The returned set is not a copy
     */
    public Set<String> getNeededRegistrations() {
        return pendingExtensions.keySet();
    }

    /**
     * Get the pending extensions. The returned set is not a copy
     */
    public Set<Extension> getPendingExtensions(String name) {
        return pendingExtensions.get(name);
    }

    @Override
    public int size() {
        return registry.size();
    }

    @Override
    public ComponentInstance getComponent(String name) {
        RegistrationInfo ri = registry.getComponent(name);
        return ri != null ? ri.getComponent() : null;
    }

    @Override
    public synchronized void shutdown() {
        stop();
        registry.destroy();
        registry = null;
        snapshot = null;
    }

    @Override
    public synchronized void register(RegistrationInfo ri) {
        String name = ri.getName();
        if (ri.isDisabled() || !ri.isEnabled()) {
            log.debug("Component {} was disabled. Ignoring.", name);
            return;
        }

        // Look if the component is not going to be removed when applying the stash,
        // before checking for duplicates.
        if (!stash.isRemoving(name)) {
            if (registry.contains(name) || stash.isAdding(name)) {
                handleError("Duplicate component name: " + name, name, null);
                return;
            }
        }

        if (shouldStash()) {
            // stash before calling ri.attach.
            stash.add(ri);
            return;
        }

        if (hasSnapshot()) {
            // we are modifying the registry after the snapshot was created
            changed = true;
        }

        ri.attach(this);

        try {
            log.debug("Registering component: {}", name);
            if (!registry.addComponent(ri)) {
                log.debug("Registration delayed for component: {}. Waiting for: {}", name, registry.getMissingDependencies(name));
            }
        } catch (RuntimeException e) {
            // don't raise this exception,
            // we want to isolate component errors from other components
            handleError("Failed to register component: " + name + " (" + e.toString() + ')', name, e);
        }
    }

    @Override
    public synchronized void unregister(String name) {
        if (shouldStash()) { // stash the un-registration
            stash.remove(name);
            return;
        }
        if (hasSnapshot()) {
            changed = true;
        }
        try {
            log.debug("Unregistering component: {}", name);
            registry.removeComponent(name);
        } catch (RuntimeException e) {
            log.error("Failed to unregister component: {}", name, e);
        }
    }

    @Override
    public void addListener(ComponentManager.Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ComponentManager.Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public ComponentInstance getComponentProvidingService(Class<?> serviceClass) {
        RegistrationInfo ri = services.get(serviceClass.getName());
        if (ri == null) {
            log.debug("The component exposing the service: {} doesn't exist", serviceClass);
            return null;
        }
        ComponentInstance ci = ri.getComponent();
        if (ci == null) {
            log.debug("The component exposing the service: {} is not resolved", serviceClass);
        }
        if (ri.getState() == RegistrationInfo.START_FAILURE
                && Boolean.getBoolean(Framework.NUXEO_STRICT_STARTUP_SYSTEM_PROP)) {
            log.warn("The component exposing the service: {} has failed to start", serviceClass);
            return null;
        }
        return ci;
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        ComponentInstance comp = getComponentProvidingService(serviceClass);
        return comp != null ? comp.getAdapter(serviceClass) : null;
    }

    @Override
    public Collection<String> getActivatingRegistrations() {
        return getRegistrations(RegistrationInfo.ACTIVATING);
    }

    @Override
    public Collection<String> getStartFailureRegistrations() {
        return getRegistrations(RegistrationInfo.START_FAILURE);
    }

    protected Collection<String> getRegistrations(int state) {
        RegistrationInfo[] comps = registry.getComponentsArray();
        Collection<String> ret = new ArrayList<>();
        for (RegistrationInfo ri : comps) {
            if (ri.getState() == state) {
                ret.add(ri.getName());
            }
        }
        return ret;
    }

    public synchronized void registerExtension(Extension extension) {
        String name = extension.getTargetComponent();
        RegistrationInfo ri = registry.getComponent(name);

        if (ri != null && ri.getComponent() != null
                && Set.of(RegistrationInfo.ACTIVATED, RegistrationInfo.STARTED).contains(ri.getState())) {
            log.debug("Register contributed extension: {}", extension);
            register(ri, extension);
            ri.getComponent().registerExtension(extension);
        } else {
            // put the extension in the pending queue
            log.debug("Enqueue contributed extension to pending queue: {}", extension);
            // must keep order in which extensions are contributed
            pendingExtensions.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(extension);
        }
    }

    public synchronized void unregisterExtension(Extension extension) {
        // TODO check if framework is shutting down and in that case do nothing
        log.debug("Unregister contributed extension: {}", extension);
        String name = extension.getTargetComponent();
        RegistrationInfo ri = registry.getComponent(name);
        if (ri != null) {
            unregister(ri, extension);
            ComponentInstance co = ri.getComponent();
            if (co != null) {
                co.unregisterExtension(extension);
            }
        } else { // maybe it's pending
            Set<Extension> extensions = pendingExtensions.get(name);
            if (extensions != null) {
                extensions.remove(extension);
                if (extensions.isEmpty()) {
                    pendingExtensions.remove(name);
                }
            }
        }
    }

    /**
     * Registers the given extension on target extension point held by given registration info.
     *
     * @since 11.5
     */
    public void register(RegistrationInfo ri, Extension xt) {
        // in new java based system contributions don't need to be loaded, this is a XML specificity reflected by
        // ExtensionPointImpl coming from XML deserialization

        // Extension point needing to load contribution are ExtensionPointImpl
        ri.getExtensionPoint(xt.getExtensionPoint()).filter(xp -> xp.getContributions() != null).ifPresent(xp -> {
            try {
                // should compute now the contributions
                Class<?>[] contributions = xp.getContributions();
                if (contributions != null) {
                    Context xctx = new XMapContext(xt.getContext());
                    Registry localRegistry = getOrCreateRegistry(ri.getName(), xp);
                    String tag = xt.getId();
                    if (!localRegistry.isNull() && !localRegistry.isTagged(tag)) {
                        localRegistry.tag(tag);
                        // fill registry
                        xp.getXMap().register(localRegistry, xctx, xt.getElement(), tag);
                    }
                }
            } catch (XMapException e) {
                String compName = xt.getComponent().getName();
                handleError(e.getMessage() + " while processing component: " + compName, compName, e);
            }
        });

    }

    protected void unregister(RegistrationInfo ri, Extension xt) {
        ri.getExtensionPoint(xt.getExtensionPoint()).filter(xp -> xp.getContributions() != null).ifPresent(xp -> {
            try {
                Optional<Registry> registryOpt = getExtensionPointRegistry(ri.getName(), xp.getName());
                if (registryOpt.isPresent() && !registryOpt.get().isNull()) {
                    try {
                        xp.getXMap().unregister(registryOpt.get(), xt.getId());
                    } catch (XMapException e) {
                        log.error("{} while unprocessing component: {}", e.getMessage(),
                                xt.getComponent().getName(), e);
                    }
                }
            } catch (RuntimeException e) {
                String compName = xt.getComponent().getName();
                handleError("Failed to unregister contributions for component " + compName, compName, e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Registry> Optional<T> getExtensionPointRegistry(String component, String point) {
        Map<String, Registry> target = registries.get(component);
        if (target != null && target.containsKey(point)) {
            Registry localRegistry = target.get(point);
            if (!localRegistry.isNull()) {
                return Optional.of((T) localRegistry);
            }
        }
        return Optional.empty();
    }

    protected Registry getOrCreateRegistry(String component, ExtensionPoint xp) {
        Optional<Registry> stored = getExtensionPointRegistry(component, xp.getName());
        if (stored.isPresent()) {
            return stored.get();
        }
        Registry localRegistry = NULL_REGISTRY;
        String point = xp.getName();
        String registryClass = xp.getRegistryClass();
        if (registryClass != null) {
            try {
                Class<?> clazz = Class.forName(registryClass);
                Constructor<?> constructor = clazz.getConstructor();
                localRegistry = (Registry) constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                String msg = String.format(
                        "Failed to create registry on component '%s', extension point '%s': error initializing class '%s' (%s).",
                        component, point, registryClass, e.toString());
                throw new RuntimeException(msg, e);
            }
        } else {
            Class<?>[] contributions = xp.getContributions();
            if (contributions.length != 0) {
                // compute registry from annotations, taking first registry
                XMap xmap = xp.getXMap();
                localRegistry = Arrays.stream(contributions)
                                 .map(xmap::getObject)
                                 .filter(Objects::nonNull)
                                 .map(xmap::getRegistry)
                                 .filter(Objects::nonNull)
                                 .findFirst()
                                 .orElse(NULL_REGISTRY);
            }
        }
        registries.computeIfAbsent(component, k -> new ConcurrentHashMap<>()).put(point, localRegistry);
        return localRegistry;
    }

    protected void createRegistries(RegistrationInfo ri) {
        // instantiate extension point registries if any
        Stream.of(ri.getExtensionPoints()).filter(xp -> xp.getContributions() != null).forEach(xp -> {
            getOrCreateRegistry(ri.getName(), xp);
        });
    }

    protected void initializeRegistries(RegistrationInfo ri) {
        String name = ri.getName();
        if (registries.containsKey(name)) {
            registries.get(name).values().forEach(Registry::initialize);
        }
    }

    protected void resetRegistries(RegistrationInfo ri) {
        registries.remove(ri.getName());
    }

    public synchronized void registerServices(RegistrationInfo ri) {
        String[] serviceNames = ri.getProvidedServiceNames();
        if (serviceNames == null) {
            return;
        }
        for (String serviceName : serviceNames) {
            log.trace("Registering service: {}", serviceName);
            services.put(serviceName, ri);
        }
    }

    public synchronized void unregisterServices(RegistrationInfo ri) {
        String[] serviceNames = ri.getProvidedServiceNames();
        if (serviceNames == null) {
            return;
        }
        for (String service : serviceNames) {
            log.trace("Unregistering service: {}", service);
            services.remove(service);
        }
    }

    @Override
    public String[] getServices() {
        return services.keySet().toArray(new String[0]);
    }

    protected static void handleError(String message, String componentName, Exception e) {
        log.error(message, e);
        Framework.getRuntime()
                 .getMessageHandler()
                 .addMessage(new RuntimeMessage(Level.ERROR, message, Source.COMPONENT, componentName));
    }

    /**
     * Activate all the resolved components and return the list of activated components in the activation order
     *
     * @return the list of the activated components in the activation order
     * @since 9.2
     */
    protected List<RegistrationInfo> activateComponents() {
        log.debug("Instantiate components");
        Watch iwatch = new Watch();
        iwatch.start();

        // first instantiate resolved components: that allows some to register as listeners on ComponentManager before
        // all components activation.
        List<RegistrationInfo> iris = new ArrayList<>();
        // first activate resolved components
        for (RegistrationInfo ri : registry.getResolvedRegistrationInfo()) {
            iwatch.start(ri.getName());
            if (instantiateComponent(ri)) {
                iris.add(ri);
                // register potential listener
                ComponentInstance component = ri.getComponent();
                if (component != null && component.getInstance() instanceof Listener listener) {
                    addListener(listener);
                }
            }
            iwatch.stop(ri.getName());
        }
        iwatch.stop();
        log.debug("Components instantiated in {}s", iwatch.total::formatSeconds);
        writeDevMetrics(iwatch, "instantiate");

        log.debug("Activate components");
        Watch awatch = new Watch();
        awatch.start();
        // make sure we start with a clean pending registry
        pendingExtensions.clear();
        List<RegistrationInfo> ris = new ArrayList<>();
        // first activate resolved components
        for (RegistrationInfo ri : iris) {
            awatch.start(ri.getName());
            activateComponent(ri);
            ris.add(ri);
            awatch.stop(ri.getName());
        }
        awatch.stop();

        log.debug("Components activated in {}s", awatch.total::formatSeconds);
        writeDevMetrics(awatch, "activate");

        return ris;
    }

    /**
     * Instantiates the given {@link RegistrationInfo}. This step will instantiate the component.
     * <p>
     * Allows registering listeners on ComponentManager at component instantiation, before all components activation.
     * <p>
     * Should be called before {@link #activateComponent(RegistrationInfo)}.
     *
     * @return false in case of error during instantiation, true otherwise.
     * @since 11.3.
     */
    protected boolean instantiateComponent(RegistrationInfo ri) {
        return ri.instantiate();
    }

    /**
     * Activates the given {@link RegistrationInfo}. This step will activate the component, register extensions and then
     * register services.
     *
     * @since 9.3
     */
    protected void activateComponent(RegistrationInfo ri) {
        createRegistries(ri);
        ri.activate();
    }

    /**
     * Deactivate all active components in the reverse resolve order
     *
     * @since 9.2
     */
    protected void deactivateComponents(boolean isShutdown) {
        log.debug("Deactivate components");
        Watch watch = new Watch();
        watch.start();
        listeners.beforeDeactivation();
        Collection<RegistrationInfo> resolved = registry.getResolvedRegistrationInfo();
        List<RegistrationInfo> reverseResolved = new ArrayList<>(resolved);
        Collections.reverse(reverseResolved);
        for (RegistrationInfo ri : reverseResolved) {
            if (ri.isActivated()) {
                watch.start(ri.getName());
                deactivateComponent(ri, isShutdown);
                watch.stop(ri.getName());
            }
        }
        // make sure the pending extension map is empty since we didn't unregistered extensions by calling
        // ri.deactivate(true)
        pendingExtensions.clear();
        watch.stop();

        log.debug("Components deactivated in {}s", watch.total::formatSeconds);
        writeDevMetrics(watch, "deactivate");
    }

    /**
     * Deactivates the given {@link RegistrationInfo}. This step will unregister the services, unregister the extensions
     * and then deactivate the component.
     *
     * @since 9.3
     */
    protected void deactivateComponent(RegistrationInfo ri, boolean isShutdown) {
        // reset registries (even when shutting down, to comply with HotDeployer logic in tests)
        resetRegistries(ri);
        // unregister potential listener
        ComponentInstance component = ri.getComponent();
        if (component.getInstance() instanceof Listener listener) {
            removeListener(listener);
        }

        // don't unregister extension if server is shutdown
        ri.deactivate(!isShutdown);
    }

    /**
     * Starts all given components.
     *
     * @since 9.2
     */
    private void startComponents(List<RegistrationInfo> ris) {
        log.debug("Start components");
        Watch watch = new Watch();
        watch.start();
        listeners.beforeStart();
        for (RegistrationInfo ri : ris) {
            watch.start(ri.getName());
            startComponent(ri);
            watch.stop(ri.getName());
        }
        this.started = ris;
        listeners.afterStart();
        watch.stop();

        log.debug("Components started in {}s", watch.total::formatSeconds);
        writeDevMetrics(watch, "start");
    }

    /**
     * Starts the given {@link RegistrationInfo}. This step will start the component.
     *
     * @since 9.3
     */
    protected void startComponent(RegistrationInfo ri) {
        // make sure corresponding registries are initialized
        initializeRegistries(ri);
        ri.start();
    }

    /**
     * Stop all started components. Stopping components is done in reverse start order.
     *
     * @since 9.2
     */
    protected void stopComponents() {
        log.debug("Stop components");
        try {
            Watch watch = new Watch();
            watch.start();
            listeners.beforeStop();
            List<RegistrationInfo> list = this.started;
            for (int i = list.size() - 1; i >= 0; i--) {
                RegistrationInfo ri = list.get(i);
                if (ri.isStarted()) {
                    watch.start(ri.getName());
                    stopComponent(ri);
                    watch.stop(ri.getName());
                }
            }
            watch.stop();

            log.debug("Components stopped in {}s", watch.total::formatSeconds);
            writeDevMetrics(watch, "stop");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while stopping components", e);
        }
    }

    /**
     * Stops the given {@link RegistrationInfo}. This step will stop the component.
     *
     * @since 9.3
     */
    protected void stopComponent(RegistrationInfo ri) throws InterruptedException {
        ri.stop();
    }

    @Override
    public synchronized boolean start() {
        if (this.started != null) {
            return false;
        }

        log.debug("Starting Nuxeo Components");

        List<RegistrationInfo> ris = activateComponents();

        ris.sort(RegistrationInfo.COMPARATOR);

        // then start activated components
        startComponents(ris);

        return true;
    }

    @Override
    public synchronized boolean stop() {
        if (this.started == null) {
            return false;
        }

        log.debug("Stopping Nuxeo Components");

        try {
            stopComponents();
            // now deactivate all active components
            deactivateComponents(true);
        } finally {
            this.started = null;
        }

        return true;
    }

    @Override
    public void stop(int timeoutInSeconds) {
        try {
            runWihtinTimeout(timeoutInSeconds, TimeUnit.SECONDS, "Timed out on stop, blocking", this::stop);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while stopping components", e);
        }
    }

    public boolean isStarted() {
        return this.started != null;
    }

    @Override
    public boolean isRunning() {
        return this.started != null;
    }

    @Override
    public boolean hasSnapshot() {
        return this.snapshot != null;
    }

    @Override
    public boolean hasChanged() {
        return this.changed;
    }

    @Override
    public synchronized void snapshot() {
        this.snapshot = new ComponentRegistry(registry);
    }

    @Override
    public synchronized void restart(boolean reset) {
        if (reset) {
            this.reset();
        } else {
            this.stop();
        }
        this.start();
    }

    @Override
    public synchronized boolean reset() {
        boolean r = this.stop();
        restoreSnapshot();
        return r;
    }

    @Override
    public synchronized boolean refresh() {
        if (this.stash.isEmpty()) {
            return false;
        }
        boolean requireStart = reset();
        Stash currentStash = this.stash;
        this.stash = new Stash();
        applyStash(currentStash);
        if (requireStart) {
            start();
        }
        return true;
    }

    protected synchronized void restoreSnapshot() {
        if (changed && snapshot != null) {
            log.debug("Restoring components snapshot");
            this.registry = new ComponentRegistry(snapshot);
            changed = false;
        }
    }

    /**
     * Tests whether new registrations should be stashed at registration time. If the component manager was started then
     * new components should be stashed otherwise they can be registered.
     * <p>
     * TODO: current implementation is stashing after the start completion. Should we also stashing while start is in
     * progress?
     */
    protected boolean shouldStash() {
        return isRunning() && !isFlushingStash;
    }

    protected synchronized void applyStash(Stash stash) {
        log.debug("Applying stashed components");
        isFlushingStash = true;
        try {
            for (String name : stash.toRemove) {
                unregister(name);
            }
            for (RegistrationInfo ri : stash.toAdd) {
                register(ri);
            }
        } finally {
            isFlushingStash = false;
        }
    }

    protected void writeDevMetrics(Watch watch, String type) {
        if (!Framework.isDevModeSet()) {
            return;
        }
        File file = new File(Environment.getDefault().getTemp(), type + "-metrics.txt");
        try (PrintStream ps = new PrintStream(new FileOutputStream(file), false, "UTF-8")) {
            ps.println(watch.getTotal());
            // print first the longest intervals
            Arrays.stream(watch.getIntervals()).sorted(Comparator.reverseOrder()).forEach(ps::println);
            ps.flush();
        } catch (IOException e) {
            log.error("Failed to write metrics file: {}", file, e);
        }
    }

    /**
     * Log a warning message if the timeout is reached while executing the given runnable.
     */
    protected static void runWihtinTimeout(long timeout, TimeUnit unit, String warn, Runnable runnable)
            throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(runnable::run);
            executor.shutdown();
            try {
                try {
                    future.get(timeout, unit);
                } catch (TimeoutException cause) {
                    log.warn(warn);
                    future.get();
                }
            } catch (ExecutionException cause) {
                throw new RuntimeException("Errors caught while stopping components, giving up", cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    protected class Listeners {

        protected List<ComponentManager.Listener> listeners = new CopyOnWriteArrayList<>();

        public void add(ComponentManager.Listener listener) {
            listeners.add(listener);
        }

        public void remove(ComponentManager.Listener listener) {
            listeners.remove(listener);
        }

        public void beforeDeactivation() {
            safeLoop(l -> l.beforeRuntimeDeactivation(ComponentManagerImpl.this), "beforeDeactivation");
        }

        /**
         * Sort listeners to follow start order on components.
         *
         * @since 11.5
         */
        protected Stream<ComponentManager.Listener> sortedForStart() {
            Stream<ComponentManager.Listener> regularListeners = listeners.stream()
                                                                       .filter(l -> !(l instanceof Component))
                                                                       .map(ComponentManager.Listener.class::cast);
            Stream<ComponentManager.Listener> componentListeners = listeners.stream()
                                                                         .filter(Component.class::isInstance)
                                                                         .map(Component.class::cast)
                                                                         .sorted(Comparator.comparingInt(
                                                                                 Component::getApplicationStartedOrder))
                                                                         .map(ComponentManager.Listener.class::cast);
            return Stream.concat(regularListeners, componentListeners);
        }

        /**
         * Sort listeners to follow stop order on components (reversing start order).
         *
         * @since 11.5
         */
        protected Stream<ComponentManager.Listener> sortedForStop() {
            return sortedForStart().collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                Collections.reverse(list);
                return list.stream();
            }));
        }

        public void beforeStart() {
            safeLoop(l -> l.beforeRuntimeStart(ComponentManagerImpl.this), "beforeStart");
        }

        public void afterStart() {
            safeLoop(l -> l.afterRuntimeStart(ComponentManagerImpl.this), "afterStart");
        }

        public void beforeStop() {
            safeLoop(l -> l.beforeRuntimeStop(ComponentManagerImpl.this), "beforeStop");
        }

        protected void safeLoop(Consumer<ComponentManager.Listener> consumer, String step) {
            streamListeners(step).forEach(listener -> {
                try {
                    consumer.accept(listener);
                } catch (RuntimeException e) {
                    String msg = String.format("An error occurred during %s listener execution", step);
                    log.error(msg, e);
                    String msgexc = String.format("%s (%s)", msg, e);
                    Framework.getRuntime()
                             .getMessageHandler()
                             .addMessage(new RuntimeMessage(Level.ERROR, msgexc, Source.UNKNOWN, null));
                }
            });
        }

        protected Stream<ComponentManager.Listener> streamListeners(String step) {
            switch (step) {
                case "beforeStart":
                case "afterStart":
                    return sortedForStart();
                case "beforeStop":
                case "afterStop":
                    return sortedForStop();
                default:
                    return listeners.stream();
            }
        }
    }

    protected static class Stash {

        protected List<RegistrationInfo> toAdd;

        protected Set<String> toRemove;

        public Stash() {
            toAdd = new ArrayList<>();
            toRemove = new HashSet<>();
        }

        public void add(RegistrationInfo ri) {
            this.toAdd.add(ri);
        }

        public void remove(String name) {
            this.toRemove.add(name);
        }

        public boolean isEmpty() {
            return toAdd.isEmpty() && toRemove.isEmpty();
        }

        /**
         * @since 11.3
         */
        public boolean isAdding(String name) {
            return toAdd.stream().anyMatch(ri -> name.equals(ri.getName()));
        }

        /**
         * @since 11.3
         */
        public boolean isRemoving(String name) {
            return toRemove.contains(name);
        }

    }

}
