/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_PATH;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBConverter.DEFAULT_CONVERTER;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.IntegerLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics;
import com.hyland.openarch.ecm.core.schema.types.primitives.AnyType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.storage.ExpressionEvaluator.PathResolver;

/**
 * Tests for {@link MongoDBRepositoryQueryBuilder}.
 */
public class TestMongoDBRepositoryQueryBuilder {

    @Test
    public void testNonProjectableFieldIsRejected() {
        SchemaManager schemaManager = mock(SchemaManager.class);
        PathResolver pathResolver = mock(PathResolver.class);
        Field field = mock(Field.class);
        doReturn("sys_path").when(field).getName();
        doReturn(new FieldCharacteristics(true, null, false, true, false, null, null, null, null, null)).when(field)
                                                                                     .getCharacteristics();
        doReturn(field).when(schemaManager).getField("sys_path");
        SelectClause select = new SelectClause(List.of(new Reference("sys_path")));
        MongoDBRepositoryQueryBuilder qb = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, null, select, null,
                pathResolver, schemaManager);

        QueryParseException e = assertThrows(QueryParseException.class, qb::walkProjection);
        assertEquals("Disallowed system property: sys_path", e.getMessage());
    }

    @Test
    public void testParentPathPredicateUsesParentId() {
        SchemaManager schemaManager = mock(SchemaManager.class);
        PathResolver pathResolver = mock(PathResolver.class);
        when(pathResolver.getIdForPath("/foo")).thenReturn("parent-id");
        Predicate predicate = new Predicate(new Reference(SYS_PARENT_PATH), Operator.EQ, new StringLiteral("/foo"));
        MongoDBRepositoryQueryBuilder qb = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, predicate, null, null,
                pathResolver, schemaManager);

        qb.walkTopLevelExpression();
        assertEquals(new Document("sys_parentId", "parent-id"), qb.getQuery());

        Predicate badOperator = new Predicate(new Reference(SYS_PARENT_PATH), Operator.LIKE,
                new StringLiteral("/foo%"));
        qb = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, badOperator, null, null, pathResolver, schemaManager);
        assertThrows(QueryParseException.class, qb::walkTopLevelExpression);
    }

    @Test
    public void testWalkReference_SubPropertyOfAny() {
        // Given a repository query builder with mocked dependencies

        SchemaManager schemaManager = mock(SchemaManager.class);
        PathResolver pathResolver = mock(PathResolver.class);

        // Field "anyTypeProperty" exists in schema and is of type Any
        Field field = mock(Field.class);
        doReturn("anyTypeProperty").when(field).getName();
        doReturn(AnyType.INSTANCE).when(field).getType();
        doReturn(field).when(schemaManager).getField("anyTypeProperty");

        // Build a simple expression: anyTypeProperty/Key1 = 'val1'
        Reference left = new Reference("anyTypeProperty/Key1");
        StringLiteral right = new StringLiteral("val1");
        Predicate expr = new Predicate(left, Operator.EQ, right);

        MongoDBRepositoryQueryBuilder qb = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, expr, /* select */ null,
                /* order */ null, pathResolver, schemaManager);

        // When walking equality on that reference
        Document doc = qb.walkEq(left, right);

        // Then the generated query should reference the nested sub-property under the Any-typed field
        // Expected MongoDB field: "anyTypeProperty.Key1"
        assertEquals(1, doc.size());
        assertEquals("val1", doc.get("anyTypeProperty.Key1"));
    }

    @Test
    public void testWalkBooleanLiteral_OnBooleanField() {
        // Given a repository query builder with a Boolean-typed schema field

        SchemaManager schemaManager = mock(SchemaManager.class);
        PathResolver pathResolver = mock(PathResolver.class);

        Field field = mock(Field.class);
        doReturn("active").when(field).getName();
        doReturn(BooleanType.INSTANCE).when(field).getType();
        doReturn(field).when(schemaManager).getField("active");

        Reference left = new Reference("active");

        // Predicate: active = TRUE
        Predicate exprTrue = new Predicate(left, Operator.EQ, BooleanLiteral.LIT_TRUE);
        MongoDBRepositoryQueryBuilder qbTrue = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, exprTrue, null,
                null, pathResolver, schemaManager);
        Document docTrue = qbTrue.walkEq(left, BooleanLiteral.LIT_TRUE);

        // Predicate: active = 1 (legacy form, must produce same query)
        Predicate exprOne = new Predicate(left, Operator.EQ, new IntegerLiteral(1L));
        MongoDBRepositoryQueryBuilder qbOne = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, exprOne, null, null,
                pathResolver, schemaManager);
        Document docOne = qbOne.walkEq(left, new IntegerLiteral(1L));

        assertEquals(docOne, docTrue);
        assertEquals(Boolean.TRUE, docTrue.get("active"));

        // Predicate: active = FALSE
        Predicate exprFalse = new Predicate(left, Operator.EQ, BooleanLiteral.LIT_FALSE);
        MongoDBRepositoryQueryBuilder qbFalse = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, exprFalse, null,
                null, pathResolver, schemaManager);
        Document docFalse = qbFalse.walkEq(left, BooleanLiteral.LIT_FALSE);

        // Predicate: active = 0
        Predicate exprZero = new Predicate(left, Operator.EQ, new IntegerLiteral(0L));
        MongoDBRepositoryQueryBuilder qbZero = new MongoDBRepositoryQueryBuilder(DEFAULT_CONVERTER, exprZero, null,
                null, pathResolver, schemaManager);
        Document docZero = qbZero.walkEq(left, new IntegerLiteral(0L));

        assertEquals(docZero, docFalse);
        assertEquals(Boolean.FALSE, docFalse.get("active"));
    }
}
