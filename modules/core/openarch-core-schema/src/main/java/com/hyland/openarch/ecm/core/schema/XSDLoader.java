/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema;

import static com.sun.xml.xsom.XSFacet.FACET_ENUMERATION;
import static com.sun.xml.xsom.XSFacet.FACET_LENGTH;
import static com.sun.xml.xsom.XSFacet.FACET_MAXEXCLUSIVE;
import static com.sun.xml.xsom.XSFacet.FACET_MAXINCLUSIVE;
import static com.sun.xml.xsom.XSFacet.FACET_MAXLENGTH;
import static com.sun.xml.xsom.XSFacet.FACET_MINEXCLUSIVE;
import static com.sun.xml.xsom.XSFacet.FACET_MININCLUSIVE;
import static com.sun.xml.xsom.XSFacet.FACET_MINLENGTH;
import static com.sun.xml.xsom.XSFacet.FACET_PATTERN;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.BLOB_TYPE;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.ecm.core.schema.types.ComplexType;
import com.hyland.openarch.ecm.core.schema.types.ComplexTypeImpl;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.ValueSource;
import com.hyland.openarch.ecm.core.schema.types.ListType;
import com.hyland.openarch.ecm.core.schema.types.ListTypeImpl;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.ecm.core.schema.types.SchemaImpl;
import com.hyland.openarch.ecm.core.schema.types.SimpleType;
import com.hyland.openarch.ecm.core.schema.types.SimpleTypeImpl;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.TypeBindingException;
import com.hyland.openarch.ecm.core.schema.types.TypeException;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.DateIntervalConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.EnumConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.LengthConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.NotNullConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.PatternConstraint;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolverService;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.runtime.api.Framework;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.xml.xsom.ForeignAttributes;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSListSimpleType;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.XmlString;
import com.sun.xml.xsom.impl.RestrictionSimpleTypeImpl;
import com.sun.xml.xsom.parser.XSOMParser;

/**
 * Loader of XSD schemas into OpenArch Schema objects.
 */
public class XSDLoader {

    private static final String ATTR_CORE_EXTERNAL_REFERENCES = "resolver";

    private static final Logger log = LogManager.getLogger(XSDLoader.class);

    private static final String ANONYMOUS_TYPE_SUFFIX = "#anonymousType";

    private static final String NAMESPACE_CORE_VALIDATION = "http://www.nuxeo.org/ecm/schemas/core/validation/";

    private static final String NAMESPACE_CORE_EXTERNAL_REFERENCES = "http://www.nuxeo.org/ecm/schemas/core/external-references/";

    private static final String NAMESPACE_CORE_MANAGED = "http://www.nuxeo.org/ecm/schemas/core/managed/";

    private static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";

    protected final SchemaManagerImpl schemaManager;

    protected List<String> referencedXSD = new ArrayList<>();

    protected boolean collectReferencedXSD = false;

    protected SchemaBindingDescriptor sd;

    public XSDLoader(SchemaManagerImpl schemaManager) {
        this.schemaManager = schemaManager;
    }

    public XSDLoader(SchemaManagerImpl schemaManager, SchemaBindingDescriptor sd) {
        this.schemaManager = schemaManager;
        this.sd = sd;
    }

    // used by Studio, do not remove
    public XSDLoader(SchemaManagerImpl schemaManager, boolean collectReferencedXSD) {
        this.schemaManager = schemaManager;
        this.collectReferencedXSD = collectReferencedXSD;
    }

    protected void registerSchema(Schema schema) throws TypeException {
        List<ValidationViolation> violations = new ArrayList<>();
        schemaManager.registerSchema(schema, violations);
        if (!violations.isEmpty()) {
            throw new TypeException(violations.get(0).getMessage(Locale.ENGLISH));
        }
    }

    protected void registerType(Type type) {
        schemaManager.registerType(type);
    }

    protected void unregisterType(Type type) {
        schemaManager.unregisterType(type);
    }

    protected Type getType(String name) {
        return schemaManager.getType(name);
    }

