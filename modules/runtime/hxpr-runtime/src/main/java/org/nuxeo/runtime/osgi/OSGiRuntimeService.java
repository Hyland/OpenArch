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
package org.nuxeo.runtime.osgi;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.common.utils.TextTemplate;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.RuntimeMessageHandler;
import org.nuxeo.runtime.RuntimeMessageHandlerImpl;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.RuntimeMessage.Level;
import org.nuxeo.runtime.RuntimeMessage.Source;
import org.nuxeo.runtime.Version;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.RuntimeContext;
import org.nuxeo.runtime.model.impl.ComponentManagerImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;

/**
 * The default implementation of NXRuntime over an OSGi compatible environment.
 *
 * @author Bogdan Stefanescu
 * @author Florent Guillaume
 */
public class OSGiRuntimeService implements RuntimeService, FrameworkListener {

    private static final Logger log = LogManager.getLogger(OSGiRuntimeService.class);

    public static final String PROP_NUXEO_BIND_ADDRESS = "nuxeo.bind.address";

    public static final String NAME = "OSGi NXRuntime";

    public static final Version VERSION = Version.parseString("1.4.0");

    private final RuntimeContext runtimeContext;

    private final BundleContext bundleContext;

    private final Map<String, RuntimeContext> runtimeContexts;

    private final Properties properties;

    private final File workingDir;

    private final RuntimeMessageHandlerImpl messageHandler = new RuntimeMessageHandlerImpl();

    private boolean isStarted;

    private boolean appStarted;

    private ComponentManager manager;

