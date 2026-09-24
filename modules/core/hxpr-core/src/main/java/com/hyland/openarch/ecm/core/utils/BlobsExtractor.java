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
package com.hyland.openarch.ecm.core.utils;

import static java.util.stream.Collectors.toSet;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.DocumentModel;

/**
 * Extractor for all the blobs of a document.
 */
public class BlobsExtractor {

    private static final Pattern SLASH_INDEX = Pattern.compile("/[0-9]+");

    private static final String SLASH_INDEX_REPLACEMENT = "/*";

    protected final Map<String, Set<String>> docBlobPaths;

    public BlobsExtractor() {
        docBlobPaths = null; // all blobs accepted
    }

    public BlobsExtractor(Map<String, Set<String>> docBlobPaths) {
        if (docBlobPaths == null) {
            throw new NullPointerException();
        }
        this.docBlobPaths = new HashMap<>();
        docBlobPaths.forEach((type, fields) -> this.docBlobPaths.put(type, normalizePaths(fields)));
    }

    protected boolean isInterestingXPath(String xpath, String type) {
        if (docBlobPaths == null) {
            return true;
        }
        xpath = SLASH_INDEX.matcher(xpath).replaceAll(SLASH_INDEX_REPLACEMENT);
        var fieldsForType = docBlobPaths.get(type);
        return fieldsForType != null && fieldsForType.contains(xpath);
    }

    /** Removes the "/key" suffix used by FulltextConfiguration. */
    private Set<String> normalizePaths(Set<String> paths) {
        if (paths == null) {
            return null;
        }
        return paths.stream().map(this::removeKeySuffix).collect(toSet());
    }

    private String removeKeySuffix(String path) {
        return StringUtils.removeEnd(path, "/" + KEY_BLOB_KEY);
    }

    /**
     * Gets the blobs of the document.
     *
     * @param doc the document
     * @return the list of blobs
     */
    public List<Blob> getBlobs(DocumentModel doc) {
        List<Blob> blobs = new ArrayList<>();
        doc.visitBlobs(blobAccessor -> {
            if (isInterestingXPath(blobAccessor.getXPath(), doc.getType())) {
                Blob blob = blobAccessor.getBlob();
                if (blob != null) {
                    blobs.add(blob);
                }
            }
        });
        return blobs;
    }

}
