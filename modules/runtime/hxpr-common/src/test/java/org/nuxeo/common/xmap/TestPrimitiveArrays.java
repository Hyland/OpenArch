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

package org.nuxeo.common.xmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.common.collections.PrimitiveArrays;

public class TestPrimitiveArrays {

    // -------------------------------------------------------------------------
    // toPrimitiveArray
    // -------------------------------------------------------------------------

    @Test
    public void testToPrimitiveArrayInteger() {
        Collection<Object> col = List.of(0, 1);
        int[] result = (int[]) PrimitiveArrays.toPrimitiveArray(col, Integer.TYPE);
        assertEquals(2, result.length);
        assertEquals(0, result[0]);
        assertEquals(1, result[1]);
    }

    @Test
    public void testToPrimitiveArrayLong() {
        Collection<Object> col = List.of(0L, 1L);
        long[] result = (long[]) PrimitiveArrays.toPrimitiveArray(col, Long.TYPE);
        assertEquals(2, result.length);
        assertEquals(0L, result[0]);
        assertEquals(1L, result[1]);
    }

    @Test
    public void testToPrimitiveArrayDouble() {
        Collection<Object> col = List.of(0.0, 1.0);
        double[] result = (double[]) PrimitiveArrays.toPrimitiveArray(col, Double.TYPE);
        assertEquals(2, result.length);
        assertEquals(0.0, result[0], 1e-8);
        assertEquals(1.0, result[1], 1e-8);
    }

    @Test
    public void testToPrimitiveArrayFloat() {
        Collection<Object> col = List.of(0.0f, 1.0f);
        float[] result = (float[]) PrimitiveArrays.toPrimitiveArray(col, Float.TYPE);
        assertEquals(2, result.length);
        assertEquals(0.0f, result[0], 1e-8f);
        assertEquals(1.0f, result[1], 1e-8f);
    }

    @Test
    public void testToPrimitiveArrayBoolean() {
        Collection<Object> col = List.of(true, false);
        boolean[] result = (boolean[]) PrimitiveArrays.toPrimitiveArray(col, Boolean.TYPE);
        assertEquals(2, result.length);
        assertEquals(true, result[0]);
        assertEquals(false, result[1]);
    }

    @Test
    public void testToPrimitiveArrayByte() {
        Collection<Object> col = List.of((byte) 1, (byte) 2);
        byte[] result = (byte[]) PrimitiveArrays.toPrimitiveArray(col, Byte.TYPE);
        assertEquals(2, result.length);
        assertEquals((byte) 1, result[0]);
        assertEquals((byte) 2, result[1]);
    }

    @Test
    public void testToPrimitiveArrayChar() {
        Collection<Object> col = List.of('a', 'b');
        char[] result = (char[]) PrimitiveArrays.toPrimitiveArray(col, Character.TYPE);
        assertEquals(2, result.length);
        assertEquals('a', result[0]);
        assertEquals('b', result[1]);
    }

    @Test
    public void testToPrimitiveArrayShort() {
        Collection<Object> col = List.of((short) 10, (short) 20);
        short[] result = (short[]) PrimitiveArrays.toPrimitiveArray(col, Short.TYPE);
        assertEquals(2, result.length);
        assertEquals((short) 10, result[0]);
        assertEquals((short) 20, result[1]);
    }

    @Test
    public void testToPrimitiveArrayUnknownTypeReturnsNull() {
        Collection<Object> col = List.of("a");
        Object result = PrimitiveArrays.toPrimitiveArray(col, String.class);
        assertEquals(null, result);
    }

    // -------------------------------------------------------------------------
    // toObjectArray
    // -------------------------------------------------------------------------

    @Test
    public void testToObjectArrayInteger() {
        Integer[] result = (Integer[]) PrimitiveArrays.toObjectArray(new int[] { 0, 1 });
        assertEquals(2, result.length);
        assertEquals(Integer.valueOf(0), result[0]);
        assertEquals(Integer.valueOf(1), result[1]);
    }

    @Test
    public void testToObjectArrayLong() {
        Long[] result = (Long[]) PrimitiveArrays.toObjectArray(new long[] { 0L, 1L });
        assertEquals(2, result.length);
        assertEquals(Long.valueOf(0), result[0]);
        assertEquals(Long.valueOf(1), result[1]);
    }

