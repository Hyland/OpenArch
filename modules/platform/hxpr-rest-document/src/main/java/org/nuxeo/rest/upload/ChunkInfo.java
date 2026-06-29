/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.upload;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Chunk information retrieved from transient store parameters.
 */
public class ChunkInfo {

    public static final String CHUNKED_PARAM_NAME = "chunked";

    public static final String CHUNK_COUNT_PARAM_NAME = "chunkCount";

    public static final String FILE_NAME_PARAM_NAME = "fileName";

    public static final String MIME_TYPE_PARAM_NAME = "mimeType";

    public static final String ENCODING_PARAM_NAME = "encoding";

    public static final String PARTIAL_FILESIZE = "partialFileSize";

    protected Integer chunkCount;

    protected String fileName;

    protected String mimeType;

    protected String encoding;

    protected Map<Integer, String> chunks;

    protected long partialFileSize;

    public ChunkInfo(Map<String, Object> parameters) {
        Long chunkCountLong = (Long) parameters.get(CHUNK_COUNT_PARAM_NAME);
        chunkCount = chunkCountLong == null ? null : chunkCountLong.intValue();
        fileName = (String) parameters.get(FILE_NAME_PARAM_NAME);
        mimeType = (String) parameters.get(MIME_TYPE_PARAM_NAME);
        encoding = (String) parameters.get(ENCODING_PARAM_NAME);
        chunks = new HashMap<>();
        parameters.forEach((key, value) -> {
            if (NumberUtils.isDigits(key)) {
                chunks.put(Integer.valueOf(key), (String) value);
            }
        });
        partialFileSize = (Long) parameters.getOrDefault(PARTIAL_FILESIZE, 0L);
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getEncoding() {
        return encoding;
    }

    public long getPartialFileSize() {
        return partialFileSize;
    }

    @JsonIgnore
    public Map<Integer, String> getChunks() {
        return Collections.unmodifiableMap(chunks);
    }

}
