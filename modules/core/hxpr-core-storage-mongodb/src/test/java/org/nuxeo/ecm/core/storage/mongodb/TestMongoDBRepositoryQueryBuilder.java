/*
 * (C) Copyright 2025 Hyland (http://www.hyland.com/) and others.
 */
package org.nuxeo.ecm.core.storage.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.primitives.AnyType;
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
}
