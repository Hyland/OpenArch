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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class FileMatcher {

    private static final Pattern VAR = Pattern.compile("(.*)?\\{(.+):(.+)\\}(.*)?"); // NOSONAR parts of this pattern need to match empty strings

    public static FileMatcher getMatcher(String path) {
        Matcher m = VAR.matcher(path);
        if (m.matches()) {
            String key = m.group(2).trim();
            return new FileMatcher(buildPattern(m), key);
        }
        return new FileMatcher(null, path);
    }

    private static Pattern buildPattern(Matcher m) {
        String prefix = m.group(1);
        String value = m.group(3).trim();
        String suffix = m.group(4);
        Pattern pattern;
        if (prefix.length() == 0) {
            if (suffix.length() == 0) {
                pattern = Pattern.compile("(" + value + ")");
            } else {
                pattern = Pattern.compile("(" + value + ")" + suffix);
            }
        } else if (suffix.length() == 0) {
            pattern = Pattern.compile(prefix + "(" + value + ")");
        } else {
            pattern = Pattern.compile(prefix + "(" + value + ")" + suffix);
        }
        return pattern;
    }

    public static FileMatcher getMatcher(File file) {
        return getMatcher(file.getName());
    }

    private final Pattern pattern;

    private final String key;

    private String value;

    public FileMatcher(Pattern pattern, String key) {
        this.pattern = pattern;
        this.key = key;
    }

    public String getKey() {
        return pattern != null ? key : null;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getValue() {
        return value;
    }

    public boolean match(String name) {
        value = null;
        if (pattern != null) {
            Matcher m = pattern.matcher(name);
            if (m.matches()) {
                value = m.group(1);
                return true;
            }
            return false;
        }
        return name.equals(key);
    }


    @Override
    public String toString() {
        return "FileMatcher{" +
                "pattern=" + pattern +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
