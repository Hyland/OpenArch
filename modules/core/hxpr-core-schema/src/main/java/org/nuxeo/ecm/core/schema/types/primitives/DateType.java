/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Bogdan Stefanescu
 *     Florent Guillaume
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */
package org.nuxeo.ecm.core.schema.types.primitives;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.DateIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;

/**
 * The date (actually timestamp) type.
 */
public class DateType extends PrimitiveType {

    private static final Pattern SIMPLE_DATE_REGEX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final Pattern TIMEZONE_WITHOUT_COLON_REGEX = Pattern.compile("^(.*[+-]\\d{2})(\\d{2})$");

    public static final String ID = "date";

    public static final DateType INSTANCE = new DateType();

    public DateType() {
        super(ID);
    }

    @Override
    public Instant convert(Object value) throws TypeException {
        if (value instanceof Instant instant) {
            return instant;
        } else if (value instanceof String string) {
            Instant instant = parse(string);
            if (instant != null) {
                return instant;
            }
            // fall through
        }
        throw new TypeException(Instant.class);
    }

    private Instant parse(String value) {
        try {
            return Instant.parse(normalize(value));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // 2022-07-28 -> 2022-07-28T00:00:00Z
    // 2022-07-28T00:00:00+1234 -> 2022-07-28T00:00:00+12:34
    private String normalize(String value) {
        if (SIMPLE_DATE_REGEX.matcher(value).matches()) {
            value += "T00:00:00Z";
        } else {
            Matcher matcher = TIMEZONE_WITHOUT_COLON_REGEX.matcher(value);
            if (matcher.matches()) {
                value = matcher.group(1) + ':' + matcher.group(2);
            }
        }
        return value;
    }

    @Override
    public Object decode(String str) {
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        return parse(str);
    }

    @Override
    public String encode(Object object) {
        if (object instanceof Instant instant) {
            return instant.toString();
        } else {
            return null;
        }
    }

    protected Object readResolve() {
        return INSTANCE;
    }

    @Override
    public boolean support(Class<? extends Constraint> constraint) {
        if (NotNullConstraint.class.equals(constraint)) {
            return true;
        }
        return DateIntervalConstraint.class.equals(constraint);
    }

}
