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
package org.nuxeo.osgi.application.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.Environment;
import com.hyland.openarch.common.utils.StringUtils;
import org.nuxeo.osgi.BundleFile;
import org.nuxeo.osgi.BundleImpl;
import org.nuxeo.osgi.DirectoryBundleFile;
import org.nuxeo.osgi.JarBundleFile;
import org.nuxeo.osgi.OSGiAdapter;
import org.nuxeo.osgi.SystemBundle;
import org.nuxeo.osgi.SystemBundleFile;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class FrameworkLoader {

    private static final Logger log = LogManager.getLogger(FrameworkLoader.class);

    private static boolean isStarted;

    private static File home;

    private static ClassLoader loader;

    private static List<File> bundleFiles;

    private static OSGiAdapter osgi;

    private FrameworkLoader() {
        // this is a utility class
    }

    public static OSGiAdapter osgi() {
        return osgi;
    }

    public static ClassLoader getLoader() {
        return loader;
    }

    public static synchronized void start(ClassLoader cl, File home, List<File> bundleFiles,
            Map<String, Object> hostEnv) throws BundleException {
        if (isStarted) {
            return;
        }
        FrameworkLoader.home = home;
        FrameworkLoader.bundleFiles = bundleFiles == null ? new ArrayList<>() : bundleFiles;
        Collections.sort(FrameworkLoader.bundleFiles);

        loader = cl;

        // build environment
        Environment env = createEnvironment(home, hostEnv);
        Environment.setDefault(env);
        loadSystemProperties();

        osgi = new OSGiAdapter(home);

        try {
            doStart();
        } finally {
            isStarted = true;
        }
    }

    public static synchronized void stop() throws BundleException {
        if (!isStarted) {
            return;
        }
        try {
            doStop();
        } finally {
            isStarted = false;
        }
    }

    private static void printDeploymentOrderInfo(List<File> files) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (File file : files) {
                if (file != null) {
                    sb.append("\n\t").append(file.getPath());
                }
            }
            log.debug("Deployment order: {}", sb);
        }
    }

    private static final Attributes.Name SYMBOLIC_NAME = new Attributes.Name(Constants.BUNDLE_SYMBOLICNAME);

    private static boolean isBundle(File f) {
        Manifest mf;
        try {
            if (f.isFile()) { // jar file
                try (JarFile jf = new JarFile(f)) {
                    mf = jf.getManifest();
                }
                if (mf == null) {
                    return false;
                }
            } else if (f.isDirectory()) { // directory
                f = new File(f, "META-INF/MANIFEST.MF");
                if (!f.isFile()) {
                    return false;
                }
                mf = new Manifest();
                try (FileInputStream input = new FileInputStream(f)) {
                    mf.read(input);
                }
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return mf.getMainAttributes().containsKey(SYMBOLIC_NAME);
    }

    private static void doStart() throws BundleException {
        printStartMessage();
        // install system bundle first
        BundleFile bf;
        try {
            bf = new SystemBundleFile(home);
        } catch (IOException e) {
            throw new BundleException("Cannot create system bundle for " + home, e);
        }
        SystemBundle systemBundle = new SystemBundle(osgi, bf, loader);
        osgi.setSystemBundle(systemBundle);
        printDeploymentOrderInfo(bundleFiles);
        for (File f : bundleFiles) {
            if (!isBundle(f)) {
                continue;
            }
            try {
                install(f);
            } catch (IOException | BundleException | RuntimeException e) {
                log.error("Failed to install bundle: {}", f, e);
            }
        }
        osgi.fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.STARTED, systemBundle, null));
    }

    private static void doStop() throws BundleException {
        try {
            osgi.shutdown();
        } catch (IOException e) {
            throw new BundleException("Cannot shutdown OSGi", e);
        }
    }

    private static String install(File f) throws IOException, BundleException {
        BundleFile bf;
        if (f.isDirectory()) {
            bf = new DirectoryBundleFile(f);
        } else {
            bf = new JarBundleFile(f);
        }
        BundleImpl bundle = new BundleImpl(osgi, bf, loader);
        if (bundle.getState() == 0) {
            // not a bundle (no Bundle-SymbolicName)
            return null;
        }
        osgi.install(bundle);
        return bundle.getSymbolicName();
    }

    private static void loadSystemProperties() {
        File file = new File(home, "system.properties");
        if (!file.isFile()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                String v = (String) entry.getValue();
                v = StringUtils.expandVars(v, System.getProperties());
                System.setProperty((String) entry.getKey(), v);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system properties", e);
        }
    }

    private static String getEnvProperty(String key, Map<String, Object> hostEnv, Properties sysprops,
            boolean addToSystemProperties) {
        String v = (String) hostEnv.get(key);
        if (v == null) {
            v = System.getProperty(key);
        }
        if (v != null) {
            v = StringUtils.expandVars(v, sysprops);
            if (addToSystemProperties) {
                sysprops.setProperty(key, v);
            }
        }
        return v;
    }

    private static Environment createEnvironment(File home, Map<String, Object> hostEnv) {
        Properties sysprops = System.getProperties();
        sysprops.setProperty(Environment.NUXEO_RUNTIME_HOME, home.getAbsolutePath());

        Environment env = Environment.getDefault();
        if (env == null) {
            env = new Environment(home);
        }
        if (!home.equals(env.getRuntimeHome())) {
            env.setRuntimeHome(home);
        }

        String v;
        v = getEnvProperty(Environment.NUXEO_DATA_DIR, hostEnv, sysprops, true);
        if (v != null) {
            env.setData(new File(v));
        } else {
            sysprops.setProperty(Environment.NUXEO_DATA_DIR, env.getData().getAbsolutePath());
        }

        v = getEnvProperty(Environment.NUXEO_LOG_DIR, hostEnv, sysprops, true);
        if (v != null) {
            env.setLog(new File(v));
        } else {
            sysprops.setProperty(Environment.NUXEO_LOG_DIR, env.getLog().getAbsolutePath());
        }

        v = getEnvProperty(Environment.NUXEO_TMP_DIR, hostEnv, sysprops, true);
        if (v != null) {
            env.setTemp(new File(v));
        } else {
            sysprops.setProperty(Environment.NUXEO_TMP_DIR, env.getTemp().getAbsolutePath());
        }

        v = getEnvProperty(Environment.NUXEO_CONFIG_DIR, hostEnv, sysprops, true);
        if (v != null) {
            env.setConfig(new File(v));
        } else {
            sysprops.setProperty(Environment.NUXEO_CONFIG_DIR, env.getConfig().getAbsolutePath());
        }

        return env;
    }

    private static void printStartMessage() {
        StringBuilder msg = getStartMessage();
        log.info(msg);
    }

    /**
     * @since 5.5
     * @return Environment summary
     */
    private static StringBuilder getStartMessage() {
        String newline = System.getProperty("line.separator");
        Environment env = Environment.getDefault();
        String hr = "======================================================================";
        StringBuilder msg = new StringBuilder(newline);
        msg.append(hr).append(newline);
        msg.append("= Starting Nuxeo Framework").append(newline);
        msg.append(hr).append(newline);
        msg.append("  * Server home = ").append(env.getServerHome()).append(newline);
        msg.append("  * Runtime home = ").append(env.getRuntimeHome()).append(newline);
        msg.append("  * Data Directory = ").append(env.getData()).append(newline);
        msg.append("  * Log Directory = ").append(env.getLog()).append(newline);
        msg.append("  * Configuration Directory = ").append(env.getConfig()).append(newline);
        msg.append("  * Temp Directory = ").append(env.getTemp()).append(newline);
        msg.append(hr);
        return msg;
    }
}
