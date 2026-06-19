/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.rest.query.io.serializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

public class AggregationsBucketSerializerTest {

    private final AggregationsBucketSerializer serializer = new AggregationsBucketSerializer();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testSerializeEmptyList() throws IOException, JSONException {
        List<StringTermsBucket> buckets = new ArrayList<>();
        String json = serializeToJson(buckets);
        String expected = """
                []
                """;
        JSONAssert.assertEquals(expected, json, true);
    }

    @Test
    public void testSerializeSingleBucket() throws IOException, JSONException {
        List<StringTermsBucket> buckets = List.of(createBucket("objectId", 5L));
        String json = serializeToJson(buckets);
        String expected = """
                [
                    {
                        "key": "objectId",
                        "docCount": 5
                    }
                ]
                """;
        JSONAssert.assertEquals(expected, json, true);
    }

    @Test
    public void testSerializeMultipleBuckets() throws IOException, JSONException {
        List<StringTermsBucket> buckets = List.of(
                createBucket("objectId", 10L),
                createBucket("sourceId", 7L),
                createBucket("userId", 3L));
        String json = serializeToJson(buckets);
        String expected = """
                [
                    {
                        "key": "objectId",
                        "docCount": 10
                    },
                    {
                        "key": "sourceId",
                        "docCount": 7
                    },
                    {
                        "key": "userId",
                        "docCount": 3
                    }
                ]
                """;
        JSONAssert.assertEquals(expected, json, true);
    }

    @Test
    public void testSerializeWithSpecialCharacters() throws IOException, JSONException {
        List<StringTermsBucket> buckets = List.of(
                createBucket("user.name", 2L),
                createBucket("data-field", 1L));
        String json = serializeToJson(buckets);
        String expected = """
                [
                    {
                        "key": "user.name",
                        "docCount": 2
                    },
                    {
                        "key": "data-field",
                        "docCount": 1
                    }
                ]
                """;
        JSONAssert.assertEquals(expected, json, true);
    }

    private String serializeToJson(List<StringTermsBucket> buckets) throws IOException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(writer);
        SerializerProvider provider = mapper.getSerializerProvider();
        serializer.serialize(buckets, jg, provider);
        jg.flush();
        return writer.toString();
    }

    private StringTermsBucket createBucket(String key, long docCount) {
        return StringTermsBucket.of(b -> b.key(key).docCount(docCount));
    }
}
