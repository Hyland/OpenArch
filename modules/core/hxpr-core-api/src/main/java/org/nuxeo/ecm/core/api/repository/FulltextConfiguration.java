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
package org.nuxeo.ecm.core.api.repository;

import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_ROOT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Info about the fulltext configuration.
 *
 * @since 10.3 in this package
 */
public class FulltextConfiguration {

    /** All Blob properties per document type to index */
    private final Map<String, Set<String>> propPathBlobProperties = new HashMap<>();

    /** All String properties per document type to index */
    private final Map<String, Set<String>> propPathStringProperties = new HashMap<>();

    private final Set<String> includedTypes = new HashSet<>();

    private final Set<String> excludedTypes = new HashSet<>();

    private boolean fulltextStoredInBlob;

    private int fulltextFieldSizeLimit;

    public Map<String, Set<String>> getPropPathBlobProperties() {
        return propPathBlobProperties;
    }

    public Map<String, Set<String>> getPropPathStringProperties() {
        return propPathStringProperties;
    }

    public Set<String> getIncludedTypes() {
        return includedTypes;
    }

    public Set<String> getExcludedTypes() {
        return excludedTypes;
    }

    public boolean isFulltextIndexable(String typeName) {
        if (SYS_ROOT.equals(typeName)) {
            return false;
        }
        return includedTypes.contains(typeName);
    }

    /** @since 11.1 */
    public boolean isFulltextStoredInBlob() {
        return fulltextStoredInBlob;
    }

    public void setFulltextStoredInBlob(boolean fulltextStoredInBlob) {
        this.fulltextStoredInBlob = fulltextStoredInBlob;
    }

    public int getFulltextFieldSizeLimit() {
        return fulltextFieldSizeLimit;
    }

    public void setFulltextFieldSizeLimit(int fulltextFieldSizeLimit) {
        this.fulltextFieldSizeLimit = fulltextFieldSizeLimit;
    }
}
