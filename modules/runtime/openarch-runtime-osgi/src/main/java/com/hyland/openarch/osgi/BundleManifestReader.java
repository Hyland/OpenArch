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

package com.hyland.openarch.osgi;

import java.io.File;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.utils.JarUtils;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public final class BundleManifestReader {

    private static final Logger log = LogManager.getLogger(BundleManifestReader.class);

    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\s*([^:\\s]+)\\s*:=\\s*([^;\\s]+)\\s*;?");

    public static final String REPOSITORY_COMPONENT = "Nuxeo-Component";

    // Utility class
    private BundleManifestReader() {
    }

    public static Dictionary<String, String> getHeadersFromJar(URL url) {
        Manifest mf = JarUtils.getManifest(url);
        if (mf != null) {
            try {
                return getHeaders(mf);
            } catch (BundleException e) {
                log.error(e, e);
            }
        }
        // not an osgi bundle
        return getDefaultHeaders(url.toExternalForm());
    }

    public static Dictionary<String, String> getHeadersFromFile(File file) {
        Manifest mf = JarUtils.getManifest(file);
        if (mf != null) {
            try {
                return getHeaders(mf);
            } catch (BundleException e) {
                log.error(e, e);
            }
        }
        // not an osgi bundle
        return getDefaultHeaders(file.getAbsolutePath());
    }

    public static Dictionary<String, String> getDefaultHeaders(String symbolicName) {
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
        headers.put(Constants.BUNDLE_ACTIVATOR, NullActivator.class.getName());
        return headers;
    }

    public static Dictionary<String, String> getHeaders(Manifest mf) throws BundleException {
        Attributes attrs = mf.getMainAttributes();
        String symbolicName = attrs.getValue(Constants.BUNDLE_SYMBOLICNAME);
        if (symbolicName == null) {
            throw new BundleException("Missing " + Constants.BUNDLE_SYMBOLICNAME);
        }
        Hashtable<String, String> headers = new Hashtable<>();
        parseSymbolicName(headers, symbolicName);
        String val = attrs.getValue(Constants.BUNDLE_ACTIVATOR);
        if (val != null) {
            headers.put(Constants.BUNDLE_ACTIVATOR, val.trim());
        }
        val = attrs.getValue(Constants.BUNDLE_CLASSPATH);
        if (val != null) {
            headers.put(Constants.BUNDLE_CLASSPATH, val.trim());
        }
        val = attrs.getValue(Constants.BUNDLE_NAME);
        if (val != null) {
            headers.put(Constants.BUNDLE_NAME, val);
        }
        val = attrs.getValue(Constants.BUNDLE_VENDOR);
        if (val != null) {
            headers.put(Constants.BUNDLE_VENDOR, val);
        }
        val = attrs.getValue(Constants.BUNDLE_VERSION);
        if (val != null) {
            headers.put(Constants.BUNDLE_VERSION, val);
        }
        val = attrs.getValue(Constants.BUNDLE_DESCRIPTION);
        if (val != null) {
            headers.put(Constants.BUNDLE_DESCRIPTION, val);
        }
        val = attrs.getValue(Constants.BUNDLE_DOCURL);
        if (val != null) {
            headers.put(Constants.BUNDLE_DOCURL, val);
        }
        val = attrs.getValue(Constants.BUNDLE_COPYRIGHT);
        if (val != null) {
            headers.put(Constants.BUNDLE_COPYRIGHT, val);
        }
        val = attrs.getValue(Constants.BUNDLE_LOCALIZATION);
        if (val != null) {
            headers.put(Constants.BUNDLE_LOCALIZATION, val);
        }
        val = attrs.getValue(Constants.REQUIRE_BUNDLE);
        if (val != null) {
            headers.put(Constants.REQUIRE_BUNDLE, val);
        }
        // Repository headers
        val = attrs.getValue(REPOSITORY_COMPONENT);
        if (val != null) {
            headers.put(REPOSITORY_COMPONENT, val);
        }
        return headers;
    }

    private static void parseSymbolicName(Dictionary<String, String> headers, String name) {
        int p = name.indexOf(';');
        if (p > 0) {
            headers.put(Constants.BUNDLE_SYMBOLICNAME, name.substring(0, p).trim());
            String tail = name.substring(p + 1);
            Matcher m = PARAMS_PATTERN.matcher(tail);
            while (m.find()) {
                headers.put(m.group(1), m.group(2));
            }
        } else {
            headers.put(Constants.BUNDLE_SYMBOLICNAME, name.trim());
        }
    }

    public static String removePropertiesFromHeaderValue(String value) {
        int p = value.indexOf(';');
        if (p > 0) {
            return value.substring(0, p).trim();
        } else {
            return value;
        }
    }

}
