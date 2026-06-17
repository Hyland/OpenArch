/*
 * (C) Copyright 2023-2025 Hyland (http://www.hyland.com/) and others.
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
 */
package org.nuxeo.opensearch.query;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.opensearch.client.json.jackson.JacksonJsonProvider;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.util.ObjectBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.json.stream.JsonGenerator;

@ExtendWith(MockitoExtension.class)
public class NxQueryBuilderTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(
            new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String EMBEDDING_TYPE = "embedding-type";

    @Mock
    private CoreSession session;

    @Mock
    private SchemaManager schemaManager;

    @Mock
    private NxqlQueryConverter nxqlQueryConverter;

    private NxQueryBuilder builder;

    @BeforeEach
    public void setup() {
        builder = new NxQueryBuilder(session, "repositoryName", nxqlQueryConverter, schemaManager);
    }

    @Test
    public void testSetTotalHitsTrackCountFalse() throws Exception {
        int countUpTo = 20;
        builder.trackTotalCountUpTo(countUpTo);
        builder.trackTotalCount(false);
        SearchRequest.Builder request = new SearchRequest.Builder();
        builder.setTotalHits(request);

        // When trackTotalCount is false and we omit countUpTo, the OpenSearch API assigns it with -1.
        assertFalse(request.build().trackTotalHits().enabled());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 20, Integer.MAX_VALUE })
    public void testSetTotalHitsTrackCountTrue(int countUpTo) {
        builder.trackTotalCountUpTo(countUpTo);
        builder.trackTotalCount(true);
        SearchRequest.Builder request = new SearchRequest.Builder();
        builder.setTotalHits(request);

        assertEquals(countUpTo, request.build().trackTotalHits().count());
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, Integer.MIN_VALUE })
    public void testSetTotalHitsTrackCountUpToBelowZero(int countUpTo) {
        // When setting trackTotalCountUpTo to a value of -1 and below, it should not be applied sp OpenSearch API can
        // provide no limit.
        builder.trackTotalCountUpTo(countUpTo);
        builder.trackTotalCount(true);
        SearchRequest.Builder request = new SearchRequest.Builder();
        builder.setTotalHits(request);

        // When trackTotalCount is true and we omit countUpTo, the OpenSearch API assigns it with Integer.MAX_VALUE.
        assertTrue(request.build().trackTotalHits().enabled());
    }

    @Test
    public void testDontSetTotalHitsTrackCountUpToTotalHitsTrue() {
        builder.trackTotalCount(true);
        SearchRequest.Builder request = new SearchRequest.Builder();
        builder.setTotalHits(request);

        assertTrue(request.build().trackTotalHits().enabled());
    }

    @Test
    public void testDontSetTotalHitsTrackCountUpToTotalHitsFalse() {
        builder.trackTotalCount(false);
        SearchRequest.Builder request = new SearchRequest.Builder();
        builder.setTotalHits(request);

        assertFalse(request.build().trackTotalHits().enabled());
    }

    @Test
    public void testMakeEmptyQuery() {
        Query query = builder.makeQuery();
        assertNull(query, "Expected a null query if no query is supplied");
    }

    @Test
    public void testMakeSelectStarQuery() {
        String nxql = "SELECT * FROM nuxeo";
        builder.nxql(nxql);
        Query osQuery = prepareBuilderToReturnOSQuery(nxql);

        Query query = builder.makeQuery();

        assertEquals(osQuery, query);
    }

    @Test
    public void testMakeSelectFieldQuery() {
        String nxql = "SELECT sys_name FROM nuxeo";
        builder.nxql(nxql);
        Query osQuery = prepareBuilderToReturnOSQuery(nxql);
        when(nxqlQueryConverter.getSelectClauseFields(nxql)).thenReturn(Map.of("sys_name", StringType.INSTANCE));

        Query query = builder.makeQuery();

        assertEquals(osQuery, query);
        assertEquals(List.of("sys_name"), Arrays.asList(builder.getSelectFields()));
    }


    @Test
    public void testIsEmbeddingQueryTrue() {
        String nxql = "SELECT * FROM SysContent";
        builder.embeddingIdQuery("myEmbeddingId", nxql);
        assertTrue(builder.isEmbeddingQuery());
    }

    @Test
    public void testEmbeddingIdQueryUsesOpenSearchResponseOnly() {
        String nxql = "SELECT * FROM SysContent";

        builder.embeddingIdQuery("myEmbeddingId", nxql);

        assertFalse(builder.returnsDocuments());
        assertFalse(builder.returnsRows());
    }

    @Test
    public void testIsEmbeddingQueryFalse() {
        String nxql = "SELECT * FROM SysContent";
        builder.embeddingIdQuery(null, nxql);
        assertFalse(builder.isEmbeddingQuery());
    }

    @Test
    public void testEmbeddingIdQueryNullKeepsDocumentMaterializationEnabled() {
        String nxql = "SELECT * FROM SysContent";

        builder.embeddingIdQuery(null, nxql);

        assertTrue(builder.returnsDocuments());
        assertFalse(builder.returnsRows());
    }

    @Test
    public void testMakeSelectWithEmbeddingId() {
        String nxql = "SELECT * FROM SysContent";
        String embeddingId = "myEmbeddingId";
        builder.embeddingIdQuery(embeddingId, nxql);
        Query nxqlQuery = prepareBuilderToReturnOSQuery(nxql);

        Query acutalQuery = builder.makeQuery();

        Query expectedEmbeddingIdQuery = QueryBuilders.match()
                                                      .field(EMBEDDING_PROPERTY_ID)
                                                      .query(FieldValue.of(embeddingId))
                                                      .build()
                                                      .toQuery();
        Query expectedQuery = QueryBuilders.bool().filter(expectedEmbeddingIdQuery, nxqlQuery).build().toQuery();

        assertEquals(serialiseQuery(expectedQuery), serialiseQuery(acutalQuery));
    }

    @Test
    public void testMakeSelectWithEmbeddingVector() {
        String nxql = "SELECT * FROM nuxeo";
        List<Double> embeddingVector = List.of(0.1, 0.2, 0.3);
        builder.embeddingQuery(EMBEDDING_TYPE, embeddingVector, null, nxql);
        Query nxqlQuery = prepareBuilderToReturnOSQuery(nxql);

        Query query = builder.makeQuery();

        Query filter = QueryBuilders.bool()
                                    .filter(QueryBuilders.wildcard()
                                                         .field(EMBEDDING_PROPERTY_TYPE)
                                                         .wildcard(EMBEDDING_TYPE)
                                                         .build()
                                                         .toQuery(),
                                            nxqlQuery)
                                    .build()
                                    .toQuery();
        Query expected = QueryBuilders.knn()
                                      .field(EMBEDDING_PROPERTY_VECTOR)
                                      .k(10)
                                      .filter(filter)
                                      .vector(embeddingVector.stream().map(Double::floatValue).toList())
                                      .build()
                                      .toQuery();
        assertEquals(serialiseQuery(expected), serialiseQuery(query));
    }

    @Test
    public void testIsHybridQueryTrue() {
        String nxql = "SELECT * FROM nuxeo";
        List<Double> embeddingVector = List.of(0.1, 0.2, 0.3);
        String chunkFtsQuery = "hello world";

        builder.embeddingQuery(EMBEDDING_TYPE, embeddingVector, chunkFtsQuery, nxql);

        assertTrue(builder.isHybridQuery());
    }

    @Test
    public void testEmbeddingVectorQueryUsesOpenSearchResponseOnly() {
        String nxql = "SELECT * FROM nuxeo";
        List<Double> embeddingVector = List.of(0.1, 0.2, 0.3);

        builder.embeddingQuery(EMBEDDING_TYPE, embeddingVector, null, nxql);

        assertFalse(builder.returnsDocuments());
        assertFalse(builder.returnsRows());
    }

    @Test
    public void testEmbeddingVectorQueryNullKeepsDocumentMaterializationEnabled() {
        String nxql = "SELECT * FROM nuxeo";

        builder.embeddingQuery(EMBEDDING_TYPE, null, null, nxql);

        assertFalse(builder.isEmbeddingQuery());
        assertTrue(builder.returnsDocuments());
        assertFalse(builder.returnsRows());
    }

    @Test
    public void testIsHybridQueryFalseWhenChunkQueryMissing() {
        String nxql = "SELECT * FROM nuxeo";
        List<Double> embeddingVector = List.of(0.1, 0.2, 0.3);

        builder.embeddingQuery(EMBEDDING_TYPE, embeddingVector, null, nxql);

        assertFalse(builder.isHybridQuery());
    }

    @Test
    public void testMakeSelectWithHybridEmbeddingQuery() {
        String nxql = "SELECT * FROM nuxeo";
        List<Double> embeddingVector = List.of(0.1, 0.2, 0.3);
        String chunkFtsQuery = "hello world";

        builder.embeddingQuery(EMBEDDING_TYPE, embeddingVector, chunkFtsQuery, nxql);
        prepareBuilderToReturnOSQuery(nxql);

        Query query = builder.makeQuery();

        assertNotNull(query);
        assertTrue(query.isHybrid(), "Expected hybrid OpenSearch query");
    }

    private Query prepareBuilderToReturnOSQuery(String nxql) {
        ObjectBuilder esQueryBuilder = mock(ObjectBuilder.class);
        when(nxqlQueryConverter.toESQueryBuilder(nxql, session)).thenReturn(esQueryBuilder);
        QueryVariant queryVariant = mock(QueryVariant.class);
        when(esQueryBuilder.build()).thenReturn(queryVariant);
        Query osQuery = mock(Query.class);
        when(queryVariant.toQuery()).thenReturn(osQuery);
        return osQuery;
    }

    private String serialiseQuery(Query query) {
        JacksonJsonpMapper jacksonJsonpMapper = new JacksonJsonpMapper(OBJECT_MAPPER);
        StringWriter writer = new StringWriter();
        JsonGenerator generator = JacksonJsonProvider.provider().createGenerator(writer);
        query.serialize(generator, jacksonJsonpMapper);
        generator.flush();
        return writer.toString();
    }
}