    public OSGiRuntimeService(BundleContext bundleContext) {
        runtimeContext = new OSGiRuntimeContext(bundleContext.getBundle(), this);
        this.bundleContext = bundleContext;
        runtimeContexts = new ConcurrentHashMap<>();

        properties = new Properties(System.getProperties());
        String bindAddress = bundleContext.getProperty(PROP_NUXEO_BIND_ADDRESS);
        if (bindAddress != null) {
            properties.put(PROP_NUXEO_BIND_ADDRESS, bindAddress);
        }
        workingDir = bundleContext.getDataFile("/");
        // environment may not be set by some bootstrappers (like tests) - we create it now if not yet created
        Environment env = Environment.getDefault();
        if (env == null) {
            env = new Environment(workingDir);
            Environment.setDefault(env);
            env.setServerHome(workingDir);
            env.init();
        }
        workingDir.mkdirs();
        log.debug("Working directory: {}", workingDir);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Version getVersion() {
        return VERSION;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    @Override
    public RuntimeMessageHandler getMessageHandler() {
        return messageHandler;
    }

    @Override
    public synchronized void start() {
        if (isStarted) {
            return;
        }
        manager = new ComponentManagerImpl();
        manager.addListener(messageHandler);
        try {
            loadConfig();
        } catch (IOException e) {
            throw new RuntimeServiceException(e);
        }
        log.debug("Starting Nuxeo Runtime service {}; version: {}",getName(), getVersion());
        try {
            bundleContext.addFrameworkListener(this);
            loadComponents(bundleContext.getBundle(), runtimeContext, false);
        } finally {
            isStarted = true;
        }
    }

    @Override
    public synchronized void stop() {
        if (!isStarted) {
            return;
        }
        log.debug("Stopping Nuxeo Runtime service {}; version: {}", getName(), getVersion());
        try {
            manager.shutdown();
            // do not destroy context since component manager is already shutdown
            bundleContext.removeFrameworkListener(this);
        } finally {
            isStarted = false;
            manager = null;
        }
    }

    @Override
    public File getHome() {
        return workingDir;
    }

    @Override
    public String getDescription() {
        return getName() + " version " + getVersion();
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public String getProperty(String name) {
        return getProperty(name, null);
    }

    @Override
    public void setProperty(String name, Object value) {
        properties.setProperty(name, value.toString());
    }

    @Override
    public Component getComponent(String name) {
        ComponentInstance co = manager.getComponent(name);
        return co != null ? co.getInstance() : null;
    }

    @Override
    public ComponentManager getComponentManager() {
        return manager;
    }

    @Override
    public RuntimeContext getContext() {
        return runtimeContext;
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        return manager.getService(serviceClass);
    }

    @Override
    public boolean getStatusMessage(StringBuilder msg) {
        List<String> warnings = messageHandler.getMessages(RuntimeMessage.Level.WARNING);
        List<String> errors = messageHandler.getMessages(RuntimeMessage.Level.ERROR);
        Map<String, Set<String>> pendingRegistrations = manager.getPendingRegistrations();
        Map<String, Set<Extension>> missingRegistrations = manager.getMissingRegistrations();
        Collection<String> unstartedRegistrations = manager.getActivatingRegistrations();
        unstartedRegistrations.addAll(manager.getStartFailureRegistrations());
        boolean noError = warnings.isEmpty() //
                && errors.isEmpty() //
                && pendingRegistrations.isEmpty() //
                && missingRegistrations.isEmpty() //
                && unstartedRegistrations.isEmpty();
        if (noError) {
            msg.append(manager.getRegistrations().size()).append(" Components Started");
            return false;
        }
        String hr = "======================================================================";
        if (!warnings.isEmpty()) {
            msg.append(hr).append("\n= Component Loading Warnings:\n");
            for (String warning : warnings) {
                msg.append("  * ").append(warning).append('\n');
            }
        }
        if (!errors.isEmpty()) {
            msg.append(hr).append("\n= Component Loading Errors:\n");
            for (String error : errors) {
                msg.append("  * ").append(error).append('\n');
            }
        }
        msg.append(hr)
           .append("\n= Component Loading Status: Pending: ")
           .append(pendingRegistrations.size())
           .append(" / Missing: ")
           .append(missingRegistrations.size())
           .append(" / Unstarted: ")
           .append(unstartedRegistrations.size())
           .append(" / Total: ")
           .append(manager.getRegistrations().size())
           .append('\n');
        for (Entry<String, Set<String>> e : pendingRegistrations.entrySet()) {
            msg.append("  * ").append(e.getKey()).append(" requires ").append(e.getValue()).append('\n');
        }
        for (Entry<String, Set<Extension>> e : missingRegistrations.entrySet()) {
            msg.append("  * ")
               .append(e.getKey())
               .append(" references missing ")
               .append(e.getValue()
                        .stream()
                        .map(ext -> "target=" + ext.getTargetComponent() + ";point=" + ext.getExtensionPoint())
                        .toList())
               .append('\n');
        }
        for (String componentName : unstartedRegistrations) {
            msg.append("  - ").append(componentName).append('\n');
        }
        msg.append(hr);
        return true;
    }

    @Override
    public boolean hasErrors() {
        List<String> errors = messageHandler.getMessages(RuntimeMessage.Level.ERROR);
        Map<String, Set<String>> pendingRegistrations = manager.getPendingRegistrations();
        Map<String, Set<Extension>> missingRegistrations = manager.getMissingRegistrations();
        Collection<String> unstartedRegistrations = manager.getActivatingRegistrations();
        unstartedRegistrations.addAll(manager.getStartFailureRegistrations());
        return !(errors.isEmpty() && pendingRegistrations.isEmpty() && missingRegistrations.isEmpty()
                && unstartedRegistrations.isEmpty());
    }

    public RuntimeContext createContext(Bundle bundle) {
        String symbolicName = bundle.getSymbolicName();
        RuntimeContext ctx = runtimeContexts.get(symbolicName);
        if (ctx == null) {
            ctx = new OSGiRuntimeContext(bundle, this);
            runtimeContexts.put(symbolicName, ctx);
            loadComponents(bundle, ctx, true);
        }
        return ctx;
    }

    public void destroyContext(Bundle bundle) {
        RuntimeContext ctx = runtimeContexts.remove(bundle.getSymbolicName());
        if (ctx != null) {
            ctx.destroy();
        }
    }

    public RuntimeContext getContext(String symbolicName) {
        return runtimeContexts.get(symbolicName);
    }

    private void loadComponents(Bundle bundle, RuntimeContext ctx, boolean isFragment) {
        String name = bundle.getSymbolicName();
        if (isFragment && name.equals(runtimeContext.getBundle().getSymbolicName())) {
            // avoid deploying again runtime components as a fragment (already handled in #doStart)
            return;
        }
        String list = getComponentsList(bundle);
        if (list == null) {
            log.debug("Bundle {} doesn't have components", name);
            return;
        }
        log.trace("Load Bundle: {} / Components: {}", name, list);
        StringTokenizer tok = new StringTokenizer(list, ", \t\n\r\f");
        while (tok.hasMoreTokens()) {
            String path = tok.nextToken();
            URL url = bundle.getEntry(path);
            if (url != null) {
                log.trace("Load component {} [{}]", name, url);
                try {
                    ctx.deploy(url);
                } catch (IOException e) {
                    log.error(e.getMessage());
                    messageHandler.addMessage(new RuntimeMessage(Level.ERROR, e.getMessage(), Source.BUNDLE, name));
                }
            } else {
                String message = "Unknown component '" + path + "' referenced by bundle '" + name + "'";
                log.error("{}. Check the MANIFEST.MF", message);
                messageHandler.addMessage(new RuntimeMessage(Level.ERROR, message, Source.BUNDLE, name));
            }
        }
    }

    public static String getComponentsList(Bundle bundle) {
        return bundle.getHeaders().get("Nuxeo-Component");
    }

    private void loadConfig() throws IOException {
        Environment env = Environment.getDefault();
        if (env != null) {
            log.debug("Configuration: host application: NXServer");
        } else {
            log.warn("Configuration: no host application");
            return;
        }

        properties.putAll(env.getProperties());
        loadNuxeoConf();

        File dir = env.getConfig();
        String[] names = dir.list();
        if (names != null) {
            Arrays.sort(names, String::compareToIgnoreCase);
            log.debug("Deployment order of configuration files: {}",
                    () -> Stream.of(names).reduce((n1, n2) -> n1 + "\n\t" + n2).map(n -> "\n\t" + n).orElse(""));
            for (String name : names) {
                if (name.endsWith("-config.xml") || name.endsWith("-bundle.xml")) {
                    File file = new File(dir, name);
                    log.trace("Configuration: deploy config component: {}", name);
                    try {
                        runtimeContext.deploy(file.toURI().toURL());
                    } catch (IOException e) {
                        String message = String.format("Error deploying config %s (%s)", name, e.getMessage());
                        log.error(message);
                        messageHandler.addMessage(new RuntimeMessage(Level.ERROR, message, Source.CONFIG, name));
                    }
                } else if (name.endsWith(".config") || name.endsWith(".ini") || name.endsWith(".properties")) {
                    File file = new File(dir, name);
                    log.trace("Configuration: loading properties: {}", name);
                    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                        loadProperties(in);
                    }
                } else {
                    log.trace("Configuration: ignoring: {}", name);
                }
            }
        } else if (dir.isFile()) { // a file - load it
            log.debug("Configuration: loading properties: {}", dir);
            try (InputStream in = new BufferedInputStream(new FileInputStream(dir))) {
                loadProperties(in);
            }
        } else {
            log.debug("Configuration: no configuration file found");
        }

        // loads default properties.
        // used for backward compatibility when adding new mandatory properties
        String contextPathProp = "org.nuxeo.ecm.contextPath";
        if (Framework.getProperty(contextPathProp) == null) {
            properties.setProperty(contextPathProp, "/nuxeo");
        }
    }

    private void loadNuxeoConf() throws IOException {
        String nuxeoConfProperty = Framework.getProperty("nuxeo.conf");
        File nuxeoConf;
        if (nuxeoConfProperty == null) {
            nuxeoConf = new File(getHome(), "bin/nuxeo.conf");
        } else {
            nuxeoConf = new File(nuxeoConfProperty);
        }
        if (nuxeoConf.exists()) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(nuxeoConf))) {
                loadProperties(in);
            }
        }
    }

    private void loadProperties(InputStream in) throws IOException {
        properties.load(in);
    }

    /**
     * Overrides the default method to be able to include OSGi properties.
     */
    @Override
    public String getProperty(String name, String defValue) {
        String value = properties.getProperty(name);
        if (value == null) {
            value = bundleContext.getProperty(name);
            if (value == null) {
                return defValue == null ? null : expandVars(defValue);
            }
        }
        if (("${" + name + "}").equals(value)) {
            // avoid loop, don't expand
            return value;
        }
        return expandVars(value);
    }

    /**
     * Overrides the default method to be able to include OSGi properties.
     */
    @Override
    public String expandVars(String expression) {
        return new TextTemplate(getProperties()) {
            @Override
            public String getVariable(String name) {
                String value = super.getVariable(name);
                if (value == null) {
                    value = bundleContext.getProperty(name);
                }
                return value;
            }

        }.processText(expression);
    }

    /* --------------- FrameworkListener API ------------------ */

    @Override
    public void frameworkEvent(FrameworkEvent event) {
        if (event.getType() != FrameworkEvent.STARTED) {
            return;
        }
        synchronized (this) {
            if (appStarted) {
                return;
            }
            appStarted = true;
        }
        // activate and start components
        manager.start();
        // create a snapshot of the started components
        manager.snapshot();
        // print the startup message
        printStatusMessage();
    }

    private void printStatusMessage() {
        StringBuilder msg = new StringBuilder();
        if (getStatusMessage(msg)) {
            msg.insert(0, "HxPR Platform Started With Errors\n");
            log.error(msg);
        } else {
            msg.insert(0, "HxPR Platform Started: ");
            log.info(msg);
        }
    }

}
