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
package com.hyland.openarch.runtime.api;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

import javax.security.auth.login.LoginException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.Environment;
import com.hyland.openarch.common.function.ThrowableRunnable;
import com.hyland.openarch.common.function.ThrowableSupplier;
import com.hyland.openarch.common.utils.DurationUtils;
import com.hyland.openarch.runtime.RuntimeService;
import com.hyland.openarch.runtime.RuntimeServiceException;
import com.hyland.openarch.runtime.api.login.LoginService;
import com.hyland.openarch.runtime.api.login.LoginContext;
import com.hyland.openarch.runtime.trackers.files.FileEvent;
import com.hyland.openarch.runtime.trackers.files.FileEventTracker;

/**
 * This class is the main entry point to a OpenArch runtime application.
 * <p>
 * It offers an easy way to create new sessions, to access system services and other resources.
 * <p>
 * There are two type of services:
 * <ul>
 * <li>Global Services - these services are uniquely defined by a service class, and there is an unique instance of the
 * service in the system per class.
 * <li>Local Services - these services are defined by a class and an URI. This type of service allows multiple service
 * instances for the same class of services. Each instance is uniquely defined in the system by an URI.
 * </ul>
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public final class Framework {

    private static final Logger log = LogManager.getLogger(Framework.class);

    /**
     * Global dev property.
     *
     * @since 5.6
     * @see #isDevModeSet()
     */
    public static final String NUXEO_DEV_SYSTEM_PROP = "org.nuxeo.dev";

    /**
     * Global testing property.
     *
     * @since 5.6
     * @see #isTestModeSet()
     */
    public static final String NUXEO_TESTING_SYSTEM_PROP = "com.hyland.openarch.runtime.testing";

    /**
     * Property for strict mode startup.
     *
     * @since TODO
     */
    public static final String NUXEO_STRICT_STARTUP_SYSTEM_PROP = "nuxeo.runtime.startup.strict";

    /**
     * The runtime instance.
     */
    private static RuntimeService runtime;

    /**
     * A class loader used to share resources between all bundles.
     * <p>
     * This is useful to put resources outside any bundle (in a directory on the file system) and then refer them from
     * XML contributions.
     * <p>
     * The resource directory used by this loader is ${nuxeo_data_dir}/resources whee ${nuxeo_data_dir} is usually
     * ${nuxeo_home}/data
     */
    protected static SharedResourceLoader resourceLoader;

    // Utility class.
    private Framework() {
    }

    public static void initialize(RuntimeService runtimeService) {
        if (runtime != null) {
            throw new RuntimeServiceException("OpenArch Framework was already initialized");
        }
        runtime = runtimeService;
        reloadResourceLoader();
        runtime.start();
    }

    public static void reloadResourceLoader() {
        File rs = new File(Environment.getDefault().getData(), "resources");
        rs.mkdirs();
        URL url;
        try {
            url = rs.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeServiceException(e);
        }
        resourceLoader = new SharedResourceLoader(new URL[] { url }, Framework.class.getClassLoader());
    }

    /**
     * Reload the resources loader, keeping URLs already tracked, and adding possibility to add or remove some URLs.
     * <p>
     * Useful for hot reload of jars.
     *
     * @since 5.6
     */
    public static void reloadResourceLoader(List<URL> urlsToAdd, List<URL> urlsToRemove) {
        File rs = new File(Environment.getDefault().getData(), "resources");
        rs.mkdirs();
        URL[] existing = null;
        if (resourceLoader != null) {
            existing = resourceLoader.getURLs();
        }
        // reinit
        URL url;
        try {
            url = rs.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeServiceException(e);
        }
        SharedResourceLoader loader = new SharedResourceLoader(new URL[] { url }, Framework.class.getClassLoader());
        // add back existing urls unless they should be removed, and add new
        // urls
        if (existing != null) {
            for (URL oldURL : existing) {
                if (urlsToRemove == null || !urlsToRemove.contains(oldURL)) {
                    loader.addURL(oldURL);
                }
            }
        }
        if (urlsToAdd != null) {
            for (URL newURL : urlsToAdd) {
                loader.addURL(newURL);
            }
        }
        if (resourceLoader != null) {
            // properly close the previous resource ClassLoader, otherwise any leak of an InputStream from it
            // will keep the JAR available in the internal sun.net.www.protocol.jar JarFileFactory cache
            // used by sun.net.www.protocol.jar.JarURLConnection, which will cause subsequent references
            // to use the old JAR.
            try {
                resourceLoader.close();
            } catch (IOException e) {
                log.error("Failed to close previous resource loader", e);
            }
        }
        resourceLoader = loader;
    }

    public static void shutdown() throws InterruptedException {
        if (runtime == null) {
            throw new IllegalStateException("runtime not exist");
        }
        try {
            runtime.stop();
        } finally {
            runtime = null;
        }
    }

    /**
     * Tests whether or not the runtime was initialized.
     *
     * @return true if the runtime was initialized, false otherwise
     */
    public static synchronized boolean isInitialized() {
        return runtime != null;
    }

    public static SharedResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    /**
     * Gets the runtime service instance.
     *
     * @return the runtime service instance
     */
    public static RuntimeService getRuntime() {
        return runtime;
    }

    /**
     * Gets a service given its class.
     */
    public static <T> T getService(Class<T> serviceClass) {
        ServiceProvider provider = DefaultServiceProvider.getProvider();
        if (provider != null) {
            return provider.getService(serviceClass);
        }
        checkRuntimeInitialized();
        // TODO impl a runtime service provider
        return runtime.getService(serviceClass);
    }

    /**
     * Runs the given {@link Runnable} while logged in as a system user.
     *
     * @param runnable what to run
     * @since 8.4
     */
    public static void doPrivileged(Runnable runnable) {
        loginAndDo(Framework::loginSystem, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Calls the given {@link Supplier} while logged in as a system user and returns its result.
     *
     * @param supplier what to call
     * @return the supplier's result
     * @since 8.4
     */
    public static <T> T doPrivileged(Supplier<T> supplier) {
        return loginAndDo(Framework::loginSystem, supplier::get);
    }

    /**
     * Runs the given {@link ThrowableRunnable} while logged in as a system user.
     *
     * @param runnable what to run
     * @since 11.3
     */
    public static <E extends Throwable> void doPrivilegedThrowing(ThrowableRunnable<E> runnable) throws E {
        loginAndDo(Framework::loginSystem, runnable.toThrowableSupplier());
    }

    /**
     * Calls the given {@link ThrowableSupplier} while logged in as a system user and returns its result.
     *
     * @param supplier what to call
     * @return the supplier's result
     * @since 11.3
     */
    public static <T, E extends Throwable> T doPrivilegedThrowing(ThrowableSupplier<T, E> supplier) throws E {
        return loginAndDo(Framework::loginSystem, supplier);
    }

    /**
     * Logs in the OpenArch platform using the given {@link ThrowableSupplier authSupplier}, then gets the given
     * {@link ThrowableSupplier supplier} before logging out.
     *
     * @since 11.1
     */
    protected static <T, E extends Throwable> T loginAndDo(ThrowableSupplier<LoginContext, LoginException> authSupplier,
            ThrowableSupplier<T, E> supplier) throws E {
        try {
            try (var __ = authSupplier.get()) {
                return supplier.get();
            }
        } catch (LoginException e) {
            throw new RuntimeServiceException(e);
        }
    }

    /**
     * Login as the system user (with no originating user).
     * <p>
     * The returned {@link LoginContext} MUST be closed when done.
     *
     * @return the login context, to be closed when done
     * @see #loginSystem(String)
     * @since 11.1
     */
    public static LoginContext loginSystem() {
        checkRuntimeInitialized();
        return getService(LoginService.class).loginSystem();
    }

    /**
     * Login as the system user, remembering the originating user.
     * <p>
     * The returned {@link LoginContext} MUST be closed when done.
     * <p>
     * If the originating user is {@code null}, then this is equivalent to {@link #loginSystem()}.
     *
     * @param originatingId the originating user id
     * @return the login context, to be closed when done
     * @see #loginSystem()
     * @since 11.1
     */
    public static LoginContext loginSystem(String originatingId) {
        checkRuntimeInitialized();
        return getService(LoginService.class).loginSystem(originatingId);
    }

    /**
     * Login as the given user. The returned {@link LoginContext} MUST be closed when done.
     *
     * @param userId the user id
     * @return the login context, to be closed when done
     * @throws LoginException on login failure
     * @since 11.1
     */
    public static LoginContext loginUser(String userId) throws LoginException {
        checkRuntimeInitialized();
        return getService(LoginService.class).loginUser(userId);
    }

    public static LoginContext loginUserOrSystem(String userId) throws LoginException {
        checkRuntimeInitialized();
        return getService(LoginService.class).loginUserOrSystem(userId);
    }

    /**
     * Gets the given property value if any, otherwise null.
     * <p>
     * The framework properties will be searched first then if any matching property is found the system properties are
     * searched too.
     *
     * @param key the property key
     * @return the property value if any or null otherwise
     */
    public static String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * Gets the given property value if any, otherwise returns the given default value.
     * <p>
     * The framework properties will be searched first then if any matching property is found the system properties are
     * searched too.
     *
     * @param key the property key
     * @param defValue the default value to use
     * @return the property value if any otherwise the default value
     */
    public static String getProperty(String key, String defValue) {
        checkRuntimeInitialized();
        return runtime.getProperty(key, defValue);
    }

    /**
     * Gets the given property parsed as an int, or the default value if missing or unparseable.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the property value
     */
    public static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(Framework.getProperty(key));
        } catch (NullPointerException | NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets the given property parsed as a long, or the default value if missing or unparseable.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the property value
     */
    public static long getLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(Framework.getProperty(key));
        } catch (NullPointerException | NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets the given property parsed as a duration, or the default value if missing or unparseable.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the property value
     */
    public static Duration getDurationProperty(String key, Duration defaultValue) {
        try {
            return DurationUtils.parse(Framework.getProperty(key));
        } catch (NullPointerException | DateTimeParseException e) {
            return defaultValue;
        }
    }

    /**
     * Gets all the framework properties. The system properties are not included in the returned map.
     *
     * @return the framework properties map. Never returns null.
     */
    public static Properties getProperties() {
        checkRuntimeInitialized();
        return runtime.getProperties();
    }

    /**
     * Expands any variable found in the given expression with the value of the corresponding framework property.
     * <p>
     * The variable format is ${property_key}.
     * <p>
     * System properties are also expanded.
     */
    public static String expandVars(String expression) {
        checkRuntimeInitialized();
        return runtime.expandVars(expression);
    }

    /**
     * Returns true if dev mode is set.
     * <p>
     * Activating this mode, some of the code may not behave as it would in production, to ease up debugging and working
     * on developing the application.
     * <p>
     * For instance, it'll enable hot-reload if some packages are installed while the framework is running. It will also
     * reset some caches when that happens.
     */
    public static boolean isDevModeSet() {
        return isBooleanPropertyTrue(NUXEO_DEV_SYSTEM_PROP);
    }

    /**
     * Returns true if test mode is set.
     * <p>
     * Activating this mode, some of the code may not behave as it would in production, to ease up testing.
     */
    public static boolean isTestModeSet() {
        return isBooleanPropertyTrue(NUXEO_TESTING_SYSTEM_PROP);
    }

    /**
     * Returns true if given property is false when compared to a boolean value. Returns false if given property in
     * unset.
     * <p>
     * Checks for the system properties if property is not found in the runtime properties.
     *
     * @since 5.8
     */
    public static boolean isBooleanPropertyFalse(String propName) {
        String v = getProperty(propName);
        if (v == null) {
            v = System.getProperty(propName);
        }
        if (StringUtils.isBlank(v)) {
            return false;
        }
        return !Boolean.parseBoolean(v);
    }

    /**
     * Returns true if given property is true when compared to a boolean value.
     * <p>
     * Checks for the system properties if property is not found in the runtime properties.
     *
     * @since 5.6
     */
    public static boolean isBooleanPropertyTrue(String propName) {
        String v = getProperty(propName);
        if (v == null) {
            v = System.getProperty(propName);
        }
        return Boolean.parseBoolean(v);
    }

    /**
     * @see FileEventTracker
     * @param file the file to delete
     * @param marker the marker Object
     */
    public static void trackFile(File file, Object marker) {
        FileEvent.onFile(Framework.class, file, marker).send();
    }

    /**
     * @since 6.0
     */
    protected static void checkRuntimeInitialized() {
        if (runtime == null) {
            throw new IllegalStateException("Runtime not initialized");
        }
    }

    /**
     * Creates an empty file in the framework temporary-file directory ({@code nuxeo.tmp.dir} vs {@code java.io.tmpdir}
     * ), using the given prefix and suffix to generate its name.
     * <p>
     * Invoking this method is equivalent to invoking
     * <code>{@link File#createTempFile(java.lang.String, java.lang.String, java.io.File)
     * File.createTempFile(prefix,&nbsp;suffix,&nbsp;Environment.getDefault().getTemp())}</code>.
     * <p>
     * The {@link #createTempFilePath(String, String, FileAttribute...)} method provides an alternative method to create
     * an empty file in the framework temporary-file directory. Files created by that method may have more restrictive
     * access permissions to files created by this method and so may be more suited to security-sensitive applications.
     *
     * @param prefix The prefix string to be used in generating the file's name; must be at least three characters long
     * @param suffix The suffix string to be used in generating the file's name; may be <code>null</code>, in which case
     *            the suffix <code>".tmp"</code> will be used
     * @return An abstract pathname denoting a newly-created empty file
     * @throws IllegalArgumentException If the <code>prefix</code> argument contains fewer than three characters
     * @throws IOException If a file could not be created
     * @throws SecurityException If a security manager exists and its <code>
     *             {@link java.lang.SecurityManager#checkWrite(java.lang.String)}</code> method does not allow a file to
     *             be created
     * @since 8.1
     * @see File#createTempFile(String, String, File)
     * @see Environment#getTemp()
     * @see #createTempFilePath(String, String, FileAttribute...)
     * @see #createTempDirectory(String, FileAttribute...)
     */
    public static File createTempFile(String prefix, String suffix) throws IOException {
        try {
            return File.createTempFile(prefix, suffix, getTempDir());
        } catch (IOException e) {
            throw new IOException("Could not create temp file in " + getTempDir(), e);
        }
    }

    /**
     * @return the OpenArch temp dir returned by {@link Environment#getTemp()}. If the Environment fails to initialize,
     *         then returns the File denoted by {@code "nuxeo.tmp.dir"} System property, or {@code "java.io.tmpdir"}.
     * @since 8.1
     */
    private static File getTempDir() {
        Environment env = Environment.getDefault();
        File temp = env != null ? env.getTemp()
                : new File(System.getProperty("nuxeo.tmp.dir", System.getProperty("java.io.tmpdir")));
        temp.mkdirs();
        return temp;
    }

    /**
     * Creates an empty file in the framework temporary-file directory ({@code nuxeo.tmp.dir} vs {@code java.io.tmpdir}
     * ), using the given prefix and suffix to generate its name. The resulting {@code Path} is associated with the
     * default {@code FileSystem}.
     * <p>
     * Invoking this method is equivalent to invoking
     * {@link Files#createTempFile(Path, String, String, FileAttribute...)
     * Files.createTempFile(Environment.getDefault().getTemp().toPath(),&nbsp;prefix,&nbsp;suffix,&nbsp;attrs)}.
     *
     * @param prefix the prefix string to be used in generating the file's name; may be {@code null}
     * @param suffix the suffix string to be used in generating the file's name; may be {@code null}, in which case "
     *            {@code .tmp}" is used
     * @param attrs an optional list of file attributes to set atomically when creating the file
     * @return the path to the newly created file that did not exist before this method was invoked
     * @throws IllegalArgumentException if the prefix or suffix parameters cannot be used to generate a candidate file
     *             name
     * @throws UnsupportedOperationException if the array contains an attribute that cannot be set atomically when
     *             creating the directory
     * @throws IOException if an I/O error occurs or the temporary-file directory does not exist
     * @throws SecurityException In the case of the default provider, and a security manager is installed, the
     *             {@link SecurityManager#checkWrite(String) checkWrite} method is invoked to check write access to the
     *             file.
     * @since 8.1
     * @see Files#createTempFile(Path, String, String, FileAttribute...)
     * @see Environment#getTemp()
     * @see #createTempFile(String, String)
     */
    public static Path createTempFilePath(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        try {
            return Files.createTempFile(getTempDir().toPath(), prefix, suffix, attrs);
        } catch (IOException e) {
            throw new IOException("Could not create temp file in " + getTempDir(), e);
        }
    }

    /**
     * Creates a new directory in the framework temporary-file directory ({@code nuxeo.tmp.dir} vs
     * {@code java.io.tmpdir}), using the given prefix to generate its name. The resulting {@code Path} is associated
     * with the default {@code FileSystem}.
     * <p>
     * Invoking this method is equivalent to invoking {@link Files#createTempDirectory(Path, String, FileAttribute...)
     * Files.createTempDirectory(Environment.getDefault().getTemp().toPath(),&nbsp;prefix,&nbsp;suffix,&nbsp;attrs)}.
     *
     * @param prefix the prefix string to be used in generating the directory's name; may be {@code null}
     * @param attrs an optional list of file attributes to set atomically when creating the directory
     * @return the path to the newly created directory that did not exist before this method was invoked
     * @throws IllegalArgumentException if the prefix cannot be used to generate a candidate directory name
     * @throws UnsupportedOperationException if the array contains an attribute that cannot be set atomically when
     *             creating the directory
     * @throws IOException if an I/O error occurs or the temporary-file directory does not exist
     * @throws SecurityException In the case of the default provider, and a security manager is installed, the
     *             {@link SecurityManager#checkWrite(String) checkWrite} method is invoked to check write access when
     *             creating the directory.
     * @since 8.1
     * @see Files#createTempDirectory(Path, String, FileAttribute...)
     * @see Environment#getTemp()
     * @see #createTempFile(String, String)
     */
    public static Path createTempDirectory(String prefix, FileAttribute<?>... attrs) throws IOException {
        try {
            return Files.createTempDirectory(getTempDir().toPath(), prefix, attrs);
        } catch (IOException e) {
            throw new IOException("Could not create temp directory in " + getTempDir(), e);
        }
    }

}
