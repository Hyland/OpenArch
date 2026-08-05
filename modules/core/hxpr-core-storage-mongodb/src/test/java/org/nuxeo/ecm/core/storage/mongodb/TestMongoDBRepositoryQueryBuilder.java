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
package org.nuxeo.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.primitives.AnyType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.storage.ExpressionEvaluator.PathResolver;

/**
 * Tests for {@link MongoDBRepositoryQueryBuilder}.
 */
public class TestMongoDBRepositoryQueryBuilder {

    @Test
    public void testWalkReference_SubPropertyOfAny() {
        // Given a repository query builder with mocked dependencies
        MongoDBRepository repository = mock(MongoDBRepository.class);
        doReturn(new MongoDBConverter()).when(repository).getConverter();

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
        Expression expr = new Expression(left, Operator.EQ, right);

        MongoDBRepositoryQueryBuilder qb = new MongoDBRepositoryQueryBuilder(repository, expr, /* select */ null,
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
        MongoDBRepository repository = mock(MongoDBRepository.class);
        doReturn(new MongoDBConverter()).when(repository).getConverter();

        SchemaManager schemaManager = mock(SchemaManager.class);
        PathResolver pathResolver = mock(PathResolver.class);

        Field field = mock(Field.class);
        doReturn("active").when(field).getName();
        doReturn(BooleanType.INSTANCE).when(field).getType();
        doReturn(field).when(schemaManager).getField("active");

        Reference left = new Reference("active");

        // Predicate: active = TRUE
        Expression exprTrue = new Expression(left, Operator.EQ, BooleanLiteral.LIT_TRUE);
        MongoDBRepositoryQueryBuilder qbTrue = new MongoDBRepositoryQueryBuilder(repository, exprTrue, null, null,
                pathResolver, schemaManager);
        Document docTrue = qbTrue.walkEq(left, BooleanLiteral.LIT_TRUE);

        // Predicate: active = 1 (legacy form, must produce same query)
        Expression exprOne = new Expression(left, Operator.EQ, new IntegerLiteral(1L));
        MongoDBRepositoryQueryBuilder qbOne = new MongoDBRepositoryQueryBuilder(repository, exprOne, null, null,
                pathResolver, schemaManager);
        Document docOne = qbOne.walkEq(left, new IntegerLiteral(1L));

        assertEquals(docOne, docTrue);
        assertEquals(Boolean.TRUE, docTrue.get("active"));

        // Predicate: active = FALSE
        Expression exprFalse = new Expression(left, Operator.EQ, BooleanLiteral.LIT_FALSE);
        MongoDBRepositoryQueryBuilder qbFalse = new MongoDBRepositoryQueryBuilder(repository, exprFalse, null, null,
                pathResolver, schemaManager);
        Document docFalse = qbFalse.walkEq(left, BooleanLiteral.LIT_FALSE);

        // Predicate: active = 0
        Expression exprZero = new Expression(left, Operator.EQ, new IntegerLiteral(0L));
        MongoDBRepositoryQueryBuilder qbZero = new MongoDBRepositoryQueryBuilder(repository, exprZero, null, null,
                pathResolver, schemaManager);
        Document docZero = qbZero.walkEq(left, new IntegerLiteral(0L));

        assertEquals(docZero, docFalse);
        assertEquals(Boolean.FALSE, docFalse.get("active"));
    }
}
