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
package org.nuxeo.opensearch.test.util;

import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_BLOB_REF;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_ID;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_BOTTOM;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_COLUMN;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_END;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_LEFT;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_PAGE;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_PARAGRAPH;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_POSITION;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_RIGHT;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_ROW;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_SHEET;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_SPREADSHEET;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_START;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TEXT;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TIMESTAMP;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TOP;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_TEXT;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_TYPE;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_VECTOR;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.impl.blob.FileBlob;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.io.LocalOutputFile;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.ecm.core.model.LocationModel;

/**
 * Utility class for generating test Parquet files with embeddings without Hadoop dependencies.
 * <p>
 * This class provides multiple methods to generate Parquet blobs:
 * <ul>
 * <li>{@link #generate(int, String)} - Generate test embeddings with sequential IDs</li>
 * <li>{@link #generateOneEmbedding(String, String, boolean)} - Generate a single embedding</li>
 * <li>{@link #generateVariantUpdate(EmbeddingVariant, int, String)} - Generate embeddings for update scenarios</li>
 * <li>{@link #generateFromEmbeddingModels(List)} - Generate from a list of EmbeddingModel objects</li>
 * </ul>
 * </p>
 */
public class ParquetBlobGenerator {

    private static final String EMBEDDING_SCHEMA_PATH = "schemas/embedding.avsc";

    private static final String EMBEDDING_ID_PREFIX = "embedding-";

    private static final String EMBEDDING_UPDATED_PREFIX = "embedding-updated-";

    private static final Schema EMBEDDING_SCHEMA;

