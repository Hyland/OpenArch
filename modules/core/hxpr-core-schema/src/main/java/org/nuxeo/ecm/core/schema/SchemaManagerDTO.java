/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.schema;

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.ANONYMOUS_TYPE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.CONSTRAINTS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.EXTENDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FIELDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.ITEM;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.LIST;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXIN_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXINS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SCHEMAS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SUPERTYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.TYPES;
import static org.nuxeo.ecm.core.schema.TypeConstants.PREFIX_SEPARATOR;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_RELATION;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.nuxeo.ecm.core.schema.dto.ConstraintsDTO;
import org.nuxeo.ecm.core.schema.dto.ContentModelDTO;
import org.nuxeo.ecm.core.schema.dto.DTOPath;
import org.nuxeo.ecm.core.schema.dto.FieldDTO;
import org.nuxeo.ecm.core.schema.dto.MixinTypeDTO;
import org.nuxeo.ecm.core.schema.dto.ModelMapCodec;
import org.nuxeo.ecm.core.schema.dto.PrimaryTypeDTO;
import org.nuxeo.ecm.core.schema.dto.SchemaDTO;
import org.nuxeo.ecm.core.schema.dto.TypeDTO;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.ComplexTypeImpl;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.CompositeTypeImpl;
import org.nuxeo.ecm.core.schema.types.FieldImpl;
import org.nuxeo.ecm.core.schema.types.ListTypeImpl;
import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.SchemaImpl;
import org.nuxeo.ecm.core.schema.types.SimpleType;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationException;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationUtils;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ParentTypeValidationViolation;
import org.nuxeo.ecm.core.schema.validation.violations.TypeRecursionViolation;
import org.nuxeo.ecm.core.schema.validation.violations.UnknownObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema Manager implementation built from a {@link ContentModelDTO}.
 * <p>
 * The DTO has already been checked for <em>shape</em> by {@link ModelMapCodec} (or by Jackson, on
 * the REST path). What is left here is <em>resolution</em>: turning names into types, schemas and
 * mixins, and reporting the ones that do not resolve.
 */
public class SchemaManagerDTO extends SchemaManagerBase {

    private static final Logger log = LoggerFactory.getLogger(SchemaManagerDTO.class);

    private final List<ValidationViolation> violations;

    public SchemaManagerDTO(ContentModelDTO model, List<ValidationViolation> violations, boolean validateOnly) {
        this.violations = violations == null ? new ArrayList<>() : violations;
        registerTypes(model.types());
        registerSchemas(model.schemas());
        registerMixinTypes(model.mixinTypes());
        registerPrimaryTypes(model.primaryTypes());
        computeDenormalizations();
        ValidationReport.checkViolations(this.violations, !validateOnly, SchemaValidationException::new);
    }

    /** Builds from the persisted map form; shape problems in the map become violations too. */
    public SchemaManagerDTO(Map<String, Object> map, List<ValidationViolation> violations, boolean validateOnly) {
        this(violations == null ? new ArrayList<>() : violations, map, validateOnly);
    }

    /**
     * Reading the map and binding the result must share one violations list, and a {@code this(…)}
     * call cannot introduce a local; hence this overload, distinguished only by parameter order.
     */
    private SchemaManagerDTO(List<ValidationViolation> violations, Map<String, Object> map, boolean validateOnly) {
        this(ModelMapCodec.read(map, violations), violations, validateOnly);
    }

    public SchemaManagerDTO(Map<String, Object> map) {
        this(map, new ArrayList<>(), false);
    }

    /*
     * ===== Types =====
     */

    /** Types not registered yet; consumed as they are resolved, so forward references work. */
    private Map<String, TypeDTO> pendingTypes;

    private void registerTypes(Map<String, TypeDTO> typeDTOs) {
        pendingTypes = new LinkedHashMap<>(typeDTOs);
        while (!pendingTypes.isEmpty()) {
            registerPendingType(pendingTypes.keySet().iterator().next());
        }
    }

    // may return null if not found
    private Type registerPendingType(String name) {
        TypeDTO dto = pendingTypes.remove(name);
        if (dto == null) {
            return null;
        }
        Type type = createType(name, dto, typePath(name, dto));
        if (type != null) {
            types.put(name, type);
        }
        return type;
    }

