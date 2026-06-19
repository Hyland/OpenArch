/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.document;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Customizes JSON Object mapper.
 */
@Component
public class ObjectMapperConfiguration implements Jackson2ObjectMapperBuilderCustomizer, Ordered {

    @Override
    public void customize(Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder) {
        jacksonObjectMapperBuilder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
        jacksonObjectMapperBuilder.featuresToEnable(
                // use BigDecimal for input and output of floats to avoid losing precision
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS,
                MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        // dates written as timestamps will be converted to long (not Calendar) when converting back to DocumentDTO
        jacksonObjectMapperBuilder.modulesToInstall(new JavaTimeModule());
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
