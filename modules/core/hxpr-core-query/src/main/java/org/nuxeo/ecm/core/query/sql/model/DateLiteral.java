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

package org.nuxeo.ecm.core.query.sql.model;

import static org.nuxeo.common.utils.DateUtils.formatISODateTime;
import static org.nuxeo.common.utils.DateUtils.parseISODateTime;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * @author Florent Guillaume
 */
public record DateLiteral(ZonedDateTime value, boolean onlyDate) implements Literal {

    public DateLiteral(ZonedDateTime value) {
        this(value, false);
    }

    public DateLiteral(String value, boolean onlyDate) {
        this(parseISODateTime(value), onlyDate);
    }

    @Override
    public String toString() {
        String s = formatISODateTime(value, onlyDate);
        if (onlyDate) {
            return "DATE '" + s + "'";
        } else {
            return "TIMESTAMP '" + s + "'";
        }
    }

    @Override
    public String asString() {
        return formatISODateTime(value, onlyDate);
    }

}
