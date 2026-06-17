/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.opensearch.query;

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
