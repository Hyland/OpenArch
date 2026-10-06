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
package com.hyland.openarch.opensearch.query;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_BLOB_KEYS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link HXQLQueryConverter#validateAggregationField}, the gate on a property named directly by an
 * aggregation rather than reached through a parsed HXQL statement.
 * <p>
 * The property is supplied in index (dot) notation, so each case states the name the way a caller sends it and
 * stubs the content model against the HXQL (slash) notation the converter must translate it to.
 * <p>
 * What this deliberately does not cover: a blob's internal sub-properties, {@code sysfile_blob.key} in
 * particular. Blobs are indexed as sub-objects, so those names are reachable, but refusing blob sub-properties
 * wholesale would also refuse faceting by {@code mimeType}. The content model, not this gate, is the right place
 * to say which of a blob's internals belong in the index. {@code sysfile_blob} currently declares no
 * characteristics at all. Tracked separately, the values exposed are storage keys for documents the caller can
 * already read.
 */
class HXQLQueryConverterAggregationFieldTest {

    private SchemaManager schemaManager;

    private HXQLQueryConverter converter;

    private RepositoryPrincipal user;

    private RepositoryPrincipal administrator;

    @BeforeEach
    void setUp() {
        schemaManager = mock(SchemaManager.class);
        converter = new HXQLQueryConverter(schemaManager);
        user = mock(RepositoryPrincipal.class);
        lenient().when(user.isAdministrator()).thenReturn(false);
        administrator = mock(RepositoryPrincipal.class);
        lenient().when(administrator.isAdministrator()).thenReturn(true);
    }

    /** Declares a field under its HXQL name, with the given characteristics. */
    private void declareField(String xpath, FieldCharacteristics characteristics) {
        Field field = mock(Field.class);
        lenient().when(field.getName()).thenReturn(xpath);
        lenient().when(field.getCharacteristics()).thenReturn(characteristics);
        lenient().when(schemaManager.getField(xpath)).thenReturn(field);
    }

    private static FieldCharacteristics characteristics(boolean notProjectable, Set<FieldCharacteristics.Representation> notOutput) {
        return new FieldCharacteristics(false, null, false, notProjectable, false, null, notOutput, null, null, null);
    }

    @Test
    void allowsAnOrdinaryProperty() {
        declareField(SYS_PRIMARY_TYPE, FieldCharacteristics.DEFAULT);

        assertDoesNotThrow(() -> converter.validateAggregationField(SYS_PRIMARY_TYPE, user));
    }

    @Test
    void allowsAComplexSubPropertyInIndexNotation() {
        // the property an aggregation caller sends as 'any_obj.myStringList' is 'any_obj/myStringList' in the
        // content model: without the translation this resolves to nothing and would be refused as unknown
        declareField("any_obj", FieldCharacteristics.DEFAULT);
        declareField("any_obj/myStringList", FieldCharacteristics.DEFAULT);

        assertDoesNotThrow(() -> converter.validateAggregationField("any_obj.myStringList", user));
    }

    @Test
    void allowsADynamicSubPropertyOfAnOpenType() {
        // 'any_obj' is declared xs:anyType, so its sub-properties exist only in indexed documents and resolve to
        // nothing in the content model. Requiring the leaf to resolve would refuse a supported aggregation, so
        // only the root is required. This is what RestQueryAggregationsFeature exercises end to end.
        declareField("any_obj", FieldCharacteristics.DEFAULT);

        assertDoesNotThrow(() -> converter.validateAggregationField("any_obj.myStringList", user));
    }

    @Test
    void allowsASubPropertyOfABlobThatIsNotSensitive() {
        // faceting documents by file type is a legitimate aggregation, so blob sub-properties are not refused
        // wholesale. See the class comment on what this leaves open.
        declareField(SYSFILE_BLOB, FieldCharacteristics.DEFAULT);

        assertDoesNotThrow(() -> converter.validateAggregationField(SYSFILE_BLOB + ".mimeType", user));
    }

    @Test
    void refusesASecurityPropertyForANonAdministrator() {
        // sys_racl carries a readPermission but is not marked notProjectable, so only the security-property
        // check refuses it
        declareField(SYS_READ_ACL, FieldCharacteristics.DEFAULT);
        when(schemaManager.isSecurityProperty(SYS_READ_ACL)).thenReturn(true);

        assertThrows(QueryParseException.class, () -> converter.validateAggregationField(SYS_READ_ACL, user));
    }

    @Test
    void allowsASecurityPropertyForAnAdministrator() {
        // matches the exemption StatementValidator.rejectSecurityProperties already makes on the statement path
        declareField(SYS_READ_ACL, FieldCharacteristics.DEFAULT);
        lenient().when(schemaManager.isSecurityProperty(SYS_READ_ACL)).thenReturn(true);

        assertDoesNotThrow(() -> converter.validateAggregationField(SYS_READ_ACL, administrator));
    }

