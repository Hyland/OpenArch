/*
 * (C) Copyright 2023-2024 Hyland (http://hyland.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.core.schema;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.COMPLEX;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.CONSTRAINTS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.DEFAULT;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.EXTENDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FIELDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FULL_TEXT_INDEXED;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.ITEM;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.LIST;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXINS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXIN_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.NO_PER_DOCUMENT_QUERY;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PROJECT_ID;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SCHEMAS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.STRING;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SUBTYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SUPERTYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.VERSION_WRITABLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.PREFIX_SEPARATOR;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_RELATION;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintUtils;
import org.nuxeo.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import org.nuxeo.ecm.core.schema.validation.SchemaDTONode;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationException;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationUtils;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.TypeRecursionViolation;
import org.nuxeo.ecm.core.schema.validation.violations.UnknownObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema Manager implementation initialized through JSON DTO.
 */
public class SchemaManagerDTO extends SchemaManagerBase {

    private static final Logger log = LoggerFactory.getLogger(SchemaManagerDTO.class);

    private final List<ValidationViolation> violations;

    public SchemaManagerDTO(Map<String, Object> dto, List<ValidationViolation> violations, boolean validateOnly) {
        this.violations = violations == null ? new ArrayList<>() : violations;
        SchemaDTONode typeNode = new SchemaDTONode(dto, TYPES);
        SchemaDTONode schemaNode = new SchemaDTONode(dto, SCHEMAS);
        SchemaDTONode mixinTypeNode = new SchemaDTONode(dto, MIXIN_TYPES);
        SchemaDTONode primaryTypeNode = new SchemaDTONode(dto, PRIMARY_TYPES);
        registerTypes(typeNode);
        registerSchemas(schemaNode);
        registerMixinTypes(mixinTypeNode);
        registerPrimaryTypes(primaryTypeNode);
        computeDenormalizations();
        checkValidParents();
        ValidationReport.checkViolations(this.violations, !validateOnly, SchemaValidationException::new);
    }

    public SchemaManagerDTO(Map<String, Object> dto) {
        this(dto, new ArrayList<>(), false);
    }

    /*
     * ===== Types =====
     */
    private SchemaDTONode pendingTypeDTONodes;

    private void registerTypes(SchemaDTONode typeNode) {
        pendingTypeDTONodes = typeNode;
        if (!typeNode.isMap()) {
            violations.add(new InvalidObjectViolation(typeNode));
            return;
        }
        while (!pendingTypeDTONodes.asMap().isEmpty()) {
            String name = pendingTypeDTONodes.asMap().keySet().iterator().next();
            registerPendingType(name);
        }
    }

