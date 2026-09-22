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
package com.hyland.openarch.common.utils.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

public class TestI18NUtils {

    private static final String BUNDLE = "messages";

    @Test
    public void testKeyFoundNoParams() {
        String result = I18NUtils.getMessageString(BUNDLE, "greeting", null, Locale.ENGLISH);
        assertEquals("Hello, World!", result);
    }

    @Test
    public void testKeyFoundWithSingleParam() {
        String result = I18NUtils.getMessageString(BUNDLE, "greeting.with.name", List.of("Alice"), Locale.ENGLISH);
        assertEquals("Hello, Alice!", result);
    }

    @Test
    public void testKeyFoundWithMultipleParams() {
        String result = I18NUtils.getMessageString(BUNDLE, "greeting.multi.params", List.of("Bob", 30), Locale.ENGLISH);
        assertEquals("Bob is 30 years old.", result);
    }

    @Test
    public void testMissingKeyReturnsKeyItself() {
        String result = I18NUtils.getMessageString(BUNDLE, "unknown.key", null, Locale.ENGLISH);
        assertEquals("unknown.key", result);
    }
}