    @Test
    void refusesASecurityPropertyWhenThereIsNoPrincipal() {
        declareField(SYS_ACL, FieldCharacteristics.DEFAULT);
        when(schemaManager.isSecurityProperty(SYS_ACL)).thenReturn(true);

        assertThrows(QueryParseException.class, () -> converter.validateAggregationField(SYS_ACL, null));
    }

    @Test
    void refusesANotProjectableProperty() {
        // sys_fulltextBinary is not a security property, so only the field-characteristics check refuses it
        declareField(SYS_FULLTEXT_BINARY, characteristics(true, null));

        assertThrows(QueryParseException.class, () -> converter.validateAggregationField(SYS_FULLTEXT_BINARY, user));
        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField(SYS_FULLTEXT_BINARY, administrator));
    }

    @Test
    void refusesAPropertyExcludedFromTheIndex() {
        declareField(SYS_BLOB_KEYS, characteristics(false, Set.of(INDEX)));

        assertThrows(QueryParseException.class, () -> converter.validateAggregationField(SYS_BLOB_KEYS, user));
    }

    @Test
    void refusesASubPropertyOfARootExcludedFromTheIndex() {
        // the root carries the exclusion, so a sub-property of it must be refused along with it
        declareField(SYS_BLOB_KEYS, characteristics(false, Set.of(INDEX)));
        declareField(SYS_BLOB_KEYS + "/key", FieldCharacteristics.DEFAULT);

        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField(SYS_BLOB_KEYS + ".key", user));
    }

    /**
     * A descendant that resolves to nothing is dynamic, and dynamic is only safe beneath a root that may itself
     * be projected: the index can hold sub-fields of a held-back property that carry the same values.
     */
    @Test
    void refusesADynamicDescendantOfANotProjectableRoot() {
        // sys_fulltextBinary is notProjectable but is not excluded from the index, so the root-level check in
        // validateFieldUse passes it and the leaf resolves to nothing
        declareField(SYS_FULLTEXT_BINARY, characteristics(true, null));

        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField(SYS_FULLTEXT_BINARY + ".keyword", user));
        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField(SYS_FULLTEXT_BINARY + ".anything", administrator));
    }

    @Test
    void stillAllowsADynamicDescendantOfAProjectableRoot() {
        declareField("any_obj", FieldCharacteristics.DEFAULT);

        assertDoesNotThrow(() -> converter.validateAggregationField("any_obj.whateverTheIndexHolds", user));
    }

    /**
     * An index sub-field of a restricted <em>intermediate</em> property resolves to nothing of its own, so the
     * deepest declared ancestor is what it has to answer to. Asking only the root would read the restricted
     * property's values through a name the content model never declared.
     */
    @Test
    void refusesAMultiFieldOfANotProjectableIntermediateProperty() {
        declareField("complex", FieldCharacteristics.DEFAULT);
        declareField("complex/secret", characteristics(true, null));

        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField("complex.secret.keyword", user));
        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField("complex.secret.keyword", administrator));
    }

    @Test
    void refusesAMultiFieldOfAnIntermediatePropertyKeptOutOfTheIndex() {
        declareField("complex", FieldCharacteristics.DEFAULT);
        declareField("complex/internal", characteristics(false, Set.of(INDEX)));

        assertThrows(QueryParseException.class,
                () -> converter.validateAggregationField("complex.internal.keyword", user));
    }

    @Test
    void refusesAMultiFieldOfAnIntermediateSecurityProperty() {
        declareField("complex", FieldCharacteristics.DEFAULT);
        declareField("complex/acl", FieldCharacteristics.DEFAULT);
        when(schemaManager.isSecurityProperty("complex/acl")).thenReturn(true);

        assertThrows(QueryParseException.class, () -> converter.validateAggregationField("complex.acl.keyword", user));
    }

    @Test
    void stillAllowsAMultiFieldOfAnUnrestrictedIntermediateProperty() {
        declareField("complex", FieldCharacteristics.DEFAULT);
        declareField("complex/title", FieldCharacteristics.DEFAULT);

        assertDoesNotThrow(() -> converter.validateAggregationField("complex.title.keyword", user));
    }

    @Test
    void refusesAnUnknownProperty() {
        // unlike a statement reference, which is left to OpenSearch to reject, an unresolvable aggregation
        // property is refused outright: the aggregation would otherwise return its values to the caller
        assertThrows(QueryParseException.class, () -> converter.validateAggregationField("not_a_property", user));
    }

    @Test
    void refusesABlankProperty() {
        assertThrows(QueryParseException.class, () -> converter.validateAggregationField("   ", user));
        assertThrows(QueryParseException.class, () -> converter.validateAggregationField(null, user));
    }
}