    // may return null if not found
    private Type getRegisteredType(String name) {
        // builtin types
        Type type = XSDTypes.getBuiltinType(name);
        if (type != null) {
            return type;
        }
        // types already loaded
        type = types.get(name);
        if (type != null) {
            return type;
        }
        // pending types
        return registerPendingType(name);
    }

    private static DTOPath typePath(String name, TypeDTO dto) {
        return DTOPath.root(TYPES, null).child(name, ModelMapCodec.toJson(dto));
    }

    private Type createType(String name, TypeDTO dto, DTOPath path) {
        if (dto.extend() != null) {
            return newSimpleType(name, dto, path);
        }
        if (dto.list() != null) {
            return newListType(name, dto.list(), path.child(LIST, ModelMapCodec.toJson(dto.list())),
                    dto.projectId());
        }
        if (dto.fields() != null) {
            return newComplexType(name, dto.fields(), path.child(FIELDS, null), dto.projectId());
        }
        violations.add(new TypeRecursionViolation(path));
        return null;
    }

    private SimpleType newSimpleType(String name, TypeDTO dto, DTOPath path) {
        DTOPath extendsPath = path.child(EXTENDS, dto.extend());
        Type superType = getRegisteredType(dto.extend());
        if (!(superType instanceof SimpleType simpleSuperType)) {
            violations.add(new InvalidObjectViolation(extendsPath, TYPES, SUPERTYPES));
            return null;
        }
        Set<Constraint> constraints = toConstraints(dto.constraints(), path.child(CONSTRAINTS, null));
        return newSimpleTypeWithConstraints(name, simpleSuperType, constraints, dto.projectId());
    }

    private SimpleType newSimpleTypeWithConstraints(String name, SimpleType superType, Set<Constraint> constraints,
            String projectId) {
        if (constraints.isEmpty()) {
            return superType;
        }
        SimpleTypeImpl type = new SimpleTypeImpl(superType, name, projectId);
        type.addConstraints(constraints);
        constraints.forEach(c -> {
            if (c instanceof ObjectResolverConstraint orc) {
                type.setResolver(orc.getResolver());
            }
        });
        return type;
    }

    private Type newListType(String name, FieldDTO item, DTOPath itemPath, String projectId) {
        FieldTypeInfo fti = getFieldTypeInfo(item, itemPath);
        if (fti.type() == null) {
            violations.add(new InvalidObjectViolation(itemPath, TYPES, LIST));
            return null;
        }
        return new ListTypeImpl(name, fti.type(), ITEM, fti.defaultValue(), fti.constraints(), projectId);
    }

    private ComplexTypeImpl newComplexType(String name, Map<String, FieldDTO> fields, DTOPath fieldsPath,
            String projectId) {
        ComplexType superType = null; // actually not needed in our use cases
        ComplexTypeImpl ct = new ComplexTypeImpl(superType, name, "", projectId);
        addFields(ct, fields, fieldsPath);
        return ct;
    }

    private void addFields(ComplexTypeImpl ct, Map<String, FieldDTO> fields, DTOPath fieldsPath) {
        fields.forEach((name, field) -> addField(ct, name, field,
                fieldsPath.child(name, ModelMapCodec.toJson(field))));
    }

    private void addField(ComplexTypeImpl ct, String name, FieldDTO field, DTOPath path) {
        FieldTypeInfo fti = getFieldTypeInfo(field, path);
        if (fti.type() == null) {
            violations.add(new InvalidObjectViolation(path, FIELDS));
            return;
        }
        ct.addField(new FieldImpl(name, ct, fti.type(), fti.defaultValue(), fti.constraints(),
                field.fullTextIndexed()));
    }

    private record FieldTypeInfo( //
            Type type, //
            Set<Constraint> constraints, //
            String defaultValue) {
    }