    protected XSOMParser getParser() {
        XSOMParser parser = new XSOMParser();
        ErrorHandler errorHandler = new SchemaErrorHandler();
        parser.setErrorHandler(errorHandler);
        if (sd != null) {
            parser.setEntityResolver(new NXSchemaResolver(schemaManager, sd));
        }
        return parser;
    }

    protected static class NXSchemaResolver implements EntityResolver {

        protected SchemaManagerImpl schemaManager;

        protected SchemaBindingDescriptor sd;

        NXSchemaResolver(SchemaManagerImpl schemaManager, SchemaBindingDescriptor sd) {
            this.schemaManager = schemaManager;
            this.sd = sd;
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws IOException {

            String[] parts = systemId.split("/" + SchemaManagerImpl.SCHEMAS_DIR_NAME + "/");
            String importXSDSubPath = parts[1];

            File xsd = new File(schemaManager.getSchemasDir(), importXSDSubPath);
            if (!xsd.exists()) {
                var path = Path.of(sd.src.getPath());
                importXSDSubPath = path.getParent().resolve(importXSDSubPath).toString();
                Context ctx = sd.src.getContext();
                URL url = ctx.getResource(importXSDSubPath);
                if (url != null) {
                    return new InputSource(url.openStream());
                }
            }

            return null;
        }

    }

    protected static class SchemaErrorHandler implements ErrorHandler {
        @Override
        public void error(SAXParseException e) throws SAXException {
            log.error("Error: {}", e.getMessage());
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            log.error("FatalError: {}", e.getMessage());
            throw e;
        }

        @Override
        public void warning(SAXParseException e) {
            log.error("Warning: {}", e.getMessage());
        }
    }

    /**
     * @param isVersionWritable if true, the schema's fields will be writable even for Version document.
     * @since 8.4
     */
    public Schema loadSchema(String name, String prefix, File file, boolean isVersionWritable)
            throws SAXException, IOException, TypeException {
        XSOMParser parser = getParser();
        String systemId = file.toURI().toURL().toExternalForm();
        if (file.getPath().startsWith("\\\\")) { // Windows UNC share
            // work around a bug in Xerces due to
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5086147
            // (xsom passes a systemId of the form file://server/share/...
            // but this is not parsed correctly when turned back into
            // a File object inside Xerces)
            systemId = systemId.replace("file://", "file:////");
        }
        try {
            parser.parse(systemId);
        } catch (SAXParseException e) {
            throw new SAXException("Error parsing schema: " + systemId, e);
        }

        XSSchemaSet xsSchemas = parser.getResult();
        if (collectReferencedXSD) {
            collectReferencedXSD(xsSchemas);
        }
        return loadSchema(name, prefix, xsSchemas, isVersionWritable);
    }

    protected void collectReferencedXSD(XSSchemaSet xsSchemas) {

        Collection<XSSchema> schemas = xsSchemas.getSchemas();
        String ns;
        for (XSSchema s : schemas) {
            ns = s.getTargetNamespace();
            if (ns.length() <= 0 || ns.equals(NS_XSD)) {
                continue;
            }

            String systemId = s.getLocator().getSystemId();
            if (systemId != null && systemId.startsWith("file:/")) {
                String filePath = systemId.substring(6);
                if (!referencedXSD.contains(filePath)) {
                    referencedXSD.add(filePath);
                }
            }
        }

    }

    /**
     * Create OpenARch schema from a XSD resource.
     *
     * @since 5.7
     * @param name schema name
     * @param prefix schema prefix
     * @param url url to load the XSD resource
     * @since 5.7
     */
    public Schema loadSchema(String name, String prefix, URL url) throws SAXException, TypeException {
        XSOMParser parser = getParser();
        parser.parse(url);
        XSSchemaSet xsSchemas = parser.getResult();
        return loadSchema(name, prefix, xsSchemas, false);
    }

