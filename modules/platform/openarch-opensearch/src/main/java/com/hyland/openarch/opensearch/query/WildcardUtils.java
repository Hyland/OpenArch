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

import java.util.Set;
import java.util.regex.Pattern;

public class WildcardUtils {

    private static final Character WILDCARD_ESCAPE_CHARACTER = '\\';

    private static final Set<Character> WILDCARD_SPECIAL = Set.of('?', '*', WILDCARD_ESCAPE_CHARACTER);

    /**
     * Prepares a user-provided wildcard pattern for an OpenSearch "wildcard" query.
     * <p>
     * Semantics:
     * <ul>
     * <li>'*' and '?' are wildcards unless escaped by a preceding backslash</li>
     * <li>'\*' -&gt; literal '*'</li>
     * <li>'\?' -&gt; literal '?'</li>
     * <li>'\\' -&gt; literal '\'</li>
     * <li>'\x' (x not in {* ? \}) -&gt; treated as literal backslash + 'x'</li>
     * <li>trailing '\' -&gt; treated as literal backslash</li>
     * </ul>
     *
     * @param input the user-provided wildcard pattern
     * @return the escaped pattern ready for OpenSearch wildcard query, or the input if null or empty
     */
    public static String escapeForOpenSearchWildcard(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder out = new StringBuilder(input.length());
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaping) {
                // User used backslash before this character.
                if (WILDCARD_SPECIAL.contains(c)) {
                    // Keep it as an escape sequence for the wildcard engine:
                    // \* or \? or \\ (meaning literal backslash)
                    out.append(WILDCARD_ESCAPE_CHARACTER).append(c);
                } else {
                    // Backslash before a non-special char: treat backslash as literal
                    // so we need to escape it for the wildcard engine => "\\"
                    out.append(WILDCARD_ESCAPE_CHARACTER).append(WILDCARD_ESCAPE_CHARACTER).append(c);
                }
                escaping = false;
                continue;
            }

            if (c == WILDCARD_ESCAPE_CHARACTER) {
                escaping = true;
                continue;
            }

            // Not escaping: keep wildcards as-is, and copy all other chars.
            out.append(c);
        }

        // Trailing backslash -> literal backslash
        if (escaping) {
            out.append(WILDCARD_ESCAPE_CHARACTER).append(WILDCARD_ESCAPE_CHARACTER);
        }

        return out.toString();
    }

    /**
     * Converts an OpenSearch wildcard pattern (already escaped with the escapeForOpenSearchWildcard method) to an
     * equivalent Java Pattern for post-filtering.
     * <p>
     * The resulting pattern is case insensitive.
     * <p>
     * Semantics (OpenSearch wildcard):
     * <ul>
     * <li>'*' =&gt; 0 to n characters</li>
     * <li>'?' =&gt; exactly 1 character</li>
     * <li>'\*', '\?', '\\' =&gt; literals</li>
     * </ul>
     *
     * @param escapedInputWildcardPattern the escaped wildcard pattern
     * @return the equivalent Java Pattern (case insensitive), or null if input is null
     */
    public static Pattern toRegex(String escapedInputWildcardPattern) {
        if (escapedInputWildcardPattern == null) {
            return null;
        }

        String s = escapedInputWildcardPattern;
        StringBuilder re = new StringBuilder(s.length() * 2);

        // Anchor for "whole term" (equivalent to wildcard on keyword)
        re.append("^");

        boolean escaping = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (escaping) {
                // In your normalized "escaped" pattern, after '\' we can have: '*', '?', '\'
                // => must become a literal
                appendRegexLiteralChar(re, c);
                escaping = false;
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }

            if (c == '*') {
                re.append(".*"); // '*' => zero or more characters
                                 // [1](https://docs.opensearch.org/latest/query-dsl/term/wildcard/)
            } else if (c == '?') {
                re.append("."); // '?' => exactly one character
                                // [1](https://docs.opensearch.org/latest/query-dsl/term/wildcard/)
            } else {
                appendRegexLiteralChar(re, c);
            }
        }

        // Trailing '\' => in your semantics, represents a literal '\'
        if (escaping) {
            appendRegexLiteralChar(re, '\\');
        }

        re.append("$");

        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Appends a character to the regex builder, escaping it if it's a regex metacharacter.
     *
     * @param re the StringBuilder building the regex pattern
     * @param c the character to append as a literal
     */
    private static void appendRegexLiteralChar(StringBuilder re, char c) {
        // Escape Java regex metacharacters
        switch (c) {
        case '\\':
        case '.':
        case '^':
        case '$':
        case '|':
        case '(':
        case ')':
        case '[':
        case ']':
        case '{':
        case '}':
        case '*':
        case '+':
        case '?':
            re.append('\\');
            // fallthrough
        default:
            re.append(c);
        }
    }

}
