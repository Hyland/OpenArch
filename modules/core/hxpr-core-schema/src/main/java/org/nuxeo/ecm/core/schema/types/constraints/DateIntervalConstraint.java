/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.schema.types.constraints;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This constraint ensures a date is in an interval.
 * <p>
 * This constraint can validate any {@link Date} or {@link Calendar}. This constraint also support {@link Number} types
 * whose long value is recognised as number of milliseconds since January 1, 1970, 00:00:00 GMT. The constraint finally
 * supports String having YYYY-MM-DD format.
 * </p>
 *
 * @since 7.1
 */
public class DateIntervalConstraint extends AbstractConstraint {

    private static final Logger log = LogManager.getLogger(DateIntervalConstraint.class);

    private static final String NAME = "DateIntervalConstraint";

    private static final String PNAME_MINIMUM = "Minimum";

    private static final String PNAME_MAXIMUM = "Maximum";

    private static final String PNAME_MIN_INC = "MinimumInclusive";

    private static final String PNAME_MAX_INC = "MaximumInclusive";

    private final Long minTime;

    private final Long maxTime;

    private final boolean includingMin;

    private final boolean includingMax;

    /**
     * Use null value to disable a bound.
     * <p>
     * Bounds could be any {@link Date} or {@link Calendar}. Bounds also support {@link Number} types whose long value
     * is recognised as number of milliseconds since January 1, 1970, 00:00:00 GMT. Bounds finally supports String
     * having YYYY-MM-DD format.
     * </p>
     * <p>
     * Invalid bound (wrong format) would be ignored with log warning.
     * </p>
     *
     * @param minDate The lower bound of the interval
     * @param includingMin true if the lower bound is included in the interval
     * @param maxDate The upper bound of the interval
     * @param includingMax true if the upper bound is included in the interval
     */
    public DateIntervalConstraint(Object minDate, boolean includingMin, Object maxDate, boolean includingMax) {
        minTime = ConstraintUtils.objectToTimeMillis(minDate);
        this.includingMin = includingMin;
        maxTime = ConstraintUtils.objectToTimeMillis(maxDate);
        this.includingMax = includingMax;
        if (minTime != null && maxTime != null && minTime > maxTime) {
            log.warn("lower bound ({}) is greater than upper bound ({}). No dates could be valid.", minDate, maxDate);
        }
        if ((minTime == null && minDate != null) || (maxTime == null && maxDate != null)) {
            log.warn("some bound was ignored due to invalid date format (supported format is {} (min = {} - max = {})", ConstraintUtils.DATE_FORMAT, minDate, maxDate);
        }
    }

