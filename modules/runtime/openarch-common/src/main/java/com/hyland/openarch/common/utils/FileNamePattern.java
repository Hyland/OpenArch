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

package com.hyland.openarch.common.utils;

import java.io.File;

import org.apache.commons.io.FilenameUtils;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class FileNamePattern {

    private final String pattern;

    public FileNamePattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean match(String text) {
        return FilenameUtils.wildcardMatch(text, pattern);
    }

    public boolean match(File file) {
        return match(file.getName());
    }

    public boolean match(java.nio.file.Path path) {
        return match(path.getFileName().toString());
    }

}
