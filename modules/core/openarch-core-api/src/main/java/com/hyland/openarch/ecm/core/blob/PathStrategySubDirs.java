/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import java.nio.file.Path;

/**
 * Stores a file in a nested subdirectory based on the initial characters of the key, in groups of 2. The key is
 * restricted the a safe subset of characters to use as filenames.
 * <p>
 * For instance for a depth of 3 and a key of 1234567890abcd the path will be 12/34/56/1234567890abcd.
 *
 * @since 11.1
 */
public class PathStrategySubDirs extends PathStrategy {

    /** The directory holding keys too short to be split into subdirectories. */
    protected static final String SHORT_KEYS_DIR = "000";

    protected final int depth;

    public PathStrategySubDirs(Path dir, int depth) {
        super(dir);
        this.depth = depth;
    }

    @Override
    public String getPathPrefixForKeyPrefix(String keyPrefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            if (keyPrefix.length() < 2 * i + 2) {
                // the key prefix ends within this subdirectory name
                return sb.append(keyPrefix.substring(2 * i)).toString();
            }
            sb.append(keyPrefix, 2 * i, 2 * i + 2).append('/');
        }
        // the full key follows the subdirectories
        return sb.append(keyPrefix).toString();
    }

    @Override
    public String getShortKeysPathPrefix() {
        return depth == 0 ? null : SHORT_KEYS_DIR + '/';
    }

    @Override
    public Path getPathForKey(String key) {
        key = safePath(key);
        if (key.length() < 2 * depth) {
            // store short keys under a special directory
            return dir.resolve(SHORT_KEYS_DIR).resolve(key);
        }
        Path current = dir;
        for (int i = 0; i < depth; i++) {
            current = current.resolve(key.substring(2 * i, 2 * i + 2));
        }
        return current.resolve(key);
    }

}
