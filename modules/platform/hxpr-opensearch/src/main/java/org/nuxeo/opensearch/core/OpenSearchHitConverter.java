/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.core;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_SCORE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.schema.types.SimpleType;
import com.hyland.openarch.ecm.core.schema.types.Type;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.opensearch.core.search.Hit;

/**
 * Converter used to convert a {@link SearchHit} to a {@link Map}&lt;{@link String}, {@link Object}&gt;.
 *
 * @since 8.4
 */
public class OpenSearchHitConverter {

    private final Map<String, Type> selectFieldsAndTypes;

    private final Map<String, Object> emptyRow;

    public OpenSearchHitConverter(Map<String, Type> selectFieldsAndTypes) {
        this.selectFieldsAndTypes = selectFieldsAndTypes;
        emptyRow = buildEmptyRow(selectFieldsAndTypes);
    }

    private static Map<String, Object> buildEmptyRow(Map<String, Type> selectFieldsAndTypes) {
        Map<String, Object> emptyRow = new HashMap<>(selectFieldsAndTypes.size());
        for (String fieldName : selectFieldsAndTypes.keySet()) {
            emptyRow.put(fieldName, null);
        }
        return emptyRow;
    }

    public List<Map<String, Object>> convert(List<Hit<DocumentDTO>> hits) {
        return hits.stream().map(this::convert).toList();
    }

    public Map<String, Object> convert(Hit<DocumentDTO> hit) {
        Map<String, Object> row = new HashMap<>(emptyRow);
        hit.source().getProperties().forEach((name, value) -> {
            // type conversion
            if (value instanceof String string) {
                Type type = selectFieldsAndTypes.get(name);
                if (type instanceof SimpleType st && st.getPrimitiveType() instanceof DateType) {
                    // convert back to calendar
                    value = type.decode((string));
                }
            }
            row.put(name, value);
        });
        if (selectFieldsAndTypes.containsKey(SYS_FULLTEXT_SCORE)) {
            row.put(SYS_FULLTEXT_SCORE, hit.score());
        }
        return row;
    }

}
