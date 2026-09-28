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
package org.nuxeo.opensearch.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.REST;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.STORE;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.ValueSource.QUERY_VIRTUAL;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.schema.SchemaManagerDTO;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.FieldDTO;
import com.hyland.openarch.ecm.core.schema.dto.SchemaDTO;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;

public class HXQLQueryConverterTest {

    private SchemaManagerDTO schemaManager;

    protected HXQLQueryConverter converter;

    @BeforeEach
    public void setUp() {
        Map<String, FieldDTO> fields = Map.of( //
                "sys_isFolderish", falseAsNullInStoreField(), //
                "sys_isTrashed", sparseBooleanField(), //
                "sys_acl", notIndexedField("string", false), //
                "sys_fulltext", syntheticField("string", true), //
                "sys_fulltextScore", syntheticField("double", false), //
                "sys_parentPath", field(false, false), //
                "sys_notindexed", notIndexedField("string", false), //
                "sys_nonqueryable", field(false, false), //
                "sys_nonprojectable", field(true, false));
        ContentModelDTO model = new ContentModelDTO(
                Map.of("system", new SchemaDTO(null, fields, false, "sys")), null, null, null);
        schemaManager = new SchemaManagerDTO(model, null, false);
        converter = new HXQLQueryConverter(schemaManager);
    }

    @Test
    public void testNonQueryableSystemFieldIsRejected() {
        var query = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_nonqueryable = 'value'");
        QueryParseException e = assertThrows(QueryParseException.class,
                () -> converter.toESQueryBuilder(query, null));
        assertEquals("Disallowed system property: sys_nonqueryable", e.getMessage());
    }

    @Test
    public void testForbiddenSystemFieldIsRejected() {
        var query = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_acl/*/principal = 'u:bob'");
        assertThrows(QueryParseException.class, () -> converter.toESQueryBuilder(query, null));
    }

    @Test
    public void testNonQueryableSystemFieldCannotBeSorted() {
        var query = HXQLParser.parse("SELECT * FROM SysContent ORDER BY sys_nonqueryable");
        assertThrows(QueryParseException.class, () -> converter.getSortInfo(query));
    }

    @Test
    public void testNonProjectableSystemFieldIsRejected() {
        var query = HXQLParser.parse("SELECT sys_nonprojectable FROM SysContent");
        QueryParseException e = assertThrows(QueryParseException.class, () -> converter.getSelectClauseFields(query));
        assertEquals("Disallowed system property: sys_nonprojectable", e.getMessage());
    }

    @Test
    public void testParentPathRemainsAvailableInOpenSearch() {
        var like = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_parentPath LIKE '/foo/%'");
        assertDoesNotThrow(() -> converter.toESQueryBuilder(like, null));

        var startsWith = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_parentPath STARTSWITH '/foo'");
        QueryParseException e = assertThrows(QueryParseException.class,
                () -> converter.toESQueryBuilder(startsWith, null));
        assertEquals("sys_parentPath does not support STARTSWITH", e.getMessage());

        var query = HXQLParser.parse("SELECT sys_parentPath FROM SysContent ORDER BY sys_parentPath");
        assertDoesNotThrow(() -> converter.getSelectClauseFields(query));
        assertDoesNotThrow(() -> converter.getSortInfo(query));
    }

    @Test
    public void testFolderishIsExplicitlyIndexed() {
        Field field = schemaManager.getField("sys_isFolderish");
        assertFalse(converter.isFalseAsNullInIndex(field));
    }

    @Test
    public void testFieldMissingFromOpenSearchIsRejected() {
        var query = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_notindexed = 'value'");
        QueryParseException e = assertThrows(QueryParseException.class,
                () -> converter.toESQueryBuilder(query, null));
        assertEquals("Disallowed system property: sys_notindexed", e.getMessage());
    }

    @Test
    public void testTrashedFieldUsesSparseIndex() {
        for (String value : List.of("TRUE", "FALSE")) {
            var query = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_isTrashed = " + value);
            assertDoesNotThrow(() -> converter.toESQueryBuilder(query, null));
        }
    }

    @Test
    public void testAclCompatibilityProjectionIsRejectedWhileNotIndexed() {
        var query = HXQLParser.parse("SELECT sys_acl/*/permission FROM SysContent");
        QueryParseException e = assertThrows(QueryParseException.class, () -> converter.getSelectClauseFields(query));
        assertEquals("Disallowed system property: sys_acl", e.getMessage());
    }

    @Test
    public void testSyntheticOpenSearchFieldsRemainAvailable() {
        var fulltext = HXQLParser.parse("SELECT * FROM SysContent WHERE sys_fulltext = 'value'");
        assertDoesNotThrow(() -> converter.toESQueryBuilder(fulltext, null));
        var score = HXQLParser.parse("SELECT sys_fulltextScore FROM SysContent");
        assertDoesNotThrow(() -> converter.getSelectClauseFields(score));
    }

    protected static FieldDTO field(boolean queryable, boolean projectable) {
        var characteristics = new FieldCharacteristics(true, null, !queryable, !projectable, false, null, null, null,
                null, null);
        return new FieldDTO("string", null, null, null, null, false, characteristics, null);
    }

    protected static FieldDTO falseAsNullInStoreField() {
        var characteristics = new FieldCharacteristics(true, null, false, false, false, Set.of(STORE), null, null, null,
                null);
        return new FieldDTO("boolean", null, null, null, null, false, characteristics, null);
    }

    protected static FieldDTO sparseBooleanField() {
        var characteristics = new FieldCharacteristics(true, null, false, false, false, Set.of(STORE, INDEX), null,
                null, null, null);
        return new FieldDTO("boolean", null, null, null, null, false, characteristics, null);
    }

    protected static FieldDTO notIndexedField(String type, boolean falseAsNullInStore) {
        var characteristics = new FieldCharacteristics(true, null, false, false, false,
                falseAsNullInStore ? Set.of(STORE) : null, Set.of(INDEX), null, null, null);
        return new FieldDTO(type, null, null, null, null, false, characteristics, null);
    }

    protected static FieldDTO syntheticField(String type, boolean notProjectable) {
        var characteristics = new FieldCharacteristics(true, QUERY_VIRTUAL, false, notProjectable, false, null,
                Set.of(REST, INDEX), null, null, null);
        return new FieldDTO(type, null, null, null, null, false, characteristics, null);
    }
}
