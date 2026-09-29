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
package com.hyland.openarch.common;

import java.io.File;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class Environment {

    private static final Logger logger = LogManager.getLogger(Environment.class);

    public static final String HOME_DIR = "nuxeo.home.dir";

    /**
     * @since 5.6
     */
    public static final String HOME = "nuxeo.home";

    /**
     * @since 5.4.2
     */
    public static final String RUNTIME_HOME = "nuxeo.runtime.home";

    public static final String DATA_DIR = "nuxeo.data.dir";

    /**
     * @since 5.9.4
     */
    public static final String DEFAULT_DATA_DIR = "data";

    public static final String LOG_DIR = "nuxeo.log.dir";

    /**
     * @since 5.9.4
     */
    public static final String DEFAULT_LOG_DIR = "log";

    public static final String TMP_DIR = "nuxeo.tmp.dir";

    /**
     * @since 5.9.4
     */
    public static final String DEFAULT_TMP_DIR = "tmp";

    public static final String CONFIG_DIR = "nuxeo.config.dir";

    /**
     * @since 5.9.4
     */
    public static final String DEFAULT_CONFIG_DIR = "config";

    // volatile for double-checked locking
    private static volatile Environment defaultEnvironment; // NOSONAR (volatile for double-checked locking init)

    protected final File home;

    protected File data;

    protected File log;

    protected File config;

    protected File temp;

    protected final Properties properties;

    // Handy parameter to distinguish from (Runtime)home
    private File serverHome = null;

    // Handy parameter to distinguish from (Server)home
    private File runtimeHome = null;

    // internal API client
    public static final String PROPERTY_CLIENT_ID = "hxpr.internal.api.service.user.client.id";

    /**
     * Call to that constructor should be followed by a call to {@link #init()}. Depending on the available System
     * properties, you may want to also call {@link #setServerHome(File)} methods
     * before {@link #init()}; here is the recommended order:
     *
     * <pre>
     * Environment env = new Environment(home);
     * Environment.setDefault(env);
     * env.setServerHome(home);
     * env.init();
     * </pre>
     *
     * @param home Root path used for most defaults. It is recommended to make it match the server home rather than the
     *            runtime home.
     * @see #init()
     */
    public Environment(File home) {
        this.home = home.getAbsoluteFile();
        this.properties = new Properties();
    }

    public static void setDefault(Environment env) {
        synchronized (Environment.class) {
            defaultEnvironment = env;
        }
    }

    public static Environment getDefault() {
        return getDefault(System.getProperties());
    }

    /**
     * @since 11.5
     */
    public static Environment getDefault(Properties systemProperties) {
        if (defaultEnvironment == null) {
            synchronized (Environment.class) {
                if (defaultEnvironment == null) {
                    String homeDir = systemProperties.getProperty(HOME);
                    if (homeDir != null) {
                        File home = new File(homeDir);
                        if (home.isDirectory()) {
                            Environment env = new Environment(home);
                            env.init();
                            defaultEnvironment = env;
                        }
                    }
                }
            }
        }
        return defaultEnvironment;
    }

    public File getHome() {
        return home;
    }

    public File getTemp() {
        if (temp == null) {
            setTemp(properties.getProperty(TMP_DIR, DEFAULT_TMP_DIR));
        }
        return temp;
    }

    /**
     * Resolve the path against {@link Environment#serverHome} if not absolute.
     *
     * @since 8.1
     */
    public void setTemp(String temp) {
        setTemp(getServerHome().toPath().resolve(temp).toFile());
    }

    public void setTemp(File temp) {
        this.temp = temp.getAbsoluteFile();
        setProperty(TMP_DIR, temp.getAbsolutePath());
        temp.mkdirs();
    }

    public File getConfig() {
        if (config == null) {
            setConfig(properties.getProperty(CONFIG_DIR, DEFAULT_CONFIG_DIR));
        }
        return config;
    }

    /**
     * Resolve the path against {@link Environment#runtimeHome} if not absolute.
     *
     * @since 8.1
     */
    public void setConfig(String config) {
        File configFile = getRuntimeHome().toPath().resolve(config).toFile();
        setConfig(configFile);
    }

    public void setConfig(File config) {
        this.config = config.getAbsoluteFile();
        setProperty(CONFIG_DIR, config.getAbsolutePath());
        config.mkdirs();
    }

    public File getLog() {
        if (log == null) {
            setLog(properties.getProperty(LOG_DIR, DEFAULT_LOG_DIR));
        }
        return log;
    }

    /**
     * Resolve the path against {@link Environment#serverHome} if not absolute.
     *
     * @since 8.1
     */
    public void setLog(String log) {
        setLog(getServerHome().toPath().resolve(log).toFile());
    }

    public void setLog(File log) {
        this.log = log.getAbsoluteFile();
        setProperty(LOG_DIR, log.getAbsolutePath());
        log.mkdirs();
    }

    public File getData() {
        if (data == null) {
            setData(properties.getProperty(DATA_DIR, DEFAULT_DATA_DIR));
        }
        return data;
    }

    /**
     * Resolve the path against {@link Environment#runtimeHome} if not absolute.
     *
     * @since 8.1
     */
    public void setData(String data) {
        setData(getRuntimeHome().toPath().resolve(data).toFile());
    }

    public void setData(File data) {
        this.data = data.getAbsoluteFile();
        setProperty(DATA_DIR, data.getAbsolutePath());
        data.mkdirs();
    }

    /**
     * @since 5.4.2
     */
    public File getRuntimeHome() {
        initRuntimeHome();
        return runtimeHome;
    }

    /**
     * @since 5.4.2
     */
    public void setRuntimeHome(File runtimeHome) {
        this.runtimeHome = runtimeHome.getAbsoluteFile();
        setProperty(RUNTIME_HOME, runtimeHome.getAbsolutePath());
    }

    /**
     * If setting a path property, consider using {@link #setPath(String, String)}
     */
    private void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * Initialization with System properties to avoid issues due to home set with runtime home instead of server home.
     * If {@link #HOME} System property is not set, or if you want to set a custom server home, then you should
     * call {@link #setServerHome(File)} before.
     *
     * @since 5.4.1
     */
    public void init() {
        initServerHome();
        initRuntimeHome();

        String dataDir = System.getProperty(DATA_DIR);
        if (StringUtils.isNotEmpty(dataDir)) {
            setData(new File(dataDir));
        }

        String configDir = System.getProperty(CONFIG_DIR);
        if (StringUtils.isNotEmpty(configDir)) {
            setConfig(new File(configDir));
        }

        String logDir = System.getProperty(LOG_DIR);
        if (StringUtils.isNotEmpty(logDir)) {
            setLog(new File(logDir));
        }

        String tmpDir = System.getProperty(TMP_DIR);
        if (StringUtils.isNotEmpty(tmpDir)) {
            setTemp(new File(tmpDir));
        }
    }

    private void initRuntimeHome() {
        if (runtimeHome != null) {
            return;
        }
        String runtimeDir = System.getProperty(RUNTIME_HOME);
        if (runtimeDir != null && !runtimeDir.isEmpty()) {
            setRuntimeHome(new File(runtimeDir));
        } else {
            setRuntimeHome(home);
        }
    }

    /**
     * This method always returns the server home (or {@link #getHome()} if {@link #HOME_DIR} is not set).
     *
     * @since 5.4.2
     * @return Server home
     */
    public File getServerHome() {
        initServerHome();
        return serverHome;
    }

    /**
     * @since 5.4.2
     */
    public void setServerHome(File serverHome) {
        this.serverHome = serverHome.getAbsoluteFile();
        setProperty(HOME_DIR, serverHome.getAbsolutePath());
    }

    private void initServerHome() {
        if (serverHome != null) {
            return;
        }
        String homeDir = System.getProperty(HOME, System.getProperty(HOME_DIR));
        if (homeDir != null && !homeDir.isEmpty()) {
            setServerHome(new File(homeDir));
        } else {
            logger.warn("Could not set the server home from {} or {} system properties, will use {}",
                    HOME, HOME_DIR, home);
            setServerHome(home);
        }
        logger.debug(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