    @Override
    public boolean validate(Object object) {
        if (object == null) {
            return true;
        }
        Long timeValue = ConstraintUtils.objectToTimeMillis(object);
        if (timeValue == null) {
            return false;
        }
        if (minTime != null) {
            if (timeValue < minTime.longValue()) {
                return false;
            }
            if (!includingMin && timeValue == minTime.longValue()) {
                return false;
            }
        }
        if (maxTime != null) {
            if (timeValue > maxTime.longValue()) {
                return false;
            }
            if (!includingMax && timeValue == maxTime.longValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Here, value is : <br>
     * name = {@value #NAME}. <br>
     * parameters =
     * <ul>
     * <li>{@value #PNAME_MINIMUM} : 2014-11-05 // only if bounded</li>
     * <li>{@value #PNAME_MIN_INC} : true // only if bounded</li>
     * <li>{@value #PNAME_MAXIMUM} : 2014-11-25 // only if bounded</li>
     * <li>{@value #PNAME_MAX_INC} : false // only if bounded</li>
     * </ul>
     */
    @Override
    public Description getDescription() {
        Map<String, Object> params = new HashMap<>();
        if (minTime != null) {
            params.put(PNAME_MINIMUM, Instant.ofEpochMilli(minTime));
            params.put(PNAME_MIN_INC, includingMin);
        }
        if (maxTime != null) {
            params.put(PNAME_MAXIMUM, Instant.ofEpochMilli(maxTime));
            params.put(PNAME_MAX_INC, includingMax);
        }
        return new Description(DateIntervalConstraint.NAME, params);
    }

    public Long getMinTime() {
        return minTime;
    }

    public Long getMaxTime() {
        return maxTime;
    }

    public boolean isIncludingMin() {
        return includingMin;
    }

    public boolean isIncludingMax() {
        return includingMax;
    }

    @Override
    public List<Object> getMessageParameters(Object invalidValue, Locale locale) {
        if (minTime != null && maxTime != null) {
            String min = Instant.ofEpochMilli(minTime).toString();
            String max = Instant.ofEpochMilli(maxTime).toString();
            return List.of(min, max);
        } else if (minTime != null) {
            String min = Instant.ofEpochMilli(minTime).toString();
            return List.of(min);
        } else {
            String max = Instant.ofEpochMilli(maxTime).toString();
            return List.of(max);
        }
    }

    @Override
    protected String getSpecificErrorMessage(Object invalidValue, Locale locale) {
        // test whether there's a custom translation for this field constraint specific translation
        // the expected key is label.schema.constraint.violation.[ConstraintName].mininmaxin or
        // the expected key is label.schema.constraint.violation.[ConstraintName].minexmaxin or
        // the expected key is label.schema.constraint.violation.[ConstraintName].mininmaxex or
        // the expected key is label.schema.constraint.violation.[ConstraintName].minexmaxex or
        // the expected key is label.schema.constraint.violation.[ConstraintName].minin or
        // the expected key is label.schema.constraint.violation.[ConstraintName].minex or
        // the expected key is label.schema.constraint.violation.[ConstraintName].maxin or
        // the expected key is label.schema.constraint.violation.[ConstraintName].maxex
        List<String> pathTokens = new ArrayList<>();
        pathTokens.add(MESSAGES_KEY);
        pathTokens.add(DateIntervalConstraint.NAME);
        String subKey = (minTime != null ? (includingMin ? "minin" : "minex") : "")
                + (maxTime != null ? (includingMax ? "maxin" : "maxex") : "");
        pathTokens.add(subKey);
        String key = StringUtils.join(pathTokens, '.');
        return getMessageString(key, getMessageParameters(invalidValue, locale), locale);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (includingMax ? 1231 : 1237);
        result = prime * result + (includingMin ? 1231 : 1237);
        result = prime * result + ((maxTime == null) ? 0 : maxTime.hashCode());
        result = prime * result + ((minTime == null) ? 0 : minTime.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DateIntervalConstraint other = (DateIntervalConstraint) obj;
        if (includingMax != other.includingMax) {
            return false;
        }
        if (includingMin != other.includingMin) {
            return false;
        }
        if (maxTime == null) {
            if (other.maxTime != null) {
                return false;
            }
        } else if (!maxTime.equals(other.maxTime)) {
            return false;
        }
        if (minTime == null) {
            if (other.minTime != null) {
                return false;
            }
        } else if (!minTime.equals(other.minTime)) {
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Object> toDTO() {
        var map = new LinkedHashMap<String, Object>();
        if (minTime != null) {
            map.put(includingMin ? "minInclusive" : "minExclusive", toISO8601(minTime));
        }
        if (maxTime != null) {
            map.put(includingMax ? "maxInclusive" : "maxExclusive", toISO8601(maxTime));
        }
        return map;
    }

    public static DateIntervalConstraint fromDTO(Map<String, Object> dto) {
        Number min = null;
        boolean includingMin = false;
        Number max = null;
        boolean includingMax = false;
        if (dto.get("minInclusive") instanceof String s) {
            min = fromISO8601(s);
            includingMin = true;
        } else if (dto.get("minExclusive") instanceof String s) {
            min = fromISO8601(s);
        }
        if (dto.get("maxInclusive") instanceof String s) {
            max = fromISO8601(s);
            includingMax = true;
        } else if (dto.get("maxExclusive") instanceof String s) {
            max = fromISO8601(s);
        }
        if (min != null || max != null) {
            return new DateIntervalConstraint(min, includingMin, max, includingMax);
        }
        return null;
    }

    private static String toISO8601(long time) {
        return Instant.ofEpochMilli(time).toString();
    }

    private static Long fromISO8601(String s) {
        Long millis = ConstraintUtils.objectToTimeMillis(s);
        if (millis == null) {
            throw new IllegalArgumentException(s);
        }
        return millis;
    }

}
