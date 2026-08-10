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

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSTEM;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.XSDTypes;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.rest.model.dto.Constants;
import org.nuxeo.rest.model.dto.FieldDTO;
import org.nuxeo.rest.model.dto.ModelDTO;
import org.nuxeo.rest.model.dto.PrimaryTypeDTO;
import org.nuxeo.rest.model.exception.ModelValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public void validate(SchemaManager schemaManager, ModelDTO modelDTOFromRequest, String projectId) {
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

        // Validate for system schema not present in content type
        validatePrimaryTypesHaveSystemSchema(schemaManager, modelDTOFromRequest);

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
    public void validate(ModelDTO modelDTOFromRequest) {
        violations.clear();
        validateFields(modelDTOFromRequest);
        validatePrimaryTypesHaveSystemSchema(modelDTOFromRequest);
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
    private void validateFullTextFields(SchemaManager schemaManager, ModelDTO modelDTOFromRequest) {
        modelDTOFromRequest.schemas().forEach((schemaName, schema) -> {
            if (schema.fields() != null) {
                schema.fields().forEach((fieldName, field) -> {
                    if (field.fullTextIndexed()) {
                        validateFieldForFullTextIndexed(field.type(), fieldName, schemaName, schemaManager);
                        validateFieldForFullTextIndexed(field.extend(), fieldName, schemaName, schemaManager);
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
    private void validateFields(ModelDTO modelDTOFromRequest) {
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
            if (DateType.ID.equals(field.extend())) {
                validateFieldForDate(field.defaultValue(), fieldName, formRequestFieldType);
            } else if (BooleanType.ID.equals(field.extend())) {
                validateFieldForBool(field.defaultValue(), fieldName, formRequestFieldType);
            }
        }
    }

    /**
     *
     * @param defaultValueOfDate default value of content model datetype
     * @param fieldName field name of content model
     * @param formRequestFieldType ModelDTO node content model
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
     * @param formRequestFieldType ModelDTO node type name content model
     */
    private void validateFieldForBool(String defaultValueOfBool, String fieldName, String formRequestFieldType) {
        if (!(defaultValueOfBool.equalsIgnoreCase("true") || defaultValueOfBool.equalsIgnoreCase("false"))) {
            violations.add(new ModelDTOViolation(
                    "The default value %s cannot be added to the field %s from schema %s as it is not a valid boolean type".formatted(defaultValueOfBool,fieldName,
                            formRequestFieldType),
                    fieldName));

        }
    }

    /**
     * Validates if the system schema is present in the primary type
     * @param modelDTOFromRequest DTO instance prepared from the request
     */

    private void validatePrimaryTypesHaveSystemSchema(SchemaManager schemaManager, ModelDTO modelDTOFromRequest) {
        new SystemSchemaValidator(schemaManager, modelDTOFromRequest).validate();
    }

    private void validatePrimaryTypesHaveSystemSchema(ModelDTO modelDTOFromRequest) {
        new SystemSchemaValidator(modelDTOFromRequest).validate();
    }

    protected class SystemSchemaValidator {

        private final Logger logger = LoggerFactory.getLogger(SystemSchemaValidator.class);

        private final ModelDTO modelDTO;

        private final SchemaManager schemaManager;

        private final Map<String, Set<String>> mixinTypesSchemas = new HashMap<>();

        private final Map<String, Set<String>> primaryTypesSchemas = new HashMap<>();

        private final Set<String> typesDone = new HashSet<>();

        public SystemSchemaValidator(SchemaManager schemaManager, ModelDTO modelDTO) {
            this.schemaManager = schemaManager;
            this.modelDTO = modelDTO;
        }

        public SystemSchemaValidator( ModelDTO modelDTO) {
            this.modelDTO = modelDTO;
            this.schemaManager = null;
        }

        public void validate() {
            expandMixinTypesSchemas();
            expandPrimaryTypesSchemas();
            modelDTO.primaryTypes().keySet().forEach(name -> {
                Set<String> schemas = primaryTypesSchemas.getOrDefault(name, new HashSet<>());
                if (!name.equals(SYS_CONTENT) && !schemas.contains(SYSTEM)) {
                    logger.error("payload responsible for validation error for primaryType without system schema is.....{}, \n\n\n and primaryType name is...{}", modelDTO.toMap(), name);
                    violations.add(new ModelDTOViolation(
                            "The system schema must be present in the following content type %s".formatted(name),
                            name));
                }
            });
        }

        private void expandMixinTypesSchemas() {
            modelDTO.mixinTypes().forEach((name, dto) -> {
                List<String> schemas = dto.schemas();
                if (schemas != null) {
                    mixinTypesSchemas.put(name, new HashSet<>(schemas));
                }
            });
        }

        private void expandPrimaryTypesSchemas() {
            modelDTO.primaryTypes().keySet().forEach(this::expandPrimaryTypeSchemas);
        }

        private void expandPrimaryTypeSchemas(String name) {
            if (!typesDone.add(name)) {
                return; // type already done
            }

            PrimaryTypeDTO dto = modelDTO.primaryTypes().get(name);
            if (dto == null) {
                // happens when extending SysContent in unit tests
                primaryTypesSchemas.put(name, Set.of());
                return;
            }

            Set<String> allSchemas = new HashSet<>();
            expandSchemasFromExtend(dto, allSchemas);
            expandSchemasFromMixins(dto, allSchemas);
            addDirectlyDefinedSchemas(dto, allSchemas);
            recordEffectiveSchemas(name, allSchemas);
        }

        private void expandSchemasFromExtend(PrimaryTypeDTO dto, Set<String> allSchemas) {
            String extend = dto.extend();
            if (extend != null) {
                expandPrimaryTypeSchemas(extend);
                if (schemaManager != null) {
                    DocumentType parentDTO = schemaManager.getDocumentType(extend);
                    if (parentDTO != null && parentDTO.getSchemaNames() != null) {
                        allSchemas.addAll(primaryTypesSchemas.getOrDefault(extend, Set.of()));
                        allSchemas.addAll(parentDTO.getSchemaNames()); // Add schemas from the extended type
                    } else if (parentDTO == null) {
                        allSchemas.addAll(primaryTypesSchemas.getOrDefault(extend, Set.of()));
                    }
                } else {
                    allSchemas.addAll(primaryTypesSchemas.getOrDefault(extend, Set.of()));
                }
            }
        }

        private void expandSchemasFromMixins(PrimaryTypeDTO dto, Set<String> allSchemas) {
            List<String> mixins = dto.mixins();
            if (mixins != null) {
                mixins.forEach(mixin -> allSchemas.addAll(mixinTypesSchemas.getOrDefault(mixin, Set.of())));
            }
        }

        private void addDirectlyDefinedSchemas(PrimaryTypeDTO dto, Set<String> allSchemas) {
            List<String> schemas = dto.schemas();
            if (schemas != null) {
                allSchemas.addAll(schemas);
            }
        }

        private void recordEffectiveSchemas(String name, Set<String> allSchemas) {
            primaryTypesSchemas.put(name, allSchemas);
        }
    }
}
