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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class WildcardUtilsTest {

    /**
     * Helper method to test if a pattern matches a given string.
     */
    private void assertMatches(Pattern pattern, String input) {
        assertTrue(pattern.matcher(input).matches(),
            "Pattern should match '" + input + "' but did not. Pattern: " + pattern.pattern());
    }

    /**
     * Helper method to test if a pattern does not match a given string.
     */
    private void assertNotMatches(Pattern pattern, String input) {
        assertFalse(pattern.matcher(input).matches(),
            "Pattern should not match '" + input + "' but did. Pattern: " + pattern.pattern());
    }

    @Test
    public void testNullInput() {
        assertNull(WildcardUtils.escapeForOpenSearchWildcard(null));
    }

    @Test
    public void testEmptyString() {
        assertEquals("", WildcardUtils.escapeForOpenSearchWildcard(""));
    }

    @Test
    public void testNoSpecialCharacters() {
        String input = "regular text";
        String expected = "regular text";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testWildcardAsterisk() {
        // Asterisk should remain as wildcard
        String input = "test*query";
        String expected = "test*query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testWildcardQuestionMark() {
        // Question mark should remain as wildcard
        String input = "test?query";
        String expected = "test?query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testEscapedAsterisk() {
        // \* should become literal asterisk
        String input = "test\\*query";
        String expected = "test\\*query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testEscapedQuestionMark() {
        // \? should become literal question mark
        String input = "test\\?query";
        String expected = "test\\?query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testEscapedBackslash() {
        // \\ should become literal backslash
        String input = "test\\\\query";
        String expected = "test\\\\query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testBackslashBeforeNonSpecialChar() {
        // \x (x not in {* ? \}) should be treated as literal backslash + 'x'
        String input = "test\\aquery";
        String expected = "test\\\\aquery";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testTrailingBackslash() {
        // Trailing backslash should be treated as literal backslash
        String input = "test\\";
        String expected = "test\\\\";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testMixedWildcardsAndEscapes() {
        // Mix of wildcards and escaped characters
        String input = "test*file\\?doc?.txt";
        String expected = "test*file\\?doc?.txt";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testMultipleBackslashesBeforeSpecial() {
        // \\* should be literal backslash followed by wildcard asterisk
        String input = "test\\\\*query";
        String expected = "test\\\\*query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testBackslashBeforeDigit() {
        // \5 should be treated as literal backslash + '5'
        String input = "test\\5query";
        String expected = "test\\\\5query";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testBackslashBeforeLetter() {
        // \n should be treated as literal backslash + 'n'
        String input = "test\\nquery";
        String expected = "test\\\\nquery";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testConsecutiveWildcards() {
        String input = "test**query??end";
        String expected = "test**query??end";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testComplexPattern() {
        // Complex pattern: doc\*.txt? means "doc" + literal '*' + ".txt" + single char wildcard
        String input = "doc\\*.txt?";
        String expected = "doc\\*.txt?";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testPathWithWildcards() {
        // Path-like pattern with wildcards and escapes
        String input = "C:\\\\folder\\\\file*.txt";
        String expected = "C:\\\\folder\\\\file*.txt";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testOnlyWildcards() {
        String input = "*?*";
        String expected = "*?*";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testOnlyEscapedWildcards() {
        String input = "\\*\\?\\*";
        String expected = "\\*\\?\\*";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testBackslashSequences() {
        // \\\\* should be escaped backslash, escaped backslash, then wildcard asterisk
        String input = "\\\\\\\\*";
        String expected = "\\\\\\\\*";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testRealWorldFilename() {
        // Real-world example: searching for files starting with "report"
        String input = "report*.pdf";
        String expected = "report*.pdf";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testRealWorldLiteralAsterisk() {
        // Real-world: searching for files with literal asterisk in name
        String input = "report\\*.pdf";
        String expected = "report\\*.pdf";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testEdgeCaseMultipleEscapes() {
        // Edge case: \\\* should be escaped backslash followed by literal asterisk
        String input = "\\\\\\*test";
        String expected = "\\\\\\*test";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    @Test
    public void testSingleCharacters() {
        assertEquals("a", WildcardUtils.escapeForOpenSearchWildcard("a"));
        assertEquals("?", WildcardUtils.escapeForOpenSearchWildcard("?"));
        assertEquals("*", WildcardUtils.escapeForOpenSearchWildcard("*"));
        assertEquals("\\\\", WildcardUtils.escapeForOpenSearchWildcard("\\"));
    }

    @Test
    public void testEscapedCharactersOnly() {
        // All special chars escaped
        String input = "\\?\\*\\\\";
        String expected = "\\?\\*\\\\";
        assertEquals(expected, WildcardUtils.escapeForOpenSearchWildcard(input));
    }

    // ========================================
    // Tests for toRegex method
    // ========================================

    @Test
    public void testToRegex_NullInput() {
        assertNull(WildcardUtils.toRegex(null));
    }

    @Test
    public void testToRegex_EmptyString() {
        Pattern pattern = WildcardUtils.toRegex("");
        assertMatches(pattern, "");
        assertNotMatches(pattern, "a");
    }

    @Test
    public void testToRegex_LiteralString() {
        Pattern pattern = WildcardUtils.toRegex("hello");
        assertMatches(pattern, "hello");
        assertMatches(pattern, "Hello"); // Case insensitive
        assertMatches(pattern, "HELLO"); // Case insensitive
        assertMatches(pattern, "HeLLo"); // Case insensitive
        assertNotMatches(pattern, "hello world");
        assertNotMatches(pattern, "hell");
    }

    @Test
    public void testToRegex_WildcardAsterisk() {
        // '*' should match zero or more characters
        Pattern pattern = WildcardUtils.toRegex("test*");
        assertMatches(pattern, "test");
        assertMatches(pattern, "test123");
        assertMatches(pattern, "testing");
        assertNotMatches(pattern, "tes");
    }

    @Test
    public void testToRegex_WildcardQuestionMark() {
        // '?' should match exactly one character
        Pattern pattern = WildcardUtils.toRegex("test?");
        assertMatches(pattern, "test1");
        assertMatches(pattern, "testa");
        assertMatches(pattern, "test!");
        assertNotMatches(pattern, "test");
        assertNotMatches(pattern, "test12");
    }

    @Test
    public void testToRegex_WildcardInMiddle() {
        Pattern pattern = WildcardUtils.toRegex("test*query");
        assertMatches(pattern, "testquery");
        assertMatches(pattern, "test123query");
        assertMatches(pattern, "test_some_query");
        assertNotMatches(pattern, "testquer");
        assertNotMatches(pattern, "testqueryextra");
    }

    @Test
    public void testToRegex_MultipleWildcards() {
        Pattern pattern = WildcardUtils.toRegex("*test*query*");
        assertMatches(pattern, "testquery");
        assertMatches(pattern, "mytestmyquery");
        assertMatches(pattern, "test query ");
        assertMatches(pattern, "xtestxqueryx");
    }

    @Test
    public void testToRegex_EscapedAsterisk() {
        // '\*' should match literal asterisk
        Pattern pattern = WildcardUtils.toRegex("test\\*query");
        assertMatches(pattern, "test*query");
        assertNotMatches(pattern, "testquery");
        assertNotMatches(pattern, "test123query");
    }

    @Test
    public void testToRegex_EscapedQuestionMark() {
        // '\?' should match literal question mark
        Pattern pattern = WildcardUtils.toRegex("test\\?query");
        assertMatches(pattern, "test?query");
        assertNotMatches(pattern, "testquery");
        assertNotMatches(pattern, "test1query");
    }

    @Test
    public void testToRegex_EscapedBackslash() {
        // '\\' should match literal backslash
        Pattern pattern = WildcardUtils.toRegex("test\\\\query");
        assertMatches(pattern, "test\\query");
        assertNotMatches(pattern, "testquery");
    }

    @Test
    public void testToRegex_CombinedWildcardsAndLiterals() {
        // Pattern: doc*.txt matches doc123.txt, document.txt, etc.
        Pattern pattern = WildcardUtils.toRegex("doc*.txt");
        assertMatches(pattern, "doc.txt");
        assertMatches(pattern, "doc123.txt");
        assertMatches(pattern, "document.txt");
        assertNotMatches(pattern, "doc.pdf");
        assertNotMatches(pattern, "mydoc.txt");
    }

    @Test
    public void testToRegex_LiteralAsteriskInPattern() {
        // Pattern: file\\*.txt should match only "file*.txt"
        Pattern pattern = WildcardUtils.toRegex("file\\*.txt");
        assertMatches(pattern, "file*.txt");
        assertNotMatches(pattern, "file123.txt");
        assertNotMatches(pattern, "file.txt");
    }

    @Test
    public void testToRegex_QuestionMarkWildcards() {
        Pattern pattern = WildcardUtils.toRegex("test???");
        assertMatches(pattern, "test123");
        assertMatches(pattern, "testabc");
        assertNotMatches(pattern, "test12");
        assertNotMatches(pattern, "test1234");
    }

    @Test
    public void testToRegex_SpecialRegexCharacters() {
        // Test that regex metacharacters are properly escaped
        Pattern pattern = WildcardUtils.toRegex("test.pdf");
        assertMatches(pattern, "test.pdf");
        assertNotMatches(pattern, "testXpdf"); // '.' should be literal, not regex wildcard
    }

    @Test
    public void testToRegex_ParenthesesAndBrackets() {
        Pattern pattern = WildcardUtils.toRegex("file(1).txt");
        assertMatches(pattern, "file(1).txt");

        Pattern pattern2 = WildcardUtils.toRegex("file[2].txt");
        assertMatches(pattern2, "file[2].txt");
    }

    @Test
    public void testToRegex_PlusAndCaret() {
        Pattern pattern = WildcardUtils.toRegex("file+name^test");
        assertMatches(pattern, "file+name^test");
    }

    @Test
    public void testToRegex_DollarSign() {
        Pattern pattern = WildcardUtils.toRegex("price$100");
        assertMatches(pattern, "price$100");
    }

    @Test
    public void testToRegex_PipeCharacter() {
        Pattern pattern = WildcardUtils.toRegex("a|b");
        assertMatches(pattern, "a|b");
        assertNotMatches(pattern, "a");
        assertNotMatches(pattern, "b");
    }

    @Test
    public void testToRegex_ComplexPattern() {
        // Pattern with wildcards and escaped characters
        Pattern pattern = WildcardUtils.toRegex("doc*\\?*.txt");
        assertMatches(pattern, "document?version.txt");
        assertMatches(pattern, "doc?test.txt");
        assertNotMatches(pattern, "document.txt"); // Missing the literal '?'
    }

    @Test
    public void testToRegex_TrailingBackslash() {
        // Trailing backslash should match literal backslash
        Pattern pattern = WildcardUtils.toRegex("test\\\\");
        assertMatches(pattern, "test\\");
        assertNotMatches(pattern, "test");
    }

    @Test
    public void testToRegex_OnlyWildcards() {
        Pattern pattern = WildcardUtils.toRegex("*");
        assertMatches(pattern, "");
        assertMatches(pattern, "anything");
        assertMatches(pattern, "test123");
    }

    @Test
    public void testToRegex_OnlyQuestionMarks() {
        Pattern pattern = WildcardUtils.toRegex("???");
        assertMatches(pattern, "abc");
        assertMatches(pattern, "123");
        assertNotMatches(pattern, "ab");
        assertNotMatches(pattern, "abcd");
    }

    @Test
    public void testToRegex_MixedEscapesAndWildcards() {
        // Pattern: \\* means literal backslash followed by wildcard asterisk
        Pattern pattern = WildcardUtils.toRegex("\\\\*");
        assertMatches(pattern, "\\");
        assertMatches(pattern, "\\test");
        assertMatches(pattern, "\\anything");
        assertNotMatches(pattern, "test");
    }

    @Test
    public void testToRegex_ConsecutiveBackslashes() {
        // Pattern: \\\\ means two literal backslashes
        Pattern pattern = WildcardUtils.toRegex("\\\\\\\\");
        assertMatches(pattern, "\\\\");
        assertNotMatches(pattern, "\\");
        assertNotMatches(pattern, "\\\\\\");
    }

    @Test
    public void testToRegex_RealWorldFilename() {
        Pattern pattern = WildcardUtils.toRegex("report*.pdf");
        assertMatches(pattern, "report.pdf");
        assertMatches(pattern, "report_2026.pdf");
        assertMatches(pattern, "report_final_version.pdf");
        assertNotMatches(pattern, "report.doc");
        assertNotMatches(pattern, "myreport.pdf");
    }

    @Test
    public void testToRegex_RealWorldPath() {
        Pattern pattern = WildcardUtils.toRegex("C:\\\\folder\\\\*");
        assertMatches(pattern, "C:\\folder\\");
        assertMatches(pattern, "C:\\folder\\file.txt");
        assertMatches(pattern, "C:\\folder\\subfolder\\more");
        assertNotMatches(pattern, "C:\\otherfolder\\file.txt");
    }

    @Test
    public void testToRegex_EmailLike() {
        Pattern pattern = WildcardUtils.toRegex("*@example.com");
        assertMatches(pattern, "@example.com");
        assertMatches(pattern, "user@example.com");
        assertMatches(pattern, "john.doe@example.com");
        assertNotMatches(pattern, "user@example.org");
    }

    @Test
    public void testToRegex_WildcardBetweenLiterals() {
        Pattern pattern = WildcardUtils.toRegex("start?end");
        assertMatches(pattern, "start1end");
        assertMatches(pattern, "start end");
        assertMatches(pattern, "start_end");
        assertNotMatches(pattern, "startend");
        assertNotMatches(pattern, "start12end");
    }

    @Test
    public void testToRegex_CurlyBraces() {
        Pattern pattern = WildcardUtils.toRegex("file{1}.txt");
        assertMatches(pattern, "file{1}.txt");
    }

    @Test
    public void testToRegex_AllRegexMetachars() {
        // Test all regex metacharacters are properly escaped
        Pattern pattern = WildcardUtils.toRegex("a.b^c$d|e(f)g[h]i{j}k*l+m?n\\\\o");
        assertMatches(pattern, "a.b^c$d|e(f)g[h]i{j}k*l+m?n\\o");
    }

    // ========================================
    // Tests for case insensitivity
    // ========================================

    @Test
    public void testToRegex_CaseInsensitive_Simple() {
        Pattern pattern = WildcardUtils.toRegex("test");
        assertMatches(pattern, "test");
        assertMatches(pattern, "Test");
        assertMatches(pattern, "TEST");
        assertMatches(pattern, "TeSt");
    }

    @Test
    public void testToRegex_CaseInsensitive_WithWildcards() {
        Pattern pattern = WildcardUtils.toRegex("test*query");
        assertMatches(pattern, "testquery");
        assertMatches(pattern, "TESTQUERY");
        assertMatches(pattern, "TestQuery");
        assertMatches(pattern, "test123QUERY");
        assertMatches(pattern, "TEST_some_Query");
    }

    @Test
    public void testToRegex_CaseInsensitive_QuestionMark() {
        Pattern pattern = WildcardUtils.toRegex("test?end");
        assertMatches(pattern, "test1end");
        assertMatches(pattern, "TEST1END");
        assertMatches(pattern, "Test1End");
        assertMatches(pattern, "tEsT1eNd");
    }

    @Test
    public void testToRegex_CaseInsensitive_RealWorld() {
        Pattern pattern = WildcardUtils.toRegex("report*.pdf");
        assertMatches(pattern, "report.pdf");
        assertMatches(pattern, "Report.pdf");
        assertMatches(pattern, "REPORT.PDF");
        assertMatches(pattern, "Report_2026.PDF");
        assertMatches(pattern, "REPORT_FINAL.pdf");
    }

    @Test
    public void testToRegex_CaseInsensitive_Email() {
        Pattern pattern = WildcardUtils.toRegex("*@example.com");
        assertMatches(pattern, "user@example.com");
        assertMatches(pattern, "USER@EXAMPLE.COM");
        assertMatches(pattern, "User@Example.Com");
        assertMatches(pattern, "john.doe@EXAMPLE.COM");
    }

    @Test
    public void testToRegex_CaseInsensitive_MixedContent() {
        Pattern pattern = WildcardUtils.toRegex("Doc*.TxT");
        assertMatches(pattern, "Doc.TxT");
        assertMatches(pattern, "doc.txt");
        assertMatches(pattern, "DOC.TXT");
        assertMatches(pattern, "Document.txt");
        assertMatches(pattern, "DOCUMENT.TXT");
    }

    @Test
    public void testToRegex_CaseInsensitive_WithEscapedChars() {
        // Pattern with escaped asterisk
        Pattern pattern = WildcardUtils.toRegex("file\\*.txt");
        assertMatches(pattern, "file*.txt");
        assertMatches(pattern, "FILE*.TXT");
        assertMatches(pattern, "File*.Txt");
    }
}