    protected Schema loadSchema(String name, String prefix, XSSchemaSet schemaSet, boolean isVersionWritable)
            throws TypeException {
        if (schemaSet == null) {
            return null;
        }
        Collection<XSSchema> schemas = schemaSet.getSchemas();
        XSSchema schema = null;
        for (XSSchema s : schemas) {
            String ns = s.getTargetNamespace();
            if (ns.length() > 0 && !ns.equals(NS_XSD)) {
                schema = s;
                break;
            }
        }
        if (schema == null) {
            return null;
        }
        Schema ecmSchema = new SchemaImpl(name, prefix, isVersionWritable, null);

        // load elements
        Collection<XSElementDecl> elements = schema.getElementDecls().values();
        for (XSElementDecl el : elements) {
            // register the type if not yet registered
            Type ecmType = loadType(ecmSchema, el.getType(), el.getName());
            if (ecmType != null) {
                // add the field to the schema
                createField(ecmSchema, el, ecmType);
            } else {
                log.warn("Failed to load field {} : {}", el.getName(), el.getType());
            }
        }

        registerSchema(ecmSchema);
        return ecmSchema;
    }

    /**
     * @param schema the OpenArch schema into we register the type.
     * @param type the XSD type to load
     * @param fieldName the field name owning this type, this is used when type is anonymous/local
     * @return the loaded type
     */
    protected Type loadType(Schema schema, XSType type, String fieldName) throws TypeBindingException {
        // look into builtin types
        String typeName = type.getName();
        Type ecmType = XSDTypes.getBuiltinType(typeName);
        if (ecmType != null) {
            return ecmType;
        }
        // maybe an alias to a primitive type?
        if (type.getTargetNamespace().equals(NS_XSD)) {
            ecmType = XSDTypes.getXSDType(typeName);
            if (ecmType == null) {
                log.warn("Cannot use unknown XSD type: {}", typeName);
            }
            return ecmType;
        }
        // look into user types for this schema
        String name = getTypeName(type, fieldName, schema);
        ecmType = getType(name);
        if (ecmType != null) {
            return ecmType;
        }
        if (type.isSimpleType()) {
            if (type instanceof XSListSimpleType xsListSimpleType) {
                ecmType = loadListType(schema, xsListSimpleType, fieldName);
            } else {
                ecmType = loadSimpleType(schema, type, fieldName);
            }
        } else {
            ecmType = loadComplexType(schema, name, type.asComplexType());
        }
        if (ecmType != null) {
            if (!type.isLocal()) {
                registerType(ecmType);
            }
        } else {
            log.warn("loadType for {} of {} returns null", fieldName, type);
        }
        return ecmType;
    }

    /**
     * @param name the type name (note, the type may have a null name if an anonymous type)
     */
    protected Type loadComplexType(Schema schema, String name, XSType type) throws TypeBindingException {
        XSType baseType = type.getBaseType();
        ComplexType superType = null;
        // the anyType is the basetype of itself
        if (baseType.getBaseType() != baseType) { // have a base type
            if (baseType.isComplexType()) {
                superType = (ComplexType) loadType(schema, baseType, name);
            } else {
                log.warn("Complex type has a non complex type super type???");
            }
        }
        XSComplexType xsct = type.asComplexType();
        // try to get the delta content
        XSContentType content = xsct.getExplicitContent();
        // if none get the entire content
        if (content == null) {
            content = xsct.getContentType();
        }
        return createComplexType(schema, superType, name, content, xsct.isAbstract());
    }