    /**
     * Minimal Avro schema for the {@code sysemb_chunks} parquet: only {@code id} and {@code text}, keyed by
     * embedding id.
     */
    private static final Schema CHUNK_TEXT_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "Chunk",
              "namespace": "com.hyland.hxpr.embeddings.avro",
              "fields": [
                { "name": "id", "type": "string" },
                { "name": "text", "type": ["null", "string"], "default": null }
              ]
            }
            """);

    public static final String PARQUET_FILE_EXTENSION = ".parquet";

    public static final String PARQUET_FILE_MIME_TYPE = "application/x-parquet";

    static {
        try (InputStream is = Thread.currentThread()
                                    .getContextClassLoader()
                                    .getResourceAsStream(EMBEDDING_SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Embedding schema not found: " + EMBEDDING_SCHEMA_PATH);
            }
            EMBEDDING_SCHEMA = new Schema.Parser().parse(is);
        } catch (IOException e) {
            throw new NuxeoException("Failed to load embedding schema", e);
        }
    }

    /**
     * Generates a Parquet blob with test embeddings.
     *
     * @param count number of embeddings to generate
     * @param type the embedding type
     * @return a Blob containing the Parquet data
     * @throws IOException if an I/O error occurs
     */
    public Blob generate(int count, String type) throws IOException {
        return generate(count, type, false);
    }

    /**
     * Creates the backing blob for a generated parquet. Uses {@link Blobs#createBlobWithExtension} by default so
     * tests running under the Nuxeo runtime get file tracking; subclasses may override to create a plain
     * {@link FileBlob} when the runtime isn't available.
     */
    protected Blob newParquetBlob() throws IOException {
        Blob blob = Blobs.createBlobWithExtension(PARQUET_FILE_EXTENSION);
        blob.setMimeType(PARQUET_FILE_MIME_TYPE);
        return blob;
    }

    /**
     * Exposes the loaded embedding Avro schema for subclasses that write parquet directly.
     */
    protected static Schema embeddingSchema() {
        return EMBEDDING_SCHEMA;
    }

    /**
     * Exposes the chunk-text Avro schema for subclasses that write parquet directly.
     */
    protected static Schema chunkTextSchema() {
        return CHUNK_TEXT_SCHEMA;
    }

    public String getRawEmbeddingIdForIndex(int index) {
        return EMBEDDING_ID_PREFIX + index;
    }

    /**
     * Generates a Parquet blob with one embedding having the given id and type and an optional blob reference value.
     */
    public Blob generateOneEmbedding(String embeddingRawId, String type, boolean withBlobRef) throws IOException {
        Blob blob = newParquetBlob();

        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(EMBEDDING_SCHEMA)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {

            writer.write(createEmbeddingRecord(embeddingRawId, type, EmbeddingVariant.INITIAL, 0,
                    withBlobRef ? buildBlobRef(embeddingRawId, type) : null));
        }

        return blob;
    }

    /**
     * Generates a Parquet blob with test embeddings and an optional blob reference value.
     */
    public Blob generate(int count, String type, boolean withBlobRef) throws IOException {
        Blob blob = newParquetBlob();

        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(EMBEDDING_SCHEMA)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {

            for (int i = 0; i < count; i++) {
                String rawId = getRawEmbeddingIdForIndex(i + 1);
                writer.write(createEmbeddingRecord(rawId, type, EmbeddingVariant.INITIAL, i,
                        withBlobRef ? buildBlobRef(rawId, type) : null));
            }
        }

        return blob;
    }

    /**
     * Generates a Parquet blob representing an update of embeddings for various variant strategies.
     */
    public Blob generateVariantUpdate(EmbeddingVariant variant, int count, String embeddingsType) throws IOException {
        return generateVariantUpdate(variant, count, embeddingsType, false);
    }

    /**
     * Generates a Parquet blob representing an update of embeddings for various variant strategies. Allows optionally
     * emitting a blob reference field to validate schema compatibility.
     */
    public Blob generateVariantUpdate(EmbeddingVariant variant, int count, String embeddingsType, boolean withBlobRef)
            throws IOException {
        Blob blob = newParquetBlob();

        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(EMBEDDING_SCHEMA)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {
            switch (variant) {
            case SAME:
                // Use the same raw IDs as initial generation to ensure deterministic scoped UUIDs
                for (int i = 0; i < count; i++) {
                    String rawId = getRawEmbeddingIdForIndex(i + 1);
                    writer.write(createEmbeddingRecord(rawId, embeddingsType, variant, i,
                            withBlobRef ? buildBlobRef(rawId, embeddingsType) : null));
                }
                break;
            case COMBINED:
                int half = count / 2;
                // first half reuse same raw IDs as initial generation
                for (int i = 0; i < half; i++) {
                    String rawId = getRawEmbeddingIdForIndex(i + 1);
                    writer.write(createEmbeddingRecord(rawId, embeddingsType, EmbeddingVariant.COMBINED_REUSED, i,
                            withBlobRef ? buildBlobRef(rawId, embeddingsType) : null));
                }
                // second half new raw ids
                for (int i = half; i < count; i++) {
                    String rawId = EMBEDDING_UPDATED_PREFIX + (i + 1 - half);
                    writer.write(createEmbeddingRecord(rawId, embeddingsType, EmbeddingVariant.COMBINED_NEW, i,
                            withBlobRef ? buildBlobRef(rawId, embeddingsType) : null));
                }
                break;
            case NEW:
            default:
                for (int i = 0; i < count; i++) {
                    String rawId = EMBEDDING_UPDATED_PREFIX + (i + 1);
                    writer.write(createEmbeddingRecord(rawId, embeddingsType, variant, i,
                            withBlobRef ? buildBlobRef(rawId, embeddingsType) : null));
                }
                break;
            }
        }

        return blob;
    }

    /** Create a record honoring the existing Avro schema. */
    protected GenericRecord createEmbeddingRecord(String rawId, String embeddingsType, EmbeddingVariant variant,
            int index) {
        return createEmbeddingRecord(rawId, embeddingsType, variant, index, null);
    }

    protected GenericRecord createEmbeddingRecord(String rawId, String embeddingsType, EmbeddingVariant variant,
            int index, String blobRef) {

        GenericRecord emb = new GenericData.Record(EMBEDDING_SCHEMA);
        emb.put(EMBEDDING_FIELD_ID, rawId);
        emb.put(EMBEDDING_FIELD_TYPE, embeddingsType);

        // Handle text generation based on variant
        String text;
        if (variant == EmbeddingVariant.INITIAL) {
            text = embeddingsType + " - " + rawId;
        } else {
            text = variant.name().toLowerCase().replace('_', '-') + " - " + rawId;
        }
        emb.put(EMBEDDING_FIELD_TEXT, text);
        emb.put(EMBEDDING_FIELD_BLOB_REF, blobRef);

        // Handle vector generation based on variant
        List<Double> vector = new ArrayList<>();
        double baseMultiplier = (variant == EmbeddingVariant.INITIAL) ? 0.1 : 0.2;
        for (int j = 0; j < 4; j++) {
            vector.add(baseMultiplier * (index + 1) + 0.01 * j);
        }
        emb.put(EMBEDDING_FIELD_VECTOR, vector);
        emb.put(EMBEDDING_FIELD_LOCATION, createLocationRecord(index));
        return emb;
    }

    protected String buildBlobRef(String rawId, String embeddingsType) {
        return "blob:" + embeddingsType + ":" + rawId;
    }

    /**
     * Generates an embeddings parquet with the two-file shape: no {@code text} column. Same ids as
     * {@link #generate(int, String)} so it can be joined against {@link #generateChunkTexts(int, java.util.function.IntFunction)}.
     */
    public Blob generateWithoutText(int count, String type) throws IOException {
        Schema noTextSchema = buildEmbeddingSchemaWithoutText();
        Blob blob = newParquetBlob();
        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(noTextSchema)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {
            for (int i = 0; i < count; i++) {
                GenericRecord rec = new GenericData.Record(noTextSchema);
                String rawId = getRawEmbeddingIdForIndex(i + 1);
                rec.put(EMBEDDING_FIELD_ID, rawId);
                rec.put(EMBEDDING_FIELD_TYPE, type);
                List<Double> vector = new ArrayList<>();
                for (int j = 0; j < 4; j++) vector.add(0.1 * (i + 1) + 0.01 * j);
                rec.put(EMBEDDING_FIELD_VECTOR, vector);
                rec.put(EMBEDDING_FIELD_LOCATION, createLocationRecord(i));
                writer.write(rec);
            }
        }
        return blob;
    }

    private Schema buildEmbeddingSchemaWithoutText() {
        List<Schema.Field> kept = new ArrayList<>();
        for (Schema.Field f : EMBEDDING_SCHEMA.getFields()) {
            if (EMBEDDING_FIELD_TEXT.equals(f.name())) continue;
            Schema.Field copy = new Schema.Field(f.name(), f.schema(), f.doc(), f.defaultVal());
            kept.add(copy);
        }
        return Schema.createRecord(EMBEDDING_SCHEMA.getName() + "NoText", EMBEDDING_SCHEMA.getDoc(),
                EMBEDDING_SCHEMA.getNamespace(), false, kept);
    }

    /**
     * Generates a chunk-text parquet blob ({@code sysemb_chunks} shape) whose rows carry only {@code id} and
     * {@code text}. Ids match the raw ids used by {@link #generate(int, String)} so the two blobs can be joined
     * by the parser.
     *
     * @param count number of chunk rows
     * @param textForIndex function that produces the text to write for a given zero-based index
     */
    public Blob generateChunkTexts(int count, java.util.function.IntFunction<String> textForIndex) throws IOException {
        Blob blob = newParquetBlob();
        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(CHUNK_TEXT_SCHEMA)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {
            for (int i = 0; i < count; i++) {
                GenericRecord rec = new GenericData.Record(CHUNK_TEXT_SCHEMA);
                rec.put(EMBEDDING_FIELD_ID, getRawEmbeddingIdForIndex(i + 1));
                rec.put(EMBEDDING_FIELD_TEXT, textForIndex.apply(i));
                writer.write(rec);
            }
        }
        return blob;
    }

    /**
     * Generates a chunk-text parquet from an explicit {@code rawId -> text} map. Use when a test wants to
     * exercise partial coverage (some ids from the embeddings blob missing from the chunks blob).
     */
    public Blob generateChunkTextsFromMap(Map<String, String> rawIdToText) throws IOException {
        Blob blob = newParquetBlob();
        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(CHUNK_TEXT_SCHEMA)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {
            for (Map.Entry<String, String> e : rawIdToText.entrySet()) {
                GenericRecord rec = new GenericData.Record(CHUNK_TEXT_SCHEMA);
                rec.put(EMBEDDING_FIELD_ID, e.getKey());
                rec.put(EMBEDDING_FIELD_TEXT, e.getValue());
                writer.write(rec);
            }
        }
        return blob;
    }

    /**
     * Generates a Parquet blob from a list of EmbeddingModel.
     *
     * @param embeddings the list of embeddings to convert to Parquet format
     * @return a Blob containing the Parquet data
     * @throws IOException if an I/O error occurs
     */
    public Blob generateFromEmbeddingModels(List<EmbeddingModel> embeddings) throws IOException {
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalArgumentException("Embeddings list cannot be null or empty");
        }
        Blob blob = newParquetBlob();

        try (var writer = AvroParquetWriter.<GenericRecord> builder(new LocalOutputFile(blob.getFile().toPath()))
                                           .withSchema(EMBEDDING_SCHEMA)
                                           .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                                           .build()) {

            for (EmbeddingModel embedding : embeddings) {
                writer.write(createEmbeddingRecordFromModel(embedding));
            }
        }

        return blob;
    }

    /**
     * Creates an Avro GenericRecord from an EmbeddingModel.
     */
    protected GenericRecord createEmbeddingRecordFromModel(EmbeddingModel model) {
        GenericRecord emb = new GenericData.Record(EMBEDDING_SCHEMA);

        // Map the basic fields - use the raw id from the model
        emb.put(EMBEDDING_FIELD_ID, model.id());
        emb.put(EMBEDDING_FIELD_TYPE, model.type());
        emb.put(EMBEDDING_FIELD_TEXT, model.text());
        emb.put(EMBEDDING_FIELD_BLOB_REF, model.blobRef());
        emb.put(EMBEDDING_FIELD_VECTOR, model.vector());

        // Map the location if present
        if (model.location() != null) {
            emb.put(EMBEDDING_FIELD_LOCATION, createLocationRecordFromModel(model.location()));
        }

        return emb;
    }

    /**
     * Creates a location GenericRecord from a LocationModel.
     */
    protected GenericRecord createLocationRecordFromModel(LocationModel locationModel) {
        Schema locSchema = EMBEDDING_SCHEMA.getField(EMBEDDING_FIELD_LOCATION).schema();
        GenericRecord location = new GenericData.Record(locSchema);

        // Map text location
        if (locationModel.text() != null) {
            Schema textLocSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_TEXT).schema());
            GenericRecord textLoc = new GenericData.Record(textLocSchema);
            textLoc.put(EMBEDDING_FIELD_LOCATION_PAGE, locationModel.text().page());
            textLoc.put(EMBEDDING_FIELD_LOCATION_PARAGRAPH, locationModel.text().paragraph());
            location.put(EMBEDDING_FIELD_LOCATION_TEXT, textLoc);
        }

        // Map position (image location)
        if (locationModel.position() != null) {
            Schema posSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_POSITION).schema());
            GenericRecord pos = new GenericData.Record(posSchema);
            pos.put(EMBEDDING_FIELD_LOCATION_LEFT, locationModel.position().left());
            pos.put(EMBEDDING_FIELD_LOCATION_TOP, locationModel.position().top());
            pos.put(EMBEDDING_FIELD_LOCATION_RIGHT, locationModel.position().right());
            pos.put(EMBEDDING_FIELD_LOCATION_BOTTOM, locationModel.position().bottom());
            location.put(EMBEDDING_FIELD_LOCATION_POSITION, pos);
        }

        // Map timestamp (video location)
        if (locationModel.timestamp() != null) {
            Schema tsSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_TIMESTAMP).schema());
            GenericRecord ts = new GenericData.Record(tsSchema);
            ts.put(EMBEDDING_FIELD_LOCATION_START, locationModel.timestamp().start());
            ts.put(EMBEDDING_FIELD_LOCATION_END, locationModel.timestamp().end());
            location.put(EMBEDDING_FIELD_LOCATION_TIMESTAMP, ts);
        }

        // Map spreadsheet location
        if (locationModel.spreadsheet() != null) {
            Schema sheetSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_SPREADSHEET).schema());
            GenericRecord sheetLoc = new GenericData.Record(sheetSchema);
            sheetLoc.put(EMBEDDING_FIELD_LOCATION_SHEET, locationModel.spreadsheet().sheet());
            sheetLoc.put(EMBEDDING_FIELD_LOCATION_ROW, locationModel.spreadsheet().row());
            sheetLoc.put(EMBEDDING_FIELD_LOCATION_COLUMN, locationModel.spreadsheet().column());
            location.put(EMBEDDING_FIELD_LOCATION_SPREADSHEET, sheetLoc);
        }

        return location;
    }

    /**
     * Extracts the non-null schema from a union type. For union types like ["null", {...record...}], returns the record
     * schema.
     */
    private Schema getNonNullSchema(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema type : schema.getTypes()) {
                if (type.getType() != Schema.Type.NULL) {
                    return type;
                }
            }
        }
        return schema;
    }

    /**
     * Creates a location record matching the embedding.avsc schema. Uses index to vary some values for tests.
     */
    private GenericRecord createLocationRecord(int index) {
        Schema locSchema = EMBEDDING_SCHEMA.getField(EMBEDDING_FIELD_LOCATION).schema();
        GenericRecord location = new GenericData.Record(locSchema);

        // text (TextLocation)
        Schema textLocSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_TEXT).schema());
        GenericRecord textLoc = new GenericData.Record(textLocSchema);
        textLoc.put(EMBEDDING_FIELD_LOCATION_PAGE, 1 + (index % 5)); // vary page modestly
        textLoc.put(EMBEDDING_FIELD_LOCATION_PARAGRAPH, 1 + (index % 3));
        location.put(EMBEDDING_FIELD_LOCATION_TEXT, textLoc);

        // position (Position)
        Schema posSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_POSITION).schema());
        GenericRecord pos = new GenericData.Record(posSchema);
        int base = 100 + (index * 5); // slight variation
        pos.put(EMBEDDING_FIELD_LOCATION_LEFT, base);
        pos.put(EMBEDDING_FIELD_LOCATION_TOP, base + 100);
        pos.put(EMBEDDING_FIELD_LOCATION_RIGHT, base + 200);
        pos.put(EMBEDDING_FIELD_LOCATION_BOTTOM, base + 140);
        location.put(EMBEDDING_FIELD_LOCATION_POSITION, pos);

        // timestamp (Timestamp)
        Schema tsSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_TIMESTAMP).schema());
        GenericRecord ts = new GenericData.Record(tsSchema);
        ts.put(EMBEDDING_FIELD_LOCATION_START, 10.0 + index);
        ts.put(EMBEDDING_FIELD_LOCATION_END, 15.0 + index);
        location.put(EMBEDDING_FIELD_LOCATION_TIMESTAMP, ts);

        // spreadsheet (SpreadsheetLocation)
        Schema sheetSchema = getNonNullSchema(locSchema.getField(EMBEDDING_FIELD_LOCATION_SPREADSHEET).schema());
        GenericRecord sheetLoc = new GenericData.Record(sheetSchema);
        sheetLoc.put(EMBEDDING_FIELD_LOCATION_SHEET, "Sheet" + (index % 2 + 1));
        sheetLoc.put(EMBEDDING_FIELD_LOCATION_ROW, 1 + index);
        sheetLoc.put(EMBEDDING_FIELD_LOCATION_COLUMN, 1 + (index % 10));
        location.put(EMBEDDING_FIELD_LOCATION_SPREADSHEET, sheetLoc);

        return location;
    }

    /**
     * Enum for different embedding generation variants.
     */
    public enum EmbeddingVariant {
        /** Initial generation with original scaling and simple text format */
        INITIAL,
        /** Update with same raw IDs as initial generation */
        SAME,
        /** Update with entirely new raw IDs */
        NEW,
        /** Combined update with mix of existing and new raw IDs */
        COMBINED,
        /** Reused IDs in combined variant */
        COMBINED_REUSED,
        /** New IDs in combined variant */
        COMBINED_NEW
    }
}
