/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.schema.dto;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.COMPLEX;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.CONSTRAINTS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.DEFAULT;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.EXTENDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FIELDS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.FULL_TEXT_INDEXED;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.LIST;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXINS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.MIXIN_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.NO_PER_DOCUMENT_QUERY;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.NULLABLE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PREFIX;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.PROJECT_ID;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.RESOLVER;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SCHEMAS;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.STRING;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SUBTYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SUPERTYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.TYPE;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.TYPES;
import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.VERSION_WRITABLE;
import static org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver.VALIDATION_PARAMETER_KEY;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.nuxeo.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;

import tools.jackson.databind.ObjectMapper;

/**
 * Converts a {@link ContentModelDTO} to and from the untyped {@code Map<String, Object>} form.
 * <p>
 * {@link #write} produces the persisted shape, which the configuration service stores as-is. It is
 * the <b>same</b> shape Jackson produces for the REST API — the two are deliberately one format, so
 * that what is stored is what the API speaks.
 * <p>
 * {@link #read} additionally accepts the <b>legacy</b> persisted shape, which every repository
 * provisioned before that convergence still has on disk until its next save:
 * <ul>
 * <li>a bare type reference written as the string itself ({@code "string"}) rather than
 * {@code {"type": "string"}};</li>
 * <li>{@code nullable} present only when it is {@code false};</li>
 * <li>a resolver's {@code validation} present only when off, and then as the string
 * {@code "false"}.</li>
 * </ul>
 * All three spellings of a type reference — a bare string, {@code type} and {@code extends} — mean
 * the same thing and are all accepted. The reader is used for the persisted config, for JSON Patch
 * results (which arrive as an untyped map, with no Jackson binding to reject malformed values) and,
 * via {@link FieldDTODeserializer}, for REST field values.
 * <p>
 * Because the map is untyped, reading is also where <em>shape</em> is validated: a value that is a
 * list where an object was expected is reported as a violation rather than silently dropped.
 * Whether the names it mentions actually resolve is a separate, later concern, handled by
 * {@link SchemaManagerDTO}.
 * <p>
 * Once no repository carries the legacy shape any more, the leniency listed above can go — but not
 * this class. JSON Patch hands us an arbitrary map with no binding step, and a bad patch has to
 * report <em>every</em> misplaced value with its model path, as a 422. Jackson stops at the first
 * mismatch with one exception and a reference-chain path, so the validating read stays.
 */