    protected SimpleType loadSimpleType(Schema schema, XSType type, String fieldName) throws TypeBindingException {
        String name = getTypeName(type, fieldName, schema);
        XSType baseType = type.getBaseType();
        SimpleType superType = null;
        if (baseType != type) {
            // have a base type
            superType = (SimpleType) loadType(schema, baseType, fieldName);
        }
        SimpleTypeImpl simpleType = new SimpleTypeImpl(superType, name);

        // add constraints/restrictions to the simple type
        if (type instanceof RestrictionSimpleTypeImpl restrictionType) {
            List<Constraint> constraints = new ArrayList<>();

            // pattern
            XSFacet patternFacet = restrictionType.getFacet(FACET_PATTERN);
            if (patternFacet != null) {
                if (simpleType.getPrimitiveType().support(PatternConstraint.class)) {
                    // String pattern
                    String pattern = patternFacet.getValue().toString();
                    Constraint constraint = new PatternConstraint(pattern);
                    constraints.add(constraint);
                } else {
                    logUnsupportedFacetRestriction(schema, fieldName, simpleType, FACET_PATTERN);
                }
            }

            // length
            XSFacet minLengthFacet = restrictionType.getFacet(FACET_MINLENGTH);
            XSFacet maxLengthFacet = restrictionType.getFacet(FACET_MAXLENGTH);
            XSFacet lengthFacet = restrictionType.getFacet(FACET_LENGTH);
            if (maxLengthFacet != null || minLengthFacet != null || lengthFacet != null) {
                if (simpleType.getPrimitiveType().support(LengthConstraint.class)) {
                    // String Length
                    Object min = null;
                    Object max = null;
                    if (lengthFacet != null) {
                        min = lengthFacet.getValue().toString();
                        max = min;
                    } else {
                        if (minLengthFacet != null) {
                            min = minLengthFacet.getValue().toString();
                        }
                        if (maxLengthFacet != null) {
                            max = maxLengthFacet.getValue().toString();
                        }
                    }
                    Constraint constraint = new LengthConstraint(min, max);
                    constraints.add(constraint);
                } else {
                    logUnsupportedFacetRestriction(schema, fieldName, simpleType, FACET_MINLENGTH, FACET_MAXLENGTH,
                            FACET_LENGTH);
                }
            }

            // Intervals
            XSFacet minExclusiveFacet = restrictionType.getFacet(FACET_MINEXCLUSIVE);
            XSFacet minInclusiveFacet = restrictionType.getFacet(FACET_MININCLUSIVE);
            XSFacet maxExclusiveFacet = restrictionType.getFacet(FACET_MAXEXCLUSIVE);
            XSFacet maxInclusiveFacet = restrictionType.getFacet(FACET_MAXINCLUSIVE);
            if (minExclusiveFacet != null || minInclusiveFacet != null || maxExclusiveFacet != null
                    || maxInclusiveFacet != null) {
                if (simpleType.getPrimitiveType().support(NumericIntervalConstraint.class)) {
                    // Numeric Interval
                    String min = null;
                    String max = null;
                    boolean includingMin = true;
                    boolean includingMax = true;
                    if (minExclusiveFacet != null) {
                        min = minExclusiveFacet.getValue().toString();
                        includingMin = false;
                    } else if (minInclusiveFacet != null) {
                        min = minInclusiveFacet.getValue().toString();
                        includingMin = true;
                    }
                    if (maxExclusiveFacet != null) {
                        max = maxExclusiveFacet.getValue().toString();
                        includingMax = false;
                    } else if (maxInclusiveFacet != null) {
                        max = maxInclusiveFacet.getValue().toString();
                        includingMax = true;
                    }
                    Constraint constraint = new NumericIntervalConstraint(min, includingMin, max, includingMax);
                    constraints.add(constraint);
                } else if (simpleType.getPrimitiveType().support(DateIntervalConstraint.class)) {
                    // Date Interval
                    String min = null;
                    String max = null;
                    boolean includingMin = true;
                    boolean includingMax = true;
                    if (minExclusiveFacet != null) {
                        min = minExclusiveFacet.getValue().toString();
                        includingMin = false;
                    }
                    if (minInclusiveFacet != null) {
                        min = minInclusiveFacet.getValue().toString();
                        includingMin = true;
                    }
                    if (maxExclusiveFacet != null) {
                        max = maxExclusiveFacet.getValue().toString();
                        includingMax = false;
                    }
                    if (maxInclusiveFacet != null) {
                        max = maxInclusiveFacet.getValue().toString();
                        includingMax = true;
                    }
                    Constraint constraint = new DateIntervalConstraint(min, includingMin, max, includingMax);
                    constraints.add(constraint);
                } else {
                    logUnsupportedFacetRestriction(schema, fieldName, simpleType, FACET_MINEXCLUSIVE,
                            FACET_MININCLUSIVE, FACET_MAXEXCLUSIVE, FACET_MAXINCLUSIVE);
                }
            }

            // Enumeration
            List<XSFacet> enumFacets = restrictionType.getFacets("enumeration");
            if (enumFacets != null && !enumFacets.isEmpty()) {
                if (simpleType.getPrimitiveType().support(EnumConstraint.class)) {
                    // string enumeration
                    List<String> enumValues = new ArrayList<>();
                    for (XSFacet enumFacet : enumFacets) {
                        enumValues.add(enumFacet.getValue().toString());
                    }
                    Constraint constraint = new EnumConstraint(enumValues);
                    constraints.add(constraint);
                } else {
                    logUnsupportedFacetRestriction(schema, fieldName, simpleType, FACET_ENUMERATION);
                }
            }

            String refName = restrictionType.getForeignAttribute(NAMESPACE_CORE_EXTERNAL_REFERENCES,
                    ATTR_CORE_EXTERNAL_REFERENCES);
            Map<String, String> refParameters = new HashMap<>();
            for (ForeignAttributes attr : restrictionType.getForeignAttributes()) {
                for (int index = 0; index < attr.getLength(); index++) {
                    String attrNS = attr.getURI(index);
                    String attrName = attr.getLocalName(index);
                    String attrValue = attr.getValue(index);
                    if (NAMESPACE_CORE_EXTERNAL_REFERENCES.equals(attrNS) && !ATTR_CORE_EXTERNAL_REFERENCES.equals(attrName)) {
                        refParameters.put(attrName, attrValue);
                    }
                }
            }
            if (refName != null) {
                ObjectResolver resolver = Framework.getService(ObjectResolverService.class)
                                                   .getResolver(refName, refParameters);
                if (resolver != null) {
                    simpleType.setResolver(resolver);
                    constraints.add(new ObjectResolverConstraint(resolver));
                } else {
                    log.info("type of {} | {} targets ObjectResolver namespace but has no matching resolver registered (please contribute to component : com.hyland.openarch.ecm.core.schema.ObjectResolverService)" ,
                            fieldName, type.getName());
                }
            }

            simpleType.addConstraints(constraints);
        }

        return simpleType;
    }

