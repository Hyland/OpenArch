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
package org.nuxeo.common.utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FileUtils {

    private static final Logger log = LogManager.getLogger(FileUtils.class);

    // This is an utility class
    private FileUtils() {
    }

    /**
     * Decodes an URL path so that is can be processed as a filename later.
     *
     * @param url the Url to be processed.
     * @return the decoded path.
     */
    public static String getFilePathFromUrl(URL url) {
        String path = "";
        if (url.getProtocol().equals("file")) {
            try {
                path = URLDecoder.decode(url.getPath(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.error(e);
            }
        }
        return path;
    }

    /**
     * Retrieves the total path of a resource from the Thread Context.
     *
     * @param resource the resource name to be retrieved.
     * @return the decoded path.
     */
    public static String getResourcePathFromContext(String resource) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
        return getFilePathFromUrl(url);
    }

    public static File getResourceFileFromContext(String resource) {
        File file;
        String filename = getResourcePathFromContext(resource);
        if (filename.equals("")) {
            file = null;
        } else {
            file = new File(filename);
        }
        return file;
    }

    public static File[] findFiles(File root, String pattern, boolean recurse) {
        List<File> result = new ArrayList<>();
        if (pattern == null) {
            if (recurse) {
                collectFiles(root, result);
            } else {
                return root.listFiles();
            }
        } else {
            FileNamePattern pat = new FileNamePattern(pattern);
            if (recurse) {
                collectFiles(root, pat, result);
            } else {
                File[] files = root.listFiles();
                for (File file : files) {
                    if (pat.match(file)) {
                        result.add(file);
                    }
                }
            }
        }
        return result.toArray(new File[result.size()]);
    }

    public static void collectFiles(File root, FileNamePattern pattern, List<File> result) {
        File[] files = root.listFiles();
        for (File file : files) {
            if (pattern.match(file)) {
                result.add(file);
                if (file.isDirectory()) {
                    collectFiles(file, pattern, result);
                }
            }
        }
    }

    public static void collectFiles(File root, List<File> result) {
        File[] files = root.listFiles();
        for (File file : files) {
            result.add(file);
            if (file.isDirectory()) {
                collectFiles(file, result);
            }
        }
    }

    /**
     * Create a file handler (this doesn't create a real file) given a file URI. This method can be used to create files
     * from invalid URL strings (e.g. containing spaces ..)
     *
     * @return a file object
     */
    public static File urlToFile(String url) throws MalformedURLException {
        return urlToFile(new URL(url));
    }

    public static File urlToFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            return new File(url.getPath());
        }
    }

}
