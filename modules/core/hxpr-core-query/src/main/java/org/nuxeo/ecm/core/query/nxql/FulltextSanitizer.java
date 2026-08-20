/*
 * (C) Copyright 2010-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.nxql;

import java.util.regex.Pattern;

/**
 * Cleans up user-supplied full-text search terms.
 * <p>
 * This is not about NXQL syntax, which is the serializer's job; it is about the full-text query language the term is
 * handed to once the query runs, where characters such as {@code &} or {@code |} are operators.
 *
 * @since 5.6
 */
public class FulltextSanitizer {

    public static final String DEFAULT_SPECIAL_CHARACTERS_REGEXP = "!#$%&'()+,./\\\\:-@{|}`^~";

    protected static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile(
            "[" + DEFAULT_SPECIAL_CHARACTERS_REGEXP + "]");

    protected static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s]+");

    private FulltextSanitizer() {
        // utility class
    }

    /**
     * Removes any special character that could be mis-interpreted as a low level full-text query operator.
     * <p>
     * Ideally the low level full-text language parser would be robust to any user input, however that is much more
     * complicated to implement correctly than this simple filtering scheme.
     *
     * @return the sanitized text value
     */
    public static String sanitize(String value) {
        StringBuilder res = new StringBuilder();
        value = SPECIAL_CHARACTERS_PATTERN.matcher(value).replaceAll(" ").trim();
        for (String token : WHITESPACE_PATTERN.split(value)) {
            if ("-".equals(token) || "*".equals(token) || "*-".equals(token) || "-*".equals(token)) {
                continue;
            }
            if (res.length() > 0) {
                res.append(' ');
            }
            if (token.startsWith("-") || token.endsWith("*")) {
                res.append(token);
            } else {
                res.append(token.replace("-", " ").replace("*", " "));
            }
        }
        return res.toString().trim();
    }

}