    private void logUnsupportedFacetRestriction(Schema schema, String fieldName, SimpleTypeImpl simpleType,
            String... facetNames) {
        StringBuilder msg = new StringBuilder();
        msg.append("schema|field|type : ").append(schema.getName());
        msg.append("|").append(fieldName);
        msg.append("|").append(simpleType.getPrimitiveType());
        msg.append(" following restriction facet are not handled by constraints API for this type :");
        for (String facetName : facetNames) {
            msg.append(facetName).append(" ");
        }
        log.warn(msg);
    }

    protected ListType loadListType(Schema schema, XSListSimpleType type, String fieldName)
            throws TypeBindingException {
        String name = getTypeName(type, fieldName, schema);
        XSType xsItemType = type.getItemType();
        Type itemType;
        if (xsItemType.getTargetNamespace().equals(NS_XSD)) {
            itemType = XSDTypes.getXSDType(xsItemType.getName());
        } else {
            itemType = loadSimpleType(schema, xsItemType, null);
        }
        if (itemType == null) {
            log.error("list item type was not defined -> you should define first the item type");
            return null;
        }
        return new ListTypeImpl(name, itemType);
    }

    protected Type createComplexType(Schema schema, ComplexType superType, String name, XSContentType content,
            boolean abstractType) throws TypeBindingException {

        ComplexType ct = new ComplexTypeImpl(superType, name);

        // to allow for recursive references, temporarily register the type
        registerType(ct);
        try {
            XSParticle particle = content.asParticle();
            if (particle == null) {
                // complex type without particle -> maybe it contains only attributes -> return it as is
                return ct;
            }
            XSTerm term = particle.getTerm();
            XSModelGroup mg = term.asModelGroup();
            return processModelGroup(schema, superType, name, ct, mg, abstractType);
        } finally {
            unregisterType(ct);
        }
    }

