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

package org.nuxeo.ecm.core.api.validation;

import static org.nuxeo.ecm.core.api.DocumentConstants.PREFIX_SEPARATOR;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MESSAGE_KEY_SEPARATOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.validation.violations.AbstractValidationViolation;

/**
 * A constraint violation description. Use {@link #getMessage(Locale)} to get the constraint violation description.
 * <p>
 * You could customize constraint violation message using the following rules :
 * <ul>
 * <li>Use {@value Constraint#MESSAGES_KEY} key in {@value Constraint#MESSAGES_BUNDLE} bundle to customize default
 * message</li>
 * <li>Append the constraint name to the previous key to customize the generic message to some constraint</li>
 * <li>Append the schema and the field name to the previous key to customize the message for a specific constraint
 * applied to some specific schema field.</li>
 * </ul>
 * <br>
 * For each messages, you can use parameters in the message :
 * <ul>
 * <li>The invalid value : {0}</li>
 * <li>The schema name : {1}</li>
 * <li>The field name : {2}</li>
 * <li>The constraint name : {3}</li>
 * <li>The first constraint parameter (if exists) : {4}</li>
 * <li>The second constraint parameter (if exists) : {5}</li>
 * <li>...</li>
 * </ul>
 * <p>
 * Examples :
 * <ul>
 * <li>label.schema.constraint.violation=Value '{0}' for field '{1}.{2}' does not respect constraint '{3}'</li>
 * <li>label.schema.constraint.violation.PatternConstraint='{1}.{2}' value ({0}) should match the following format :
 * '{4}'</li>
 * <li>label.schema.constraint.violation.PatternConstraint.myuserschema.firstname ='The firstname should not be empty'
 * </li>
 * </ul>
 *
 * @since 7.1
 */
public class ConstraintViolation extends AbstractValidationViolation {

    private static final Logger log = LogManager.getLogger(ConstraintViolation.class);

    private final transient List<PathNode> pathList;

    private final transient Constraint constraint;

    public ConstraintViolation(List<PathNode> fieldPath, Constraint constraint, Object invalidValue) {
        super(invalidValue);
        this.pathList = new ArrayList<>(fieldPath);
        this.constraint = constraint;
    }

    public List<PathNode> getPathList() {
        return Collections.unmodifiableList(pathList);
    }

    @Override
    public String getPath() {
        return pathList.stream().map(PathNode::toString).collect(Collectors.joining("/"));
    }

    public Constraint getConstraint() {
        return constraint;
    }

    @Override
    public List<Object> getMessageParameters(Locale locale) {
        String computedInvalidValue = "null";
        if (getInvalidValue() != null) {
            String invalidValueString = getInvalidValue().toString();
            if (invalidValueString.length() > 20) {
                computedInvalidValue = invalidValueString.substring(0, 15) + "...";
            } else {
                computedInvalidValue = invalidValueString;
            }
        }
        if (locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            // use the constraint message
            return constraint.getMessageParameters(computedInvalidValue, locale);
        }
        return List.of(computedInvalidValue);
    }

    @Override
    protected String getDefaultMessage(Locale locale) {
        return constraint.getErrorMessage(getInvalidValue(), locale);
    }

    @Override
    public String toString() {
        return getMessage(Locale.ENGLISH);
    }

    /**
     * Allows to locates some constraint violation in a document.
     * <p>
     * {@link #getIndex()} are used to indicates which element violates the constraint for list properties.
     * </p>
     *
     * @since 7.1
     */
    public static class PathNode {

        private Field field;

        private boolean listItem = false;

        int index = 0;

        public PathNode(Field field) {
            this.field = field;
        }

        public PathNode(Field field, int index) {
            super();
            this.field = field;
            this.index = index;
            listItem = true;
        }

        public Field getField() {
            return field;
        }

        public int getIndex() {
            return index;
        }

        public boolean isListItem() {
            return listItem;
        }

        @Override
        public String toString() {
            if (listItem) {
                return field.getName() + "/" + index;
            } else {
                return field.getName();
            }
        }

    }

    @Override
    public String getMessageKey() {
        // test whether there's a specific translation for this field and this constraint
        // the expected key is label.schema.constraint.violation.[constraintName].[schemaName].[field].[subField]
        List<String> pathTokens = new ArrayList<>();
        pathTokens.add(constraint.getMessageKey());
        for (PathNode node : pathList) {
            String name = node.getField().getName();
            // remove prefix (historical)
            name = name.substring(name.indexOf(PREFIX_SEPARATOR) + 1);
            pathTokens.add(name);
        }
        return StringUtils.join(pathTokens, MESSAGE_KEY_SEPARATOR);
    }

    @Override
    protected Logger getLog() {
        return log;
    }

}
