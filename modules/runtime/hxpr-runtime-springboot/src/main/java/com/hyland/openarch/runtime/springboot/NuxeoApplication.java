/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot;

import static com.hyland.openarch.common.Environment.NUXEO_CONFIG_DIR;
import static com.hyland.openarch.common.Environment.NUXEO_DATA_DIR;
import static com.hyland.openarch.common.Environment.NUXEO_HOME;
import static com.hyland.openarch.common.Environment.NUXEO_LOG_DIR;
import static com.hyland.openarch.common.Environment.NUXEO_RUNTIME_HOME;
import static com.hyland.openarch.common.Environment.NUXEO_TMP_DIR;

import com.hyland.openarch.common.Environment;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.osgi.BundleManifestReader;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.springboot.application.loader.SpringBootFrameworkLoader;
import com.hyland.openarch.runtime.springboot.framework.ApplicationContextHelper;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Basic runtime startup triggered by {@link ContextRefreshedEvent}
 */
public class NuxeoApplication {

    private static final Logger log = LoggerFactory.getLogger(NuxeoApplication.class);

    public static final String STATUS_NONE = "none";

    public static final String STATUS_STARTED = "started";

    public static final String STATUS_STOPPED = "stopped";

    private static final String DEFAULT_HOME = "nuxeo";

    private static final List<String> DEFAULT_RUNTIME_PROPERTIES = List.of(NUXEO_HOME, NUXEO_RUNTIME_HOME);

    private static final List<String> DEFAULT_SERVER_PROPERTIES = List.of(NUXEO_CONFIG_DIR, //
            NUXEO_DATA_DIR, //
            NUXEO_LOG_DIR, //
            NUXEO_TMP_DIR);

    private static final List<String> DEFAULT_PROPERTIES = Stream.concat(DEFAULT_RUNTIME_PROPERTIES.stream(),
            DEFAULT_SERVER_PROPERTIES.stream()).toList();

    protected String status = STATUS_NONE;

    public NuxeoApplication() {
        // empty
    }

    void start(ApplicationContext applicationContext) {
        AbstractEnvironment env = (AbstractEnvironment) applicationContext.getEnvironment();
        log.info("Starting Nuxeo Runtime");
        try {
            long startTime = System.currentTimeMillis();

            Map<String, File> bundles = getBundles();
            Map<String, Object> nxHostEnv = buildNuxeoHostEnv(env);
            // init nuxeo.tmp.dir to system tmp dir if unset
            if (!nxHostEnv.containsKey(NUXEO_TMP_DIR)) {
                nxHostEnv.put(NUXEO_TMP_DIR, System.getProperty("java.io.tmpdir"));
            }
            Map<String, String> nxConfEnv = buildNuxeoConfEnv(env);

            String homePath = (String) nxHostEnv.get(NUXEO_RUNTIME_HOME);
            System.setProperty(Environment.NUXEO_HOME, (String) nxHostEnv.get(NUXEO_RUNTIME_HOME));
            File home = new File(homePath);

            Properties sysprops = System.getProperties();
            for (Map.Entry<String, String> prop : nxConfEnv.entrySet()) {
                String val = prop.getValue();
                if (val != null) {
                    sysprops.setProperty(prop.getKey(), val);
                }
            }
            SpringBootFrameworkLoader.start(getClass().getClassLoader(), home, new ArrayList<>(bundles.values()),
                    nxHostEnv);

            setApplicationContexts(applicationContext);

            long finishedTime = System.currentTimeMillis();
            Double duration = (finishedTime - startTime) / 1000.0;
            var messageStartUp = String.format("Nuxeo Runtime started in %.1f sec.", duration);
            log.info(messageStartUp);

            status = STATUS_STARTED;
        } catch (BundleException e) {
            log.error("Exception during Nuxeo startup", e);
        }
    }

    public static void setApplicationContexts(ApplicationContext applicationContext) {
        if (Framework.isInitialized()) {
            Arrays.stream(Framework.getRuntime().getComponentManager().getServices()).map(service -> {
                try {
                    return (Object) Framework.getService(Class.forName(service));
                } catch (ClassNotFoundException e) {
                    return null;
                }
            })
                  .filter(ApplicationContextAware.class::isInstance)
                  .map(ApplicationContextAware.class::cast)
                  .forEach(service -> service.setApplicationContext(applicationContext));
        }
        // Not all OSGI components are accessible via Framework.getService() so we have a helper class for those
        // instances
        ApplicationContextHelper.setApplicationContext(applicationContext);
    }

