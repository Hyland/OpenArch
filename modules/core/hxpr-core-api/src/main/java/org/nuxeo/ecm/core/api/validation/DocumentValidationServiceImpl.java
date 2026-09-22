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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.BaseValuesVisitor;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation.PathNode;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.runtime.model.DefaultComponent;

public class DocumentValidationServiceImpl extends DefaultComponent implements DocumentValidationService {

    protected static final String XP = "activations";

    private Map<String, Boolean> validationActivations;

    @Override
    public void start() {
        validationActivations = this.<DocumentValidationDescriptor> getRegistryContributions(XP)
                                    .stream()
                                    .collect(Collectors.toMap(DocumentValidationDescriptor::getContext,
                                            DocumentValidationDescriptor::isActivated));
    }

    @Override
    public void stop() throws InterruptedException {
        validationActivations = null;
    }

    @Override
    public boolean isActivated(String context, Map<String, Serializable> contextMap) {
        if (contextMap != null) {
            Forcing flag = (Forcing) contextMap.get(DocumentValidationService.CTX_MAP_KEY);
            if (flag != null) {
                switch (flag) {
                case TURN_ON:
                    return true;
                case TURN_OFF:
                    return false;
                case USUAL:
                    break;
                }
            }
        }
        Boolean activated = validationActivations.get(context);
        if (activated == null) {
            return false;
        } else {
            return activated;
        }
    }

    @Override
    public ValuesVisitor getValidationVisitor(List<ValidationViolation> violations) {
        return new ValidationVisitor(violations);
    }

    // used only in tests
    @Override
    public DocumentValidationReport validate(DocumentModel document) {
        List<ValidationViolation> violations = new ArrayList<>();
        ValuesVisitor visitor = new ValidationVisitor(violations);
        document.visitFields(visitor);
        return new DocumentValidationReport(violations);
    }

    public static class ValidationVisitor extends BaseValuesVisitor {

        protected final List<ValidationViolation> violations;

        public ValidationVisitor(List<ValidationViolation> violations) {
            this.violations = violations;
        }

        @Override
        public boolean visitActualBlobs() {
            return false; // we want blobs to be expanded as a complex field
        }

        private List<PathNode> getPathNodeList() {
            List<PathNode> list = new ArrayList<>();
            int size = xpath.size();
            for (int i = 0 ; i < size; i++) {
                Field field = (Field) xpath.get(i);
                PathNode pathNode;
                if (i < size - 1 && xpath.get(i + 1) instanceof Integer index) {
                    i++;
                    pathNode = new PathNode(field, index);
                } else {
                    pathNode = new PathNode(field);
                }
                list.add(pathNode);
            }
            return list;
        }

        @Override
        public void value(Object value, Field field) {
            if (field == null) {
                return;
            }
            for (Constraint constraint : field.getConstraints()) {
                if (!constraint.validate(value)) {
                    violations.add(new ConstraintViolation(getPathNodeList(), constraint, value));
                }
            }
        }

        @Override
        public void list(List<Object> list, Field listField, Field elemField) {
            int i = 0;
            for (Object value : list) {
                startListElement(i++);
                value(value, elemField);
                endListElement();
            }
        }
    }

}
