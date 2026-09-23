/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.model.validation;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.LIST;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MAX_EXCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MAX_INCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIN_EXCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIN_INCLUSIVE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.PATH_DELIMITER;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;

import java.util.ArrayList;
import java.util.List;

import com.hyland.openarch.ecm.core.schema.dto.ModelMapCodec;
import org.apache.commons.lang3.StringUtils;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeConstants;
import com.hyland.openarch.ecm.core.schema.XSDTypes;
import com.hyland.openarch.ecm.core.schema.dto.ConstraintsDTO;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.FieldDTO;
import com.hyland.openarch.ecm.core.schema.types.ComplexType;
import com.hyland.openarch.ecm.core.schema.types.ListType;
import com.hyland.openarch.ecm.core.schema.types.SimpleTypeImpl;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.constraints.ConstraintUtils;
import com.hyland.openarch.ecm.core.schema.types.TypeException;
import com.hyland.openarch.ecm.core.schema.types.primitives.BinaryType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import com.hyland.openarch.ecm.core.schema.types.primitives.StringType;
import com.hyland.openarch.ecm.core.schema.validation.ValidationReport;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.rest.model.Constants;
import org.nuxeo.rest.model.exception.ModelValidationException;
import org.springframework.stereotype.Component;

@Component
public class ModelValidator {

    private List<ValidationViolation> violations = new ArrayList<>();

    /**
     * Validates the content model passed in the request body for a project.
     * <p>
     * Checks if a content model item (schema/mixinTypes/primaryTypes) passed in the request body for a project is
     * already present as a default content model item. Checks if a content model item (schema/mixinTypes/primaryTypes)
     * passed in the request body for a project exists for a different project in the content model
     * </p>
     *
     * @param schemaManager the existing content model
     * @param modelDTOFromRequest DTO instance prepared from the request made for a project
     * @param projectId the one associated with the proposed update
     */
    public void validate(SchemaManager schemaManager, ContentModelDTO modelDTOFromRequest, String projectId) {
        violations.clear();
        // MixinTypes
        modelDTOFromRequest.mixinTypes().forEach((key, value) -> {
            var valueItem = schemaManager.getMixin(key);
            if (valueItem != null) {
                checkForProjectIdConflict(valueItem.getProjectId(), Constants.MIXINTYPE, key, projectId);
            }
        });
        // Primarytypes
        modelDTOFromRequest.primaryTypes().forEach((key, value) -> {
            var valueItem = schemaManager.getDocumentType(key);
            if (valueItem != null) {
                checkForProjectIdConflict(valueItem.getProjectId(), Constants.PRIMARYTYPE, key, projectId);
            }
        });
        // Schemas
        modelDTOFromRequest.schemas().forEach((key, value) -> {
            var valueItem = schemaManager.getSchema(key);
            if (valueItem != null) {
                checkForProjectIdConflict(valueItem.getProjectId(), Constants.SCHEMA, key, projectId);
            }
        });
        // Types
        modelDTOFromRequest.types().forEach((key, value) -> {
            var valueItem = schemaManager.getType(key);
            if (valueItem != null) {
                checkForProjectIdConflict(valueItem.getProjectId(), Constants.TYPE, key, projectId);
            }
        });

        // Validate the fullTextIndexed on fields
        validateFullTextFields(schemaManager, modelDTOFromRequest);

        // Validate default value of fields (e.g: boolean/date)
        validateFields(modelDTOFromRequest);

        checkViolations();
    }

    /**
     * Validates the content model primitive types passes in the request body for PUT /api/repository/model.
     * <p>
     *     Checks if the primitive type(e.g. date) passed as default value for a content model item (schema/Types) in
     *     the field is valid.
     * </p>
     * @param modelDTOFromRequest DTO instance prepared from the request.
     */
    public void validate(ContentModelDTO modelDTOFromRequest) {
        violations.clear();
        validateFields(modelDTOFromRequest);
        checkViolations();
    }

    private void checkViolations() {
        ValidationReport.checkViolations(violations, true, ModelValidationException::new);
    }