    @Test
    public void testToObjectArrayDouble() {
        Double[] result = (Double[]) PrimitiveArrays.toObjectArray(new double[] { 0.0, 1.0 });
        assertEquals(2, result.length);
        assertEquals(0.0, result[0], 1e-8);
        assertEquals(1.0, result[1], 1e-8);
    }

    @Test
    public void testToObjectArrayFloat() {
        Float[] result = (Float[]) PrimitiveArrays.toObjectArray(new float[] { 0.0f, 1.0f });
        assertEquals(2, result.length);
        assertEquals(0.0f, result[0], 1e-8f);
        assertEquals(1.0f, result[1], 1e-8f);
    }

    @Test
    public void testToObjectArrayChar() {
        Character[] result = (Character[]) PrimitiveArrays.toObjectArray(new char[] { 'a', 'b' });
        assertEquals(2, result.length);
        assertEquals(Character.valueOf('a'), result[0]);
        assertEquals(Character.valueOf('b'), result[1]);
    }

    @Test
    public void testToObjectArrayByte() {
        Byte[] result = (Byte[]) PrimitiveArrays.toObjectArray(new byte[] { 1, 2 });
        assertEquals(2, result.length);
        assertEquals(Byte.valueOf((byte) 1), result[0]);
        assertEquals(Byte.valueOf((byte) 2), result[1]);
    }

    @Test
    public void testToObjectArrayShort() {
        Short[] result = (Short[]) PrimitiveArrays.toObjectArray(new short[] { 10, 20 });
        assertEquals(2, result.length);
        assertEquals(Short.valueOf((short) 10), result[0]);
        assertEquals(Short.valueOf((short) 20), result[1]);
    }

    @Test
    public void testToObjectArrayBooleanPrimitiveReturnsNull() {
        // boolean[] has no ArrayUtils.toObject path — method returns null
        Object result = PrimitiveArrays.toObjectArray(new boolean[] { true, false });
        assertEquals(null, result);
    }

    @Test
    public void testToObjectArrayObjectPassthrough() {
        String[] input = { "x", "y" };
        Object[] result = PrimitiveArrays.toObjectArray(input);
        assertEquals(2, result.length);
        assertEquals("x", result[0]);
        assertEquals("y", result[1]);
    }

    @Test
    public void testToObjectArrayNotAnArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> PrimitiveArrays.toObjectArray("not an array"));
    }

    // -------------------------------------------------------------------------
    // toList
    // -------------------------------------------------------------------------

    @Test
    public void testToListInteger() {
        assertIterableEquals(List.of(0, 1), PrimitiveArrays.toList(new int[] { 0, 1 }));
    }

    @Test
    public void testToListLong() {
        assertIterableEquals(List.of(0L, 1L), PrimitiveArrays.toList(new long[] { 0L, 1L }));
    }

    @Test
    public void testToListDouble() {
        assertIterableEquals(List.of(0.0, 1.0), PrimitiveArrays.toList(new double[] { 0.0, 1.0 }));
    }

    @Test
    public void testToListFloat() {
        assertIterableEquals(List.of(0.0f, 1.0f), PrimitiveArrays.toList(new float[] { 0.0f, 1.0f }));
    }

    @Test
    public void testToListChar() {
        assertIterableEquals(List.of('a', 'b'), PrimitiveArrays.toList(new char[] { 'a', 'b' }));
    }

    @Test
    public void testToListByte() {
        assertIterableEquals(List.of((byte) 1, (byte) 2), PrimitiveArrays.toList(new byte[] { 1, 2 }));
    }

    @Test
    public void testToListShort() {
        assertIterableEquals(List.of((short) 10, (short) 20), PrimitiveArrays.toList(new short[] { 10, 20 }));
    }

    @Test
    public void testToListBooleanPrimitiveReturnsNull() {
        // boolean[] has no toList path — method returns null
        Object result = PrimitiveArrays.toList(new boolean[] { true });
        assertEquals(null, result);
    }

    @Test
    public void testToListObjectArray() {
        assertIterableEquals(List.of("x", "y"), PrimitiveArrays.toList(new String[] { "x", "y" }));
    }

}
