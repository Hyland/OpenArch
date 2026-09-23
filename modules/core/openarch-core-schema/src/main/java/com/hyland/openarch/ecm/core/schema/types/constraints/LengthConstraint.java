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

package com.hyland.openarch.ecm.core.schema.types.constraints;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MAX_LENGTH;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIN_LENGTH;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * This constraints checks whether an object's String representation size is in some interval.
 * <p>
 * This constraint's bounds are not strict (i.e. &gt;= and &lt;=).
 * </p>
 *
 * @since 7.1
 */
public class LengthConstraint extends AbstractConstraint implements RangeConstraint<Long> {

    private static final String NAME = "LengthConstraint";

    private static final String PNAME_MIN_LENGTH = "Minimum";

    private static final String PNAME_MAX_LENGTH = "Maximum";

    private final Long min;

    private final Long max;

    /**
     * For a fixed length, use min = max values.
     * <p>
     * Bounds could be any object having toString representating an integer.
     * </p>
     *
     * @param min Minimum length for the validated String, use a null value to get unbounded length.
     * @param max Maximum length for the validated String, use a null value to get unbounded length.
     */
    public LengthConstraint(Object min, Object max) {
        this.min = ConstraintUtils.objectToPostiveLong(min);
        this.max = ConstraintUtils.objectToPostiveLong(max);
    }

    @Override
    public boolean validate(Object object) {
        if (object == null) {
            return true;
        }
        int len = object.toString().length();
        if (min != null && len < min) {
            return false;
        }
        return !(max != null && len > max);
    }

    /**
     * Here, value is : <br>
     * name = {@value #NAME} <br>
     * parameters =
     * <ul>
     * <li>{@value #PNAME_MIN_LENGTH} : 5 // only is bounded</li>
     * <li>{@value #PNAME_MAX_LENGTH} : 10 // only if bounded</li>
     * </ul>
     */
    @Override
    public Description getDescription() {
        Map<String, Object> params = new HashMap<>();
        if (min != null) {
            params.put(PNAME_MIN_LENGTH, min);
        }
        if (max != null) {
            params.put(PNAME_MAX_LENGTH, max);
        }
        return new Description(LengthConstraint.NAME, params);
    }

    /**
     * @return This constraints minimum length if bounded, null otherwise.
     * @since 7.1
     */
    @Override
    public Long getMin() {
        return min;
    }

    /**
     * @return This constraints maximum length if bounded, null otherwise.
     * @since 7.1
     */
    @Override
    public Long getMax() {
        return max;
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
        // the expected key is label.schema.constraint.violation.[ConstraintName].minmax or
        // the expected key is label.schema.constraint.violation.[ConstraintName].min or
        // the expected key is label.schema.constraint.violation.[ConstraintName].max
        String subKey;
        if (min != null && max != null) {
            subKey = "minmax";
        } else if (min != null) {
            subKey = "min";
        } else {
            subKey = "max";
        }
        List<String> pathTokens = new ArrayList<>();
        pathTokens.add(MESSAGES_KEY);
        pathTokens.add(LengthConstraint.NAME);
        pathTokens.add(subKey);
        String key = StringUtils.join(pathTokens, '.');
        return getMessageString(key, getMessageParameters(invalidValue, locale), locale);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
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
        LengthConstraint other = (LengthConstraint) obj;
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
            map.put(MIN_LENGTH, min);
        }
        if (max != null) {
            map.put(MAX_LENGTH, max);
        }
        return map;
    }

    public static LengthConstraint fromMap(Map<String, Object> map) {
        Number min = null;
        Number max = null;
        if (map.get(MIN_LENGTH) instanceof Number val) {
            min = val;
        }
        if (map.get(MAX_LENGTH) instanceof Number val) {
            max = val;
        }
        if (min != null || max != null) {
            return new LengthConstraint(min, max);
        }
        return null;
    }

}