    /**
     * Checks if a content model item present in the request for the supplied ProjectId is already present in the
     * existing content model as a default content model item or is associated to a different projectId and adds the
     * violation around it
     *
     * @param value the corresponding projectId value associated with the existing content model item
     * @param modelItem content model item type (schema/mixinTypes/primaryTypes)
     * @param key the content model item key associated with the same projectId that is being used for the proposed
     *            update
     * @param projectId the one associated with the proposed update
     */
    private void checkForProjectIdConflict(String value, String modelItem, String key, String projectId) {
        if (value == null) {
            violations.add(new ModelDTOViolation(
                    "Content model item " + modelItem + " = [" + key + "] already exists in default content model",
                    modelItem));
        } else if (!value.equals(projectId)) {
            violations.add(new ModelDTOViolation(
                    "Content model item " + modelItem + " = [" + key + "] already exists for projectId " + value,
                    modelItem));
        }
    }

    /**
     * Validate the usage of the flag fullTextIndexed.
     *
     * @param modelDTOFromRequest The model to validate.
     */
    private void validateFullTextFields(SchemaManager schemaManager, ContentModelDTO modelDTOFromRequest) {
        modelDTOFromRequest.schemas().forEach((schemaName, schema) -> {
            if (schema.fields() != null) {
                schema.fields().forEach((fieldName, field) -> {
                    if (field.fullTextIndexed()) {
                        validateFieldForFullTextIndexed(field.typeRef(), fieldName, schemaName, schemaManager);
                    }
                });
            }
        });
    }

    private void validateFieldForFullTextIndexed(String typeName, String fieldName, String schemaName, SchemaManager schemaManager) {
        if (typeName != null && (!checkValidFieldTypeForFullTextIndexed(typeName, schemaManager))) {
                violations.add(new ModelDTOViolation(
                        "The flag fullTextIndexed cannot be added to the field %s from schema %s".formatted(fieldName,
                                schemaName),
                        fieldName));

        }
    }

    /**
     * Check if the flag fullTextIndexed can be added to the given field type. It can only be added if the field is of
     * type String or Blob.
     *
     * @param typeName
     * @param schemaManager
     */
    private boolean checkValidFieldTypeForFullTextIndexed(String typeName, SchemaManager schemaManager) {
        // Check builtin types first
        Type type = XSDTypes.getBuiltinType(typeName);
        if (type == null) {
            // type from the schema manager
            type = schemaManager.getType(typeName);
        }
        if (type == null) {
            return false;
        }
        return isBlobField(type) || isStringField(type);
    }

    private boolean isBlobField(Type fieldType) {
        var type = getBaseType(fieldType);
        if (TypeConstants.isBlobType(type)) {
            type = ((ComplexType) type).getField(KEY_BLOB_KEY).getType(); // BinaryType
        }
        return type instanceof BinaryType;
    }

    private boolean isStringField(Type fieldType) {
        var type = getBaseType(fieldType);
        return type instanceof StringType;
    }

    private Type getBaseType(Type type) {
        if (type instanceof SimpleTypeImpl) {
            return getBaseType(type.getSuperType());
        }
        if (type instanceof ListType listType) {
            return getBaseType(listType.getFieldType());
        }
        return type;
    }

    /**
     * Validates default value of field is of correct type or not
     * @param modelDTOFromRequest DTO instance prepared from the request
     */
    private void validateFields(ContentModelDTO modelDTOFromRequest) {
        modelDTOFromRequest.schemas().forEach((schemaName, schema) -> {
            if (schema.fields() != null) {
                schema.fields().forEach((fieldName, field) -> checkValue(field, fieldName, schemaName));
            }
        });

        modelDTOFromRequest.types().forEach((typeName, type) -> {
            if (type.fields() != null) {
                type.fields().forEach((fieldName, field) -> checkValue(field, fieldName, typeName));
            }
        });
    }

