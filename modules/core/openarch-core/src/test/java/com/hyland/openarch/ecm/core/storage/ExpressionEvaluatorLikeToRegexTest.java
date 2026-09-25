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
package com.hyland.openarch.ecm.core.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@link ExpressionEvaluator#likeToRegex}, asserting behavior of the produced regex so the escaping
 * branches (wildcards, escaped wildcards, and regex-special characters) are all exercised.
 */
class ExpressionEvaluatorLikeToRegexTest {

    private static boolean likeMatches(String likePattern, String input) {
        return Pattern.matches(ExpressionEvaluator.likeToRegex(likePattern), input);
    }

    @Test
    void percentIsMultiCharWildcard() {
        assertTrue(likeMatches("Par%", "Paris"));
        assertTrue(likeMatches("Par%", "Par"));
        assertFalse(likeMatches("Par%", "London"));
    }

    @Test
    void underscoreIsSingleCharWildcard() {
        assertTrue(likeMatches("Par_s", "Paris"));
        assertFalse(likeMatches("Par_s", "Pars"));
        assertFalse(likeMatches("Par_s", "Paaris"));
    }

    @Test
    void regexSpecialCharactersAreEscaped() {
        assertTrue(likeMatches("a.b", "a.b"));
        assertFalse(likeMatches("a.b", "axb"));
        assertTrue(likeMatches("a+b", "a+b"));
        assertTrue(likeMatches("(x)", "(x)"));
    }

    @Test
    void backslashEscapesWildcards() {
        // \% and \_ become literal % and _
        assertTrue(likeMatches("50\\%", "50%"));
        assertFalse(likeMatches("50\\%", "50off"));
        assertTrue(likeMatches("a\\_b", "a_b"));
        assertFalse(likeMatches("a\\_b", "aXb"));
    }

    @Test
    void trailingEscapeIsIgnored() {
        assertTrue(likeMatches("abc\\", "abc"));
    }
}
