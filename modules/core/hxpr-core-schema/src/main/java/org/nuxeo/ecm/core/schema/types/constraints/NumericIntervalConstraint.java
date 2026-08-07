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

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MAX_EXCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MAX_INCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIN_EXCLUSIVE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIN_INCLUSIVE;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * This constraint ensures a numeric is in an interval.
 * <p>
 * This constraint can validate any {@link Number}.
 * </p>
 *
 * @since 7.1
 */
public class NumericIntervalConstraint extends AbstractConstraint implements RangeConstraint<BigDecimal> {

    private static final String NAME = "NumericIntervalConstraint";

    private static final String PNAME_MINIMUM = "Minimum";

    private static final String PNAME_MAXIMUM = "Maximum";

    private static final String PNAME_MIN_INC = "MinimumInclusive";

    private static final String PNAME_MAX_INC = "MaximumInclusive";

    private final BigDecimal min;

    private final BigDecimal max;

    private final boolean includingMin;

    private final boolean includingMax;

    /**
     * Use null value to disable a bound.
     * <p>
     * Bounds could be any object having toString representating a number.
     * </p>
     *
     * @param min The lower bound of the interval
     * @param includingMin true if the lower bound is included in the interval
     * @param max The upper bound of the interval
     * @param includingMax true if the upper bound is included in the interval
     */
    public NumericIntervalConstraint(Object min, boolean includingMin, Object max, boolean includingMax) {
        this.min = ConstraintUtils.objectToBigDecimal(min);
        this.includingMin = includingMin;
        this.max = ConstraintUtils.objectToBigDecimal(max);
        this.includingMax = includingMax;
    }

    @Override
    public boolean validate(Object object) {
        BigDecimal val = ConstraintUtils.objectToBigDecimal(object);
        if (val == null) {
            return true;
        }
        if (min != null) {
            int test = min.compareTo(val);
            if (test > 0) {
                return false;
            }
            if (!includingMin && test == 0) {
                return false;
            }
        }
        if (max != null) {
            int test = max.compareTo(val);
            if (test < 0) {
                return false;
            }
            if (!includingMax && test == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Here, value is : <br>
     * name = {@value #NAME} <br>
     * parameters =
     * <ul>
     * <li>{@value #PNAME_MINIMUM} : -125.87 // only if bounded</li>
     * <li>{@value #PNAME_MIN_INC} : true // only if bounded</li>
     * <li>{@value #PNAME_MAXIMUM} : 232 // only if bounded</li>
     * <li>{@value #PNAME_MAX_INC} : false // only if bounded</li>
     * </ul>
     */
    @Override
    public Description getDescription() {
        Map<String, Object> params = new HashMap<>();
        if (min != null) {
            params.put(PNAME_MINIMUM, min);
            params.put(PNAME_MIN_INC, includingMin);
        }
        if (max != null) {
            params.put(PNAME_MAXIMUM, max);
            params.put(PNAME_MAX_INC, includingMax);
        }
        return new Description(NumericIntervalConstraint.NAME, params);
    }

    @Override
    public BigDecimal getMin() {
        return min;
    }

    @Override
    public BigDecimal getMax() {
        return max;
    }

    public boolean isIncludingMin() {
        return includingMin;
    }

    public boolean isIncludingMax() {
        return includingMax;
    }

    @Override
    public List<Object> getMessageParameters(Object invalidValue, Locale locale) {
        if (min != null && max != null) {
            return List.of(min, max);
        } else if (min != null) {
            return List.of(min);
        } else {
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
        String subKey = (min != null ? (includingMin ? "minin" : "minex") : "")
                + (max != null ? (includingMax ? "maxin" : "maxex") : "");
        List<String> pathTokens = new ArrayList<>();
        pathTokens.add(MESSAGES_KEY);
        pathTokens.add(NumericIntervalConstraint.NAME);
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
        result = prime * result + ((max == null) ? 0 : max.hashCode());
        result = prime * result + ((min == null) ? 0 : min.hashCode());
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
        NumericIntervalConstraint other = (NumericIntervalConstraint) obj;
        if (includingMax != other.includingMax) {
            return false;
        }
        if (includingMin != other.includingMin) {
            return false;
        }
        if (max == null) {
            if (other.max != null) {
                return false;
            }
        } else if (!max.equals(other.max)) {
            return false;
        }
        if (min == null) {
            if (other.min != null) {
                return false;
            }
        } else if (!min.equals(other.min)) {
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        if (min != null) {
            map.put(includingMin ? MIN_INCLUSIVE : MIN_EXCLUSIVE, toNumber(min));
        }
        if (max != null) {
            map.put(includingMax ? MAX_INCLUSIVE : MAX_EXCLUSIVE, toNumber(max));
        }
        return map;
    }

    private static Number toNumber(BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            return value.doubleValue();
        }
    }

    public static NumericIntervalConstraint fromMap(Map<String, Object> map) {
        Number min = null;
        boolean includingMin = false;
        Number max = null;
        boolean includingMax = false;
        if (map.get(MIN_INCLUSIVE) instanceof Number val) {
            min = val;
            includingMin = true;
        } else if (map.get(MIN_EXCLUSIVE) instanceof Number val) {
            min = val;
        }
        if (map.get(MAX_INCLUSIVE) instanceof Number val) {
            max = val;
            includingMax = true;
        } else if (map.get(MAX_EXCLUSIVE) instanceof Number val) {
            max = val;
        }
        if (min != null || max != null) {
            return new NumericIntervalConstraint(min, includingMin, max, includingMax);
        }
        return null;
    }

    @Override
    public boolean validRange() {
        if (!(getMax() == null || getMin() == null) && (!isIncludingMax() || !isIncludingMin())) {
            return getMin().compareTo(getMax()) < 0;
        } else {
            return RangeConstraint.super.validRange();
        }
    }

}