    private void checkValue(FieldDTO field, String fieldName, String formRequestFieldType) {
        if (field.defaultValue() != null) {
            if (DateType.ID.equals(field.typeRef())) {
                validateFieldForDate(field.defaultValue(), fieldName, formRequestFieldType);
            } else if (BooleanType.ID.equals(field.typeRef())) {
                validateFieldForBool(field.defaultValue(), fieldName, formRequestFieldType);
            }
        }
        validateConstraintBounds(field.constraints(), fieldName, formRequestFieldType);
        // a complex field carries nested fields, and a list field the type of its items; both hold defaults and
        // constraints of their own, and are reported by the path that leads to them
        if (field.fields() != null) {
            field.fields()
                 .forEach((nestedName, nested) -> checkValue(nested, fieldName + PATH_DELIMITER + nestedName,
                         formRequestFieldType));
        }
        if (field.list() != null) {
            checkValue(field.list(), fieldName + PATH_DELIMITER + LIST, formRequestFieldType);
        }
    }

    /**
     * Checks that the interval bounds of a constraint can be read back by the constraint layer.
     * <p>
     * A bound it cannot convert is dropped, and a missing bound means "unbounded": the constraint would then silently
     * not apply at all, leaving the caller believing a rule is in place when it is not. Report it as a violation
     * instead. The conversions of {@link ConstraintUtils} are used as the oracle, so a bound is only rejected when it
     * would really produce no constraint.
     * <p>
     * Length bounds are not checked here: a length is held as a {@link Long} and so a non-integer value cannot survive
     * deserialization to reach this point, which is why {@code minLength}/{@code maxLength} are validated by
     * {@link ModelMapCodec} as the model is read instead.
     */
    private void validateConstraintBounds(ConstraintsDTO constraints, String fieldName, String formRequestFieldType) {
        if (constraints == null) {
            return;
        }
        validateIntervalBound(constraints.minInclusive(), MIN_INCLUSIVE, fieldName, formRequestFieldType);
        validateIntervalBound(constraints.minExclusive(), MIN_EXCLUSIVE, fieldName, formRequestFieldType);
        validateIntervalBound(constraints.maxInclusive(), MAX_INCLUSIVE, fieldName, formRequestFieldType);
        validateIntervalBound(constraints.maxExclusive(), MAX_EXCLUSIVE, fieldName, formRequestFieldType);
    }

    /** An interval bound must be a number or an ISO 8601 date, the two forms the interval constraints can read. */
    private void validateIntervalBound(Object bound, String boundName, String fieldName, String formRequestFieldType) {
        if (bound != null && ConstraintUtils.objectToBigDecimal(bound) == null
                && ConstraintUtils.objectToTimeMillis(bound) == null) {
            violations.add(new ModelDTOViolation(
                    "The %s value %s cannot be added to the field %s from schema %s as it is neither a number nor a valid ISO 8601 format [yyyy-MM-dd[THH:mm:ss[.SSS][+tz]]]".formatted(
                            boundName, bound, fieldName, formRequestFieldType),
                    fieldName));
        }
    }

    /**
     *
     * @param defaultValueOfDate default value of content model datetype
     * @param fieldName field name of content model
     * @param formRequestFieldType ContentModelDTO node content model
     */
    private void validateFieldForDate(String defaultValueOfDate, String fieldName, String formRequestFieldType) {
        try {
            if (!StringUtils.isEmpty(defaultValueOfDate)) {
                DateType.INSTANCE.convert(defaultValueOfDate);
            }
        } catch (TypeException ie) {
            violations.add(new ModelDTOViolation(
                    "The default value %s cannot be added to the field %s from schema %s as it is not a valid ISO 8601 format [yyyy-MM-dd[THH:mm:ss[.SSS][+tz]]]".formatted(
                            defaultValueOfDate, fieldName, formRequestFieldType),
                    fieldName));
        }
    }

    /**
     *
     * @param defaultValueOfBool default value of content model boolean type
     * @param fieldName field name of content model
     * @param formRequestFieldType ContentModelDTO node type name content model
     */
    private void validateFieldForBool(String defaultValueOfBool, String fieldName, String formRequestFieldType) {
        if (!(defaultValueOfBool.equalsIgnoreCase("true") || defaultValueOfBool.equalsIgnoreCase("false"))) {
            violations.add(new ModelDTOViolation(
                    "The default value %s cannot be added to the field %s from schema %s as it is not a valid boolean type".formatted(defaultValueOfBool,fieldName,
                            formRequestFieldType),
                    fieldName));

        }
    }

}