public class ModelMapCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("rawtypes")
    private static final Class<Map> MAP_TYPE = Map.class;

    private ModelMapCodec() {
        // utility class
    }

    /*
     * ===== read =====
     */

    /** Reads a model, ignoring any shape problems. */
    public static ContentModelDTO read(Map<String, Object> map) {
        return read(map, new ArrayList<>());
    }

    /** Reads a model, reporting shape problems into {@code violations}. */
    public static ContentModelDTO read(Map<String, Object> map, List<ValidationViolation> violations) {
        return read(map, violations, new AtomicBoolean());
    }

    /**
     * Reads a model, reporting shape problems into {@code violations} and setting {@code legacyShape}
     * if anything in the document was written in the legacy shape.
     * <p>
     * Only meaningful when reading a persisted configuration. The REST API accepts the same
     * spellings by design, so a request body using them is lenient input, not a legacy document.
     */
    public static ContentModelDTO read(Map<String, Object> map, List<ValidationViolation> violations,
            AtomicBoolean legacyShape) {
        return new Reader(violations, legacyShape).readModel(map);
    }

    /**
     * Reads a field, a list item or an inline anonymous type, reporting shape problems into
     * {@code violations}. Accepts all three spellings of a type reference.
     */
    public static FieldDTO readField(Object json, List<ValidationViolation> violations) {
        return new Reader(violations, new AtomicBoolean()).readField(json, DTOPath.root(FIELDS, json));
    }

    private static class Reader {

        private final List<ValidationViolation> violations;

        private final AtomicBoolean legacyShape;

        Reader(List<ValidationViolation> violations, AtomicBoolean legacyShape) {
            this.violations = violations;
            this.legacyShape = legacyShape;
        }

        ContentModelDTO readModel(Map<String, Object> map) {
            if (map == null) {
                return ContentModelDTO.empty();
            }
            return new ContentModelDTO( //
                    readAll(map, SCHEMAS, this::readSchema), //
                    readAll(map, TYPES, this::readType), //
                    readAll(map, MIXIN_TYPES, this::readMixinType), //
                    readAll(map, PRIMARY_TYPES, this::readPrimaryType));
        }

        /**
         * Reads one top-level section. An entry the reader cannot make sense of is reported and
         * left out, which is what the schema layer used to do by refusing to register it.
         */
        private <V> Map<String, V> readAll(Map<String, Object> model, String section,
                BiFunction<Object, DTOPath, V> reader) {
            Object value = model.get(section);
            DTOPath path = DTOPath.root(section, value);
            if (value == null) {
                return Map.of();
            }
            if (!(value instanceof Map)) {
                violations.add(new InvalidObjectViolation(path));
                return Map.of();
            }
            Map<String, V> result = new LinkedHashMap<>();
            asMap(value).forEach((key, entry) -> {
                V read = reader.apply(entry, path.child(key, entry));
                if (read != null) {
                    result.put(key, read);
                }
            });
            return result;
        }

        SchemaDTO readSchema(Object json, DTOPath path) {
            if (!(json instanceof Map)) {
                violations.add(new InvalidObjectViolation(path, SCHEMAS));
                return null;
            }
            Map<String, Object> map = asMap(json);
            Object fields = map.get(FIELDS);
            DTOPath fieldsPath = path.child(FIELDS, fields);
            if (!(fields instanceof Map)) {
                violations.add(new InvalidObjectViolation(fieldsPath, FIELDS, COMPLEX));
                return null;
            }
            return new SchemaDTO(readProjectId(map, path), readFields(fields, fieldsPath),
                    TRUE.equals(map.get(VERSION_WRITABLE)), string(map.get(PREFIX)));
        }

        TypeDTO readType(Object json, DTOPath path) {
            if (!(json instanceof Map)) {
                violations.add(new InvalidObjectViolation(path, TYPES));
                return null;
            }
            Map<String, Object> map = asMap(json);
            String projectId = readProjectId(map, path);
            ConstraintsDTO constraints = readConstraints(map, path);
            Object extend = map.get(EXTENDS);
            if (extend != null) {
                if (!(extend instanceof String s)) {
                    violations.add(new InvalidObjectViolation(path.child(EXTENDS, extend), TYPES, SUPERTYPES, STRING));
                    return null;
                }
                return new TypeDTO(projectId, null, constraints, null, s);
            }
            Object list = map.get(LIST);
            if (list != null) {
                FieldDTO item = readField(list, path.child(LIST, list));
                return item == null ? null : new TypeDTO(projectId, null, constraints, item, null);
            }
            Object fields = map.get(FIELDS);
            if (fields != null) {
                DTOPath fieldsPath = path.child(FIELDS, fields);
                if (!(fields instanceof Map)) {
                    violations.add(new InvalidObjectViolation(fieldsPath, TYPES, COMPLEX));
                    return null;
                }
                return new TypeDTO(projectId, readFields(fields, fieldsPath), constraints, null, null);
            }
            // declares nothing: the binder reports this as a type recursion
            return new TypeDTO(projectId, null, constraints, null, null);
        }

        MixinTypeDTO readMixinType(Object json, DTOPath path) {
            if (!(json instanceof Map)) {
                violations.add(new InvalidObjectViolation(path, MIXINS, TYPES));
                return null;
            }
            Map<String, Object> map = asMap(json);
            Object schemas = map.get(SCHEMAS);
            if (schemas != null && !(schemas instanceof List)) {
                violations.add(new InvalidObjectViolation(path.child(SCHEMAS, schemas), MIXINS, TYPES));
                return null;
            }
            return new MixinTypeDTO(readProjectId(map, path), TRUE.equals(map.get(NO_PER_DOCUMENT_QUERY)),
                    stringList(schemas));
        }

        PrimaryTypeDTO readPrimaryType(Object json, DTOPath path) {
            if (!(json instanceof Map)) {
                violations.add(new InvalidObjectViolation(path, PRIMARY_TYPES));
                return null;
            }
            Map<String, Object> map = asMap(json);
            String extend = null;
            Object extendValue = map.get(EXTENDS);
            if (extendValue != null) {
                if (extendValue instanceof String s) {
                    extend = s;
                } else {
                    violations.add(new InvalidObjectViolation(path.child(EXTENDS, extendValue), PRIMARY_TYPES, EXTENDS));
                }
            }
            return new PrimaryTypeDTO(readProjectId(map, path), extend, //
                    readStringList(map, SCHEMAS, path, PRIMARY_TYPES, SCHEMAS), //
                    readStringList(map, MIXINS, path, PRIMARY_TYPES, MIXINS), //
                    readStringList(map, SUBTYPES, path, PRIMARY_TYPES, SUBTYPES));
        }

        FieldDTO readField(Object json, DTOPath path) {
            if (json instanceof String typeRef) {
                legacyShape.set(true); // the current shape writes {"type": …}
                return FieldDTO.ofType(typeRef);
            }
            if (!(json instanceof Map)) {
                violations.add(new InvalidObjectViolation(path));
                return null;
            }
            Map<String, Object> map = asMap(json);
            // "type" and "extends" are the same thing, so a bad value is reported the same way
            String typeRef = null;
            Object type = map.get(TYPE);
            Object extend = map.get(EXTENDS);
            if (type != null) {
                if (!(type instanceof String s)) {
                    violations.add(new InvalidObjectViolation(path.child(TYPE, type)));
                    return null;
                }
                typeRef = s;
            } else if (extend != null) {
                if (!(extend instanceof String s)) {
                    violations.add(new InvalidObjectViolation(path.child(EXTENDS, extend)));
                    return null;
                }
                typeRef = s;
            }
            Map<String, FieldDTO> fields = null;
            Object fieldsValue = map.get(FIELDS);
            if (fieldsValue != null) {
                DTOPath fieldsPath = path.child(FIELDS, fieldsValue);
                if (!(fieldsValue instanceof Map)) {
                    violations.add(new InvalidObjectViolation(fieldsPath, FIELDS, COMPLEX));
                    return null;
                }
                fields = readFields(fieldsValue, fieldsPath);
            }
            Object list = map.get(LIST);
            FieldDTO item = null;
            if (list != null) {
                item = readField(list, path.child(LIST, list));
                if (item == null) {
                    return null; // the item is unusable, so the field is too
                }
            }
            return new FieldDTO(typeRef, //
                    fields, //
                    item, //
                    readConstraints(map, path), //
                    string(map.get(DEFAULT)), //
                    TRUE.equals(map.get(FULL_TEXT_INDEXED)), //
                    readProjectId(map, path));
        }

        private Map<String, FieldDTO> readFields(Object json, DTOPath path) {
            Map<String, FieldDTO> fields = new LinkedHashMap<>();
            asMap(json).forEach((key, value) -> {
                FieldDTO field = readField(value, path.child(key, value));
                if (field != null) {
                    fields.put(key, field);
                }
            });
            return fields;
        }

        private ConstraintsDTO readConstraints(Map<String, Object> map, DTOPath path) {
            Object constraints = map.get(CONSTRAINTS);
            if (constraints == null) {
                return null;
            }
            if (!(constraints instanceof Map)) {
                violations.add(new InvalidObjectViolation(path.child(CONSTRAINTS, constraints)));
                return null;
            }
            Map<String, Object> constraintsMap = asMap(constraints);
            if (!constraintsMap.containsKey(NULLABLE)) {
                legacyShape.set(true); // the current shape always writes "nullable"
            } else if (constraintsMap.get(RESOLVER) instanceof Map<?, ?> resolver
                    && !(resolver.get(VALIDATION_PARAMETER_KEY) instanceof Boolean)) {
                legacyShape.set(true); // the current shape always writes a boolean "validation"
            }
            return ConstraintsDTO.fromMap(constraintsMap);
        }

        private String readProjectId(Map<String, Object> map, DTOPath path) {
            Object projectId = map.get(PROJECT_ID);
            if (projectId == null) {
                return null;
            }
            if (projectId instanceof String s) {
                return s;
            }
            // the value, not the key, is what is wrong, but the message points at the owner
            violations.add(new InvalidObjectViolation(path, PROJECT_ID));
            return null;
        }

        private List<String> readStringList(Map<String, Object> map, String key, DTOPath path, String... keyArgs) {
            Object value = map.get(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof List)) {
                violations.add(new InvalidObjectViolation(path.child(key, value), keyArgs));
                return null;
            }
            return stringList(value);
        }

        @SuppressWarnings("unchecked")
        private static List<String> stringList(Object value) {
            return value instanceof List ? new ArrayList<>((List<String>) value) : null;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asMap(Object json) {
            return json instanceof Map ? (Map<String, Object>) json : Map.of();
        }

        private static String string(Object value) {
            return value instanceof String s ? s : null;
        }
    }

    /*
     * ===== write =====
     */

    /**
     * The persisted form, which is now the same shape Jackson produces for the REST API. Reading
     * still accepts the older, more compact form; see the class javadoc.
     */
    public static Map<String, Object> write(ContentModelDTO model) {
        return MAPPER.convertValue(model, MAP_TYPE);
    }

    /**
     * A DTO rendered the way it appears on the wire, so that a validation violation echoes the
     * value in the shape callers recognise.
     */
    public static Object toJson(Object dto) {
        return dto == null ? null : MAPPER.convertValue(dto, Object.class);
    }
}
