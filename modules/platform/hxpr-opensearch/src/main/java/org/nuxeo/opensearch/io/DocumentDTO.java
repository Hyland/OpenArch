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
package org.nuxeo.opensearch.io;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.DocumentModel;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.json.stream.JsonGenerator;

/**
 * DTO to communicate a DocumentModel to/from OpenSearch
 * <p>
 * Serialization is done through the fact that we inherit from {@link JsonpSerializable}, and the serialize() method.
 * <p>
 * Deserialization is done in a limited manner (only a few properties) using normal Jackson bindings. Then we can fetch
 * the whole document from the core.
 */
public class DocumentDTO implements JsonpSerializable {

    private final DocumentModel doc;

    private final Map<String, Object> properties;

    public DocumentDTO(DocumentModel doc) {
        this.doc = doc;
        properties = null;
    }

    // for test construction
    public DocumentDTO(Map<String, Object> properties) {
        this.doc = null;
        this.properties = new HashMap<>(properties);
    }

    // for Jackson construction
    public DocumentDTO() {
        this(Map.of());
    }

    @JsonAnySetter
    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }

    @JsonIgnore
    public Map<String, Object> getProperties() {
        return properties;
    }

    public int size(JsonpMapper mapper) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            serialize(generator, mapper);
            return writer.toString().length();
        }
    }

    @SuppressWarnings("resource")
    @Override
    public void serialize(JsonGenerator generator, JsonpMapper mapper) {
        if (doc != null) {
            new JsonOpenSearchDocumentWriter(generator, mapper).write(doc);
        } else {
            generator.writeStartObject();
            properties.forEach((key, value) -> {
                generator.writeKey(key);
                generator.write((String) value); // used in tests only, String is ok
            });
            generator.writeEnd();
        }
    }

}
