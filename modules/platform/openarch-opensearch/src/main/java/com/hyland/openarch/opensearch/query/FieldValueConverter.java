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
package com.hyland.openarch.opensearch.query;

import org.opensearch.client.opensearch._types.FieldValue;

/**
 * Converts between OpenSearch {@link FieldValue} (JSONP) and plain Java objects (Jackson-serializable).
 * <p>
 * {@link FieldValue} uses the JSONP framework and cannot be serialized directly by Jackson. This utility provides the
 * two-way conversion needed at the boundary between the OpenSearch response (JSONP) and the search_after token
 * (Jackson/Base64).
 */
public class FieldValueConverter {

    private FieldValueConverter() {
        // utility class
    }

    /**
     * Converts a {@link FieldValue} to its plain Java equivalent (String, Long, Double, Boolean, or null).
     */
    public static Object toObject(FieldValue fv) {
        return fv._get();
    }

    /**
     * Converts a plain Java object to a {@link FieldValue}.
     */
    public static FieldValue toFieldValue(Object obj) {
        return switch (obj) {
            case null -> FieldValue.NULL;
            case String s -> FieldValue.of(s);
            case Long l -> FieldValue.of(l);
            case Integer i -> FieldValue.of(i);
            case Double d -> FieldValue.of(d);
            case Boolean b -> FieldValue.of(b);
            default -> FieldValue.of(obj.toString());
        };
    }
}