    protected Map<String, Object> buildNuxeoHostEnv(AbstractEnvironment env) {
        Map<String, Object> nxEnv = new HashMap<>();
        for (String param : DEFAULT_RUNTIME_PROPERTIES) {
            nxEnv.put(param, env.getProperty(param, DEFAULT_HOME));
        }
        for (String param : DEFAULT_SERVER_PROPERTIES) {
            Optional<String> value = Optional.ofNullable(env.getProperty(param, String.class));
            if (value.isPresent() && StringUtils.isNotBlank(value.get())) {
                nxEnv.put(param, value.get());
            }
        }
        // Get the Default properties and return as a Map.
        return nxEnv;
    }

    protected Map<String, String> buildNuxeoConfEnv(AbstractEnvironment env) {
        Map<String, String> confEnv = getAllPropertyNames(env).stream()
                                                              .filter(propName -> !DEFAULT_PROPERTIES.contains(
                                                                      propName))
                                                              .collect(Collectors.toMap(propName -> propName,
                                                                      env::getProperty));
        return addMissingEnvironmentVariables(confEnv);
    }

    protected String envKeyToProperty(String key) {
        return key.toLowerCase().replace("_", ".");
    }

    /**
     * It seems that some environment variables are not converted to properties by Spring Boot and can't be accessed by
     * OSGI components, so we add them manually.
     */
    protected Map<String, String> addMissingEnvironmentVariables(Map<String, String> confEnv) {
        Map<String, String> envMap = System.getenv();
        envMap.entrySet()
              .stream()
              .filter(entry -> !confEnv.containsKey(envKeyToProperty(entry.getKey())))
              .forEach(entry -> confEnv.put(envKeyToProperty(entry.getKey()), entry.getValue()));
        return confEnv;
    }

    protected Set<String> getAllPropertyNames(AbstractEnvironment env) {
        Set<String> keySet = new HashSet<>();
        MutablePropertySources propertySources = env.getPropertySources();
        propertySources.stream()
                       .filter(MapPropertySource.class::isInstance)
                       .map(MapPropertySource.class::cast)
                       .forEach(propertySource -> keySet.addAll((propertySource).getSource().keySet()));
        return keySet;
    }

    protected Map<String, File> getBundles() {
        Map<String, File> bundles = new HashMap<>();
        ClassLoader classLoader = NuxeoApplication.class.getClassLoader();
        if (classLoader == null) {
            return bundles;
        }
        Enumeration<URL> res;
        try {
            res = classLoader.getResources(JarFile.MANIFEST_NAME);
            while (res.hasMoreElements()) {
                URL url = res.nextElement();
                // check manifest for nuxeo deployment
                try (InputStream is = url.openStream()) {

                    Manifest manifest = new Manifest(is);
                    Attributes attrs = manifest.getMainAttributes();
                    String name = attrs.getValue("Bundle-SymbolicName");

                    if (name != null) {
                        int pn = name.indexOf(';');
                        if (pn > 0) {
                            name = name.substring(0, pn).trim();
                        }
                        if (attrs.containsKey(new Name(BundleManifestReader.NUXEO_COMPONENT))) {
                            bundles.put(name, getFileFromUrl(url));
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.error(e.toString(), e);
        }
        return bundles;
    }

    private File getFileFromUrl(URL url) throws URISyntaxException {
        String path = url.toExternalForm();
        // hack to get the bundle...
        if (path.startsWith("file:")) {
            return new File(url.toURI()).getParentFile().getParentFile();
        } else if (path.startsWith("jar:file:")) {
            int p = path.lastIndexOf('!');
            if (p > -1) {
                path = path.substring(4, p);
            } else {
                path = path.substring(4);
            }
            return new File(new URI(path));
        } else if (path.startsWith("jar:nested:")) {
            int p = path.lastIndexOf('!');
            if (p > -1) {
                path = path.substring(11, p);
            } else {
                path = path.substring(11);
            }
            // This will not be considered a file or directory and will be handled as a nested jar
            return new File(path);
        } else {
            throw new IllegalArgumentException("Unsupported URL: " + url);
        }
    }

    public void stop() {
        log.info("Stopping Nuxeo Runtime");
        try {
            SpringBootFrameworkLoader.stop();
        } catch (BundleException e) {
            log.error("Error during runtime shudown", e);
        }
        status = STATUS_STOPPED;
    }

    public String getStatus() {
        return status;
    }

    @EventListener(ContextRefreshedEvent.class)
    void onStart(ContextRefreshedEvent event) {
        if (!STATUS_STARTED.equals(getStatus())) {
            log.info("Starting Nuxeo Runtime after startup event");
            start(event.getApplicationContext());
        }
        setApplicationContexts(event.getApplicationContext());
    }

    @PreDestroy
    void onStop() {
        log.info("Stopping Nuxeo Runtime after shutdown event");
        stop();
    }

}
