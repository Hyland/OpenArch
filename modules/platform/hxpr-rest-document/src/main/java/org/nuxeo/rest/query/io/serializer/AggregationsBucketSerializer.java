/*
 * (C) Copyright 2025 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.rest.query.io.serializer;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class AggregationsBucketSerializer extends JsonSerializer<List<StringTermsBucket>> {

    private static final String KEY_FIELD = "key";

    private static final String DOC_COUNT_FIELD = "docCount";

    @Override
    public void serialize(List<StringTermsBucket> stringTermsBuckets, JsonGenerator jg,
                          SerializerProvider serializerProvider) throws IOException {
        jg.writeStartArray();
        for (StringTermsBucket bucket : stringTermsBuckets) {
            jg.writeStartObject();
            jg.writeStringField(KEY_FIELD, bucket.key());
            jg.writeNumberField(DOC_COUNT_FIELD, bucket.docCount());
            jg.writeEndObject();
        }
        jg.writeEndArray();
    }
}