    protected Type processModelGroup(Schema schema, ComplexType superType, String name, ComplexType ct, XSModelGroup mg,
            boolean abstractType) throws TypeBindingException {
        if (mg == null) {
            // TODO don't know how to handle this for now
            throw new TypeBindingException("unsupported complex type");
        }
        XSParticle[] group = mg.getChildren();
        if (group.length == 0) {
            return null;
        }
        if (group.length == 1 && superType == null && group[0].isRepeated() && !abstractType) {
            // a list ?
            // only convert to list of type is not abstract
            return createListType(schema, name, group[0]);
        }
        for (XSParticle child : group) {
            XSTerm term = child.getTerm();
            XSElementDecl element = term.asElementDecl();
            int maxOccurs = child.getMaxOccurs().intValue();
            boolean isList = maxOccurs < 0 || maxOccurs > 1;

            if (element == null) {
                log.warn("Ignoring {} unsupported complex type", name);
                return null;
            } else {
                XSType elementType = element.getType();
                // type could be anonymous
                // concat complex name to enforce inner element type unity across type
                String fieldName = name + '#' + element.getName();
                if (isList) {
                    Type fieldType = loadType(schema, elementType, fieldName);
                    if (fieldType != null) {
                        ListType listType = createListType(schema, fieldName + "#anonymousListType", fieldType);
                        // add the listfield to the current CT
                        ct.addField(element.getName(), listType, null, null);
                    }
                } else {
                    Type fieldType = loadType(schema, elementType, fieldName);
                    if (fieldType != null) {
                        createField(ct, element, fieldType);
                    }
                }
            }
        }

        // add fields from Parent
        if (superType != null && superType.isComplexType()) {
            for (Field parentField : superType.getFields()) {
                ct.addField(parentField.getName(), parentField.getType(),
                        (String) parentField.getDefaultValue(), null);
            }
        }
        return ct;
    }

    protected ListType createListType(Schema schema, String name, XSParticle particle) throws TypeBindingException {
        XSElementDecl element = particle.getTerm().asElementDecl();
        if (element == null) {
            log.warn("Ignoring {} unsupported list type", name);
            return null;
        }
        // type could be anonymous
        // concat list name to enforce inner element type unity across type
        Type type = loadType(schema, element.getType(), name + '#' + element.getName());
        if (type == null) {
            log.warn("Unable to find type for {}", element.getName());
            return null;
        }

        // XXX default value in type??
        XmlString dv = element.getDefaultValue();
        String defValue = null;
        if (dv != null) {
            defValue = dv.value;
        }
        boolean computedNillable = isNillable(element);
        Set<Constraint> constraints = new HashSet<>();
        if (!computedNillable) {
            constraints.add(NotNullConstraint.get());
        }
        if (type instanceof SimpleType st) {
            constraints.addAll(st.getConstraints());
        }

        return new ListTypeImpl(name, type, element.getName(), defValue, constraints);
    }

    protected static ListType createListType(Schema schema, String name, Type itemType) {
        String elementName = name + "#item";
        return new ListTypeImpl(name, itemType, elementName);
    }

    protected static Field createField(ComplexType type, XSElementDecl element, Type fieldType) {
        String elementName = element.getName();
        XmlString dv = element.getDefaultValue();
        String defValue = null;
        if (dv != null) {
            defValue = dv.value;
        }
        boolean computedNillable = isNillable(element);
        Set<Constraint> constraints = new HashSet<>();
        if (!computedNillable) {
            constraints.add(NotNullConstraint.get());
        }
        if (fieldType instanceof SimpleType st) {
            constraints.addAll(st.getConstraints());
        }
        return type.addField(elementName, fieldType, defValue, constraints, getFieldCharacteristics(element));
    }

