/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static com.hyland.openarch.ecm.core.schema.types.FieldCharacteristics.Representation.INDEX;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.nuxeo.ecm.core.SystemPropertyValues;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DecimalType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DoubleType;
import com.hyland.openarch.ecm.core.schema.types.primitives.LongType;
import org.opensearch.client.json.JsonpMapper;

import jakarta.json.stream.JsonGenerator;

/**
 * JSon writer that outputs a format ready to eat by opensearch.
 *
 * @since 5.9.3
 */
public class JsonOpenSearchDocumentWriter {

    private final JsonGenerator jg;

    private final JsonpMapper mapper;

    public JsonOpenSearchDocumentWriter(JsonGenerator jg, JsonpMapper mapper) {
        this.jg = jg;
        this.mapper = mapper;
    }

    public void write(DocumentModel doc) {
        jg.writeStartObject();
        writeSystemProperties(doc);
        writeProperties(doc);
        jg.writeEnd();
        jg.flush();
    }

    /**
     * @since 7.2
     */
    protected void writeSystemProperties(DocumentModel doc) {
        Map<String, Object> values = SystemPropertyValues.forOutput(doc, INDEX);
        sortList(values, SYS_MIXIN_TYPES, false);
        sortList(values, SYS_SECURITY_POLICIES, false);
        sortList(values, SYS_READ_ACL, true);
        values.remove(SYS_FULLTEXT_BINARY);
        values.forEach(this::writeValue);
        writeFulltextBinary(doc);
    }

    /**
     * Writes full-text binary content. Denormalized writers override this to omit it.
     */
    protected void writeFulltextBinary(DocumentModel doc) {
        String fulltext = (String) doc.getPropertyValue(SYS_FULLTEXT_BINARY);
        if (isNotBlank(fulltext)) {
            jg.write(SYS_FULLTEXT_BINARY, fulltext);
        }
    }

    protected void sortList(Map<String, Object> values, String name, boolean unique) {
        Object value = values.get(name);
        if (value instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) l;
            List<String> sorted = unique ? new ArrayList<>(new TreeSet<>(list)) : list.stream().sorted().toList();
            values.put(name, sorted);
        }
    }

    protected void writeValue(String name, Object value) {
        if (value == null || SYS_FULLTEXT_BINARY.equals(name) && value instanceof String s && !isNotBlank(s)) {
            return;
        }
        jg.writeKey(name);
        mapper.serialize(value, jg);
    }

    protected void writeProperties(DocumentModel doc) {
        try {
            doc.visitValues(new JsonGeneratorValuesVisitor(jg));
        } catch (ClassCastException e) {
            throw new NuxeoException("writing JSON property failed on document: %s".formatted(doc), e);
        }
    }

    public static class JsonGeneratorValuesVisitor implements ValuesVisitor {

        private final JsonGenerator jg;

        private int depth;

        // Managed fields are written separately; track nesting while suppressing their visitor subtree.
        private int skipDepth;

        public JsonGeneratorValuesVisitor(JsonGenerator jg) {
            this.jg = jg;
        }

        @Override
        public boolean visitActualBlobs() {
            return false;
        }

        @Override
        public String getXPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Comparator<String> keySorter() {
            return null;
        }

        @Override
        public void startObject() {
            if (skipDepth > 0) {
                return;
            }
            if (depth++ != 0) {
                jg.writeStartObject();
            }
        }

        @Override
        public void endObject() {
            if (skipDepth > 0) {
                return;
            }
            if (--depth != 0) {
                jg.writeEnd();
            }
        }

        @Override
        public void startObjectElement(Field field) {
            if (skipDepth > 0) {
                skipDepth++;
            } else if (field.isRepositoryManaged()) {
                skipDepth = 1;
            } else {
                jg.writeKey(field.getName());
            }
        }

        @Override
        public void endObjectElement() {
            if (skipDepth > 0) {
                skipDepth--;
            }
        }

        @Override
        public void startList() {
            if (skipDepth > 0) {
                return;
            }
            jg.writeStartArray();
        }

        @Override
        public void endList() {
            if (skipDepth > 0) {
                return;
            }
            jg.writeEnd();
        }

        @Override
        public void startListElement(int i) {
            // nothing
        }

        @Override
        public void endListElement() {
            // nothing
        }

        @Override
        public void value(Object value, Field field) {
            if (skipDepth > 0) {
                return;
            }
            Type type = field.getType();
            if (type instanceof BooleanType) {
                jg.write((Boolean) value);
            } else if (type instanceof LongType) {
                jg.write(((Number) value).longValue());
            } else if (type instanceof DoubleType) {
                jg.write(((Number) value).doubleValue());
            } else if (type instanceof DecimalType) {
                jg.write((BigDecimal) value);
            } else {
                jg.write(type.encode(value));
            }
        }

        @Override
        public void list(List<Object> list, Field listField, Field elemField) {
            if (skipDepth > 0) {
                return;
            }
            jg.writeStartArray();
            for (Object value : list) {
                value(value, elemField);
            }
            jg.writeEnd();
        }

        @Override
        public void blob(Blob blob, Object state) {
            throw new UnsupportedOperationException();
        }
    }

}
