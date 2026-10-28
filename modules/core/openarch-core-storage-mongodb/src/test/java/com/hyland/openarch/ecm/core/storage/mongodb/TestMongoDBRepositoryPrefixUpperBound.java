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
package com.hyland.openarch.ecm.core.storage.mongodb;

import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.prefixUpperBound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class TestMongoDBRepositoryPrefixUpperBound {

    @ParameterizedTest
    @CsvSource({ //
            "0, 1", //
            "9, :", //
            "a, b", //
            "f, g", //
            "00, 01", //
            "a9, a:", //
            "af, ag", //
    })
    public void testPrefixUpperBound(String prefix, String expected) {
        assertEquals(expected, prefixUpperBound(prefix));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "9", "f", "00", "9f", "ff" })
    public void testPrefixUpperBoundRange(String prefix) {
        String upper = prefixUpperBound(prefix);
        // keys with the prefix are in [prefix, upper)
        for (String suffix : new String[] { "", "0", "f", "ffffffff", "\uffff" }) {
            String key = prefix + suffix;
            assertTrue(key.compareTo(prefix) >= 0, key);
            assertTrue(key.compareTo(upper) < 0, key);
        }
    }

}
