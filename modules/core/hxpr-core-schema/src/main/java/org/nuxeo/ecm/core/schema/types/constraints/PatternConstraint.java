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

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PATTERN;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * This constraint ensures some object's String representation match a pattern.
 *
 * @since 7.1
 */
public class PatternConstraint extends AbstractConstraint {

    private static final String NAME = "PatternConstraint";

    private static final String PNAME_PATTERN = "Pattern";

    protected final Pattern pattern;

    public PatternConstraint(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public boolean validate(Object object) {
        if (object == null) {
            return true;
        }
        return pattern.matcher(object.toString()).matches();
    }

    /**
     * Here, value is : <br>
     * name = {@value #NAME} <br>
     * parameters =
     * <ul>
     * <li>{@value #PNAME_PATTERN} : [0-9]+</li>
     * </ul>
     */
    @Override
    public Description getDescription() {
        Map<String, Object> params = new HashMap<>();
        params.put(PNAME_PATTERN, pattern.pattern());
        return new Description(PatternConstraint.NAME, params);
    }

    /**
     * @return The pattern used by this constraint to validate.
     * @since 7.1
     */
    public String getPattern() {
        return pattern.pattern();
    }

    @Override
    public List<Object> getMessageParameters(Object invalidValue, Locale locale) {
        return List.of(getPattern());
    }

    @Override
    protected String getSpecificErrorMessage(Object invalidValue, Locale locale) {
        // test whether there's a custom translation for this field constraint specific translation
        // the expected key is label.schema.constraint.violation.[ConstraintName]
        List<String> pathTokens = new ArrayList<>();
        pathTokens.add(MESSAGES_KEY);
        pathTokens.add(PatternConstraint.NAME);
        String key = StringUtils.join(pathTokens, '.');
        return getMessageString(key, getMessageParameters(invalidValue, locale), locale);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((pattern == null) ? 0 : pattern.pattern().hashCode());
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
        PatternConstraint other = (PatternConstraint) obj;
        if (pattern == null) {
            if (other.pattern != null) {
                return false;
            }
        } else if (!pattern.pattern().equals(other.pattern.pattern())) {
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of(PATTERN, pattern.pattern());
    }

    public static PatternConstraint fromMap(Map<String, Object> map) {
        if (map.get(PATTERN) instanceof String pattern) {
            return new PatternConstraint(pattern);
        }
        return null;
    }

}