    // may return null if not found
    private Type registerPendingType(String name) {
        SchemaDTONode childNode = pendingTypeDTONodes.get(name);
        if (pendingTypeDTONodes.isMap() && pendingTypeDTONodes.asMap().remove(name) == null) {
            return null;
        }
        if (!childNode.isMap()) {
            violations.add(new InvalidObjectViolation(childNode, TYPES));
            return null;
        }
        Type type = createType(childNode);
        if (type != null) {
            types.put(childNode.getKey(), type);
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

    private Type createType(SchemaDTONode node) {
        Type type;
        String projectId = getProjectId(node);
        if (node.containsKey(EXTENDS)) {
            type = newSimpleType(node.get(EXTENDS), projectId);
        } else if (node.containsKey(LIST)) {
            type = newListType(node.get(LIST), projectId);
        } else if (node.containsKey(FIELDS)) {
            type = newComplexType(node.get(FIELDS), projectId);
        } else {
            violations.add(new TypeRecursionViolation(node));
            return null;
        }
        return type;
    }

    private SimpleType newSimpleType(SchemaDTONode node, String projectId) {
        if (!(node.getNode() instanceof String st)) {
            violations.add(new InvalidObjectViolation(node, TYPES, SUPERTYPES, STRING));
            return null;
        }
        Type t = getRegisteredType(st);
        if (!(t instanceof SimpleType superType)) {
            violations.add(new InvalidObjectViolation(node, TYPES, SUPERTYPES));
            return null;
        }
        Set<Constraint> constraints = constraintsFromDTO(node.getParentNode().get(CONSTRAINTS));
        return newSimpleTypeWithConstraints(node.getParentNode().getKey(), superType, constraints, projectId);
    }

    private SimpleType newSimpleTypeWithConstraints(String name, SimpleType superType, Set<Constraint> constraints, String projectId) {
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

    private Type newListType(SchemaDTONode node, String projectId) {
        FieldTypeInfo fti = getFieldTypeInfo(node);
        if (fti == null) {
            return null; // We have already found a violation, no need to continue
        }
        Type type = fti.type();
        if (type == null) {
            violations.add(new InvalidObjectViolation(node, TYPES, LIST));
            return type;
        }
        return new ListTypeImpl(node.getParentNode().getKey(), type, ITEM, fti.defaultValue(),
                fti.constraints(), projectId);
    }

    private ComplexTypeImpl newComplexType(SchemaDTONode node, String projectId) {
        if (node.getNode() == null || !node.isMap()) {
            violations.add(new InvalidObjectViolation(node, TYPES, COMPLEX));
            return null;
        }

        ComplexType syperType = null; // actually not needed in our use cases
        ComplexTypeImpl ct = new ComplexTypeImpl(syperType, node.getParentNode().getKey(), "", projectId);
        addFields(ct, node);
        return ct;
    }

    private void addFields(ComplexTypeImpl ct, SchemaDTONode node) {
        node.asMap().keySet().forEach(name -> addField(ct, node.get(name)));
    }

    private void addField(ComplexTypeImpl ct, SchemaDTONode node) {
        FieldTypeInfo fti = getFieldTypeInfo(node);
        if (fti == null) {
            return; // We've already discovered a violation, no need to continue
        }
        Type type = fti.type();
        if (type == null) {
            violations.add(new InvalidObjectViolation(node, FIELDS));
            return;
        }
        var fullTextIndexedNode = node.get(FULL_TEXT_INDEXED);
        var fullTextIndexed = false;
        if (fullTextIndexedNode != null && fullTextIndexedNode.getNode() instanceof Boolean fullTextIndexedValue) {
            fullTextIndexed = fullTextIndexedValue;
        }

        FieldImpl field = new FieldImpl(node.getKey(), ct, type, fti.defaultValue(), fti.constraints(), fullTextIndexed);
        ct.addField(field);
    }

    private record FieldTypeInfo( //
            Type type, //
            Set<Constraint> constraints, //
            String defaultValue) {

        public static FieldTypeInfo ofType(Type type) {
            return new FieldTypeInfo(type, Set.of(), null);
        }
    }

    private FieldTypeInfo getFieldTypeInfo(SchemaDTONode node) {
        if (node.getNode() instanceof String typeName) {
            return getStringFieldTypeInfo(typeName);
        } else if (node.isMap()) {
            Map<String, Object> dto = node.asMap();
            if (dto.containsKey("type") && dto.get("type") != null) {
                String typeName = (String) dto.get("type");
                return getStringFieldTypeInfo(typeName);
            }
            Type type;
            if (dto.containsKey(EXTENDS)) {
                SchemaDTONode extendsNode = node.get(EXTENDS);
                if (extendsNode.getNode() instanceof String extendz) {
                    type = getRegisteredType(extendz);
                } else {
                    violations.add(new InvalidObjectViolation(extendsNode));
                    return null;
                }
            } else {
                // anonymous type
                type = createType(node.createAnonymousNode());
            }
            return getDefaultFieldTypeInfo(node, type);
        } else {
            violations.add(new InvalidObjectViolation(node));
            return null;
        }
    }

    private FieldTypeInfo getDefaultFieldTypeInfo(SchemaDTONode node, Type type) {
        Set<Constraint> constraints = constraintsFromDTO(node.get(CONSTRAINTS));
        String defaultValue = (String) node.get(DEFAULT).getNode();
        if (type instanceof PrimitiveType pt) {
            type = newSimpleTypeWithConstraints(type.getName(), pt, constraints, null);
        }
        return new FieldTypeInfo(type, constraints, defaultValue);
    }

    private FieldTypeInfo getStringFieldTypeInfo(String name) {
        Type type = getRegisteredType(name);
        return FieldTypeInfo.ofType(type);
    }

    /*
     * ===== Schemas =====
     */

    private void registerSchemas(SchemaDTONode schemaDTOs) {
        schemaDTOs.asMap().keySet().forEach(key -> registerSchema(schemaDTOs.get(key)));
    }

    private void registerSchema(SchemaDTONode node) {
        if (node.getNode() == null || !node.isMap()) {
            violations.add(new InvalidObjectViolation(node, SCHEMAS));
            return;
        }
        Schema schema = newSchema(node);
        if (schema != null) {
            List<ValidationViolation> violations = new ArrayList<>(); // temporary diagnostic code
            registerSchema(schema, violations);
            if (!violations.isEmpty()) {
                // for now just log an error, to diagnose pre-existing issues
                violations.forEach(violation -> log.error(violation.getMessage(Locale.ENGLISH)));
            }
        }
    }

    private SchemaImpl newSchema(SchemaDTONode node) {
        String name = node.getKey();
        Map<String, Object> dto = node.asMap();
        SchemaDTONode fieldsNode = node.get(FIELDS);
        if (fieldsNode.getNode() == null || !fieldsNode.isMap()) {
            violations.add(new InvalidObjectViolation(fieldsNode, FIELDS, COMPLEX));
            return null;
        }

        String prefix;
        if (!fieldsNode.asMap().isEmpty()) {
            String fieldName = fieldsNode.asMap().keySet().iterator().next();
            int i = fieldName.indexOf(PREFIX_SEPARATOR);
            prefix = i == -1 ? "" : fieldName.substring(0, i);
        } else {
            prefix = "";
        }
        boolean versionWritable = TRUE.equals(dto.get(VERSION_WRITABLE));

        String projectId = getProjectId(node);
        SchemaImpl schema = new SchemaImpl(name, prefix, versionWritable, projectId);
        addFields(schema, fieldsNode);
        return schema;
    }

    private Schema getSchemaOrThrow(String name, SchemaDTONode node) {
        Schema schema = getSchema(name);
        if (schema == null) {
            violations.add(new UnknownObjectViolation(node, SCHEMAS));
        }
        return schema;
    }

    /*
     * ===== Mixin Types =====
     */

    private void registerMixinTypes(SchemaDTONode mixinDTOs) {
        mixinDTOs.asMap().keySet().forEach(key -> registerMixinType(mixinDTOs.get(key)));
    }

    private void registerMixinType(SchemaDTONode node) {
        String name = node.getKey();
        if (node.getNode() == null || !node.isMap()) {
            violations.add(new InvalidObjectViolation(node, MIXINS, TYPES));
            return;
        }

        CompositeTypeImpl ct = newCompositeType(node);
        if (ct == null) {
            // We've discovered a violation, no need to continue
            return;
        }
        mixins.put(ct.getName(), ct);
        boolean noPerDocumentQuery = TRUE.equals(node.asMap().get(NO_PER_DOCUMENT_QUERY));
        if (noPerDocumentQuery) {
            noPerDocumentQueryMixins.add(name);
        }
    }

    private CompositeTypeImpl newCompositeType(SchemaDTONode node) {
        String name = node.getKey();
        final SchemaDTONode schemasNode = node.get(SCHEMAS);
        List<String> schemaNames;
        if (schemasNode.getNode() != null) {
            if (!schemasNode.isList()) {
                violations.add(new InvalidObjectViolation(schemasNode, MIXINS, TYPES));
                return null;
            }
            schemaNames = schemasNode.asList();
        } else {
            schemaNames = List.of();
        }
        List<Schema> schemas = schemaNames.stream() //
                                          .map(schemaName -> getSchemaOrThrow(schemaName, schemasNode))
                                          .filter(Objects::nonNull) // Filter out incoming nulls from violations
                                          .toList();
        String projectId = getProjectId(node);
        return new CompositeTypeImpl(null, name, schemas, projectId);
    }

    private CompositeType getMixinOrThrow(String name, SchemaDTONode node) {
        CompositeType mixin = getMixin(name);
        if (mixin == null) {
            violations.add(new UnknownObjectViolation(node, MIXINS));
        }
        return mixin;
    }

    /*
     * ===== Primary Types =====
     */

    private SchemaDTONode pendingPrimaryTypeDTOs;

    void checkValidParents() {
        var docTypes = this.documentTypes;
        for (DocumentType docType : docTypes.values()) {
            if (docType.getName().equals(SYS_CONTENT) || docType.getName().equals(SYS_RELATION)) {
                continue; // root types have no super type
            }
            if (docType.getSuperType() == null) {
                log.warn("Document type {} has no super type", docType.getName());
            }
        }
    }

    private void registerPrimaryTypes(SchemaDTONode typeNode) {
        pendingPrimaryTypeDTOs = typeNode;
        if (!typeNode.isMap()) {
            violations.add(new InvalidObjectViolation(typeNode));
            return;
        }
        // compute documentTypes and specialDocumentTypes
        while (!pendingPrimaryTypeDTOs.asMap().isEmpty()) {
            String name = pendingPrimaryTypeDTOs.asMap().keySet().iterator().next();
            registerPendingDocumentType(name);
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
        SchemaDTONode typeNode = pendingPrimaryTypeDTOs.get(name);
        if (pendingPrimaryTypeDTOs.isMap() && pendingPrimaryTypeDTOs.asMap().remove(name) == null) {
            return null;
        }
        if (!typeNode.isMap()) {
            violations.add(new InvalidObjectViolation(typeNode, PRIMARY_TYPES));
            return null;
        }

        DocumentTypeImpl doctype = newDocumentType(typeNode);
        documentTypes.put(name, doctype);
        return doctype;
    }

    private DocumentTypeImpl newDocumentType(SchemaDTONode node) {
        DocumentType superType = getNewDocumentTypeSuperType(node);

        List<Schema> schemas = getNewDocumentTypeSchemas(node, superType);

        List<CompositeType> mixins = getNewDocumentTypeMixins(node, superType);

        List<String> subtypes = getNewDocumentTypeSubTypes(node);

        String projectId = getProjectId(node);
        DocumentTypeImpl dt = new DocumentTypeImpl(node.getKey(), superType, schemas, mixins, projectId);
        dt.setSubtypes(subtypes);
        return dt;
    }

    private DocumentType getNewDocumentTypeSuperType(SchemaDTONode node) {
        DocumentType superType;
        SchemaDTONode extendsNode = node.get(EXTENDS);
        if (extendsNode.getNode() != null) {
            if (!(extendsNode.getNode() instanceof String extendz)) {
                violations.add(new InvalidObjectViolation(extendsNode, PRIMARY_TYPES, EXTENDS));
                superType = null;
            } else {
                superType = getRegisteredDocumentType(extendz);
            }
        } else {
            superType = null;
        }
        return superType;
    }

    private List<Schema> getNewDocumentTypeSchemas(SchemaDTONode node, DocumentType superType) {
        Set<String> schemaNames;
        final SchemaDTONode schemasNode = node.get(SCHEMAS);
        if (schemasNode.getNode() != null) {
            if (!schemasNode.isList()) {
                violations.add(new InvalidObjectViolation(schemasNode, PRIMARY_TYPES, SCHEMAS));
                schemaNames = new HashSet<>();
            } else {
                schemaNames = new HashSet<>(schemasNode.asList());
            }
        } else {
            schemaNames = new HashSet<>();
        }
        // add parent schemas
        if (superType != null) {
            schemaNames.addAll(superType.getSchemaNames());
        }
        // add mixin schemas
        final SchemaDTONode mixinsNode = node.get(MIXINS);
        if (mixinsNode != null && mixinsNode.isList()) {
            mixinsNode.asList()
                      .stream()
                      .map(this::getMixin)
                      .filter(Objects::nonNull)
                      .forEach(mixin -> schemaNames.addAll(mixin.getSchemaNames()));
        }
        return schemaNames.stream() //
                          .map(schemaName -> getSchemaOrThrow(schemaName, schemasNode)) //
                          .filter(Objects::nonNull) // Filter out incoming nulls from violations
                          .toList();
    }

    private List<CompositeType> getNewDocumentTypeMixins(SchemaDTONode node, DocumentType superType) {
        List<String> mixins;
        SchemaDTONode mixinsNode = node.get(MIXINS);
        if (mixinsNode.getNode() != null) {
            if (!mixinsNode.isList()) {
                violations.add(new InvalidObjectViolation(mixinsNode, PRIMARY_TYPES, MIXINS));
                mixins = new ArrayList<>();
            } else {
                mixins = new ArrayList<>(mixinsNode.asList());
            }
        } else {
            mixins = new ArrayList<>();
        }
        // add parent mixins
        if (superType != null) {
            mixins.addAll(superType.getMixins());
        }
        return mixins.stream() //
                     .map(mixin -> getMixinOrThrow(mixin, mixinsNode)) //
                     .filter(Objects::nonNull) // Filter out incoming nulls from violations
                     .toList();
    }

    private List<String> getNewDocumentTypeSubTypes(SchemaDTONode node) {
        List<String> subtypes;
        SchemaDTONode subtypeNode = node.get(SUBTYPES);
        if (subtypeNode.getNode() != null) {
            if (!subtypeNode.isList()) {
                violations.add(new InvalidObjectViolation(subtypeNode, PRIMARY_TYPES, SUBTYPES));
                subtypes = null;
            } else {
                subtypes = new ArrayList<>(subtypeNode.asList());
            }
        } else {
            subtypes = null;
        }
        return subtypes;
    }

    private String getProjectId(SchemaDTONode node) {
        if (node.containsKey(PROJECT_ID)) {
            if (node.get(PROJECT_ID).getNode() instanceof String projectId) {
                return projectId;
            } else {
                violations.add(new InvalidObjectViolation(node, PROJECT_ID));
            }
        }
        return null;
    }

    private Set<Constraint> constraintsFromDTO(SchemaDTONode node) {
        Set<Constraint> constraints;
        try {
            constraints = ConstraintUtils.constraintsFromDTO(node.getNode());
        } catch (IllegalArgumentException e) {
            violations.add(new InvalidObjectViolation(node, PRIMARY_TYPES, CONSTRAINTS));
            return Set.of();
        }
        SchemaValidationUtils.validateConstraints(constraints, node, violations);
        return constraints;
    }
}
