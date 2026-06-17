/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */

package org.nuxeo.opensearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldValue;

class FieldValueConverterTest {

    // --- toObject ---

    @Test
    void toObject_null() {
        assertNull(FieldValueConverter.toObject(FieldValue.NULL));
    }

    @Test
    void toObject_string() {
        assertEquals("hello", FieldValueConverter.toObject(FieldValue.of("hello")));
    }

    @Test
    void toObject_intTolong() {
        assertEquals(15L, FieldValueConverter.toObject(FieldValue.of(15)));
    }

    @Test
    void toObject_long() {
        assertEquals(42L, FieldValueConverter.toObject(FieldValue.of(42L)));
    }

    @Test
    void toObject_double() {
        assertEquals(3.14, FieldValueConverter.toObject(FieldValue.of(3.14)));
    }

    @Test
    void toObject_booleanTrue() {
        assertEquals(true, FieldValueConverter.toObject(FieldValue.of(true)));
    }

    @Test
    void toObject_booleanFalse() {
        assertEquals(false, FieldValueConverter.toObject(FieldValue.of(false)));
    }

    // --- toFieldValue ---

    @Test
    void toFieldValue_null() {
        FieldValue fv = FieldValueConverter.toFieldValue(null);
        assertTrue(fv.isNull());
    }

    @Test
    void toFieldValue_string() {
        FieldValue fv = FieldValueConverter.toFieldValue("world");
        assertTrue(fv.isString());
        assertEquals("world", fv.stringValue());
    }

    @Test
    void toFieldValue_long() {
        FieldValue fv = FieldValueConverter.toFieldValue(99L);
        assertTrue(fv.isLong());
        assertEquals(99L, fv.longValue());
    }

    @Test
    void toFieldValue_integer_widensToLong() {
        FieldValue fv = FieldValueConverter.toFieldValue(7);
        assertTrue(fv.isLong());
        assertEquals(7L, fv.longValue());
    }

    @Test
    void toFieldValue_double() {
        FieldValue fv = FieldValueConverter.toFieldValue(2.71);
        assertTrue(fv.isDouble());
        assertEquals(2.71, fv.doubleValue());
    }

    @Test
    void toFieldValue_booleanTrue() {
        FieldValue fv = FieldValueConverter.toFieldValue(true);
        assertTrue(fv.isBoolean());
        assertTrue(fv.booleanValue());
    }

    @Test
    void toFieldValue_booleanFalse() {
        FieldValue fv = FieldValueConverter.toFieldValue(false);
        assertTrue(fv.isBoolean());
        assertFalse(fv.booleanValue());
    }

    @Test
    void toFieldValue_unknownType_fallsBackToString() {
        Object obj = new Object() {
            @Override
            public String toString() {
                return "custom";
            }
        };
        FieldValue fv = FieldValueConverter.toFieldValue(obj);
        assertTrue(fv.isString());
        assertEquals("custom", fv.stringValue());
    }

    // --- roundtrip ---

    @Test
    void roundtrip_string() {
        assertEquals("text", FieldValueConverter.toObject(FieldValueConverter.toFieldValue("text")));
    }

    @Test
    void roundtrip_long() {
        assertEquals(123L, FieldValueConverter.toObject(FieldValueConverter.toFieldValue(123L)));
    }

    @Test
    void roundtrip_double() {
        assertEquals(1.5, FieldValueConverter.toObject(FieldValueConverter.toFieldValue(1.5)));
    }

    @Test
    void roundtrip_boolean() {
        assertEquals(true, FieldValueConverter.toObject(FieldValueConverter.toFieldValue(true)));
    }

    @Test
    void roundtrip_null() {
        assertNull(FieldValueConverter.toObject(FieldValueConverter.toFieldValue(null)));
    }
}
