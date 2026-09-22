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
package org.nuxeo.ecm.core.query.sql;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import com.hyland.openarch.common.utils.PeriodAndDuration;
import org.nuxeo.runtime.api.Framework;

/**
 * This defines the constants for HXQL queries.
 *
 * @author Florent Guillaume
 */
public class HXQL {

    /**
     * Property containing the test value of the current date for {@link #nowPlusPeriodAndDuration}.
     * <p>
     * Only for tests, not a public API.
     *
     * @since 11.1
     */
    public static final String TEST_HXQL_NOW = "test.hxql.now";

    // constant utility class
    private HXQL() {
    }

    /** The HXQL query type. */
    public static final String HXQL = "HXQL";

    /**
     * Deprecated compatibility suffix for sys_acl component, use "perm" instead.
     */
    public static final String KEY_ACE_PERMISSION_COMPAT = "permission";

    /**
     * The function returning the current datetime. It can optionally have as an argument a duration that will be added
     * to the current datetime, expressed as a ISO 8601 period.
     * <p>
     * See {@link PeriodAndDuration#parse} for the exact format.
     *
     * @see PeriodAndDuration#parse
     * @since 11.1
     */
    public static final String NOW_FUNCTION = "NOW";

    /**
     * Escapes a string into a single-quoted string for HXQL.
     * <p>
     * Any single quote or backslash characters are escaped with a backslash.
     *
     * @param s the string to escape
     * @return the escaped string
     * @since 5.7, 5.6.0-HF08
     */
    public static String escapeString(String s) {
        return "'" + escapeStringInner(s) + "'";
    }

    /**
     * Escapes a string (assumed to be single quoted) for HXQL.
     * <p>
     * Any single quote or backslash characters are escaped with a backslash, as are the line breaks the grammar does
     * not allow raw inside a string.
     *
     * @param s the string to escape
     * @return the escaped string, without external quotes
     * @since 5.7, 5.6.0-HF08
     */
    public static String escapeStringInner(String s) {
        // backslash -> backslash backslash
        // quote -> backslash quote
        // newline -> backslash n
        // carriage return -> backslash r
        return s.replace("\\", "\\\\").replace("'", "\\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Returns the current dateTime to which the period and duration (if present) has been added.
     *
     * @param periodAndDurationText the period and duration as text, or {@code null}
     * @return the current dateTime to which the period and duration has been added
     * @throws IllegalArgumentException if the period and duration cannot be parsed
     * @since 11.1
     */
    // also used by HXQLQueryConverter
    public static ZonedDateTime nowPlusPeriodAndDuration(String periodAndDurationText) {
        ZonedDateTime now;
        if (Framework.getProperty(TEST_HXQL_NOW) != null) {
            now = ZonedDateTime.parse(Framework.getProperty(TEST_HXQL_NOW));
        } else {
            now = ZonedDateTime.now(ZoneOffset.UTC);
        }
        if (periodAndDurationText == null) {
            return now;
        } else {
            PeriodAndDuration pd;
            try {
                pd = PeriodAndDuration.parse(periodAndDurationText);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid period: " + periodAndDurationText, e);
            }
            return now.plus(pd);
        }
    }

}