    private FieldTypeInfo getFieldTypeInfo(FieldDTO field, DTOPath path) {
        Type type;
        if (field.typeRef() != null) {
            // a reference to a named type, however it was spelled
            type = getRegisteredType(field.typeRef());
        } else {
            // an anonymous type declared inline
            type = createAnonymousType(field, path);
        }
        Set<Constraint> constraints = toConstraints(field.constraints(), path.child(CONSTRAINTS, null));
        if (type instanceof PrimitiveType pt) {
            type = newSimpleTypeWithConstraints(type.getName(), pt, constraints, null);
        }
        return new FieldTypeInfo(type, constraints, field.defaultValue());
    }

    private Type createAnonymousType(FieldDTO field, DTOPath path) {
        if (field.list() != null) {
            return newListType(ANONYMOUS_TYPE, field.list(),
                    path.child(LIST, ModelMapCodec.toJson(field.list())), field.projectId());
        }
        if (field.fields() != null) {
            return newComplexType(ANONYMOUS_TYPE, field.fields(), path.child(FIELDS, null), field.projectId());
        }
        violations.add(new TypeRecursionViolation(path.anonymous()));
        return null;
    }

    /*
     * ===== Schemas =====
     */

    private void registerSchemas(Map<String, SchemaDTO> schemaDTOs) {
        schemaDTOs.forEach(this::registerSchema);
    }

    private void registerSchema(String name, SchemaDTO dto) {
        Schema schema = newSchema(name, dto);
        List<ValidationViolation> schemaViolations = new ArrayList<>(); // temporary diagnostic code
        registerSchema(schema, schemaViolations);
        if (!schemaViolations.isEmpty()) {
            // for now just log an error, to diagnose pre-existing issues
            schemaViolations.forEach(violation -> log.error(violation.getMessage(Locale.ENGLISH)));
        }
    }

    private SchemaImpl newSchema(String name, SchemaDTO dto) {
        Map<String, FieldDTO> fields = dto.fields();
        // the prefix is derived from the field names, not read from the DTO
        String prefix = "";
        if (!fields.isEmpty()) {
            String fieldName = fields.keySet().iterator().next();
            int i = fieldName.indexOf(PREFIX_SEPARATOR);
            prefix = i == -1 ? "" : fieldName.substring(0, i);
        }
        SchemaImpl schema = new SchemaImpl(name, prefix, dto.versionWritable(), dto.projectId());
        addFields(schema, fields, DTOPath.root(SCHEMAS, null).child(name, null).child(FIELDS, null));
        return schema;
    }

    private Schema getSchemaOrThrow(String name, DTOPath path) {
        Schema schema = getSchema(name);
        if (schema == null) {
            violations.add(new UnknownObjectViolation(path, SCHEMAS));
        }
        return schema;
    }

    /*
     * ===== Mixin Types =====
     */

    private void registerMixinTypes(Map<String, MixinTypeDTO> mixinDTOs) {
        mixinDTOs.forEach(this::registerMixinType);
    }

    private void registerMixinType(String name, MixinTypeDTO dto) {
        DTOPath path = DTOPath.root(MIXIN_TYPES, null).child(name, null);
        List<String> schemaNames = dto.schemas() == null ? List.of() : dto.schemas();
        DTOPath schemasPath = path.child(SCHEMAS, schemaNames);
        List<Schema> schemas = schemaNames.stream() //
                                          .map(schemaName -> getSchemaOrThrow(schemaName, schemasPath))
                                          .filter(Objects::nonNull) // Filter out incoming nulls from violations
                                          .toList();
        mixins.put(name, new CompositeTypeImpl(null, name, schemas, dto.projectId()));
        if (dto.noPerDocumentQuery()) {
            noPerDocumentQueryMixins.add(name);
        }
    }

    private CompositeType getMixinOrThrow(String name, DTOPath path) {
        CompositeType mixin = getMixin(name);
        if (mixin == null) {
            violations.add(new UnknownObjectViolation(path, MIXINS));
        }
        return mixin;
    }

    /*
     * ===== Primary Types =====
     */

    private Map<String, PrimaryTypeDTO> pendingPrimaryTypes;

    private void registerPrimaryTypes(Map<String, PrimaryTypeDTO> primaryTypeDTOs) {
        pendingPrimaryTypes = new LinkedHashMap<>(primaryTypeDTOs);
        // compute documentTypes and specialDocumentTypes
        while (!pendingPrimaryTypes.isEmpty()) {
            registerPendingDocumentType(pendingPrimaryTypes.keySet().iterator().next());
        }
    }

