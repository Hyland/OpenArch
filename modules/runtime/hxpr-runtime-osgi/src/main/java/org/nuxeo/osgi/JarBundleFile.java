/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.osgi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.StringUtils;
import org.osgi.framework.Constants;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class JarBundleFile implements BundleFile {

    private static final Logger log = LogManager.getLogger(JarBundleFile.class);

    protected JarFile jarFile;

    protected String urlBase;

    @SuppressWarnings("resource")
    public JarBundleFile(File file) throws IOException {
        this(new JarFile(file));
    }

    public JarBundleFile(JarFile jarFile) {
        this.jarFile = jarFile;
        try {
            urlBase = "jar:" + new File(jarFile.getName()).toURI().toURL() + "!/";
        } catch (MalformedURLException e) {
            log.error("Failed to convert bundle location to an URL: {}. Bundle getEntry will not work.", jarFile.getName(), e);
        }
    }

    @Override
    public URL getEntry(String name) {
        ZipEntry entry = jarFile.getEntry(name);
        if (entry == null) {
            return null;
        }
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        try {
            return new URL(urlBase + name);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public String getFileName() {
        String path = jarFile.getName();
        int punix = path.lastIndexOf('/');
        int pwin = path.lastIndexOf('\\');
        int p = punix > pwin ? punix : pwin;
        if (p == -1) {
            return path;
        }
        if (p == 0) {
            return "";
        }
        return path.substring(p + 1);
    }

    protected String getLocation() {
        return jarFile.getName();
    }

    @Override
    public Manifest getManifest() {
        try {
            return jarFile.getManifest();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Collection<BundleFile> getNestedBundles(File tmpDir) throws IOException {
        Attributes attrs = jarFile.getManifest().getMainAttributes();
        String cp = attrs.getValue(Constants.BUNDLE_CLASSPATH);
        if (cp == null) {
            cp = attrs.getValue("Class-Path");
        }
        if (cp == null) {
            return null;
        }
        String[] paths = StringUtils.split(cp, ',', true);
        URL base = new URL("jar:" + new File(jarFile.getName()).toURI().toURL().toExternalForm() + "!/");
        String fileName = getFileName();
        List<BundleFile> nested = new ArrayList<>();
        for (String path : paths) {
            if (path.equals(".")) {
                continue; // TODO
            }
            String location = base + path;
            String name = path.replace('/', '_');
            File dest = new File(tmpDir, fileName + '-' + name);
            try {
                extractNestedJar(jarFile, path, dest);
                nested.add(new NestedJarBundleFile(location, dest));
            } catch (FileNotFoundException e) {
                log.error("A nested jar is referenced in manifest but not found: {}", location);
            } catch (IOException e) {
                log.error(e);
            }
        }
        return nested;
    }

    public static void extractNestedJar(JarFile file, String path, File dest) throws IOException {
        extractNestedJar(file, file.getEntry(path), dest);
    }

    public static void extractNestedJar(JarFile file, ZipEntry entry, File dest) throws IOException {
        if (!dest.getAbsolutePath().startsWith(dest.getParentFile().getCanonicalPath())) {
            throw new IOException("Invalid destination path for nested jar: " + dest);
        }
        try (InputStream in = file.getInputStream(entry)) {
            FileUtils.copyInputStreamToFile(in, dest.getCanonicalFile());
        }
    }

    @Override
    public Collection<BundleFile> findNestedBundles(File tmpDir) throws IOException {
        URL base = new URL("jar:" + new File(jarFile.getName()).toURI().toURL().toExternalForm() + "!/");
        String fileName = getFileName();
        Enumeration<JarEntry> entries = jarFile.entries();
        List<BundleFile> nested = new ArrayList<>();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String path = entry.getName();
            if (path.endsWith(".jar") && !path.contains("..")) {
                String location = base + path;
                String name = path.replace('/', '_');
                File dest = new File(tmpDir, fileName + '-' + name);
                extractNestedJar(jarFile, entry, dest);
                nested.add(new NestedJarBundleFile(location, dest));
            }
        }
        return nested;
    }

    @Override
    public String getSymbolicName() {
        try {
            String value = jarFile.getManifest().getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
            return value != null ? BundleManifestReader.removePropertiesFromHeaderValue(value) : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public URL getURL() {
        try {
            return new File(jarFile.getName()).toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public URL getJarURL() {
        try {
            String url = new File(jarFile.getName()).toURI().toURL().toExternalForm();
            return new URL("jar:" + url + "!/");
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return getLocation();
    }

    protected final URL getEntryUrl(String name) throws MalformedURLException {
        return new URL(urlBase + name);
    }

}
