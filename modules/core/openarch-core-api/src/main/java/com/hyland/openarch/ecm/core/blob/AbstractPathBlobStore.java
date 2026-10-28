/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

/**
 * Base class for blob stores mapping keys to storage paths through a {@link PathStrategy}.
 */
public abstract class AbstractPathBlobStore extends AbstractBlobStore {

    protected final PathStrategy pathStrategy;

    protected AbstractPathBlobStore(String blobProviderId, String name, KeyStrategy keyStrategy,
            PathStrategy pathStrategy) {
        super(blobProviderId, name, keyStrategy);
        this.pathStrategy = pathStrategy;
    }

    protected abstract class AbstractPathGarbageCollector extends AbstractGarbageCollector {

        /**
         * Decodes a storage path and records it as a candidate if its key belongs to this run and key strategy.
         */
        protected void collectCandidate(String path, long size) {
            String key = pathStrategy.getKeyForPath(path);
            if (key == null || !markingState.slot().contains(key)) {
                return;
            }
            // Ignore files that cannot be digests in a deduplicating store, for safety.
            if (keyStrategy instanceof KeyStrategyDigest digestStrategy && !digestStrategy.isValidDigest(key)) {
                return;
            }
            status.add(1, size);
            toDelete.add(key);
        }
    }
}