    private DocumentType getRegisteredDocumentType(String name) {
        // types already loaded
        DocumentType doctype = documentTypes.get(name);
        if (doctype != null) {
            return doctype;
        }
        // pending types
        return registerPendingDocumentType(name);
    }

    private DocumentType registerPendingDocumentType(String name) {
        PrimaryTypeDTO dto = pendingPrimaryTypes.remove(name);
        if (dto == null) {
            return null;
        }
        DocumentTypeImpl doctype = newDocumentType(name, dto);
        documentTypes.put(name, doctype);
        return doctype;
    }

    private DocumentTypeImpl newDocumentType(String name, PrimaryTypeDTO dto) {
        DTOPath path = DTOPath.root(PRIMARY_TYPES, null).child(name, null);
        DocumentType superType = null;
        if (!name.equals(SYS_CONTENT) && !name.equals(SYS_RELATION)) {
            if (dto.extend() == null) {
                violations.add(new ParentTypeValidationViolation(path.child(EXTENDS, null).withValue(name), name, true));
            } else {
                superType = getRegisteredDocumentType(dto.extend());
                if (superType == null) {
                    violations.add(new ParentTypeValidationViolation(path.child(EXTENDS, dto.extend()).withValue(name), name, false));
                }
            }
        }
        List<Schema> schemas = getNewDocumentTypeSchemas(dto, path, superType);
        List<CompositeType> docMixins = getNewDocumentTypeMixins(dto, path, superType);
        List<String> subtypes = dto.subtypes() == null ? null : new ArrayList<>(dto.subtypes());

        DocumentTypeImpl dt = new DocumentTypeImpl(name, superType, schemas, docMixins, dto.projectId());
        dt.setSubtypes(subtypes);
        return dt;
    }

    private List<Schema> getNewDocumentTypeSchemas(PrimaryTypeDTO dto, DTOPath path, DocumentType superType) {
        Set<String> schemaNames = new HashSet<>(dto.schemas() == null ? List.of() : dto.schemas());
        // add parent schemas
        if (superType != null) {
            schemaNames.addAll(superType.getSchemaNames());
        }
        // add mixin schemas
        if (dto.mixins() != null) {
            dto.mixins()
               .stream()
               .map(this::getMixin)
               .filter(Objects::nonNull)
               .forEach(mixin -> schemaNames.addAll(mixin.getSchemaNames()));
        }
        DTOPath schemasPath = path.child(SCHEMAS, dto.schemas());
        return schemaNames.stream() //
                          .map(schemaName -> getSchemaOrThrow(schemaName, schemasPath)) //
                          .filter(Objects::nonNull) // Filter out incoming nulls from violations
                          .toList();
    }

    private List<CompositeType> getNewDocumentTypeMixins(PrimaryTypeDTO dto, DTOPath path, DocumentType superType) {
        List<String> mixinNames = new ArrayList<>(dto.mixins() == null ? List.of() : dto.mixins());
        // add parent mixins
        if (superType != null) {
            mixinNames.addAll(superType.getMixins());
        }
        DTOPath mixinsPath = path.child(MIXINS, dto.mixins());
        return mixinNames.stream() //
                         .map(mixin -> getMixinOrThrow(mixin, mixinsPath)) //
                         .filter(Objects::nonNull) // Filter out incoming nulls from violations
                         .toList();
    }

    /*
     * ===== Constraints =====
     */

    private Set<Constraint> toConstraints(ConstraintsDTO dto, DTOPath path) {
        if (dto == null) {
            return Set.of();
        }
        Set<Constraint> constraints;
        try {
            constraints = dto.toConstraints();
        } catch (IllegalArgumentException e) {
            violations.add(new InvalidObjectViolation(path.withValue(dto.toMap()), PRIMARY_TYPES, CONSTRAINTS));
            return Set.of();
        }
        SchemaValidationUtils.validateConstraints(constraints, path.withValue(dto.toMap()), violations);
        return constraints;
    }
}
