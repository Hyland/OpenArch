/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.opensearch.api;

import java.util.Map;

public class OpenSearchIndexStoreStats {

    /**
     * Sum of the sizes of all indexes, in bytes.
     */
    long totalSize;

    /**
     * Sizes for each of the indexes, in bytes.
     */
    Map<String, Long> indexSizes;

    public OpenSearchIndexStoreStats(long totalSize, Map<String, Long> indexSizes) {
        this.totalSize = totalSize;
        this.indexSizes = indexSizes;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public Map<String, Long> getIndexSizes() {
        return indexSizes;
    }

}
