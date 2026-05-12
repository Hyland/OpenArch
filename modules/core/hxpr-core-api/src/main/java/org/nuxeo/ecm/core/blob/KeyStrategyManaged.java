/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.blob;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.utils.BlobUtils;

/**
 * Represents trusted managed blob key computation
 *
 * @since 11.2
 */
public class KeyStrategyManaged implements KeyStrategy {

    @Override
    public boolean useDeDuplication() {
        return false;
    }

    @Override
    public String getDigestFromKey(String key) {
        return null;
    }

    @Override
    public BlobWriteContext getBlobWriteContext(BlobContext blobContext) {
        // use the unprefixed key if it's a managed blob, or a random string otherwise
        String key = blobContext.blob instanceof ManagedBlob managedBlob
                ? StringUtils.removeStart(managedBlob.getKey(), managedBlob.getProviderId() + ':')
                             // strip version id if part of file key
                             .split(String.valueOf(VER_SEP))[0]
                : BlobUtils.randomString();
        return new BlobWriteContext(blobContext, null, () -> key, this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof KeyStrategyManaged;
    }

    @Override
    public int hashCode() {
        return 0;
    }

}