    protected static FieldCharacteristics getFieldCharacteristics(XSElementDecl element) {
        String systemOwnedValue = getCharacteristic(element, "systemOwned");
        String sourceValue = getCharacteristic(element, "source");
        String notQueryableValue = getCharacteristic(element, "notQueryable");
        String notProjectableValue = getCharacteristic(element, "notProjectable");
        String noAutoCheckoutValue = getCharacteristic(element, "noAutoCheckout");
        String falseAsNullValue = getCharacteristic(element, "falseAsNull");
        String readPermission = getCharacteristic(element, "readPermission");
        String writePermission = getCharacteristic(element, "writePermission");
        String versionWritePermission = getCharacteristic(element, "versionWritePermission");
        String notOutputValue = getCharacteristic(element, "notOutput");
        if (systemOwnedValue == null && sourceValue == null && notQueryableValue == null && notProjectableValue == null
                && noAutoCheckoutValue == null && falseAsNullValue == null && readPermission == null
                && writePermission == null && versionWritePermission == null && notOutputValue == null) {
            return null;
        }
        boolean systemOwned = Boolean.TRUE.equals(booleanValue(systemOwnedValue));
        ValueSource source = enumValue(ValueSource.class, sourceValue);
        boolean notQueryable = Boolean.TRUE.equals(booleanValue(notQueryableValue));
        boolean notProjectable = Boolean.TRUE.equals(booleanValue(notProjectableValue));
        boolean noAutoCheckout = Boolean.TRUE.equals(booleanValue(noAutoCheckoutValue));
        Set<Representation> falseAsNull = enumSetValue(Representation.class, falseAsNullValue);
        Set<Representation> notOutput = enumSetValue(Representation.class, notOutputValue);
        return new FieldCharacteristics(systemOwned, source, notQueryable, notProjectable, noAutoCheckout, falseAsNull,
                notOutput, readPermission, writePermission, versionWritePermission);
    }

    private static String getCharacteristic(XSElementDecl element, String name) {
        return element.getForeignAttribute(NAMESPACE_CORE_MANAGED, name);
    }

    private static Boolean booleanValue(String value) {
        return switch (value) {
        case null -> null;
        case "true" -> Boolean.TRUE;
        case "false" -> Boolean.FALSE;
        default -> throw new IllegalArgumentException("Invalid boolean value: " + value);
        };
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static <E extends Enum<E>> Set<E> enumSetValue(Class<E> type, String value) {
        Set<E> values = EnumSet.noneOf(type);
        if (value != null) {
            for (String item : value.split(",")) {
                values.add(enumValue(type, item.strip()));
            }
        }
        return values;
    }

    protected static String getTypeName(XSType type, String fieldName, Schema schema) {
        String typeName = type.getName();
        if (typeName == null || type.isLocal()) {
            return getAnonymousTypeName(type, fieldName);
        } else if (typeName.equals(BLOB_TYPE)) {
            // special case, this is a pseudo-global type
            return typeName;
        } else {
            return schema.getName() + '#' + typeName;
        }
    }

    protected static String getAnonymousTypeName(XSType type, String fieldName) {
        if (type.isComplexType()) {
            XSElementDecl container = type.asComplexType().getScope();
            String elName = container.getName();
            return elName + ANONYMOUS_TYPE_SUFFIX;
        } else {
            return fieldName + ANONYMOUS_TYPE_SUFFIX;
        }
    }

    // used by Studio, do not remove
    public List<String> getReferencedXSD() {
        return referencedXSD;
    }

    /**
     * ignore case where xsd:nillable is recognized as false by xsom (we don't know if it's not specified and we want to
     * preserve a default value to true. Therefore, we provide a custom attribute nxs:nillable to force nillable as
     * false) NB: if xsd:nillable is present and sets to true, deducted value will be true even if nxs:nillable is false
     *
     * @since 7.1
     */
    protected static boolean isNillable(XSElementDecl element) {
        String value = element.getForeignAttribute(NAMESPACE_CORE_VALIDATION, "nillable");
        return element.isNillable() || value == null || Boolean.parseBoolean(value);
    }

}
