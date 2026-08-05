/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.transientstore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;

/**
 * Class representing an entry stored in the {@link TransientStore}.
 *
 * @since 7.10
 */
public class StorageEntry {

    protected Logger log = LogManager.getLogger(StorageEntry.class);

    protected ConcurrentMap<String, Object> params;

    protected Map<String, String> blobInfo;

    protected long size;

    protected boolean completed;

    public StorageEntry() {
        this(0, false);
    }

    public StorageEntry(long size, boolean completed) {
        params = null;
        blobInfo = null;
        this.size = size;
        this.completed = completed;
    }

    public Map<String, Object> getParams() {
        if (params == null) {
            params = new ConcurrentHashMap<>();
        }
        return params;
    }

    public void putParams(Map<String, Object> params) {
        if (this.params == null) {
            this.params = new ConcurrentHashMap<>();
        }
        this.params.putAll(params);
    }

    public Object getParam(String param) {
        if (params == null) {
            return null;
        }
        return params.get(param);
    }

    public void putParam(String param, Object value) {
        if (params == null) {
            params = new ConcurrentHashMap<>();
        }
        params.put(param, value);
    }

    public Map<String, String> getBlobInfo() {
        return blobInfo;
    }

    public void setBlobInfo(Map<String, String> blobInfo) {
        this.blobInfo = blobInfo;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

}
