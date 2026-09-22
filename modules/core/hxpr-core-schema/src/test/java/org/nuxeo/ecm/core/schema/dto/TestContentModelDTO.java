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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.schema.types.FieldCharacteristics;
import org.nuxeo.ecm.core.schema.types.FieldCharacteristics.ValueSource;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.skyscreamer.jsonassert.JSONAssert;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the one shape a content model is written in, and the legacy shape that is still read.
 * <p>
 * The persisted form and the REST form are now the same JSON. Repositories provisioned before that
 * convergence still hold the legacy form, so reading must accept both and both must yield exactly
 * the same model.
 */
public class TestContentModelDTO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The legacy persisted shape: bare type references, no {@code nullable} unless false. */
    private static final String LEGACY_JSON = """
            {
              "types": {
                "blob": {
                  "fields": {
                    "digest": "string",
                    "length": "long"
                  }
                },
                "stringList": {
                  "list": "string"
                },
                "resolvedList": {
                  "list": {
                    "extends": "string",
                    "constraints": {
                      "resolver": {
                        "name": "userManagerResolver",
                        "type": "user",
                        "validation": "false"
                      }
                    }
                  }
                },
                "boundedInt": {
                  "extends": "long",
                  "constraints": {
                    "maxInclusive": 10,
                    "minInclusive": 1
                  }
                }
              },
              "schemas": {
                "myschema": {
                  "prefix": "my",
                  "fields": {
                    "my_flag": {
                      "default": "false",
                      "extends": "boolean"
                    },
                    "my_text": {
                      "extends": "string",
                      "fullTextIndexed": true,
                      "managed": {
                        "systemOwned": true,
                        "notQueryable": true,
                        "notProjectable": true,
                        "notOutput": [ "INDEX" ]
                      }
                    },
                    "my_title": "string",
                    "my_required": {
                      "extends": "string",
                      "constraints": {
                        "maxLength": 20,
                        "nullable": false
                      }
                    }
                  },
                  "versionWritable": true
                }
              },
              "mixinTypes": {
                "MyMixin": {
                  "schemas": [ "myschema" ],
                  "noPerDocumentQuery": true
                }
              },
              "primaryTypes": {
                "MyDoc": {
                  "extends": "SysContent",
                  "schemas": [ "myschema" ],
                  "mixins": [ "MyMixin" ],
                  "subtypes": [ "MyDoc" ]
                }
              }
            }
            """;

    /** The current shape, used for both the persisted document and the REST API. */
    private static final String CURRENT_JSON = """
            {
              "types": {
                "blob": {
                  "fields": {
                    "digest": { "type": "string" },
                    "length": { "type": "long" }
                  }
                },
                "stringList": {
                  "list": { "type": "string" }
                },
                "resolvedList": {
                  "list": {
                    "extends": "string",
                    "constraints": {
                      "nullable": true,
                      "resolver": {
                        "name": "userManagerResolver",
                        "type": "user",
                        "validation": false
                      }
                    }
                  }
                },
                "boundedInt": {
                  "extends": "long",
                  "constraints": {
                    "nullable": true,
                    "maxInclusive": 10,
                    "minInclusive": 1
                  }
                }
              },
              "schemas": {
                "myschema": {
                  "prefix": "my",
                  "fields": {
                    "my_flag": {
                      "default": "false",
                      "extends": "boolean"
                    },
                    "my_text": {
                      "extends": "string",
                      "fullTextIndexed": true,
                      "managed": {
                        "systemOwned": true,
                        "notQueryable": true,
                        "notProjectable": true,
                        "notOutput": [ "INDEX" ]
                      }
                    },
                    "my_title": { "type": "string" },
                    "my_required": {
                      "extends": "string",
                      "constraints": {
                        "maxLength": 20,
                        "nullable": false
                      }
                    }
                  },
                  "versionWritable": true
                }
              },
              "mixinTypes": {
                "MyMixin": {
                  "schemas": [ "myschema" ],
                  "noPerDocumentQuery": true
                }
              },
              "primaryTypes": {
                "MyDoc": {
                  "extends": "SysContent",
                  "schemas": [ "myschema" ],
                  "mixins": [ "MyMixin" ],
                  "subtypes": [ "MyDoc" ]
                }
              }
            }
            """;

    private static ContentModelDTO readMap(String json) {
        return ModelMapCodec.read(MAPPER.readValue(json, Map.class));
    }

    /** What we write is the current shape, whichever shape we read. */
    @Test
    public void testLegacyIsRewrittenInTheCurrentShape() throws Exception {
        String actual = MAPPER.writeValueAsString(ModelMapCodec.write(readMap(LEGACY_JSON)));
        JSONAssert.assertEquals(actual, CURRENT_JSON, actual, true);
    }

    @Test
    public void testRestRoundTrip() throws Exception {
        ContentModelDTO model = MAPPER.readValue(CURRENT_JSON, ContentModelDTO.class);
        String actual = MAPPER.writeValueAsString(model);
        JSONAssert.assertEquals(actual, CURRENT_JSON, actual, true);
    }

    /** Reading either shape must produce exactly the same model. */
    @Test
    public void testLegacyAndCurrentShapesAreEquivalent() throws Exception {
        assertEquals(readMap(LEGACY_JSON), MAPPER.readValue(CURRENT_JSON, ContentModelDTO.class));
        assertEquals(readMap(LEGACY_JSON), readMap(CURRENT_JSON));
    }

    /** The persisted document and the REST body are byte-for-byte the same JSON. */
    @Test
    public void testPersistedAndRestShapesAreIdentical() throws Exception {
        ContentModelDTO model = readMap(CURRENT_JSON);
        String persisted = MAPPER.writeValueAsString(ModelMapCodec.write(model));
        String rest = MAPPER.writeValueAsString(model);
        JSONAssert.assertEquals(persisted, rest, persisted, true);
    }

    @Test
    public void testCurrentShapeRoundTrips() throws Exception {
        String actual = MAPPER.writeValueAsString(ModelMapCodec.write(readMap(CURRENT_JSON)));
        JSONAssert.assertEquals(actual, CURRENT_JSON, actual, true);
    }

    @Test
    public void testSparseManagedCharacteristicsUseBooleanDefaults() {
        FieldCharacteristics characteristics = readMap(
                """
                        {"schemas":{"system":{"fields":{"sys_test":{"extends":"string","managed":{"source":"COMPUTED"}}}}}}
                        """).schemas().get("system").fields().get("sys_test").characteristics();
        assertEquals(ValueSource.COMPUTED, characteristics.source());
        assertFalse(characteristics.systemOwned());
        assertFalse(characteristics.notQueryable());
        assertFalse(characteristics.notProjectable());
        assertFalse(characteristics.noAutoCheckout());
        assertEquals(Set.of(), characteristics.falseAsNull());
        assertEquals(Set.of(), characteristics.notOutput());
        assertNull(characteristics.readPermission());
        assertNull(characteristics.writePermission());
    }

    /**
     * A bare type name is a third accepted spelling of a reference, on both sides.
     */
    @Test
    public void testBareTypeNameIsAccepted() throws Exception {
        FieldDTO field = MAPPER.readValue("\"string\"", FieldDTO.class);
        assertEquals(FieldDTO.ofType("string"), field);
        assertTrue(field.isCompact());
        assertEquals("string", field.type());
        assertNull(field.extend());
    }

    /**
     * The defect the unification removes: {@code type} used to be read by a branch that ignored
     * everything else on the field, so constraints and defaults were silently dropped.
     */
    @Test
    public void testTypeKeepsConstraintsAndDefault() throws Exception {
        FieldDTO field = MAPPER.readValue("""
                { "type": "string", "default": "x", "constraints": { "maxLength": 10 } }
                """, FieldDTO.class);
        assertEquals("string", field.typeRef());
        assertEquals("x", field.defaultValue());
        assertNotNull(field.constraints());
        assertEquals(10L, field.constraints().maxLength());
        // it is no longer compact, so it is spelled "extends" on the way out
        assertNull(field.type());
        assertEquals("string", field.extend());
    }

    /** Interval bounds must compare equal whether they arrived as Integer, Long or String. */
    @Test
    public void testIntervalBoundsAreNormalized() {
        ConstraintsDTO fromJson = ConstraintsDTO.fromMap(Map.of("minInclusive", Integer.valueOf(5)));
        ConstraintsDTO fromConstraint = ConstraintsDTO.fromMap(Map.of("minInclusive", Long.valueOf(5)));
        ConstraintsDTO fromString = ConstraintsDTO.fromMap(Map.of("minInclusive", "5"));
        assertEquals(fromJson, fromConstraint);
        assertEquals(fromJson, fromString);
        // an ISO-8601 date bound stays a string
        assertEquals("2025-01-01", ConstraintsDTO.fromMap(Map.of("minInclusive", "2025-01-01")).minInclusive());
    }

    /** Merging is "present keys win", so an inherited NotNull is not undone by an absent one. */
    @Test
    public void testConstraintsMergeKeepsNotNull() {
        ConstraintsDTO type = ConstraintsDTO.fromMap(Map.of("nullable", Boolean.FALSE));
        ConstraintsDTO field = ConstraintsDTO.fromMap(Map.of("maxLength", 10));
        ConstraintsDTO merged = ConstraintsDTO.merge(type, field);
        assertNotNull(merged);
        assertFalse(merged.nullable());
        assertEquals(10L, merged.maxLength());
    }

    /**
     * A length bound is normalized to a {@link Long} whatever numeric type it came with, so that two constraints that
     * differ only in numeric width — an {@link Integer} from JSON against a {@link Long} from {@code LengthConstraint} —
     * compare equal instead of looking like a modification. The width is not observable elsewhere: it makes no
     * difference in JSON, and the persisted document is only ever read whole by id, never queried on these values.
     */
    @Test
    public void testLengthBoundIsNormalizedToLong() {
        Map<String, Object> constraints = Map.of("minLength", Integer.valueOf(1), "maxLength", Long.valueOf(2000));
        ConstraintsDTO dto = ConstraintsDTO.fromMap(constraints);
        assertNotNull(dto);
        assertEquals(Long.class, dto.minLength().getClass());
        assertEquals(Long.class, dto.maxLength().getClass());
        assertEquals(Map.of("minLength", 1L, "maxLength", 2000L), dto.toMap());
        // the same bounds coming from JSON, i.e. as Integers, give an equal DTO
        assertEquals(dto, ConstraintsDTO.fromMap(Map.of("minLength", 1, "maxLength", 2000)));
        // a value that is not a plain integer is turned to null
        assertNull(ConstraintsDTO.fromMap(Map.of("maxLength", 1.5)).maxLength());
    }

    /**
     * A bound may arrive as a string: {@code LengthConstraint} takes "any object having toString representating an
     * integer", so dropping it would silently leave the field unbounded instead of length-limited.
     */
    @Test
    public void testLengthBoundAcceptsANumericString() {
        ConstraintsDTO dto = ConstraintsDTO.fromMap(Map.of("minLength", "1", "maxLength", "2000"));
        assertNotNull(dto);
        assertEquals(1L, dto.minLength());
        assertEquals(2000L, dto.maxLength());
        assertEquals(Map.of("minLength", 1L, "maxLength", 2000L), dto.toMap());
        // a value that is not numeric at all cannot be held as a Number, so it is still dropped
        assertNull(ConstraintsDTO.fromMap(Map.of("maxLength", "abc")).maxLength());
    }

    /**
     * Reading a legacy document reports it, so that loading one can be logged; reading a current
     * document reports nothing. This is what tells an operator a repository still predates the
     * shape convergence.
     */
    @Test
    public void testLegacyShapeIsReported() {
        assertTrue(readReportingShape(LEGACY_JSON), "legacy document should be reported");
        assertFalse(readReportingShape(CURRENT_JSON), "current document should not be reported");
    }

    private static boolean readReportingShape(String json) {
        AtomicBoolean legacyShape = new AtomicBoolean();
        ModelMapCodec.read(MAPPER.readValue(json, Map.class), new ArrayList<>(), legacyShape);
        return legacyShape.get();
    }

    /** A field value of the wrong shape is a bad request, not a field to quietly drop. */
    @Test
    public void testMalformedFieldIsRejected() {
        assertThrows(DatabindException.class,
                () -> MAPPER.readValue("{\"schemas\":{\"s\":{\"fields\":{\"f\":[\"nope\"]}}}}",
                        ContentModelDTO.class));
    }

    /**
     * A malformed value must be reported at the path it sits on, not quietly dropped: a field whose
     * {@code type} is not a string, and one whose inline {@code fields} is not an object. Both used
     * to pass through silently, leaving a partially applied model instead of a violation.
     */
    @Test
    public void testMalformedFieldMembersAreReported() {
        assertViolationAt("schemas/s/fields/f/type",
                "{\"schemas\":{\"s\":{\"fields\":{\"f\":{\"type\":123}}}}}");
        assertViolationAt("schemas/s/fields/f/fields",
                "{\"schemas\":{\"s\":{\"fields\":{\"f\":{\"fields\":[\"nope\"]}}}}}");
    }

    private static void assertViolationAt(String path, String json) {
        List<ValidationViolation> violations = new ArrayList<>();
        ModelMapCodec.read(MAPPER.readValue(json, Map.class), violations);
        assertTrue(violations.stream().anyMatch(v -> path.equals(v.getPath())),
                () -> "expected a violation at " + path + " but got "
                        + violations.stream().map(ValidationViolation::getPath).toList());
    }

    /**
     * A length bound must be a non-negative integer. A negative, a fraction or a non-numeric string is dropped by the
     * constraint layer, silently leaving the field unbounded, so it is reported as the model is read rather than let to
     * disappear: a length is held as a {@link Long} and cannot carry such a value any further.
     */
    @Test
    public void testInvalidLengthBoundIsReported() {
        assertViolationAt("schemas/s/fields/f/constraints/maxLength",
                "{\"schemas\":{\"s\":{\"fields\":{\"f\":{\"extends\":\"string\",\"constraints\":{\"maxLength\":-5}}}}}}");
        assertViolationAt("schemas/s/fields/f/constraints/minLength",
                "{\"schemas\":{\"s\":{\"fields\":{\"f\":{\"extends\":\"string\",\"constraints\":{\"minLength\":1.5}}}}}}");
        assertViolationAt("schemas/s/fields/f/constraints/maxLength",
                "{\"schemas\":{\"s\":{\"fields\":{\"f\":{\"extends\":\"string\",\"constraints\":{\"maxLength\":\"abc\"}}}}}}");
    }

    /**
     * A bad length bound is reported wherever it sits, by the path that leads to it: on a sub-field of a complex field,
     * and on the item type of a list. Both are collected in a single read.
     */
    @Test
    public void testInvalidNestedLengthBoundIsReported() {
        List<ValidationViolation> violations = new ArrayList<>();
        ModelMapCodec.read(MAPPER.readValue("""
                {"schemas":{"s":{"fields":{
                    "cx":{"fields":{"inner":{"extends":"string","constraints":{"maxLength":-1}}}},
                    "ls":{"list":{"extends":"string","constraints":{"minLength":1.5}}}
                }}}}
                """, Map.class), violations);
        List<String> paths = violations.stream().map(ValidationViolation::getPath).toList();
        assertTrue(paths.contains("schemas/s/fields/cx/fields/inner/constraints/maxLength"), paths::toString);
        assertTrue(paths.contains("schemas/s/fields/ls/list/constraints/minLength"), paths::toString);
    }

    /** A valid non-negative integer length, including one written as a numeric string, is not reported. */
    @Test
    public void testValidLengthBoundIsNotReported() {
        List<ValidationViolation> violations = new ArrayList<>();
        ModelMapCodec.read(MAPPER.readValue(
                "{\"schemas\":{\"s\":{\"fields\":{\"f\":{\"extends\":\"string\",\"constraints\":{\"minLength\":0,\"maxLength\":\"42\"}}}}}}",
                Map.class), violations);
        assertTrue(violations.isEmpty(), violations::toString);
    }
}
