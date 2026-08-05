/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Customizes JSON Object mapper.
 */
@Component
public class ObjectMapperConfiguration implements JsonMapperBuilderCustomizer, Ordered {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
               .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
               .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
               // Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default; restore the Jackson 2 behaviour where a
               // missing or null value for a primitive DTO property becomes the Java default instead of failing.
               .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
               .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
               .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
               .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
               .configure(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        // dates written as timestamps will be converted to long (not Calendar) when converting back to DocumentDTO
    }

    /*
     * Default config is order 0. We want to be sure that this class is executed after default config so that this
     * config is merged with, and not overriding the default config.
     */
    @Override
    public int getOrder() {
        return 1;
    }
}
