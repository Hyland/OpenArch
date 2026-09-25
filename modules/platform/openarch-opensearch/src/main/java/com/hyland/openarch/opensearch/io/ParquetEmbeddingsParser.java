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
package com.hyland.openarch.opensearch.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.io.LocalInputFile;
import com.hyland.openarch.common.utils.IdUtils;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.CloseableFile;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.ecm.core.model.LocationModel;

/**
 * Reads a Parquet embeddings blob and returns a collection of {@link EmbeddingModel} objects.
 */
public class ParquetEmbeddingsParser implements EmbeddingsParser {

    private static final Logger log = LogManager.getLogger(ParquetEmbeddingsParser.class);

    // Field name constants for Parquet schema
    private static final String FIELD_ID = "id";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_BLOBREF = "blobref";

    // TODO: Overload the parse method to accept a schema as input for dynamic parsing
    @Override
    public Collection<EmbeddingModel> parse(Blob blob, String repositoryName, String parentDocId, String embeddingsType) {
        if (blob == null) {
            log.debug("Parse skip: null blob (parentId={} type={})", parentDocId, embeddingsType);
            return List.of();
        }
        long len = blob.getLength();
        String mt = blob.getMimeType();
        if (len == 0) {
            log.debug("Parse skip: empty blob length=0 (parentId={} type={})", parentDocId, embeddingsType);
            return List.of();
        }
        List<EmbeddingModel> embeddings = new ArrayList<>();
        try (CloseableFile closeableFile = blob.getCloseableFile()) {
            var inputFile = new LocalInputFile(closeableFile.getFile().toPath());

            try (var reader = AvroParquetReader.<GenericRecord> builder(inputFile).build()) {
                for (GenericRecord emb = reader.read(); emb != null; emb = reader.read()) {
                    embeddings.add(toEmbeddingModel(emb, repositoryName, parentDocId, embeddingsType));
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Parse failure treated as empty (parentId={} type={} mimeType={} length={}): {}", parentDocId,
                    embeddingsType, mt, len, e.toString());
            return List.of();
        }
        return embeddings;
    }

    @Override
    public Map<String, String> parseChunkTexts(Blob chunksBlob, String embeddingsType) {
        if (chunksBlob == null) {
            return Map.of();
        }
        long len = chunksBlob.getLength();
        if (len == 0) {
            log.debug("parseChunkTexts skip: empty blob length=0 (type={})", embeddingsType);
            return Map.of();
        }
        Map<String, String> chunks = new HashMap<>();
        try (CloseableFile closeableFile = chunksBlob.getCloseableFile()) {
            var inputFile = new LocalInputFile(closeableFile.getFile().toPath());
            try (var reader = AvroParquetReader.<GenericRecord> builder(inputFile).build()) {
                GenericRecord first = reader.read();
                if (first == null) {
                    return Map.of();
                }
                if (first.getSchema().getField(FIELD_ID) == null || first.getSchema().getField(FIELD_TEXT) == null) {
                    log.debug("parseChunkTexts skip: id/text field missing from schema (type={})", embeddingsType);
                    return Map.of();
                }
                for (GenericRecord rec = first; rec != null; rec = reader.read()) {
                    String id = toScopedId(rec.get(FIELD_ID), embeddingsType);
                    String text = toNullableString(rec.get(FIELD_TEXT));
                    chunks.put(id, text);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Impact: with the chunks blob present but unreadable, the two-blob parse will overlay an empty map
            // and every embedding for this document ends up with null text. Elevated to error so ops sees it.
            log.error("parseChunkTexts failure - indexed text will be null for all rows (type={} mimeType={} length={})",
                    embeddingsType, chunksBlob.getMimeType(), len, e);
            return Map.of();
        }
        return chunks;
    }

    protected EmbeddingModel toEmbeddingModel(GenericRecord emb, String repositoryName, String parentDocId,
            String embeddingsType) {
        String id = toScopedId(emb.get(FIELD_ID), embeddingsType);
        // Gate on schema presence: Avro throws if the field isn't declared, and the two-file shape omits it.
        String text = null;
        if (emb.getSchema().getField(FIELD_TEXT) != null) {
            text = toNullableString(emb.get(FIELD_TEXT));
        }
        List<Double> vector = toVector(emb.get(FIELD_VECTOR));
        LocationModel location = toLocation(emb.get(FIELD_LOCATION));
        String blobRef = null;
        if (emb.getSchema().getField(FIELD_BLOBREF) != null) {
            Object rawBlobRef = emb.get(FIELD_BLOBREF);
            blobRef = toNullableString(rawBlobRef);
        }
        return new EmbeddingModel(id, repositoryName, parentDocId, embeddingsType, vector, text, location, blobRef);
    }

    protected String toScopedId(Object value, String embeddingsType) {
        String raw = toStringValue(value);
        if (raw == null) {
            return UUID.randomUUID().toString();
        }
        if (IdUtils.isValidUUID(raw)) {
            // Assume caller already provided a globally unique identifier
            return raw;
        }
        // Combine embeddingsType and raw id to avoid collisions among siblings, then derive deterministic UUID.
        String composite = embeddingsType + ":" + raw; // stable across re-parses
        return UUID.nameUUIDFromBytes(composite.getBytes(StandardCharsets.UTF_8)).toString();
    }

    protected List<Double> toVector(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof GenericArray<?> array) {
            List<Double> vector = new ArrayList<>(array.size());
            for (Object o : array) {
                vector.add(toDouble(o));
            }
            return vector;
        } else if (value instanceof Collection<?> collection) {
            List<Double> vector = new ArrayList<>(collection.size());
            for (Object o : collection) {
                vector.add(toDouble(o));
            }
            return vector;
        }
        throw new IllegalArgumentException("Unsupported vector type: " + value.getClass());
    }

    Double toDouble(Object o) {
        if (o instanceof Number number) {
            return number.doubleValue();
        }
        if (o instanceof GenericData.Record record) {
            return toDouble(record.get("element"));
        }
        throw new IllegalArgumentException("Unsupported vector element type: " + o.getClass());
    }

    protected String toNullableString(Object value) {
        // Returns null to preserve the semantic difference between a missing value and an empty string.
        // Used for optional textual fields ("text") so downstream consumers can distinguish
        // between: (a) value not present (null) and (b) explicitly present but empty ("").
        if (value == null) {
            return null;
        }
        return toStringValue(value);
    }

    protected LocationModel toLocation(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocationModel locationModel) {
            return locationModel;
        }
        if (value instanceof GenericRecord record) {
            return LocationModel.from(recordToMap(record));
        }
        if (value instanceof Map<?, ?> map) {
            return LocationModel.from(normalizeMap(map));
        }
        if (value instanceof CharSequence sequence) {
            return LocationModel.from(sequence.toString());
        }
        log.debug("Unsupported Parquet location type {}, ignoring", value.getClass());
        return null;
    }

    protected Map<String, Object> recordToMap(GenericRecord record) {
        Map<String, Object> map = new HashMap<>();
        for (var field : record.getSchema().getFields()) {
            Object fieldValue = record.get(field.name());
            Object normalized = normalizeValue(fieldValue);
            if (normalized != null) {
                map.put(field.name(), normalized);
            }
        }
        return map;
    }

    protected Map<String, Object> normalizeMap(Map<?, ?> map) {
        Map<String, Object> normalized = new HashMap<>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() != null) {
                Object normalizedValue = normalizeValue(entry.getValue());
                if (normalizedValue != null) {
                    normalized.put(entry.getKey().toString(), normalizedValue);
                }
            }
        }
        return normalized;
    }

    protected Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof GenericRecord nestedRecord) {
            return recordToMap(nestedRecord);
        }
        if (value instanceof GenericArray<?> array) {
            List<Object> list = new ArrayList<>(array.size());
            for (Object element : array) {
                Object normalized = normalizeValue(element);
                if (normalized != null) {
                    list.add(normalized);
                }
            }
            return list;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object element : collection) {
                Object normalized = normalizeValue(element);
                if (normalized != null) {
                    list.add(normalized);
                }
            }
            return list;
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Utf8 utf8) {
            return utf8.toString();
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        return value;
    }

    protected String toStringValue(Object value) {
        if (value == null) {
            return ""; // deliberate: callers relying on guaranteed non-null (e.g. ID generation)
        }
        return value instanceof Utf8 ? value.toString() : String.valueOf(value);
    }


}
